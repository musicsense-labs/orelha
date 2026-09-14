"""riff-extractor: extração de features de áudio para o riff-lab. Sem teoria musical aqui."""

VERSION = "0.3.0"

MODELS = {
    "chords": "chordmini/btc_model_best.pth@aa6e3a8",
    "beats": "madmom/RNNDownBeat+DBN@27f032e",
    "key": "madmom/CNNKeyRecognition@27f032e",
    "stems": "demucs/htdemucs@4.1.0",
    "bass": "basic-pitch/icassp2022@0.4.0",
    "timbre": "librosa@0.10.1",
    "loudness": "pyloudnorm@0.1.1",
}
