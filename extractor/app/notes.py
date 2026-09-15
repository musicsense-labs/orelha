"""Transcrição de um stem para notas MIDI com basic-pitch (baixo e voz). Só notas: zero teoria aqui."""
from pathlib import Path

from basic_pitch import ICASSP_2022_MODEL_PATH
from basic_pitch.inference import predict

# Baixo elétrico de 4/5 cordas (B0 ≈ 31 Hz) até o registro agudo usado em solos.
BASS_MIN_HZ = 30.0
BASS_MAX_HZ = 500.0
# Voz cantada: de um baixo grave (E2 ≈ 82 Hz) ao agudo de soprano (C6 ≈ 1047 Hz); harmônicos acima disso
# viram notas fantasmas, por isso o teto.
VOCAL_MIN_HZ = 80.0
VOCAL_MAX_HZ = 1100.0


def transcribe(stem_wav: Path, min_hz: float, max_hz: float) -> list[dict]:
    _, _, note_events = predict(str(stem_wav), model_or_model_path=ICASSP_2022_MODEL_PATH,
                                minimum_frequency=min_hz, maximum_frequency=max_hz)
    notes = [
        {"start_s": round(float(start), 3), "end_s": round(float(end), 3),
         "midi": int(pitch), "velocity": max(1, min(127, int(round(amplitude * 127))))}
        for start, end, pitch, amplitude, _bends in note_events
        if end > start
    ]
    notes.sort(key=lambda n: (n["start_s"], n["midi"]))
    return notes


def transcribe_bass(bass_wav: Path) -> list[dict]:
    return transcribe(bass_wav, BASS_MIN_HZ, BASS_MAX_HZ)


def transcribe_vocals(vocals_wav: Path) -> list[dict]:
    return transcribe(vocals_wav, VOCAL_MIN_HZ, VOCAL_MAX_HZ)
