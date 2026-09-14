"""Beats e downbeats com madmom (RNN + DBN). position = posição no compasso, 1 = downbeat."""
from pathlib import Path

import numpy as np
from madmom.features.downbeats import DBNDownBeatTrackingProcessor, RNNDownBeatProcessor

FPS = 100


def track_beats(audio_path: Path) -> tuple[list[dict], float | None, str | None]:
    activations = RNNDownBeatProcessor()(str(audio_path))
    tracked = DBNDownBeatTrackingProcessor(beats_per_bar=[3, 4], fps=FPS)(activations)
    beats = [{"time_s": round(float(t), 3), "position": int(p)} for t, p in tracked]
    if len(beats) < 2:
        return beats, None, None
    intervals = np.diff([b["time_s"] for b in beats])
    bpm = round(float(60.0 / np.median(intervals)), 2)
    beats_per_bar = max(b["position"] for b in beats)
    return beats, bpm, f"{beats_per_bar}/4"
