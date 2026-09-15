import {
  Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, viewChild, viewChildren,
} from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Beat, Note, Segment, Timeline as TimelineDto, Track } from '../api/models';
import { Metronome } from '../shared/metronome';
import { Sections } from './sections';
import {
  KEY_RELATION_COLORS, KEY_RELATION_ORDER, chordName, formatTime, keyName, noteName, percent,
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
  /** Lanes: acordes (a mais alta), baixo (estreita: só a nota) e voz (piano roll). */
  readonly laneHeight = 44;
  readonly bassLane = 22;
  readonly vocalLane = 40;
  readonly bassTop = this.laneHeight + 2;
  readonly vocalTop = this.bassTop + this.bassLane + 2;
  /** Altura de uma linha da timeline: as três lanes + eixo de tempo. */
  readonly rowHeight = this.vocalTop + this.vocalLane + 30;
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

  /** Tessitura da voz nesta faixa (p5–p95 das notas), para o piano roll ocupar a lane inteira. */
  readonly vocalRange = computed(() => {
    const midis = (this.vocalNotes.value() ?? []).map((n) => n.midi).sort((a, b) => a - b);
    if (midis.length === 0) {
      return { low: 48, high: 72 };
    }
    const low = midis[Math.floor(midis.length * 0.05)];
    const high = midis[Math.min(midis.length - 1, Math.floor(midis.length * 0.95))];
    return high - low < 12 ? { low: low - 6, high: low + 6 } : { low, high };
  });

  /** Notas da voz recortadas por linha, já com a geometria do piano roll. */
  readonly vocalPieces = computed(() => {
    const span = this.rowSpan();
    const rows = this.rows();
    const { low, high } = this.vocalRange();
    const step = this.vocalLane / (high - low + 1);
    const out: { key: string; row: number; x: number; w: number; y: number; h: number; midi: number }[] = [];
    (this.vocalNotes.value() ?? []).forEach((n, i) => {
      const first = Math.min(rows - 1, Math.floor(n.startS / span));
      const last = Math.min(rows - 1, Math.max(first, Math.ceil(n.endS / span) - 1));
      const clamped = Math.min(high, Math.max(low, n.midi));
      const y = this.vocalTop + (high - clamped) * step;
      for (let row = first; row <= last; row++) {
        const start = Math.max(n.startS, row * span);
        const end = Math.min(n.endS, (row + 1) * span);
        if (end <= start) {
          continue;
        }
        out.push({
          key: i + ':' + row, row, midi: n.midi,
          x: ((start - row * span) / span) * this.width,
          w: Math.max(((end - start) / span) * this.width, 1),
          y, h: Math.max(step, 2),
        });
      }
    });
    return out;
  });

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
  protected readonly Math = Math;
  protected readonly noteName = noteName;
  protected readonly percent = percent;
}
