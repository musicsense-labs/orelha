"""orelha-extractor: extração de features de áudio para o Orelha. Sem teoria musical aqui."""
import os

VERSION = "0.6.0"

MODELS = {
    "chords": "chordmini/btc_model_best.pth@aa6e3a8",
    "beats": "madmom/RNNDownBeat+DBN@27f032e",
    "key": "madmom/CNNKeyRecognition@27f032e",
    "stems": "demucs/htdemucs@4.1.0",
    "bass": "basic-pitch/icassp2022@0.4.0",
    "vocals": "basic-pitch/icassp2022@0.4.0",
    "lyrics": "faster-whisper/" + os.environ.get("WHISPER_MODEL", "small") + "@1.1.1",
    "timbre": "librosa@0.10.1",
    "loudness": "pyloudnorm@0.1.1",
}
