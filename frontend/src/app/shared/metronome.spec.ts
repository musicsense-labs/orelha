import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Metronome } from './metronome';

/** AudioContext falso: registra os cliques agendados (quando, frequência) sem tocar nada. */
class FakeAudioContext {
  currentTime = 10;
  state = 'running';
  destination = {};
  scheduled: { when: number; hz: number }[] = [];
  resume = vi.fn();
  close = vi.fn();
  createGain() {
    return { gain: { value: 1, setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() }, connect: () => ({ connect: () => undefined }) };
  }
  createOscillator() {
    const ctx = this;
    const osc = {
      type: 'sine', frequency: { value: 0 },
      connect: () => ({ connect: () => undefined }),
      start: (when: number) => ctx.scheduled.push({ when, hz: osc.frequency.value }),
      stop: () => undefined,
    };
    return osc;
  }
}

describe('Metronome', () => {
  let ctx: FakeAudioContext;

  beforeEach(() => {
    ctx = new FakeAudioContext();
    vi.stubGlobal('AudioContext', function () { return ctx; });
  });

  afterEach(() => vi.unstubAllGlobals());

  const beats = [
    { timeS: 0.0, downbeat: true }, { timeS: 0.5, downbeat: false }, { timeS: 1.0, downbeat: false },
    { timeS: 1.5, downbeat: false }, { timeS: 2.0, downbeat: true },
  ];

  it('schedules only the beats inside the lookahead window, accenting downbeats', () => {
    const m = new Metronome();
    m.setBeats(beats);
    m.reset(0.9);
    m.schedule(0.9);                       // janela 0.9–1.15: só o beat de 1.0
    expect(ctx.scheduled).toEqual([{ when: 10.1, hz: 950 }]);
    m.schedule(1.4);                       // 1.5 entra; 1.0 já foi agendado e não repete
    expect(ctx.scheduled.map((s) => s.when)).toEqual([10.1, 10.1]);
    m.schedule(1.9);                       // downbeat de 2.0, acentuado
    expect(ctx.scheduled.at(-1)).toEqual({ when: 10.1, hz: 1400 });
  });

  it('reset after a seek moves the cursor and never re-schedules past beats', () => {
    const m = new Metronome();
    m.setBeats(beats);
    m.reset(1.3);
    m.schedule(1.3);
    expect(ctx.scheduled.map((s) => s.hz)).toEqual([950]);   // só 1.5 (dentro de 1.3–1.55)
    m.reset(0);
    m.schedule(0);
    expect(ctx.scheduled.at(-1)?.hz).toBe(1400);              // voltou ao downbeat 0.0
  });

  it('is silent before setBeats and after the last beat', () => {
    const m = new Metronome();
    m.schedule(5);
    expect(ctx.scheduled).toEqual([]);
    m.setBeats(beats);
    m.reset(3);
    m.schedule(3);
    expect(ctx.scheduled).toEqual([]);
  });
});
