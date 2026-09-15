"""Separação de stems com demucs (htdemucs: drums, bass, other, vocals).

A separação sai em WAV num diretório de trabalho — é o que basic-pitch, chroma e timbre leem, sem
perda. O que fica no volume persistente (STEMS_DIR/<sha256>/<stem>.<ext>) é a versão codificada
para o player da UI, no formato STEM_FORMAT (opus por padrão: ~11× menor que WAV).
"""
import os
import subprocess
from pathlib import Path

from demucs.api import Separator, save_audio

STEM_FORMAT = os.environ.get("STEM_FORMAT", "opus")

# formato -> (extensão, argumentos do ffmpeg)
CODECS = {
    "opus": ("ogg", ["-c:a", "libopus", "-b:a", "128k"]),
    "aac": ("m4a", ["-c:a", "aac", "-b:a", "192k"]),
    "flac": ("flac", ["-c:a", "flac"]),
    "wav": ("wav", ["-c:a", "pcm_s16le"]),
}

_separator: Separator | None = None


def _get_separator() -> Separator:
    global _separator
    if _separator is None:
        _separator = Separator(model="htdemucs", device="cpu", progress=False)
    return _separator


def separate_stems(audio_path: Path, work_dir: Path) -> dict[str, Path]:
    """Stems em WAV (sem perda) no diretório de trabalho; entrada das análises por stem."""
    work_dir.mkdir(parents=True, exist_ok=True)
    separator = _get_separator()
    _, separated = separator.separate_audio_file(audio_path)
    paths = {}
    for name, tensor in separated.items():
        path = work_dir / f"{name}.wav"
        save_audio(tensor, path, samplerate=separator.samplerate)
        paths[name] = path
    return paths


def persist_stems(wavs: dict[str, Path], out_dir: Path, fmt: str = STEM_FORMAT) -> dict[str, Path]:
    """Codifica os WAVs para o volume persistente, no formato pedido."""
    if fmt not in CODECS:
        raise ValueError(f"STEM_FORMAT desconhecido: {fmt} (use {', '.join(CODECS)})")
    ext, args = CODECS[fmt]
    out_dir.mkdir(parents=True, exist_ok=True)
    encoded = {}
    for name, wav in wavs.items():
        target = out_dir / f"{name}.{ext}"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), *args, str(target)],
                       check=True, capture_output=True, text=True)
        encoded[name] = target
    return encoded


def codec_label(fmt: str = STEM_FORMAT) -> str:
    """Para a proveniência: 'opus@128k', 'aac@192k', 'flac', 'wav'."""
    _, args = CODECS[fmt]
    return fmt + ("@" + args[args.index("-b:a") + 1] if "-b:a" in args else "")
