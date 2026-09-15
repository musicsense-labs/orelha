"""Converte stems já persistidos (WAV de runs anteriores) para STEM_FORMAT, sem reanalisar.

    docker exec riff-lab-extractor python -m app.convert_stems

Imprime um JSON {caminho_antigo: caminho_novo}; o backend precisa atualizar analysis_run.stems com
esse mapeamento (ver README). Os WAVs são apagados após a conversão bem-sucedida.
"""
import json
import os
import sys
from pathlib import Path

from .stems import CODECS, STEM_FORMAT, persist_stems

STEMS_DIR = Path(os.environ.get("STEMS_DIR", "/data/stems"))


def main() -> None:
    ext, _ = CODECS[STEM_FORMAT]
    mapping: dict[str, str] = {}
    for run_dir in sorted(p for p in STEMS_DIR.iterdir() if p.is_dir()):
        wavs = {p.stem: p for p in run_dir.glob("*.wav")}
        if not wavs:
            continue
        encoded = persist_stems(wavs, run_dir)
        for name, wav in wavs.items():
            mapping[str(wav)] = str(encoded[name])
            wav.unlink()
        print(f"{run_dir.name}: {len(wavs)} stems -> .{ext}", file=sys.stderr)
    json.dump(mapping, sys.stdout, indent=1)


if __name__ == "__main__":
    main()
