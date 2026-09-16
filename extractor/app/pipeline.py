import logging
import os
from pathlib import Path

import librosa
import pyloudnorm

from . import MODELS, VERSION
from .notes import transcribe_bass, transcribe_vocals
from .beats import track_beats
from .chords import recognize_chords
from .chroma import chroma_low_per_segment, chroma_per_segment
from .key import estimate_key
from .lyrics import transcribe_lyrics
from .stems import STEM_FORMAT, codec_label, persist_stems, separate_stems
from .timbre import timbre_summaries

log = logging.getLogger(__name__)

FEATURES_DIR = Path(os.environ.get("FEATURES_DIR", "/data/features"))
STEMS_DIR = Path(os.environ.get("STEMS_DIR", "/data/stems"))


def analyze(audio_path: Path, audio_sha256: str, work_dir: Path) -> dict:
    y, sr = librosa.load(audio_path, sr=None, mono=True)
    duration_s = float(len(y) / sr)
    lufs = float(pyloudnorm.Meter(sr).integrated_loudness(y))

    log.info("stems")
    stems = separate_stems(audio_path, work_dir / "stems")          # WAV temporário: entrada das análises
    log.info("chords")
    chords = recognize_chords(audio_path, work_dir / "chords")
    log.info("chroma")
    spans = [(c["start_s"], c["end_s"]) for c in chords]
    guitars, guitars_sr = librosa.load(stems["other"], sr=None, mono=True)
    for chord, chroma, chroma_low in zip(chords, chroma_per_segment(y, sr, spans),
                                         chroma_low_per_segment(guitars, guitars_sr, spans)):
        chord["chroma"] = chroma
        chord["chroma_low"] = chroma_low
    log.info("beats")
    beats, bpm, time_signature = track_beats(audio_path)
    log.info("key")
    key = estimate_key(audio_path)
    log.info("bass")
    bass_notes = transcribe_bass(stems["bass"])
    log.info("vocals")
    vocal_notes = transcribe_vocals(stems["vocals"])
    log.info("lyrics")
    lyrics = transcribe_lyrics(stems["vocals"])
    log.info("stems: encoding as %s", STEM_FORMAT)
    persisted = persist_stems(stems, STEMS_DIR / audio_sha256)      # o que a UI toca
    log.info("timbre")
    FEATURES_DIR.mkdir(parents=True, exist_ok=True)
    features_path = FEATURES_DIR / f"{audio_sha256}.parquet"
    timbre = timbre_summaries(stems, "htdemucs", features_path)

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
        "features_path": str(features_path),
        "stems": {name: str(path) for name, path in sorted(persisted.items())},
    }
