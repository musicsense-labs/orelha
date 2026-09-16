# orelha-extractor — camada de extração "burra": modelos de terceiros + DSP, zero teoria musical.
# Devolve JSON com rótulos Harte, tempos, chroma e descritores; nunca graus ou funções.
FROM python:3.10-slim

ENV PIP_NO_CACHE_DIR=1 PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 \
    CHORDMINI_DIR=/opt/chordmini FEATURES_DIR=/data/features STEMS_DIR=/data/stems WORK_DIR=/tmp/orelha-extractor \
    HF_HOME=/opt/hf WHISPER_MODEL=small

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential git ffmpeg libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt

# 1. numpy + torch CPU primeiro, para que ChordMini/demucs não puxem a build CUDA.
RUN pip install numpy==1.26.4 cython==3.0.11 \
    && pip install "torch==2.9.1+cpu" "torchaudio==2.9.1+cpu" --extra-index-url https://download.pytorch.org/whl/cpu

# 2. ChordMini (MIT): pesos BTC + script de inferência, pinado por commit. torch já está instalado.
ARG CHORDMINI_REF=aa6e3a8d7b017f082fd2aaff9329d5c26af49c03
RUN git clone --filter=blob:none https://github.com/ptnghia-j/ChordMini.git chordmini \
    && cd chordmini && git checkout --quiet ${CHORDMINI_REF} \
    && grep -viE '^(torch|setuptools)==' requirements.txt > /tmp/chordmini-req.txt \
    && pip install -r /tmp/chordmini-req.txt

# 3. madmom (beats, downbeats, tonalidade) do git: o release do PyPI não instala em Python moderno.
ARG MADMOM_REF=27f032e8947204902c675e5e341a3faf5dc86dae
RUN pip install --no-build-isolation "git+https://github.com/CPJKU/madmom.git@${MADMOM_REF}"

# 4. A cola.
COPY requirements.txt /opt/extractor/requirements.txt
RUN pip install -r /opt/extractor/requirements.txt

# 5. Baixa os pesos do demucs e do Whisper no build para o container subir pronto (e offline).
RUN python -c "from demucs.pretrained import get_model; get_model('htdemucs')" \
    && python -c "from faster_whisper import WhisperModel; WhisperModel('${WHISPER_MODEL}', device='cpu', compute_type='int8')"

COPY app /opt/extractor/app
WORKDIR /opt/extractor
RUN mkdir -p ${FEATURES_DIR} ${STEMS_DIR} ${WORK_DIR}
VOLUME ["/data/features", "/data/stems"]
EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]
