# riff-extractor

Camada [1] da arquitetura: recebe áudio, devolve JSON. Modelos de terceiros e DSP; **nenhuma
teoria musical** — rótulos Harte, tempos, vetores. O Spring Boot interpreta.

| Capacidade | Modelo | Licença |
|---|---|---|
| Acordes (170 classes) | BTC via [ChordMini](https://github.com/ptnghia-j/ChordMini) `btc_model_best.pth` | MIT |
| Beats / downbeats | madmom RNNDownBeat + DBN | BSD |
| Tonalidade (24) | madmom CNNKeyRecognition | BSD |
| Stems | demucs `htdemucs` | MIT |
| Baixo → MIDI | basic-pitch (stem de baixo) | Apache-2.0 |
| Chroma por segmento, descritores por stem, LUFS | librosa, pyloudnorm | ISC / MIT |

## API

`GET /health` → `{status, version, models}`

`POST /analyze` (multipart: `file`, `audio_sha256`) → síncrono, minutos por faixa:

```json
{
  "extractor": {"name": "riff-extractor", "version": "0.1.0", "models": {"chords": "...", "...": "..."}},
  "audio": {"duration_s": 32.0, "sample_rate": 44100, "integrated_lufs": -14.2},
  "key": {"tonic_pc": 9, "mode": "minor", "confidence": 0.71},
  "tempo": {"bpm": 120.0, "time_signature": "4/4"},
  "beats": [{"time_s": 0.5, "position": 1}],
  "chords": [{"start_s": 0.0, "end_s": 2.0, "label": "A:min",
              "chroma": [0.9, 0.1, "..."], "chroma_low": [0.8, 0.05, "..."]}],
  "bass_notes": [{"start_s": 0.0, "end_s": 0.5, "midi": 45, "velocity": 90}],
  "timbre": [{"stem_model": "htdemucs", "stem": "bass", "centroid_mean": 412.5, "centroid_std": 88.1,
              "flatness_mean": 0.02, "rolloff_p95": 1800.0, "rms_mean": 0.12}],
  "features_path": "/data/features/<sha256>.parquet",
  "stems": {"bass": "/data/stems/<sha256>/bass.wav", "drums": "...", "other": "...", "vocals": "..."}
}
```

`chroma` é a mixagem inteira; `chroma_low` é o stem de guitarra (`other`) restrito a C2–F4 — a
evidência para decidir power chord sem o 5º harmônico da distorção.

`features_path` e `stems` apontam para os volumes do container (`/data/features`, `/data/stems`),
bind-mounted no host pelo compose (`./data`): o backend guarda os caminhos e serve os stems para o
player multi-stem.

## Build e smoke test

```bash
docker build -t riff-extractor extractor/
docker run --rm -v "$PWD/extractor/out:/out" riff-extractor python -m app.testaudio /out/progression.wav
docker run --rm -p 8000:8000 riff-extractor
curl -F file=@extractor/out/progression.wav -F audio_sha256=$(sha256sum extractor/out/progression.wav | cut -d' ' -f1) http://localhost:8000/analyze
```
