import { Component, DestroyRef, ElementRef, computed, inject, input, signal, viewChild } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Segment, Timeline as TimelineDto, Track } from '../api/models';
import {
  KEY_RELATION_COLORS, KEY_RELATION_ORDER, chordName, formatTime, keyName, noteName, percent,
} from '../shared/music';

/**
 * Timeline harmônica em SVG dirigido por signals, sincronizada com o <audio> nativo.
 * O playhead é um computed sobre currentTime; clicar num segmento faz seek.
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

  private readonly audio = viewChild<ElementRef<HTMLAudioElement>>('audio');

  readonly currentTime = signal(0);
  readonly playing = signal(false);
  readonly hovered = signal<Segment | null>(null);

  /** Largura lógica do SVG; o viewBox escala para a largura real. */
  readonly width = 1200;
  readonly laneHeight = 44;

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

  seek(s: Segment): void {
    const el = this.audio()?.nativeElement;
    if (el) {
      el.currentTime = s.startS;
      this.currentTime.set(s.startS);
    }
  }

  onPlay(): void {
    this.playing.set(true);
    const tick = () => {
      const el = this.audio()?.nativeElement;
      if (el) {
        this.currentTime.set(el.currentTime);
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
    if (el) {
      this.currentTime.set(el.currentTime);
    }
  }

  onSeeked(): void {
    const el = this.audio()?.nativeElement;
    if (el) {
      this.currentTime.set(el.currentTime);
    }
  }

  protected readonly formatTime = formatTime;
  protected readonly noteName = noteName;
  protected readonly percent = percent;
}
