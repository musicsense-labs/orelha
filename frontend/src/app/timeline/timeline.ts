import {
  Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, viewChild, viewChildren,
} from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Beat, LyricSegment, Note, Segment, Timeline as TimelineDto, Track } from '../api/models';
import { Lyrics } from '../api/models';
import { Metronome } from '../shared/metronome';
import { Sections } from './sections';
import {
  KEY_RELATION_COLORS, KEY_RELATION_ORDER, chordName, formatTime, keyName, noteName, percent, relationHint, relationLabel,
} from '../shared/music';

/**
 * Timeline harmônica em SVG dirigido por signals, sincronizada com o <audio> nativo.
 * O playhead é um computed sobre currentTime; clicar num segmento faz seek.
 *
 * Player: a mixagem é o relógio-mestre; cada stem é um <audio> escondido que segue play/pause/seek
 * e é corrigido quando deriva mais de 150 ms. Mix e stems são mutuamente exclusivos (ligar o mix
 * silencia os stems; ligar um stem silencia o mix); stems se combinam entre si; cada canal tem
 * volume. O metrônomo (Web Audio sobre os beats do run) é independente e toca por cima.
 */
function readRows(): number {
  try {
    const n = Number(localStorage.getItem('orelha.timeline.rows'));
    return n >= 1 && n <= 4 ? n : 1;
  } catch {
    return 1;
  }
}

@Component({
  selector: 'app-timeline',
  imports: [RouterLink, Sections],
  templateUrl: './timeline.html',
  styleUrl: './timeline.scss',
})
export class Timeline {
  readonly id = input.required<string>();

  readonly track = httpResource<Track>(() => `/api/tracks/${this.id()}`);
  readonly timeline = httpResource<TimelineDto>(() => `/api/tracks/${this.id()}/timeline`);
  readonly stems = httpResource<string[]>(() => `/api/tracks/${this.id()}/stems`);
  readonly beats = httpResource<Beat[]>(() => `/api/tracks/${this.id()}/beats`);
  readonly vocalNotes = httpResource<Note[]>(() => `/api/tracks/${this.id()}/vocal-notes`);
  readonly bassNotes = httpResource<Note[]>(() => `/api/tracks/${this.id()}/bass-notes`);
  /** Listas seguras: um recurso em erro (404 num backend antigo, rede) vale como "sem notas", não como falha da tela. */
  readonly bassNoteList = computed(() => (this.bassNotes.hasValue() ? this.bassNotes.value() : []));
  readonly vocalNoteList = computed(() => (this.vocalNotes.hasValue() ? this.vocalNotes.value() : []));
  readonly lyrics = httpResource<Lyrics>(() => `/api/tracks/${this.id()}/lyrics`);
  readonly lyricSegments = computed(() => (this.lyrics.hasValue() ? this.lyrics.value().segments : []));
  /** Notas que o ASR sugere serem outro instrumento no stem de voz ficam escondidas, salvo pedido. */
  readonly showLeak = signal(false);
  readonly leakCount = computed(() => this.vocalNoteList().filter((n) => n.kind === 'LIKELY_LEAK').length);
  readonly vocalShown = computed(() =>
    this.showLeak() ? this.vocalNoteList() : this.vocalNoteList().filter((n) => n.kind !== 'LIKELY_LEAK'));

  private readonly audio = viewChild<ElementRef<HTMLAudioElement>>('audio');
  private readonly stemAudios = viewChildren<ElementRef<HTMLAudioElement>>('stemAudio');

  readonly currentTime = signal(0);
  readonly playing = signal(false);
  readonly hovered = signal<Segment | null>(null);

  /** O que está audível: 'mix' ou um conjunto de stems — nunca os dois. */
  readonly audible = signal<ReadonlySet<string>>(new Set(['mix']));
  /** Volume por canal (0–1), inclusive 'mix' e 'metronome'. */
  readonly volumes = signal<Record<string, number>>({ mix: 1, drums: 1, bass: 1, other: 1, vocals: 1, metronome: 0.8 });
  readonly metronomeOn = signal(false);

  /** Largura lógica do SVG; o viewBox escala para a largura real. */
  readonly width = 1200;
  /** Lanes: acordes (a mais alta), baixo e voz (piano rolls das notas dos stems). */
  readonly laneHeight = 44;
  readonly bassLane = 36;
  readonly vocalLane = 40;
  readonly bassTop = this.laneHeight + 2;
  readonly vocalTop = this.bassTop + this.bassLane + 2;
  /** Lane da letra: trechos transcritos pelo ASR, sob a voz. */
  readonly lyricLane = 16;
  readonly lyricTop = this.vocalTop + this.vocalLane + 2;
  readonly lanesBottom = this.lyricTop + this.lyricLane;
  /** Altura de uma linha da timeline: as quatro lanes + eixo de tempo. */
  readonly rowHeight = this.lanesBottom + 30;
  /** Em quantas linhas a timeline quebra (preferência do visitante, guardada no navegador). */
  readonly rows = signal(readRows());

  readonly stemLabels: Record<string, string> = { drums: 'bateria', bass: 'baixo', other: 'guitarras/teclados', vocals: 'voz' };

  readonly duration = computed(() => {
    const segments = this.timeline.value()?.segments ?? [];
    const fromSegments = segments.length ? segments[segments.length - 1].endS : 0;
    return Math.max(this.track.value()?.durationS ?? 0, fromSegments, 1);
  });

  readonly segments = computed(() => this.timeline.value()?.segments ?? []);
  readonly downbeats = computed(() => (this.beats.value() ?? []).filter((b) => b.downbeat));
  readonly downbeatTimes = computed(() => this.downbeats().map((b) => b.timeS));

  readonly current = computed(() => {
    const t = this.currentTime();
    return this.segments().find((s) => s.startS <= t && t < s.endS) ?? null;
  });

  readonly currentBar = computed(() => {
    const t = this.currentTime();
    const beats = this.beats.value() ?? [];
    let bar: number | null = null;
    for (const b of beats) {
      if (b.timeS > t) {
        break;
      }
      bar = b.barNo;
    }
    return bar;
  });

  readonly focus = computed(() => this.hovered() ?? this.current());

  readonly playheadX = computed(() => this.xIn(this.currentTime()));

  /** Segundos por linha. */
  readonly rowSpan = computed(() => this.duration() / this.rows());
  readonly rowIndexes = computed(() => Array.from({ length: this.rows() }, (_, i) => i));

  /** Segmentos recortados por linha: um pedaço por linha que o segmento atravessa. */
  readonly pieces = computed(() => {
    const span = this.rowSpan();
    const rows = this.rows();
    const out: { key: string; s: Segment; row: number; x: number; w: number }[] = [];
    for (const s of this.segments()) {
      const first = Math.min(rows - 1, Math.floor(s.startS / span));
      const last = Math.min(rows - 1, Math.max(first, Math.ceil(s.endS / span) - 1));
      for (let row = first; row <= last; row++) {
        const start = Math.max(s.startS, row * span);
        const end = Math.min(s.endS, (row + 1) * span);
        if (end <= start) {
          continue;
        }
        const x = ((start - row * span) / span) * this.width;
        const w = Math.max(((end - start) / span) * this.width, 0.5);
        out.push({ key: s.seqNo + ':' + row, s, row, x, w });
      }
    }
    return out;
  });

  /** Piano roll da linha de baixo (stem), na lane do baixo. */
  readonly bassRoll = computed(() => this.roll(this.bassNoteList(), this.bassTop, this.bassLane, 'b'));
  /** Piano roll da voz, na lane da voz. */
  readonly vocalRoll = computed(() => this.roll(this.vocalShown(), this.vocalTop, this.vocalLane, 'v'));

  /** Trechos da letra recortados por linha, com o texto que cabe na largura (≈ 6 px por caractere). */
  readonly lyricPieces = computed(() => {
    const span = this.rowSpan();
    const rows = this.rows();
    const out: { key: string; row: number; x: number; w: number; text: string; leak: boolean; seg: LyricSegment }[] = [];
    this.lyricSegments().forEach((s, i) => {
      const first = Math.min(rows - 1, Math.floor(s.startS / span));
      const last = Math.min(rows - 1, Math.max(first, Math.ceil(s.endS / span) - 1));
      for (let row = first; row <= last; row++) {
        const start = Math.max(s.startS, row * span);
        const end = Math.min(s.endS, (row + 1) * span);
        if (end <= start) {
          continue;
        }
        const w = Math.max(((end - start) / span) * this.width, 1);
        const chars = Math.floor((w - 4) / 6);
        const text = chars < 3 ? '' : (s.text.length <= chars ? s.text : s.text.slice(0, chars - 1) + '…');
        out.push({
          key: 'l' + i + ':' + row, row, x: ((start - row * span) / span) * this.width, w, text,
          leak: (s.noSpeechProb ?? 0) >= 0.6, seg: s,
        });
      }
    });
    return out;
  });

  /** Trecho da letra no instante e as palavras dele, com a que está sendo cantada marcada. */
  readonly currentLine = computed(() => {
    const t = this.currentTime();
    const seg = this.lyricSegments().find((s) => s.startS <= t && t < s.endS) ?? null;
    if (!seg) {
      return null;
    }
    const words = seg.words.map((w) => ({ text: w.text, on: w.startS <= t && t < w.endS + 0.12 }));
    return { seg, words };
  });

  /**
   * Nota do baixo (stem) no instante: em execução ("on") ou a última que soou ("off"), até a próxima
   * começar — a UI mostra a primeira em negrito e a segunda leve, para o nome não piscar.
   */
  readonly bassNow = computed(() => this.noteAt(this.bassNoteList(), this.currentTime()));
  readonly vocalNow = computed(() => this.noteAt(this.vocalShown(), this.currentTime()));

  /** Tessitura (p5–p95) das notas, para o piano roll ocupar a lane inteira; ao menos uma oitava. */
  private static range(notes: Note[]): { low: number; high: number } {
    const midis = notes.map((n) => n.midi).sort((a, b) => a - b);
    if (midis.length === 0) {
      return { low: 48, high: 72 };
    }
    const low = midis[Math.floor(midis.length * 0.05)];
    const high = midis[Math.min(midis.length - 1, Math.floor(midis.length * 0.95))];
    return high - low < 12 ? { low: low - 6, high: low + 6 } : { low, high };
  }

  /** Notas recortadas por linha, com a geometria do piano roll dentro de uma lane. */
  private roll(notes: Note[], top: number, lane: number, prefix: string) {
    const span = this.rowSpan();
    const rows = this.rows();
    const { low, high } = Timeline.range(notes);
    const step = lane / (high - low + 1);
    const out: { key: string; row: number; x: number; w: number; y: number; h: number; midi: number; kind: string | null }[] = [];
    notes.forEach((n, i) => {
      const first = Math.min(rows - 1, Math.floor(n.startS / span));
      const last = Math.min(rows - 1, Math.max(first, Math.ceil(n.endS / span) - 1));
      const clamped = Math.min(high, Math.max(low, n.midi));
      const y = top + (high - clamped) * step;
      for (let row = first; row <= last; row++) {
        const start = Math.max(n.startS, row * span);
        const end = Math.min(n.endS, (row + 1) * span);
        if (end <= start) {
          continue;
        }
        out.push({
          key: prefix + i + ':' + row, row, midi: n.midi, kind: n.kind ?? null,
          x: ((start - row * span) / span) * this.width,
          w: Math.max(((end - start) / span) * this.width, 1),
          y, h: Math.max(step, 2),
        });
      }
    });
    return out;
  }

  /** A nota que soa em t (a mais forte, se várias) ou, sem nenhuma, a última que terminou antes de t. */
  private noteAt(notes: Note[], t: number): { name: string; state: 'on' | 'off' } | null {
    let sounding: Note | null = null;
    let last: Note | null = null;
    for (const n of notes) {
      if (n.startS > t) {
        break;
      }
      if (t < n.endS) {
        if (sounding == null || (n.velocity ?? 0) > (sounding.velocity ?? 0)) {
          sounding = n;
        }
      } else if (last == null || n.endS > last.endS) {
        last = n;
      }
    }
    const n = sounding ?? last;
    return n ? { name: noteName(n.midi % 12) + (Math.floor(n.midi / 12) - 1), state: sounding ? 'on' : 'off' } : null;
  }

  /** Marcas de tempo a cada 30 s, cada uma na sua linha. */
  readonly ticks = computed(() => {
    const out: { seconds: number; row: number; x: number }[] = [];
    for (let seconds = 0; seconds < this.duration(); seconds += 30) {
      out.push({ seconds, row: this.rowOf(seconds), x: this.xIn(seconds) });
    }
    return out;
  });

  readonly keyLabel = computed(() => {
    const k = this.timeline.value()?.key;
    return k ? `${keyName(k.tonicPc, k.mode)} (${k.source.toLowerCase()}${k.confidence != null ? ', ' + percent(k.confidence) : ''})` : '—';
  });

  readonly legend = KEY_RELATION_ORDER.map((r) => ({ relation: r, color: KEY_RELATION_COLORS[r] }));

  private readonly metronome = new Metronome();
  private frame = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      cancelAnimationFrame(this.frame);
      this.metronome.dispose();
    });
    // Mudo e volume seguem os signals; o mestre continua tocando mesmo mudo para manter o relógio.
    effect(() => {
      const audible = this.audible();
      const volumes = this.volumes();
      const master = this.audio()?.nativeElement;
      if (master) {
        master.muted = !audible.has('mix');
        master.volume = volumes['mix'] ?? 1;
      }
      for (const ref of this.stemAudios()) {
        const el = ref.nativeElement;
        const name = el.dataset['stem'] ?? '';
        el.muted = !audible.has(name);
        el.volume = volumes[name] ?? 1;
      }
      this.metronome.setVolume(volumes['metronome'] ?? 0.8);
    });
    effect(() => {
      this.metronome.setBeats(this.beats.value() ?? []);
    });
  }

  /** Linha em que cai um instante. */
  rowOf(seconds: number): number {
    return Math.min(this.rows() - 1, Math.max(0, Math.floor(seconds / this.rowSpan())));
  }

  /** Posição horizontal de um instante dentro da sua linha. */
  xIn(seconds: number): number {
    const span = this.rowSpan();
    return ((seconds - this.rowOf(seconds) * span) / span) * this.width;
  }

  setRows(n: number): void {
    this.rows.set(n);
    try {
      localStorage.setItem('orelha.timeline.rows', String(n));
    } catch {
      // sem storage: a escolha vale só nesta visita
    }
  }

  color(s: Segment): string {
    return KEY_RELATION_COLORS[s.keyRelation] ?? '#ccc';
  }

  label(s: Segment): string {
    return chordName(s.rootPc, s.quality, s.bassPc);
  }

  /** Cifra com o baixo da harmonia na notação acorde/baixo, quando o baixo efetivo não é a fundamental. */
  labelWithBass(s: Segment): string {
    const slash = s.effectiveBassPc != null && s.effectiveBassPc !== s.rootPc ? s.effectiveBassPc : null;
    return chordName(s.rootPc, s.quality, slash);
  }


  // --- mixer ----------------------------------------------------------------------------------

  isAudible(name: string): boolean {
    return this.audible().has(name);
  }

  /** Mix e stems são exclusivos: ligar o mix desliga os stems; ligar um stem desliga o mix. */
  toggle(name: string): void {
    const current = this.audible();
    if (name === 'mix') {
      this.audible.set(current.has('mix') ? new Set() : new Set(['mix']));
      return;
    }
    const next = new Set([...current].filter((n) => n !== 'mix'));
    if (current.has(name)) {
      next.delete(name);
    } else {
      next.add(name);
    }
    this.audible.set(next);
  }

  /** Só este canal. */
  solo(name: string): void {
    this.audible.set(new Set([name]));
  }

  setVolume(name: string, value: number): void {
    this.volumes.update((v) => ({ ...v, [name]: Math.min(1, Math.max(0, value)) }));
  }

  volume(name: string): number {
    return this.volumes()[name] ?? 1;
  }

  toggleMetronome(): void {
    this.metronomeOn.update((on) => !on);
    const el = this.audio()?.nativeElement;
    this.metronome.reset(el ? el.currentTime : 0);
  }

  // --- transporte -----------------------------------------------------------------------------

  seek(s: Segment): void {
    this.seekTo(s.startS);
  }

  togglePlay(): void {
    const el = this.audio()?.nativeElement;
    if (!el) {
      return;
    }
    if (el.paused) {
      void el.play().catch(() => undefined);
    } else {
      el.pause();
    }
  }

  onPlay(): void {
    this.playing.set(true);
    for (const ref of this.stemAudios()) {
      void ref.nativeElement.play().catch(() => undefined);
    }
    const el0 = this.audio()?.nativeElement;
    this.metronome.reset(el0 ? el0.currentTime : 0);
    const tick = () => {
      const el = this.audio()?.nativeElement;
      if (el) {
        this.currentTime.set(el.currentTime);
        this.keepStemsInSync(el.currentTime);
        if (this.metronomeOn()) {
          this.metronome.schedule(el.currentTime);
        }
      }
      if (this.playing()) {
        this.frame = requestAnimationFrame(tick);
      }
    };
    this.frame = requestAnimationFrame(tick);
  }

  onPause(): void {
    this.playing.set(false);
    cancelAnimationFrame(this.frame);
    for (const ref of this.stemAudios()) {
      ref.nativeElement.pause();
    }
    const el = this.audio()?.nativeElement;
    if (el) {
      this.currentTime.set(el.currentTime);
      this.metronome.reset(el.currentTime);
    }
  }

  onSeeked(): void {
    const el = this.audio()?.nativeElement;
    if (el) {
      this.currentTime.set(el.currentTime);
      for (const ref of this.stemAudios()) {
        ref.nativeElement.currentTime = el.currentTime;
      }
      this.metronome.reset(el.currentTime);
    }
  }

  seekTo(seconds: number): void {
    const el = this.audio()?.nativeElement;
    if (el) {
      el.currentTime = seconds;   // dispara (seeked), que alinha stems e metrônomo
      this.currentTime.set(seconds);
    }
  }

  private keepStemsInSync(masterTime: number): void {
    for (const ref of this.stemAudios()) {
      const el = ref.nativeElement;
      if (Math.abs(el.currentTime - masterTime) > 0.15) {
        el.currentTime = masterTime;
      }
    }
  }

  protected readonly formatTime = formatTime;
  protected readonly relationLabel = relationLabel;
  protected readonly relationHint = relationHint;
  protected readonly Math = Math;
  protected readonly noteName = noteName;
  protected readonly percent = percent;
}
