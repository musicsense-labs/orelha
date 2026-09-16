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

/** Nome curto em pt-BR e explicação de uma relação — só apresentação; as definições são as do backend. */
export interface RelationText {
  label: string;
  hint: string;
}

/** Eixo A: o acorde em relação à tonalidade vigente. */
export const KEY_RELATION_TEXT: Record<KeyRelation, RelationText> = {
  DIATONIC: { label: 'diatônico', hint: 'Todas as notas do acorde (inclusive a 7ª) estão na escala da tonalidade; em menor, a dominante da harmônica conta como diatônica.' },
  AMBIGUOUS: { label: 'ambíguo', hint: 'Power chord (sem terça) cuja díade cabe na escala: não dá para dizer se seria maior ou menor.' },
  BORROWED: { label: 'empréstimo modal', hint: 'Todas as notas cabem na escala paralela (maior ↔ menor natural): ♭VII, ♭VI, iv, ♭III em maior; IV maior, ii em menor.' },
  SECONDARY_DOMINANT: { label: 'dominante secundária', hint: 'Dominante de um grau que não é a tônica: acorde com 7ª de dominante fora do campo (E7 → Am em Dó é V/vi), ou tríade maior que resolve uma 5ª abaixo.' },
  CHROMATIC: { label: 'cromático', hint: 'Fora do campo e da escala paralela: ♭II frígio, III e VI maiores, ♭V, mediantes cromáticos.' },
  NONE: { label: 'sem acorde', hint: 'Silêncio ou trecho sem fundamental reconhecida.' },
};

/** Eixo B: o acorde em relação ao anterior, sobre as tríades reduzidas. */
export const CHORD_RELATION_TEXT: Record<string, RelationText> = {
  SAME: { label: 'mesmo acorde', hint: 'Mesma fundamental e mesma qualidade.' },
  SAME_ROOT: { label: 'mesma fundamental', hint: 'A fundamental fica; muda a qualidade ou o baixo (C → Csus4, C → C7, C → C/E).' },
  PARALLEL: { label: 'paralelo (P)', hint: 'Maior ↔ menor sobre a mesma fundamental (C ↔ Cm): a terça desce ou sobe meio tom, duas notas em comum.' },
  RELATIVE: { label: 'relativo (R)', hint: 'Maior ↔ o menor relativo (C ↔ Am): duas notas em comum, a quinta sobe um tom.' },
  LEITTONWECHSEL: { label: 'sensível (L)', hint: 'Maior ↔ o menor uma terça acima (C ↔ Em, F ↔ Am): a fundamental desce meio tom para a sensível; duas notas em comum.' },
  HEXATONIC_POLE: { label: 'polo hexatônico', hint: 'L·P·L: maior ↔ menor uma terça maior acima (C ↔ A♭m): nenhuma nota em comum, o salto mais distante entre tríades.' },
  DIATONIC_MEDIANT: { label: 'mediante diatônico', hint: 'Fundamentais a uma terça com duas notas em comum, sem ser R nem L (envolve tríade diminuta ou aumentada).' },
  CHROMATIC_MEDIANT: { label: 'mediante cromático', hint: 'Fundamentais a uma terça com uma nota em comum (C → E, C → A♭, C → E♭, C → A): a classe que marca o rock e o metal.' },
  DOUBLY_CHROMATIC_MEDIANT: { label: 'mediante duplamente cromático', hint: 'Fundamentais a uma terça sem nota em comum (C → E♭m), fora o polo hexatônico.' },
  MEDIANT: { label: 'terça (sem tríade)', hint: 'Fundamentais a uma terça, mas um dos acordes não tem terça (E5 → C5, sus): as notas comuns não decidem.' },
  FIFTH_DOWN: { label: 'quinta abaixo (V → I)', hint: 'A fundamental desce uma quinta justa (G → C): o movimento de dominante para tônica, o mais forte da harmonia tonal.' },
  FIFTH_UP: { label: 'quinta acima (I → V)', hint: 'A fundamental sobe uma quinta justa (C → G): afasta-se da tônica, prepara o retorno.' },
  TRITONE: { label: 'trítono', hint: 'Fundamentais a três tons (C → F♯): substituição de trítono, ♭V blue note.' },
  SEMITONE: { label: 'semitom', hint: 'A fundamental sobe ou desce meio tom (E → F, C → B): ♭II frígio, cromatismo de baixo.' },
  WHOLE_TONE: { label: 'tom inteiro', hint: 'A fundamental sobe ou desce um tom (C → D, C → B♭): IV → V, ♭VII → I.' },
};

export function relationLabel(code: string | null | undefined): string {
  return code == null ? '—' : (CHORD_RELATION_TEXT[code]?.label ?? KEY_RELATION_TEXT[code as KeyRelation]?.label ?? code);
}

export function relationHint(code: string | null | undefined): string {
  return code == null ? '' : (CHORD_RELATION_TEXT[code]?.hint ?? KEY_RELATION_TEXT[code as KeyRelation]?.hint ?? '');
}
