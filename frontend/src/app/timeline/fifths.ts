import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChordQuality } from '../api/models';
import { noteName } from '../shared/music';

/** Índice no ciclo (0 = C, sentido horário por quintas) da classe de altura. */
const FIFTHS: number[] = Array.from({ length: 12 }, (_, i) => (i * 7) % 12);   // C G D A E B F♯ C♯ G♯ D♯ A♯ F
const INDEX_OF: number[] = FIFTHS.reduce((acc, pc, i) => { acc[pc] = i; return acc; }, new Array<number>(12));

const MINOR_FAMILY = new Set<ChordQuality>(['MIN', 'MIN6', 'MIN7', 'MINMAJ7', 'DIM', 'DIM7', 'HDIM7']);
const MINOR_THIRD_MODES = new Set(['MINOR', 'AEOLIAN', 'DORIAN', 'PHRYGIAN', 'LOCRIAN']);

/** Um setor anular entre dois raios e dois ângulos (graus, 0 = topo, horário). */
function wedge(r0: number, r1: number, a0: number, a1: number): string {
  const p = (r: number, a: number) => {
    const t = ((a - 90) * Math.PI) / 180;
    return `${(50 + r * Math.cos(t)).toFixed(2)} ${(50 + r * Math.sin(t)).toFixed(2)}`;
  };
  return `M ${p(r1, a0)} A ${r1} ${r1} 0 0 1 ${p(r1, a1)} L ${p(r0, a1)} A ${r0} ${r0} 0 0 0 ${p(r0, a0)} Z`;
}

/**
 * Ciclo das quintas: anel de fora com as maiores (C no topo, horário por quintas), anel de dentro com as
 * relativas menores na mesma posição (Am sob C). O acorde atual acende o setor da sua fundamental — no anel
 * de fora se a tríade é maior (ou sus/power/7), no de dentro se é menor ou diminuta — com a cor do eixo A
 * (relação com a tonalidade), e a tônica da tonalidade recebe um contorno no anel do seu modo.
 */
@Component({
  selector: 'app-fifths',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 100 100" [attr.width]="size()" [attr.height]="size()" aria-hidden="true">
      @for (w of wedges(); track w.key) {
        <path [attr.d]="w.d" [attr.fill]="w.fill" [class.lit]="w.lit" [class.tonic]="w.tonic" />
      }
      @for (w of wedges(); track w.key) {
        <text [attr.x]="w.tx" [attr.y]="w.ty" [attr.font-size]="w.inner ? 7.5 : 9" [class.lit]="w.lit"
              text-anchor="middle" dominant-baseline="central">{{ w.label }}</text>
      }
    </svg>
  `,
  styles: `
    :host { display: inline-block; line-height: 0; }
    path { stroke: var(--surface); stroke-width: 0.8; }
    path.tonic { stroke: var(--ink); stroke-width: 1.6; }
    text { font-family: var(--font-mono); fill: var(--muted); pointer-events: none; }
    text.lit { fill: var(--paper); font-weight: 700; }
  `,
})
export class Fifths {
  readonly rootPc = input<number | null>(null);
  readonly quality = input<ChordQuality>('UNKNOWN');
  readonly tonicPc = input<number | null>(null);
  readonly mode = input<string | null>(null);
  /** Cor do eixo A do acorde atual (a mesma do badge). */
  readonly color = input<string>('#ccc');
  readonly size = input(84);

  readonly wedges = computed(() => {
    const root = this.rootPc();
    const q = this.quality();
    const chordInner = MINOR_FAMILY.has(q);
    const chordIndex = root == null || q === 'NO_CHORD' || q === 'UNKNOWN' ? -1 : INDEX_OF[(root + (chordInner ? 3 : 0)) % 12];
    const tonic = this.tonicPc();
    const tonicInner = MINOR_THIRD_MODES.has((this.mode() ?? '').toUpperCase());
    const tonicIndex = tonic == null ? -1 : INDEX_OF[(tonic + (tonicInner ? 3 : 0)) % 12];
    const out: { key: string; d: string; fill: string; lit: boolean; tonic: boolean; inner: boolean; label: string; tx: number; ty: number }[] = [];
    for (let i = 0; i < 12; i++) {
      const a0 = i * 30 - 15;
      const a1 = a0 + 30;
      const mid = ((a0 + 15 - 90) * Math.PI) / 180;
      const major = FIFTHS[i];
      for (const inner of [false, true]) {
        const lit = i === chordIndex && inner === chordInner;
        const r = inner ? 27 : 42;
        out.push({
          key: (inner ? 'i' : 'o') + i,
          d: inner ? wedge(19, 34, a0, a1) : wedge(34, 49, a0, a1),
          fill: lit ? this.color() : inner ? 'var(--panel)' : 'var(--surface)',
          lit,
          tonic: i === tonicIndex && inner === tonicInner,
          inner,
          label: inner ? noteName((major + 9) % 12).toLowerCase() : noteName(major),
          tx: +(50 + r * Math.cos(mid)).toFixed(2),
          ty: +(50 + r * Math.sin(mid)).toFixed(2),
        });
      }
    }
    // a tônica por cima, para o contorno não ser coberto pelos vizinhos
    return out.sort((a, b) => Number(a.tonic) - Number(b.tonic));
  });
}
