// Espelho dos DTOs do backend (dev.musicsense.orelha.*Response / acervo). Só o que a UI consome.

export interface Artist {
  id: number;
  name: string;
  country: string | null;
  formedYear: number | null;
}

export type MetadataSource = 'TAGS' | 'MUSICBRAINZ' | 'MANUAL';

export interface Album {
  id: number;
  artistId: number;
  title: string;
  /** O que as tags disseram: a edição que está no disco. */
  year: number | null;
  /** Primeira edição do release-group (MusicBrainz). */
  firstReleased: number | null;
  /** O que vale como era: firstReleased, ou year enquanto ninguém identificou. */
  effectiveYear: number | null;
  mbid: string | null;
  metadataSource: MetadataSource;
}

/** Um release-group candidato, já pontuado pelo backend. */
export interface MusicBrainzCandidate {
  mbid: string;
  title: string;
  artist: string;
  primaryType: string | null;
  firstReleased: number | null;
  disambiguation: string | null;
  score: number;
  confident: boolean;
}

export interface MusicBrainzCandidates {
  searchedTitle: string;
  configured: boolean;
  candidates: MusicBrainzCandidate[];
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
  /** Presente quando o texto do ASR foi corrigido pelo .lrc que veio com a faixa. */
  lrcMerge: LrcMerge | null;
}

export interface LrcMerge {
  corrected: number;
  inserted: number;
  dropped: number;
  kept: number;
}

export interface AccessInfo {
  remote: boolean;
  email: string | null;
  importFolderAllowed: boolean;
  admin: boolean;
}

/** Fila de análise: se o worker está consumindo e o que falta (só o administrador lê). */
export interface WorkerState {
  enabled: boolean;
  queued: number;
  running: number;
  /** Faixas cujo run mais recente falhou. */
  failed: number;
  /** Quando o worker se pausou sozinho (falhas seguidas), o motivo; null nos outros casos. */
  pausedBecause: string | null;
}

/** Auditoria (só o administrador lê). */
export type AuditKind = 'ENTER' | 'OPEN_TRACK' | 'ACTION';

export interface AuditEvent {
  id: number;
  at: string;
  actor: string;
  remote: boolean;
  ip: string | null;
  kind: AuditKind;
  method: string;
  path: string;
  status: number;
  durationMs: number | null;
  trackId: number | null;
  userAgent: string | null;
}

export interface ActorSummary {
  actor: string;
  firstSeen: string;
  lastSeen: string;
  events: number;
  actions: number;
  remoteEvents: number;
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

/** Análise humana de referência (TheoryTab transcrito pelo dono). */
export interface ReferenceSection {
  label: string;
  progression: string;
}

export interface Reference {
  trackId: number;
  source: string | null;
  url: string | null;
  tonicPc: number | null;
  mode: string | null;
  rawText: string | null;
  sections: ReferenceSection[];
  updatedAt: string | null;
}

export interface ComparisonKey {
  tonicPc: number | null;
  mode: string | null;
  source: string | null;
}

export interface SectionMatch {
  referenceLabel: string;
  referenceKeys: string[];
  ourLabel: string | null;
  ourKeys: string[];
  sequenceSimilarity: number;
  vocabularyCoverage: number;
  missingKeys: string[];
}

export interface Comparison {
  trackId: number;
  runId: number;
  referenceKey: ComparisonKey | null;
  ourKey: ComparisonKey | null;
  tonicMatches: boolean;
  modeMatches: boolean;
  sequenceSimilarity: number;
  vocabularyCoverage: number;
  sections: SectionMatch[];
  ourPartLabels: string[];
}

/** Trends do Hooktheory: próximo acorde provável e canções com a progressão. */
export interface HooktheoryNode {
  chordId: string;
  chordHtml: string;
  probability: number;
  childPath: string;
}

export interface HooktheorySong {
  artist: string;
  song: string;
  section: string;
  url: string;
}

/** Tablatura do baixo (Practice): corda 0 = a mais grave; casa 0 = solta. */
export interface TabNote {
  startS: number;
  endS: number;
  midi: number;
  string: number;
  fret: number;
  /** A nota transcrita não cabia no braço e subiu/desceu de oitava. */
  octaveShifted: boolean;
}

export interface BassTab {
  /** MIDI de cada corda solta, grave → aguda (E1 A1 D2 G2 = 28 33 38 43). */
  tuning: number[];
  strings: string[];
  preferOpen: boolean;
  notes: TabNote[];
}
