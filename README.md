# riff-lab

Plataforma de análise harmônica e tímbrica de música gravada: acumula um corpus e
compara vocabulário harmônico entre artistas, álbuns e eras.

- `backend/` — Spring Boot 3 (Java 21): domínio, normalização harmônica, analítica de corpus.
- `frontend/` — Angular 21 (LTS): timeline harmônica, heatmaps, comparação.
- `extractor/` — riff-extractor (Python, Docker): acordes, beats, tonalidade, stems, baixo MIDI, timbre.
- `docker-compose.yml` — PostgreSQL 16 + extractor.

Contexto, arquitetura e regras de trabalho estão em [CLAUDE.md](CLAUDE.md).

## Rodando

```bash
docker compose up -d --build
cd backend && mvn spring-boot:run
```

Requer JDK 21 (`JAVA_HOME` apontando para ele) e Docker.
