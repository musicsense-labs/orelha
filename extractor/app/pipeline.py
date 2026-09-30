import contextlib
import logging
import os
import time
from pathlib import Path

import librosa
import pyloudnorm

from . import MODELS, VERSION
from .notes import transcribe_bass, transcribe_vocals
from .beats import track_beats
from .chords import recognize_chords
from .chroma import chroma_per_segment
from .key import estimate_key
from .lyrics import transcribe_lyrics
from .stems import STEM_FORMAT, codec_label, persist_stems, separate_stems
from .timbre import timbre_summaries

log = logging.getLogger(__name__)


@contextlib.contextmanager
def step(name: str, timings: dict):
    """Cronometra uma etapa: é o que diz onde o tempo vai (o total é minutos e as etapas são desiguais)."""
    started = time.monotonic()
    try:
        yield
    finally:
        timings[name] = round(time.monotonic() - started, 1)
        log.info("%s: %.1fs", name, timings[name])

STEMS_DIR = Path(os.environ.get("STEMS_DIR", "/data/stems"))


def analyze(audio_path: Path, audio_sha256: str, work_dir: Path) -> dict:
    y, sr = librosa.load(audio_path, sr=None, mono=True)
    duration_s = float(len(y) / sr)
    lufs = float(pyloudnorm.Meter(sr).integrated_loudness(y))

    timings: dict[str, float] = {}
    with step("stems", timings):
        stems = separate_stems(audio_path, work_dir / "stems")      # WAV temporário: entrada das análises
    with step("chords", timings):
        chords = recognize_chords(audio_path, work_dir / "chords")
    with step("chroma", timings):
        spans = [(c["start_s"], c["end_s"]) for c in chords]
        for chord, chroma in zip(chords, chroma_per_segment(y, sr, spans)):
            chord["chroma"] = chroma
    with step("beats", timings):
        beats, bpm, time_signature = track_beats(audio_path)
    with step("key", timings):
        key = estimate_key(audio_path)
    with step("bass", timings):
        bass_notes = transcribe_bass(stems["bass"])
    with step("vocals", timings):
        vocal_notes = transcribe_vocals(stems["vocals"])
    with step("lyrics", timings):
        lyrics = transcribe_lyrics(stems["vocals"])
    with step(f"encode-{STEM_FORMAT}", timings):
        persisted = persist_stems(stems, STEMS_DIR / audio_sha256)  # o que a UI toca
    with step("timbre", timings):
        timbre = timbre_summaries(stems, "htdemucs")
    log.info("tempos por etapa: %s", timings)

    return {
        "extractor": {"name": "orelha-extractor", "version": VERSION,
                      "models": {**MODELS, "stems_codec": codec_label()}},
        "audio": {"duration_s": round(duration_s, 3), "sample_rate": int(sr), "integrated_lufs": round(lufs, 2)},
        "key": key,
        "tempo": {"bpm": bpm, "time_signature": time_signature},
        "beats": beats,
        "chords": chords,
        "bass_notes": bass_notes,
        "vocal_notes": vocal_notes,
        "lyrics": lyrics,
        "timbre": timbre,
        "stems": {name: str(path) for name, path in sorted(persisted.items())},
    }
