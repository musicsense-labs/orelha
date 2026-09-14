import logging
import os
from pathlib import Path

import librosa
import pyloudnorm

from . import MODELS, VERSION
from .bass import transcribe_bass
from .beats import track_beats
from .chords import recognize_chords
from .chroma import chroma_per_segment
from .key import estimate_key
from .stems import separate_stems
from .timbre import timbre_summaries

log = logging.getLogger(__name__)

FEATURES_DIR = Path(os.environ.get("FEATURES_DIR", "/data/features"))


def analyze(audio_path: Path, audio_sha256: str, work_dir: Path) -> dict:
    y, sr = librosa.load(audio_path, sr=None, mono=True)
    duration_s = float(len(y) / sr)
    lufs = float(pyloudnorm.Meter(sr).integrated_loudness(y))

    log.info("stems")
    stems = separate_stems(audio_path, work_dir / "stems")
    log.info("chords")
    chords = recognize_chords(audio_path, work_dir / "chords")
    log.info("chroma")
    chromas = chroma_per_segment(y, sr, [(c["start_s"], c["end_s"]) for c in chords])
    for chord, chroma in zip(chords, chromas):
        chord["chroma"] = chroma
    log.info("beats")
    beats, bpm, time_signature = track_beats(audio_path)
    log.info("key")
    key = estimate_key(audio_path)
    log.info("bass")
    bass_notes = transcribe_bass(stems["bass"])
    log.info("timbre")
    FEATURES_DIR.mkdir(parents=True, exist_ok=True)
    features_path = FEATURES_DIR / f"{audio_sha256}.parquet"
    timbre = timbre_summaries(stems, "htdemucs", features_path)

    return {
        "extractor": {"name": "riff-extractor", "version": VERSION, "models": MODELS},
        "audio": {"duration_s": round(duration_s, 3), "sample_rate": int(sr), "integrated_lufs": round(lufs, 2)},
        "key": key,
        "tempo": {"bpm": bpm, "time_signature": time_signature},
        "beats": beats,
        "chords": chords,
        "bass_notes": bass_notes,
        "timbre": timbre,
        "features_path": str(features_path),
    }
