"""Chroma médio por segmento de acorde (12 classes, C = 0).

chroma      — mixagem inteira, todas as oitavas: perfil geral do segmento.
chroma_low  — stem de guitarra (demucs 'other'), só C2–F4: evidência para o Java decidir power chord.
              Numa pestana em forma de E (fundamental E2–C♯3) a terça tocada cai em G♯3–F4; o 5º
              harmônico da fundamental, que imita uma terça maior sob distorção, cai em G♯4 ou acima
              e fica fora da janela.
"""
import librosa
import numpy as np

SR = 22050
HOP = 512
LOW_FMIN = librosa.note_to_hz("C2")  # ≈ 65 Hz
LOW_BINS = 30                        # C2 … F4 (30 semitons), 1 bin por semitom


def chroma_per_segment(y: np.ndarray, sr: int, segments: list[tuple[float, float]]) -> list[list[float]]:
    y22 = _resample(y, sr)
    chroma = librosa.feature.chroma_cqt(y=y22, sr=SR, hop_length=HOP)  # (12, frames), cada frame normalizado a 1
    return _average(chroma, segments)


def chroma_low_per_segment(y: np.ndarray, sr: int, segments: list[tuple[float, float]]) -> list[list[float]]:
    y22 = _resample(y, sr)
    cqt = np.abs(librosa.cqt(y=y22, sr=SR, hop_length=HOP, fmin=LOW_FMIN, n_bins=LOW_BINS, bins_per_octave=12))
    chroma = np.zeros((12, cqt.shape[1]))
    for b in range(LOW_BINS):          # bin 0 = C2, logo bin % 12 é a classe de altura
        chroma[b % 12] += cqt[b]
    peak = chroma.max(axis=0, keepdims=True)
    chroma = np.divide(chroma, peak, out=np.zeros_like(chroma), where=peak > 0)
    return _average(chroma, segments)


def _resample(y: np.ndarray, sr: int) -> np.ndarray:
    return librosa.resample(y, orig_sr=sr, target_sr=SR) if sr != SR else y


def _average(chroma: np.ndarray, segments: list[tuple[float, float]]) -> list[list[float]]:
    times = librosa.frames_to_time(np.arange(chroma.shape[1]), sr=SR, hop_length=HOP)
    result = []
    for start, end in segments:
        mask = (times >= start) & (times < end)
        if not mask.any():
            mask = np.abs(times - start).argmin() == np.arange(len(times))
        result.append([round(float(v), 4) for v in chroma[:, mask].mean(axis=1)])
    return result
