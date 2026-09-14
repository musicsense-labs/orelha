import {
  Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, viewChild, viewChildren,
} from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Segment, Timeline as TimelineDto, Track } from '../api/models';
import {
  KEY_RELATION_COLORS, KEY_RELATION_ORDER, chordName, formatTime, keyName, noteName, percent,
} from '../shared/music';

/**
 * Timeline harmônica em SVG dirigido por signals, sincronizada com o <audio> nativo.
 * O playhead é um computed sobre currentTime; clicar num segmento faz seek.
 *
 * Player multi-stem: a mixagem é o relógio-mestre; cada stem é um <audio> escondido que segue o
 * mestre (play/pause/seek) e é corrigido quando deriva mais de 150 ms. Ouvir "só o baixo" =
 * silenciar a mixagem e os outros stems.
 */
@Component({
  selector: 'app-timeline',
  imports: [RouterLink],
  templateUrl: './timeline.html',
  styleUrl: './timeline.scss',
})
export class Timeline {
  readonly id = input.required<string>();

  readonly track = httpResource<Track>(() => `/api/tracks/${this.id()}`);
  readonly timeline = httpResource<TimelineDto>(() => `/api/tracks/${this.id()}/timeline`);
  readonly stems = httpResource<string[]>(() => `/api/tracks/${this.id()}/stems`);

  private readonly audio = viewChild<ElementRef<HTMLAudioElement>>('audio');
  private readonly stemAudios = viewChildren<ElementRef<HTMLAudioElement>>('stemAudio');

  readonly currentTime = signal(0);
  readonly playing = signal(false);
  readonly hovered = signal<Segment | null>(null);

  /** O que está audível: 'mix' e/ou nomes de stems. */
  readonly audible = signal<ReadonlySet<string>>(new Set(['mix']));

  /** Largura lógica do SVG; o viewBox escala para a largura real. */
  readonly width = 1200;
  readonly laneHeight = 44;

  readonly stemLabels: Record<string, string> = { drums: 'bateria', bass: 'baixo', other: 'guitarras/teclados', vocals: 'voz' };

  readonly duration = computed(() => {
    const segments = this.timeline.value()?.segments ?? [];
    const fromSegments = segments.length ? segments[segments.length - 1].endS : 0;
    return Math.max(this.track.value()?.durationS ?? 0, fromSegments, 1);
  });

  readonly segments = computed(() => this.timeline.value()?.segments ?? []);

  readonly current = computed(() => {
    const t = this.currentTime();
    return this.segments().find((s) => s.startS <= t && t < s.endS) ?? null;
  });

  readonly focus = computed(() => this.hovered() ?? this.current());

  readonly playheadX = computed(() => this.x(this.currentTime()));

  readonly keyLabel = computed(() => {
    const k = this.timeline.value()?.key;
    return k ? `${keyName(k.tonicPc, k.mode)} (${k.source.toLowerCase()}${k.confidence != null ? ', ' + percent(k.confidence) : ''})` : '—';
  });

  readonly legend = KEY_RELATION_ORDER.map((r) => ({ relation: r, color: KEY_RELATION_COLORS[r] }));

  private frame = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.frame));
    // Mute/unmute segue a seleção; o mestre continua tocando mesmo mudo para manter o relógio.
    effect(() => {
      const audible = this.audible();
      const master = this.audio()?.nativeElement;
      if (master) {
        master.muted = !audible.has('mix');
      }
      for (const ref of this.stemAudios()) {
        const el = ref.nativeElement;
        el.muted = !audible.has(el.dataset['stem'] ?? '');
      }
    });
  }

  x(seconds: number): number {
    return (seconds / this.duration()) * this.width;
  }

  widthOf(s: Segment): number {
    return Math.max(this.x(s.endS) - this.x(s.startS), 0.5);
  }

  color(s: Segment): string {
    return KEY_RELATION_COLORS[s.keyRelation] ?? '#ccc';
  }

  label(s: Segment): string {
    return chordName(s.rootPc, s.quality, s.bassPc);
  }

  showsLabel(s: Segment): boolean {
    return this.widthOf(s) >= 22;
  }

  isAudible(name: string): boolean {
    return this.audible().has(name);
  }

  toggle(name: string): void {
    const next = new Set(this.audible());
    if (next.has(name)) {
      next.delete(name);
    } else {
      next.add(name);
    }
    this.audible.set(next);
  }

  /** Só este stem (ou só a mixagem). */
  solo(name: string): void {
    this.audible.set(new Set([name]));
  }

  seek(s: Segment): void {
    this.seekTo(s.startS);
  }

  onPlay(): void {
    this.playing.set(true);
    for (const ref of this.stemAudios()) {
      void ref.nativeElement.play().catch(() => undefined);
    }
    const tick = () => {
      const el = this.audio()?.nativeElement;
      if (el) {
        this.currentTime.set(el.currentTime);
        this.keepStemsInSync(el.currentTime);
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
    }
  }

  onSeeked(): void {
    const el = this.audio()?.nativeElement;
    if (el) {
      this.currentTime.set(el.currentTime);
      for (const ref of this.stemAudios()) {
        ref.nativeElement.currentTime = el.currentTime;
      }
    }
  }

  private seekTo(seconds: number): void {
    const el = this.audio()?.nativeElement;
    if (el) {
      el.currentTime = seconds;   // dispara (seeked), que alinha os stems
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
  protected readonly noteName = noteName;
  protected readonly percent = percent;
}
