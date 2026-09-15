import logging
import os
import shutil
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool

from . import MODELS, VERSION
from .pipeline import analyze

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("orelha-extractor")

WORK_DIR = Path(os.environ.get("WORK_DIR", "/tmp/orelha-extractor"))

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
