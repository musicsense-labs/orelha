"""Chroma médio por segmento de acorde (12 classes, C = 0). Evidência para o Java decidir power chord."""
import librosa
import numpy as np

SR = 22050
HOP = 512


def chroma_per_segment(y: np.ndarray, sr: int, segments: list[tuple[float, float]]) -> list[list[float]]:
    y22 = librosa.resample(y, orig_sr=sr, target_sr=SR) if sr != SR else y
    chroma = librosa.feature.chroma_cqt(y=y22, sr=SR, hop_length=HOP)  # (12, frames), cada frame normalizado a 1
    times = librosa.frames_to_time(np.arange(chroma.shape[1]), sr=SR, hop_length=HOP)
    result = []
    for start, end in segments:
        mask = (times >= start) & (times < end)
        if not mask.any():
            mask = np.abs(times - start).argmin() == np.arange(len(times))
        result.append([round(float(v), 4) for v in chroma[:, mask].mean(axis=1)])
    return result
