"""Letra por ASR (faster-whisper) sobre o stem de voz: trechos e palavras com tempo. Zero teoria aqui.

Além do texto, cada trecho traz `no_speech_prob`: a probabilidade que o modelo atribui a "isto não é
fala". É a evidência que o núcleo usa para separar voz cantada de vazamento (solo de guitarra no stem
de voz). Trechos que o modelo descarta como não-fala não aparecem na lista — ausência também é sinal.
"""
import logging
import os
from pathlib import Path

from faster_whisper import WhisperModel

log = logging.getLogger(__name__)

WHISPER_MODEL = os.environ.get("WHISPER_MODEL", "small")
# Vazio = detectar por faixa (o acervo mistura inglês e português).
WHISPER_LANGUAGE = os.environ.get("WHISPER_LANGUAGE") or None

_model: WhisperModel | None = None


def model() -> WhisperModel:
    global _model
    if _model is None:
        _model = WhisperModel(WHISPER_MODEL, device="cpu", compute_type="int8")
    return _model


def transcribe_lyrics(vocals_wav: Path) -> dict:
    # condition_on_previous_text=False: sem contexto entre janelas o modelo repete menos alucinações em
    # trechos instrumentais. vad_filter=False: o VAD é de fala e derruba canto sustentado.
    # no_speech_threshold=0.95: canto limpo já pontua ~0,8 em "não é fala" (medido no Creep, 2026-09-15);
    # com o padrão 0,6 a ponte sob guitarra distorcida sumia. A janela só é descartada quando, além disso,
    # o log-prob médio é ruim — intro e solo instrumentais continuam sem trecho.
    segments, info = model().transcribe(str(vocals_wav), language=WHISPER_LANGUAGE, word_timestamps=True,
                                        vad_filter=False, condition_on_previous_text=False, beam_size=5,
                                        no_speech_threshold=0.95)
    out = []
    for s in segments:
        words = [
            {"start_s": round(float(w.start), 3), "end_s": round(float(w.end), 3), "text": w.word.strip(),
             "probability": round(float(w.probability), 3)}
            for w in (s.words or []) if w.end > w.start and w.word.strip()
        ]
        text = s.text.strip()
        if not text:
            continue
        out.append({"start_s": round(float(s.start), 3), "end_s": round(float(s.end), 3), "text": text,
                    "no_speech_prob": round(float(s.no_speech_prob), 3), "words": words})
    log.info("lyrics: %d segments, language %s (%.2f)", len(out), info.language, info.language_probability)
    return {"language": info.language, "language_probability": round(float(info.language_probability), 3),
            "segments": out}
