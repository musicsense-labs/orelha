# Orelha

Um produto do **Music Sense Labs**. Plataforma de análise harmônica e tímbrica de música gravada: acumula um acervo e
compara vocabulário harmônico entre artistas, álbuns e eras.

- `backend/` — Spring Boot 3 (Java 21): domínio, normalização harmônica, analítica do acervo.
- `frontend/` — Angular 21 (LTS): timeline harmônica, heatmaps, comparação.
- `extractor/` — orelha-extractor (Python, Docker): acordes, beats, tonalidade, stems, baixo MIDI, timbre.
- `docker-compose.yml` — PostgreSQL 16 + extractor.

Contexto, arquitetura e regras de trabalho estão em [CLAUDE.md](CLAUDE.md).

## Rodando

```bash
docker compose up -d --build
cd backend && mvn spring-boot:run        # ou .\backend\run.ps1 [-Port 8081] no PowerShell
cd frontend && npx ng serve              # http://localhost:4200, proxy /api → :8080
```

Requer JDK 21 (`JAVA_HOME` apontando para ele), Node 24 e Docker.
