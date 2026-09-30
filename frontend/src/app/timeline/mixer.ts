import { Component, DestroyRef, ElementRef, effect, inject, input, signal, viewChildren } from '@angular/core';
import { Beat } from '../api/models';
import { Metronome } from '../shared/metronome';
import { StemPanner } from '../shared/panner';

/**
 * Como a faixa soa: um canal por fonte — a mixagem (o `<audio>` mestre, que é da timeline), cada stem (um
 * `<audio>` escondido daqui) e o metrônomo —, com liga/desliga, volume e balanço L/R, e a velocidade de
 * todos juntos. Mix e stems são mutuamente exclusivos (ligar o mix silencia os stems; ligar um stem
 * silencia o mix); stems se combinam entre si; duplo clique = solo. O metrônomo (Web Audio sobre os beats do
 * run) é independente e toca por cima.
 *
 * Quando é da timeline: ela é dona do relógio (play, pause, seek) e avisa o mixer a cada mudança e a cada
 * quadro, para os stems seguirem o mestre e o metrônomo agendar os cliques.
 */
@Component({
  selector: 'app-mixer',
  templateUrl: './mixer.html',
  styleUrl: './mixer.scss',
})
export class Mixer {
  readonly trackId = input.required<number>();
  readonly stems = input<string[] | undefined>(undefined);
  readonly stemsLoading = input(false);
  readonly beats = input<Beat[]>([]);
  /** O `<audio>` da mixagem: o mixer decide se ele soa, quão alto e para que lado; o tempo é da timeline. */
  readonly master = input<HTMLAudioElement | undefined>(undefined);

  readonly stemLabels: Record<string, string> = { drums: 'bateria', bass: 'baixo', other: 'guitarras/teclados', vocals: 'voz' };

  /** O que está audível: 'mix' ou um conjunto de stems — nunca os dois. */
  readonly audible = signal<ReadonlySet<string>>(new Set(['mix']));
  /** Volume por canal (0–1), inclusive 'mix' e 'metronome'. */
  readonly volumes = signal<Record<string, number>>({ mix: 1, drums: 1, bass: 1, other: 1, vocals: 1, metronome: 0.8 });
  /** Balanço L/R por canal (-1 esquerda … +1 direita), inclusive 'mix' e 'metronome'. */
  readonly pans = signal<Record<string, number>>({ mix: 0, drums: 0, bass: 0, other: 0, vocals: 0, metronome: 0 });
  readonly metronomeOn = signal(false);

  private readonly stemAudios = viewChildren<ElementRef<HTMLAudioElement>>('stemAudio');
  private readonly metronome = new Metronome();
  private readonly panner = new StemPanner();
  private rate = 1;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.metronome.dispose();
      this.panner.dispose();
    });
    // Mudo e volume seguem os signals; o mestre continua tocando mesmo mudo para manter o relógio.
    effect(() => {
      const audible = this.audible();
      const volumes = this.volumes();
      const master = this.master();
      if (master) {
        master.muted = !audible.has('mix');
        master.volume = volumes['mix'] ?? 1;
      }
      for (const el of this.stemElements()) {
        const name = el.dataset['stem'] ?? '';
        el.muted = !audible.has(name);
        el.volume = volumes[name] ?? 1;
      }
      this.metronome.setVolume(volumes['metronome'] ?? 0.8);
    });
    // Balanço: mix e stems pelo grafo Web Audio (o elemento continua fonte e relógio); metrônomo no dele.
    effect(() => {
      const pans = this.pans();
      const master = this.master();
      if (master) {
        this.panner.setPan(master, pans['mix'] ?? 0);
      }
      for (const el of this.stemElements()) {
        this.panner.setPan(el, pans[el.dataset['stem'] ?? ''] ?? 0);
      }
      this.metronome.setPan(pans['metronome'] ?? 0);
    });
    effect(() => {
      this.metronome.setBeats(this.beats());
    });
  }

  // --- o relógio da timeline avisa -------------------------------------------------------------

  /** Começou a tocar em {@code t}: stems acompanham, na velocidade corrente. */
  started(t: number): void {
    this.panner.resume();
    this.applyRate();
    for (const el of this.stemElements()) {
      void el.play().catch(() => undefined);
    }
    this.metronome.reset(t);
  }

  stopped(t: number): void {
    for (const el of this.stemElements()) {
      el.pause();
    }
    this.metronome.reset(t);
  }

  /** O mestre pulou para {@code t}: stems e metrônomo vão junto. */
  seeked(t: number): void {
    for (const el of this.stemElements()) {
      el.currentTime = t;
    }
    this.metronome.reset(t);
  }

  /** A cada quadro tocando: stem que derivou mais de 150 ms é corrigido, e o metrônomo agenda os próximos cliques. */
  tick(t: number): void {
    for (const el of this.stemElements()) {
      if (Math.abs(el.currentTime - t) > 0.15) {
        el.currentTime = t;
      }
    }
    if (this.metronomeOn()) {
      this.metronome.schedule(t, this.rate);
    }
  }

  /** Velocidade de todos juntos, sem mudar o tom; os cliques já agendados foram calculados na antiga. */
  setRate(rate: number, t: number): void {
    this.rate = rate;
    this.applyRate();
    this.metronome.reset(t);
  }

  // --- canais -----------------------------------------------------------------------------------

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

  /**
   * Perto do centro (±0,15) o balanço gruda em 0: achar o meio no slider de 46 px é difícil a olho. Vindo do
   * próprio slider, o valor grudado é reescrito nele — o binding [value] não reescreve quando o sinal já era 0.
   */
  setPan(name: string, value: number | HTMLInputElement): void {
    const el = typeof value === 'number' ? null : value;
    const raw = typeof value === 'number' ? value : +value.value;
    const snapped = Math.min(1, Math.max(-1, Math.abs(raw) < 0.15 ? 0 : raw));
    this.pans.update((p) => ({ ...p, [name]: snapped }));
    if (el) {
      el.value = String(snapped);
    }
  }

  pan(name: string): number {
    return this.pans()[name] ?? 0;
  }

  panLabel(name: string): string {
    const v = this.pan(name);
    return v === 0 ? 'centro' : `${Math.round(Math.abs(v) * 100)}% ${v < 0 ? 'esquerda' : 'direita'}`;
  }

  toggleMetronome(): void {
    this.metronomeOn.update((on) => !on);
    this.metronome.reset(this.master()?.currentTime ?? 0);
  }

  label(name: string): string {
    return this.stemLabels[name] ?? name;
  }

  /** playbackRate no mestre e em cada stem (os stems podem chegar depois do play: started reaplica). */
  private applyRate(): void {
    const master = this.master();
    for (const el of master ? [master, ...this.stemElements()] : this.stemElements()) {
      el.preservesPitch = true;
      if (el.playbackRate !== this.rate) {
        el.playbackRate = this.rate;
      }
    }
  }

  private stemElements(): HTMLAudioElement[] {
    return this.stemAudios().map((r) => r.nativeElement);
  }
}
