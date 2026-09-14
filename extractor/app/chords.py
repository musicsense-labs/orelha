"""Acordes via o script de inferência do ChordMini (BTC, vocabulário de 170 classes).

Rótulos saem como o ChordMini escreve: 'C' (maior), 'C#:min7', 'N' (sem acorde), 'X' (desconhecido).
O parse desses rótulos é responsabilidade do Java.
"""
import os
import subprocess
import sys
from pathlib import Path

CHORDMINI_DIR = Path(os.environ.get("CHORDMINI_DIR", "/opt/chordmini"))
CHECKPOINT = "checkpoints/btc_model_best.pth"


def recognize_chords(audio_path: Path, out_dir: Path) -> list[dict]:
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        sys.executable, "src/evaluation/test.py",
        "--model_type", "BTC",
        "--checkpoint", CHECKPOINT,
        "--config", "config/ChordMini.yaml",
        "--audio_dir", str(audio_path),
        "--save_dir", str(out_dir),
        "--smooth_logits", "--use_overlap", "--use_gaussian", "--kernel_size", "9",
        "--vote_aggregation", "logit",
        "--min_segment_duration", "0.5",
        "--smooth_predictions",
    ]
    subprocess.run(cmd, cwd=CHORDMINI_DIR, check=True, capture_output=True, text=True)
    labs = sorted(out_dir.rglob("*.lab"))
    if not labs:
        raise RuntimeError(f"ChordMini produced no .lab file in {out_dir}")
    return parse_lab(labs[0])


def parse_lab(path: Path) -> list[dict]:
    segments = []
    for line in path.read_text().splitlines():
        parts = line.split()
        if len(parts) < 3:
            continue
        start, end, label = float(parts[0]), float(parts[1]), parts[2]
        if end > start:
            segments.append({"start_s": round(start, 3), "end_s": round(end, 3), "label": label})
    return segments
