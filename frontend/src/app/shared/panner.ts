/**
 * Balanço L/R dos <audio> do player (mix e stems) com Web Audio: cada elemento vira uma
 * MediaElementSource ligada a um StereoPannerNode e daí à saída. O elemento continua sendo a fonte
 * e o relógio (streaming, sem decodificar em memória); volume e mudo seguem no próprio elemento.
 * Um elemento só pode ser ligado uma vez: o mapa guarda o panner de cada um.
 * -1 = tudo à esquerda, 0 = centro, +1 = tudo à direita (pan de potência constante).
 */
export class StemPanner {
  private ctx: AudioContext | null = null;
  private readonly panners = new WeakMap<HTMLMediaElement, StereoPannerNode>();

  setPan(el: HTMLMediaElement, value: number): void {
    const v = Math.min(1, Math.max(-1, value));
    let panner = this.panners.get(el);
    if (!panner) {
      if (v === 0) {
        return;   // centro sem grafo: não vale ligar o elemento ao Web Audio só para isso
      }
      const ctx = this.context();
      try {
        panner = ctx.createStereoPanner();
        ctx.createMediaElementSource(el).connect(panner).connect(ctx.destination);
      } catch {
        return;   // já ligado por outro caminho ou elemento inválido: fica sem balanço
      }
      this.panners.set(el, panner);
    }
    panner.pan.setTargetAtTime(v, this.context().currentTime, 0.01);
  }

  /** O contexto começa suspenso até o primeiro gesto; chamar ao dar play. */
  resume(): void {
    if (this.ctx && this.ctx.state === 'suspended') {
      void this.ctx.resume();
    }
  }

  private context(): AudioContext {
    if (!this.ctx) {
      this.ctx = new AudioContext();
    }
    return this.ctx;
  }

  dispose(): void {
    void this.ctx?.close();
    this.ctx = null;
  }
}
