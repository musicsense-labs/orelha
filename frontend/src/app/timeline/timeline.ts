import {
  Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, viewChild, viewChildren,
} from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Beat, Segment, Timeline as TimelineDto, Track } from '../api/models';
import { Metronome } from '../shared/metronome';
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
  readonly beats = httpResource<Beat[]>(() => `/api/tracks/${this.id()}/beats`);

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
  readonly laneHeight = 44;

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

  readonly playheadX = computed(() => this.x(this.currentTime()));

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

  private seekTo(seconds: number): void {
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
  protected readonly noteName = noteName;
  protected readonly percent = percent;
}
