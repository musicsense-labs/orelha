"""Descritores espectrais por stem, agregados por faixa (as séries por frame não saem daqui desde a 0.7.0)."""
from pathlib import Path

import librosa
import numpy as np

N_FFT = 2048
HOP = 512


def timbre_summaries(stems: dict[str, Path], stem_model: str) -> list[dict]:
    summaries = []
    for name, path in sorted(stems.items()):
        y, sr = librosa.load(path, sr=None, mono=True)
        S = np.abs(librosa.stft(y, n_fft=N_FFT, hop_length=HOP))
        centroid = librosa.feature.spectral_centroid(S=S, sr=sr)[0]
        flatness = librosa.feature.spectral_flatness(S=S)[0]
        rolloff = librosa.feature.spectral_rolloff(S=S, sr=sr, roll_percent=0.95)[0]
        rms = librosa.feature.rms(S=S)[0]

        # Frames quase silenciosos distorcem centroide/rolloff; agrega só onde há sinal.
        active = rms > 0.01 * rms.max() if rms.max() > 0 else np.zeros_like(rms, dtype=bool)
        summaries.append({
            "stem_model": stem_model,
            "stem": name,
            "centroid_mean": _stat(np.mean, centroid[active]),
            "centroid_std": _stat(np.std, centroid[active]),
            "flatness_mean": _stat(np.mean, flatness[active]),
            "rolloff_p95": _stat(lambda v: np.percentile(v, 95), rolloff[active]),
            "rms_mean": _stat(np.mean, rms),
        })
    return summaries


def _stat(fn, values: np.ndarray) -> float | None:
    return round(float(fn(values)), 4) if len(values) else None
