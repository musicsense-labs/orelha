"""Separação de stems com demucs (htdemucs: drums, bass, other, vocals).

Os stems são persistidos em STEMS_DIR/<sha256>/<stem>.wav — o backend os serve para o player
multi-stem da UI e os reusa para timbre/baixo; nada de teoria musical aqui.
"""
from pathlib import Path

from demucs.api import Separator, save_audio

_separator: Separator | None = None


def _get_separator() -> Separator:
    global _separator
    if _separator is None:
        _separator = Separator(model="htdemucs", device="cpu", progress=False)
    return _separator


def separate_stems(audio_path: Path, out_dir: Path) -> dict[str, Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    separator = _get_separator()
    _, separated = separator.separate_audio_file(audio_path)
    paths = {}
    for name, tensor in separated.items():
        path = out_dir / f"{name}.wav"
        save_audio(tensor, path, samplerate=separator.samplerate)
        paths[name] = path
    return paths
