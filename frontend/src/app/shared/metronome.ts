/**
 * Metrônomo sobre a grade de beats do extrator, sintetizado com Web Audio: cliques curtos
 * (downbeat acentuado) agendados um pouco à frente do relógio do <audio> mestre.
 * Sem análise aqui: só toca o que o backend já mediu.
 */
export interface BeatLike {
  timeS: number;
  downbeat: boolean;
}

const LOOKAHEAD_S = 0.25;
const DOWNBEAT_HZ = 1400;
const BEAT_HZ = 950;

export class Metronome {
  private ctx: AudioContext | null = null;
  private gain: GainNode | null = null;
  private beats: BeatLike[] = [];
  private nextIndex = 0;
  private lastMasterTime = -1;
  private volumeValue = 0.8;
  private scheduled: OscillatorNode[] = [];

  setBeats(beats: BeatLike[]): void {
    this.beats = [...beats].sort((a, b) => a.timeS - b.timeS);
    this.reset(this.lastMasterTime);
  }

  setVolume(v: number): void {
    this.volumeValue = v;
    if (this.gain && this.ctx) {
      this.gain.gain.setValueAtTime(v, this.ctx.currentTime);
    }
  }

  /** Reposiciona após seek/pause: cancela cliques pendentes e volta o cursor de beats. */
  reset(masterTime: number): void {
    for (const osc of this.scheduled) {
      try {
        osc.stop();
      } catch {
        // já parou
      }
    }
    this.scheduled = [];
    this.lastMasterTime = masterTime;
    this.nextIndex = masterTime < 0 ? 0 : this.beats.findIndex((b) => b.timeS >= masterTime);
    if (this.nextIndex < 0) {
      this.nextIndex = this.beats.length;
    }
  }

  /** Chamado a cada frame enquanto toca: agenda os beats dentro da janela de lookahead. */
  schedule(masterTime: number): void {
    const ctx = this.context();
    if (ctx.state === 'suspended') {
      void ctx.resume();
    }
    if (masterTime < this.lastMasterTime - 0.05) {
      this.reset(masterTime);   // voltou no tempo sem passar pelo seek
    }
    this.lastMasterTime = masterTime;
    const horizon = masterTime + LOOKAHEAD_S;
    while (this.nextIndex < this.beats.length && this.beats[this.nextIndex].timeS <= horizon) {
      const beat = this.beats[this.nextIndex++];
      const when = ctx.currentTime + Math.max(0, beat.timeS - masterTime);
      this.click(when, beat.downbeat);
    }
  }

  private click(when: number, accent: boolean): void {
    const ctx = this.context();
    const osc = ctx.createOscillator();
    const env = ctx.createGain();
    osc.type = 'square';
    osc.frequency.value = accent ? DOWNBEAT_HZ : BEAT_HZ;
    env.gain.setValueAtTime(0.0001, when);
    env.gain.exponentialRampToValueAtTime(accent ? 1 : 0.55, when + 0.002);
    env.gain.exponentialRampToValueAtTime(0.0001, when + (accent ? 0.06 : 0.035));
    osc.connect(env).connect(this.gain!);
    osc.start(when);
    osc.stop(when + 0.08);
    this.scheduled.push(osc);
    if (this.scheduled.length > 64) {
      this.scheduled.splice(0, 32);
    }
  }

  private context(): AudioContext {
    if (!this.ctx) {
      this.ctx = new AudioContext();
      this.gain = this.ctx.createGain();
      this.gain.gain.value = this.volumeValue;
      this.gain.connect(this.ctx.destination);
    }
    return this.ctx;
  }

  dispose(): void {
    this.reset(-1);
    void this.ctx?.close();
    this.ctx = null;
    this.gain = null;
  }
}
