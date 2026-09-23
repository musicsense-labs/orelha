import logging
import os
import shutil
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool

from . import MODELS, VERSION
from .pipeline import analyze

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s", force=True)
# O uvicorn troca os handlers do root ao subir; sem isto os tempos por etapa do pipeline não saem no log.
logging.getLogger("app").setLevel(logging.INFO)
log = logging.getLogger("orelha-extractor")

WORK_DIR = Path(os.environ.get("WORK_DIR", "/tmp/orelha-extractor"))

# Quantas threads o torch pode usar neste processo. Com vários workers do uvicorn (EXTRACTOR_WORKERS), cada
# um pediria por padrão metade dos núcleos lógicos e os processos brigariam pela mesma CPU; dividir o total
# pelo número de workers mantém a soma no tamanho da máquina. TORCH_THREADS manda, quando definido.
def _torch_threads() -> int | None:
    explicit = os.environ.get("TORCH_THREADS")
    if explicit:
        return max(1, int(explicit))
    workers = max(1, int(os.environ.get("EXTRACTOR_WORKERS", "1")))
    if workers == 1:
        return None   # um processo só: o torch e o CTranslate2 escolhem melhor que nós (medido em 2026-09-23)
    return max(2, (os.cpu_count() or 4) // workers)


_threads = _torch_threads()
if _threads:
    os.environ.setdefault("OMP_NUM_THREADS", str(_threads))   # madmom/numpy/ctranslate2 leem daqui
    try:
        import torch

        torch.set_num_threads(_threads)
        log.info("torch com %d threads (EXTRACTOR_WORKERS=%s)", _threads, os.environ.get("EXTRACTOR_WORKERS", "1"))
    except Exception as exc:   # pragma: no cover - torch sempre existe na imagem
        log.warning("não deu para limitar as threads do torch: %s", exc)

app = FastAPI(title="orelha-extractor", version=VERSION)


@app.get("/health")
def health():
    return {"status": "ok", "version": VERSION, "models": MODELS}


@app.post("/analyze")
async def analyze_endpoint(file: UploadFile = File(...), audio_sha256: str = Form(...)):
    """Síncrono de propósito: o Spring já enfileira e faz polling; aqui uma faixa por vez."""
    if len(audio_sha256) != 64:
        raise HTTPException(status_code=400, detail="audio_sha256 must be 64 hex chars")
    job_dir = Path(tempfile.mkdtemp(prefix=audio_sha256[:12] + "-", dir=WORK_DIR))
    # Nome neutro: títulos com pontos ("N.I.B..mp3"), espaços ou unicode já derrubaram o ChordMini.
    suffix = Path(file.filename or "").suffix.lower() or ".audio"
    audio_path = job_dir / f"audio{suffix}"
    try:
        with audio_path.open("wb") as out:
            shutil.copyfileobj(file.file, out)
        log.info("analyzing %s (%s)", audio_path.name, audio_sha256[:12])
        return await run_in_threadpool(analyze, audio_path, audio_sha256, job_dir)
    except Exception as e:  # noqa: BLE001 — o chamador precisa da mensagem, não do stack
        log.exception("analysis failed")
        raise HTTPException(status_code=500, detail=f"{type(e).__name__}: {e}") from e
    finally:
        shutil.rmtree(job_dir, ignore_errors=True)
