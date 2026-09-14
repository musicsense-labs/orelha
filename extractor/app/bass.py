"""Transcrição do stem de baixo para notas MIDI com basic-pitch."""
from pathlib import Path

from basic_pitch import ICASSP_2022_MODEL_PATH
from basic_pitch.inference import predict

# Faixa do baixo elétrico de 4/5 cordas (B0 ≈ 31 Hz) até o registro agudo usado em solos.
MIN_HZ = 30.0
MAX_HZ = 500.0


def transcribe_bass(bass_wav: Path) -> list[dict]:
    _, _, note_events = predict(str(bass_wav), model_or_model_path=ICASSP_2022_MODEL_PATH,
                                minimum_frequency=MIN_HZ, maximum_frequency=MAX_HZ)
    notes = [
        {"start_s": round(float(start), 3), "end_s": round(float(end), 3),
         "midi": int(pitch), "velocity": max(1, min(127, int(round(amplitude * 127))))}
        for start, end, pitch, amplitude, _bends in note_events
        if end > start
    ]
    notes.sort(key=lambda n: (n["start_s"], n["midi"]))
    return notes
