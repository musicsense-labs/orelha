// Espelho dos DTOs do backend (dev.musicsense.orelha.*Response / acervo). Só o que a UI consome.

export interface Artist {
  id: number;
  name: string;
  country: string | null;
  formedYear: number | null;
}

export interface Album {
  id: number;
  artistId: number;
  title: string;
  year: number | null;
}

export interface Track {
  id: number;
  albumId: number;
  title: string;
  trackNo: number | null;
  durationS: number | null;
  audioPath: string;
  audioSha256: string;
  sampleRate: number | null;
  canonicalRunId: number | null;
  latestRunId: number | null;
  latestRunStatus: 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED' | null;
  latestRunError: string | null;
}

export type ChordQuality =
  | 'MAJ' | 'MIN' | 'DIM' | 'AUG' | 'MAJ6' | 'MIN6' | 'MAJ7' | 'MIN7' | 'DOM7' | 'DIM7' | 'HDIM7'
  | 'MINMAJ7' | 'SUS2' | 'SUS4' | 'POWER' | 'NO_CHORD' | 'UNKNOWN';

export type KeyRelation = 'NONE' | 'AMBIGUOUS' | 'DIATONIC' | 'BORROWED' | 'SECONDARY_DOMINANT' | 'CHROMATIC';

export interface KeyInfo {
  tonicPc: number;
  mode: string;
  confidence: number | null;
  source: 'EXTRACTOR' | 'DERIVED' | 'MANUAL';
}

export interface Segment {
  seqNo: number;
  startS: number;
  endS: number;
  rootPc: number | null;
  quality: ChordQuality;
  bassPc: number | null;
  confidence: number | null;
  degreeInterval: number | null;
  degreeLabel: string | null;
  keyRelation: KeyRelation;
  relationFromPrev: string | null;
  inverted: boolean;
  effectiveBassPc: number | null;
}

export interface Timeline {
  trackId: number;
  runId: number | null;
  normalizerVersion: string;
  key: KeyInfo | null;
  bpm: number | null;
  timeSignature: string | null;
  segments: Segment[];
}

export interface Beat {
  timeS: number;
  beatNo: number;
  barNo: number | null;
  downbeat: boolean;
}

export interface Share {
  bySegment: number;
  byDuration: number;
}

export interface DegreeDistribution {
  bySegment: number[];
  byDuration: number[];
  entropyBySegmentBits: number;
  entropyByDurationBits: number;
}

export interface TransitionMatrix {
  counts: number[][];
  rowNormalized: number[][];
  total: number;
}

export interface AlbumTimbre {
  albumId: number;
  albumTitle: string;
  albumYear: number | null;
  stem: string;
  tracks: number;
  centroidMean: number | null;
  centroidStd: number | null;
  flatnessMean: number | null;
  rolloffP95: number | null;
  rmsMean: number | null;
}

export interface HarmonicProfile {
  scope: 'artist' | 'album';
  id: number;
  name: string;
  tracks: number;
  segments: number;
  durationS: number;
  keyRelations: Record<string, Share>;
  nonDiatonic: Share;
  degreeLabels: string[];
  degrees: DegreeDistribution;
  transitions: TransitionMatrix;
  relations: Record<string, Share>;
  timbreByAlbum: AlbumTimbre[];
}

export interface Comparison {
  a: HarmonicProfile;
  b: HarmonicProfile;
  distances: { transitionJsBits: number; degreeL1: number; keyRelationL1: number };
}

export interface PedalPassage {
  trackId: number;
  trackTitle: string;
  startS: number;
  endS: number;
  fromLabel: string;
  toLabel: string;
  relation: string;
  bassPc: number;
}

/** Nota MIDI transcrita de um stem (voz). */
/** Nota do stem de voz à luz da letra: com texto, sem texto (vocalise) ou provável vazamento de outro instrumento. */
export type VocalNoteKind = 'LEXICAL' | 'NON_LEXICAL' | 'LIKELY_LEAK';

export interface Note {
  startS: number;
  endS: number;
  midi: number;
  velocity: number | null;
  /** Só nas notas de voz; null no baixo. */
  kind?: VocalNoteKind | null;
}

export interface LyricWord {
  startS: number;
  endS: number;
  text: string;
  /** Confiança do ASR; null em palavra corrigida pelo dono. */
  probability: number | null;
  barNo: number | null;
  /** Ataque da nota de voz que coincide com a palavra (dentro da folga), e a altura dela; null sem nota perto. */
  noteStartS: number | null;
  midi: number | null;
}

export interface LyricSegment {
  startS: number;
  endS: number;
  text: string;
  /** Probabilidade que o ASR dá a "não é fala" neste trecho. */
  noSpeechProb: number | null;
  barNo: number | null;
  words: LyricWord[];
}

export type LyricSource = 'EXTRACTOR' | 'MANUAL';

export interface Lyrics {
  runId: number | null;
  source: LyricSource | null;
  language: string | null;
  languageConfidence: number | null;
  segments: LyricSegment[];
}

export type SectionSource = 'DERIVED' | 'EXTRACTOR' | 'MANUAL';

/** Um acorde da progressão de uma parte (um ciclo), já anotado pela tonalidade preferida. */
export interface SectionChord {
  startS: number;
  endS: number;
  rootPc: number | null;
  quality: ChordQuality;
  bassPc: number | null;
  degreeLabel: string | null;
  keyRelation: KeyRelation | null;
}

export interface SectionPart {
  id: number;
  label: string;
  startS: number;
  endS: number;
  cycleEndS: number;
  repeats: number;
  chords: SectionChord[];
}

export interface Sections {
  trackId: number;
  runId: number | null;
  source: SectionSource | null;
  parts: SectionPart[];
}

/** O que o PUT /sections recebe: a lista inteira, como editada. */
export interface SectionRequest {
  startS: number;
  endS: number;
  label: string;
  cycleEndS?: number | null;
  repeats?: number | null;
}
