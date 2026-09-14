import { ChordQuality, KeyRelation } from '../api/models';

// Só apresentação: nomes de nota, cifra e formatação. A teoria fica no backend.

export const NOTE_NAMES = ['C', 'C♯', 'D', 'E♭', 'E', 'F', 'F♯', 'G', 'A♭', 'A', 'B♭', 'B'];

const QUALITY_SUFFIX: Record<ChordQuality, string> = {
  MAJ: '', MIN: 'm', DIM: '°', AUG: '+', MAJ6: '6', MIN6: 'm6', MAJ7: 'maj7', MIN7: 'm7', DOM7: '7',
  DIM7: '°7', HDIM7: 'ø7', MINMAJ7: 'mM7', SUS2: 'sus2', SUS4: 'sus4', POWER: '5', NO_CHORD: 'N', UNKNOWN: 'X',
};

export function noteName(pc: number | null | undefined): string {
  return pc == null ? '—' : NOTE_NAMES[((pc % 12) + 12) % 12];
}

export function chordName(rootPc: number | null, quality: ChordQuality, bassPc: number | null = null): string {
  if (rootPc == null) {
    return QUALITY_SUFFIX[quality];
  }
  const base = noteName(rootPc) + QUALITY_SUFFIX[quality];
  return bassPc != null && bassPc !== rootPc ? `${base}/${noteName(bassPc)}` : base;
}

export function keyName(tonicPc: number, mode: string): string {
  return `${noteName(tonicPc)} ${mode.toLowerCase().replace('_', ' ')}`;
}

export function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export const KEY_RELATION_COLORS: Record<KeyRelation, string> = {
  DIATONIC: '#4c9a6a',
  AMBIGUOUS: '#9ccfae',
  BORROWED: '#e0a458',
  SECONDARY_DOMINANT: '#b56bd6',
  CHROMATIC: '#d9534f',
  NONE: '#c8c8c8',
};

export const KEY_RELATION_ORDER: KeyRelation[] = [
  'DIATONIC', 'AMBIGUOUS', 'BORROWED', 'SECONDARY_DOMINANT', 'CHROMATIC', 'NONE',
];

export function percent(x: number | null | undefined, digits = 0): string {
  return x == null ? '—' : `${(100 * x).toFixed(digits)}%`;
}
