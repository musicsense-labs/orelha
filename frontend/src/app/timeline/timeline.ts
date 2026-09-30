import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Album, Artist, BassTab, Beat, LyricSegment, Lyrics, Note, Segment, Timeline as TimelineDto, Track } from '../api/models';
import { Device } from '../shared/device';
import { readFlag, readNumber, save, saveFlag } from '../shared/prefs';
import { Sections } from './sections';
import { ReferencePanel } from './reference';
import { Mixer } from './mixer';
import { DetailPanel } from './detail';
import {
  KEY_RELATION_COLORS, KEY_RELATION_ORDER, chordName, formatTime, keyName, noteName, percent, relationHint, relationLabel,
} from '../shared/music';

/**
 * Timeline harmônica em SVG dirigido por signals, sincronizada com o `<audio>` nativo da mixagem, que é o
 * relógio: o playhead é um computed sobre currentTime e clicar num segmento faz seek. Esta tela decide
 * <em>quando</em> (tocar, pausar, pular, repetir uma parte, a velocidade); o {@link Mixer} decide <em>como
 * soa</em> (mix × stems, volume, balanço, metrônomo) e o {@link DetailPanel} mostra o instante.
 */
@Component({
  selector: 'app-timeline',
  imports: [RouterLink, Sections, ReferencePanel, Mixer, DetailPanel],
  templateUrl: './timeline.html',
  styleUrl: './timeline.scss',
})
export class Timeline {
  readonly id = input.required<string>();

  readonly track = httpResource<Track>(() => `/api/tracks/${this.id()}`);
  readonly album = httpResource<Album>(() => {
    const t = this.track.hasValue() ? this.track.value() : null;
    return t ? `/api/albums/${t.albumId}` : undefined;
  });
  readonly artist = httpResource<Artist>(() => {
    const a = this.album.hasValue() ? this.album.value() : null;
    return a ? `/api/artists/${a.artistId}` : undefined;
  });
  readonly timeline = httpResource<TimelineDto>(() => `/api/tracks/${this.id()}/timeline`);
  readonly stems = httpResource<string[]>(() => `/api/tracks/${this.id()}/stems`);
  readonly beats = httpResource<Beat[]>(() => `/api/tracks/${this.id()}/beats`);
  readonly vocalNotes = httpResource<Note[]>(() => `/api/tracks/${this.id()}/vocal-notes`);
  readonly bassNotes = httpResource<Note[]>(() => `/api/tracks/${this.id()}/bass-notes`);
  /** Só é pedida no modo tab (Practice: TabArranger no núcleo). */
  readonly bassTab = httpResource<BassTab>(() => (this.tabMode() ? `/api/tracks/${this.id()}/bass-tab` : undefined));
  readonly lyrics = httpResource<Lyrics>(() => `/api/tracks/${this.id()}/lyrics`);
  /** Listas seguras: um recurso em erro (404, rede) vale como "sem dados", não como falha da tela. */
  readonly beatList = computed(() => (this.beats.hasValue() ? this.beats.value() : []));
  readonly bassNoteList = computed(() => (this.bassNotes.hasValue() ? this.bassNotes.value() : []));
  readonly vocalNoteList = computed(() => (this.vocalNotes.hasValue() ? this.vocalNotes.value() : []));
  readonly lyricSegments = computed(() => (this.lyrics.hasValue() ? this.lyrics.value().segments : []));
  readonly shownTab = computed(() => (this.tabMode() && this.bassTab.hasValue() ? this.bassTab.value() : null));

  /** Notas que a letra aponta como outro instrumento no stem de voz ficam escondidas, salvo pedido. */
  readonly showLeak = signal(false);
  /** Lane de baixo como tablatura (corda e casa por nota) em vez de piano roll; preferência guardada no navegador. */
  readonly tabMode = signal(readFlag('orelha.timeline.bassTab'));
  /** Velocidade de reprodução (1 = normal), mestre e stems juntos, sem mudar o tom; volta a 1 a cada visita. */
  readonly rate = signal(1);
  readonly rates = [0.5, 0.6, 0.7, 0.75, 0.8, 0.9, 1, 1.1, 1.25];
  /** Parte em repetição: ao cruzar o fim, volta ao início (só quando chega lá tocando — um seek para depois não volta). */
  readonly loop = signal<{ partId: number; startS: number; endS: number } | null>(null);
  private lastTickS = 0;
  readonly leakCount = computed(() => this.vocalNoteList().filter((n) => n.kind === 'LIKELY_LEAK').length);
  readonly vocalShown = computed(() =>
    this.showLeak() ? this.vocalNoteList() : this.vocalNoteList().filter((n) => n.kind !== 'LIKELY_LEAK'));

  private readonly audio = viewChild<ElementRef<HTMLAudioElement>>('audio');
  private readonly mixer = viewChild(Mixer);

  readonly currentTime = signal(0);
  readonly playing = signal(false);
  readonly hovered = signal<Segment | null>(null);

  /** Largura lógica do SVG; o viewBox escala para a largura real. */
  readonly width = 1200;
  /** Lanes: acordes (a mais alta), baixo e voz (piano rolls das notas dos stems). */
  readonly laneHeight = 44;
  readonly bassLane = 36;
  readonly vocalLane = 40;
  readonly bassTop = this.laneHeight + 2;
  readonly vocalTop = this.bassTop + this.bassLane + 2;
  /** Lane da letra, sob a voz. */
  readonly lyricLane = 16;
  readonly lyricTop = this.vocalTop + this.vocalLane + 2;
  readonly lanesBottom = this.lyricTop + this.lyricLane;
  /** Altura de uma linha da timeline: as quatro lanes + eixo de tempo. */
  readonly rowHeight = this.lanesBottom + 30;
  readonly device = inject(Device);
  /** Em quantas linhas a timeline quebra (preferência do visitante, guardada no navegador; 4 no celular, 2 no PC). */
  readonly rows = signal(readNumber('orelha.timeline.rows', this.device.narrow() ? 4 : 2, (n) => n >= 1 && n <= 4));

  readonly duration = computed(() => {
    const segments = this.timeline.value()?.segments ?? [];
    const fromSegments = segments.length ? segments[segments.length - 1].endS : 0;
    return Math.max(this.track.value()?.durationS ?? 0, fromSegments, 1);
  });

  readonly segments = computed(() => this.timeline.value()?.segments ?? []);
  readonly downbeats = computed(() => this.beatList().filter((b) => b.downbeat));
  readonly downbeatTimes = computed(() => this.downbeats().map((b) => b.timeS));

  readonly current = computed(() => {
    const t = this.currentTime();
    return this.segments().find((s) => s.startS <= t && t < s.endS) ?? null;
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

  /** y de cada corda dentro da lane de baixo (G em cima, E embaixo), como numa tab impressa. */
  readonly stringYs = computed(() => {
    const n = this.shownTab()?.tuning.length ?? 4;
    const step = this.bassLane / n;
    return Array.from({ length: n }, (_, s) => this.bassTop + this.bassLane - step * (s + 0.5));
  });

  /** Casas na lane de baixo, uma por nota, na linha da sua corda; nota que cruza a borda vai à linha do ataque. */
  readonly tabMarks = computed(() => {
    const tab = this.shownTab();
    if (!tab) {
      return [];
    }
    const span = this.rowSpan();
    const rows = this.rows();
    const ys = this.stringYs();
    return tab.notes.map((n, i) => {
      const row = Math.min(rows - 1, Math.floor(n.startS / span));
      return {
        key: 't' + i, row, fret: n.fret, shifted: n.octaveShifted, midi: n.midi, string: n.string,
        x: ((n.startS - row * span) / span) * this.width,
        w: Math.max((Math.min(n.endS, (row + 1) * span) - n.startS) / span * this.width, 1),
        y: ys[n.string],
      };
    });
  });

  /** Piano roll da voz, na lane da voz. */
  readonly vocalRoll = computed(() => this.roll(this.vocalShown(), this.vocalTop, this.vocalLane, 'v'));

  /** Trechos da letra recortados por linha, com o texto que cabe na largura (≈ 6 px por caractere). */
  readonly lyricPieces = computed(() => {
    const span = this.rowSpan();
    const rows = this.rows();
    const out: { key: string; row: number; x: number; w: number; text: string; seg: LyricSegment }[] = [];
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
          key: 'l' + i + ':' + row, row, x: ((start - row * span) / span) * this.width, w, text, seg: s,
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

  private frame = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
  }

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

  /** Linha em que cai um instante. */
  rowOf(seconds: number): number {
    return Math.min(this.rows() - 1, Math.max(0, Math.floor(seconds / this.rowSpan())));
  }

  /** Posição horizontal de um instante dentro da sua linha. */
  xIn(seconds: number): number {
    const span = this.rowSpan();
    return ((seconds - this.rowOf(seconds) * span) / span) * this.width;
  }

  setTabMode(on: boolean): void {
    this.tabMode.set(on);
    saveFlag('orelha.timeline.bassTab', on);
  }

  setRows(n: number): void {
    this.rows.set(n);
    save('orelha.timeline.rows', String(n));
  }

  color(s: Segment): string {
    return KEY_RELATION_COLORS[s.keyRelation] ?? '#ccc';
  }

  label(s: Segment): string {
    return chordName(s.rootPc, s.quality, s.bassPc);
  }

  /** A letra mudou (correção do dono): a classificação das notas de voz segue a letra. */
  onLyricsSaved(): void {
    this.lyrics.reload();
    this.vocalNotes.reload();
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

  pause(): void {
    this.audio()?.nativeElement.pause();
  }

  onPlay(): void {
    this.playing.set(true);
    const master = this.audio()?.nativeElement;
    this.mixer()?.started(master?.currentTime ?? 0);
    const tick = () => {
      const el = this.audio()?.nativeElement;
      if (el) {
        const l = this.loop();
        if (l && this.lastTickS < l.endS && el.currentTime >= l.endS) {
          el.currentTime = l.startS;   // (seeked) realinha stems e metrônomo
        }
        this.lastTickS = el.currentTime;
        this.currentTime.set(el.currentTime);
        this.mixer()?.tick(el.currentTime);
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
    const el = this.audio()?.nativeElement;
    const t = el?.currentTime ?? this.currentTime();
    this.currentTime.set(t);
    this.mixer()?.stopped(t);
  }

  onSeeked(): void {
    const el = this.audio()?.nativeElement;
    if (el) {
      this.lastTickS = el.currentTime;
      this.currentTime.set(el.currentTime);
      this.mixer()?.seeked(el.currentTime);
    }
  }

  setLoop(l: { partId: number; startS: number; endS: number } | null): void {
    this.loop.set(l);
    const el = this.audio()?.nativeElement;
    if (l && el && (el.currentTime < l.startS || el.currentTime >= l.endS)) {
      this.seekTo(l.startS);
    }
  }

  /** Fim do áudio: com loop numa parte que termina no fim da faixa, volta e segue; senão é pausa. */
  onEnded(): void {
    const l = this.loop();
    const el = this.audio()?.nativeElement;
    if (l && el) {
      el.currentTime = l.startS;
      void el.play().catch(() => undefined);
      return;
    }
    this.onPause();
  }

  setRate(rate: number): void {
    this.rate.set(rate);
    this.mixer()?.setRate(rate, this.audio()?.nativeElement.currentTime ?? 0);
  }

  seekTo(seconds: number): void {
    const el = this.audio()?.nativeElement;
    if (el) {
      el.currentTime = seconds;   // dispara (seeked), que alinha stems e metrônomo
      this.currentTime.set(seconds);
    }
  }

  protected readonly formatTime = formatTime;
  protected readonly relationLabel = relationLabel;
  protected readonly relationHint = relationHint;
  protected readonly Math = Math;
  protected readonly noteName = noteName;
}
