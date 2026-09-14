"""Tonalidade global com a CNN do madmom (24 classes maior/menor)."""
from pathlib import Path

from madmom.features.key import CNNKeyRecognitionProcessor, key_prediction_to_label

_NOTE_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def estimate_key(audio_path: Path) -> dict:
    prediction = CNNKeyRecognitionProcessor()(str(audio_path))
    label = key_prediction_to_label(prediction)  # ex.: 'Bb major', 'C# minor'
    note, mode = label.split()
    pc = _NOTE_PC[note[0]] + {"#": 1, "b": -1}.get(note[1:], 0)
    return {"tonic_pc": pc % 12, "mode": mode, "confidence": round(float(prediction.max()), 4)}
