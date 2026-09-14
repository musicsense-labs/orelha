"""Gera um WAV sintético (Am F C G, 120 BPM, baixo na fundamental) para fixtures e smoke tests.

    python -m app.testaudio out/progression.wav
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

SR = 44100
BPM = 120
BEATS_PER_CHORD = 4
# Am F C G em pcs (A=9, F=5, C=0, G=7): tríades como offsets de semitons.
PROGRESSION = [(9, (0, 3, 7)), (5, (0, 4, 7)), (0, (0, 4, 7)), (7, (0, 4, 7))]
LOOPS = 2


def midi_hz(midi: float) -> float:
    return 440.0 * 2 ** ((midi - 69) / 12)


def tone(freq: float, seconds: float, amp: float, harmonics: int = 6) -> np.ndarray:
    t = np.arange(int(seconds * SR)) / SR
    y = sum((amp / h) * np.sin(2 * np.pi * freq * h * t) for h in range(1, harmonics + 1))
    env = np.minimum(1.0, np.minimum(t / 0.01, (seconds - t) / 0.05))
    return y * env


def render() -> np.ndarray:
    beat = 60.0 / BPM
    chunks = []
    for _ in range(LOOPS):
        for root, intervals in PROGRESSION:
            chord = np.zeros(int(beat * BEATS_PER_CHORD * SR))
            for interval in intervals:
                # Registro de guitarra (C3–C4): é onde o chroma_low procura a terça.
                chord += tone(midi_hz(48 + root + interval), beat * BEATS_PER_CHORD, 0.15)
            for b in range(BEATS_PER_CHORD):
                bass = tone(midi_hz(36 + root), beat, 0.4, harmonics=3)
                kick = tone(55, 0.1, 0.5, harmonics=1) * np.exp(-np.arange(int(0.1 * SR)) / (0.02 * SR))
                start = int(b * beat * SR)
                chord[start:start + len(bass)] += bass
                chord[start:start + len(kick)] += kick
            chunks.append(chord)
    y = np.concatenate(chunks)
    return (y / np.abs(y).max() * 0.9).astype(np.float32)


if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "progression.wav")
    out.parent.mkdir(parents=True, exist_ok=True)
    sf.write(out, render(), SR)
    print(out)
