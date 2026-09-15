import { Component, computed, inject, input, output, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { SectionChord, SectionPart, SectionRequest, Sections as SectionsDto } from '../api/models';
import { KEY_RELATION_COLORS, chordName, formatTime } from '../shared/music';

/**
 * Resumo harmônico por parte (A, B, C… ou os nomes que o dono der): a progressão de um ciclo com
 * cada cifra na cor do eixo A, e "×N" quando o ciclo se repete. Edições (renomear, juntar com a
 * anterior) vão inteiras no PUT e viram MANUAL; "voltar à derivação" manda a lista vazia.
 */
@Component({
  selector: 'app-sections',
  templateUrl: './sections.html',
  styleUrl: './sections.scss',
})
export class Sections {
  readonly trackId = input.required<string>();
  readonly currentTime = input(0);
  /** Inícios de compasso (downbeats) e duração da faixa: bordas editadas encaixam no compasso. */
  readonly downbeats = input<number[]>([]);
  readonly duration = input(0);
  readonly seek = output<number>();

  private readonly http = inject(HttpClient);

  readonly sections = httpResource<SectionsDto>(() => `/api/tracks/${this.trackId()}/sections`);
  readonly editing = signal<number | null>(null);   // id da parte cujo nome está sendo editado
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly parts = computed(() => this.sections.value()?.parts ?? []);
  readonly source = computed(() => this.sections.value()?.source ?? null);

  readonly currentPart = computed(() => {
    const t = this.currentTime();
    return this.parts().find((p) => p.startS <= t && t < p.endS) ?? null;
  });

  readonly currentId = computed(() => this.currentPart()?.id ?? null);

  /** Em que repetição do ciclo a parte atual está (1..repeats); null fora dela ou sem repetição. */
  readonly currentRepeat = computed(() => {
    const p = this.currentPart();
    if (!p || p.repeats <= 1) {
      return null;
    }
    const cycle = p.cycleEndS - p.startS;
    if (cycle <= 0) {
      return null;
    }
    return Math.min(p.repeats, Math.floor((this.currentTime() - p.startS) / cycle) + 1);
  });

  /**
   * O acorde em execução, projetado no ciclo exibido: a parte mostra só a primeira repetição, então o
   * instante atual é reduzido módulo o comprimento do ciclo e casado com o último acorde que começa antes.
   */
  readonly currentChordStart = computed(() => {
    const p = this.currentPart();
    if (!p || p.chords.length === 0) {
      return null;
    }
    const cycle = p.cycleEndS - p.startS;
    const offset = cycle > 0 ? (this.currentTime() - p.startS) % cycle : 0;
    const inCycle = p.startS + offset;
    let found: number | null = null;
    for (const c of p.chords) {
      if (c.startS <= inCycle) {
        found = c.startS;
      } else {
        break;
      }
    }
    return found;
  });

  chord(c: SectionChord): string {
    return chordName(c.rootPc, c.quality, c.bassPc);
  }

  color(c: SectionChord): string {
    return c.keyRelation ? KEY_RELATION_COLORS[c.keyRelation] : '#c8c8c8';
  }

  rename(part: SectionPart, label: string): void {
    this.editing.set(null);
    const trimmed = label.trim();
    if (!trimmed || trimmed === part.label) {
      return;
    }
    this.save(this.parts().map((p) => (p.id === part.id ? { ...p, label: trimmed } : p)));
  }

  /** Junta a parte com a anterior: o ciclo e o "×N" da anterior ficam; o fim passa a ser o desta. */
  mergeWithPrevious(index: number): void {
    const parts = this.parts();
    if (index <= 0) {
      return;
    }
    const previous = parts[index - 1];
    const merged = { ...previous, endS: parts[index].endS };
    this.save([...parts.slice(0, index - 1), merged, ...parts.slice(index + 1)]);
  }

  revert(): void {
    this.save([]);
  }

  /** Descarta tudo: a música vira uma parte manual só, para marcar do zero com "dividir aqui". */
  reset(): void {
    this.save([{ id: 0, label: 'A', startS: 0, endS: this.duration(), cycleEndS: this.duration(), repeats: 1, chords: [] }]);
  }

  /** Corta a parte em execução no compasso mais próximo do playhead. */
  splitHere(): void {
    const parts = this.parts();
    const index = parts.findIndex((p) => p.id === this.currentId());
    if (index < 0) {
      return;
    }
    const p = parts[index];
    const cut = this.snap(this.currentTime());
    if (cut <= p.startS || cut >= p.endS) {
      return;
    }
    const cycle = p.cycleEndS - p.startS;
    const keepsCycle = p.repeats > 1 && p.cycleEndS <= cut;
    const first: SectionPart = {
      ...p, endS: cut,
      cycleEndS: keepsCycle ? p.cycleEndS : cut,
      repeats: keepsCycle ? Math.max(1, Math.round((cut - p.startS) / cycle)) : 1,
    };
    const second: SectionPart = { ...p, id: -1, label: this.nextLabel(parts), startS: cut, cycleEndS: p.endS, repeats: 1 };
    this.save([...parts.slice(0, index), first, second, ...parts.slice(index + 1)]);
  }

  /** Move a borda entre a parte {@code index} e a anterior (start) ou a seguinte (end) um compasso. */
  nudge(index: number, edge: 'start' | 'end', direction: -1 | 1): void {
    const parts = this.parts().map((p) => ({ ...p }));
    const p = parts[index];
    const boundary = edge === 'start' ? p.startS : p.endS;
    const target = this.neighbourDownbeat(boundary, direction);
    if (target == null) {
      return;
    }
    if (edge === 'start') {
      const previous = index > 0 ? parts[index - 1] : null;
      const low = previous ? previous.startS + this.minBar() : 0;
      if (target < low || target > p.endS - this.minBar()) {
        return;
      }
      p.startS = target;
      if (previous) {
        previous.endS = target;
        previous.cycleEndS = Math.min(previous.cycleEndS, target);
      }
      p.cycleEndS = Math.max(p.cycleEndS, target + this.minBar());
    } else {
      const next = index + 1 < parts.length ? parts[index + 1] : null;
      const high = next ? next.endS - this.minBar() : this.duration();
      if (target > high || target < p.startS + this.minBar()) {
        return;
      }
      p.endS = target;
      p.cycleEndS = Math.min(p.cycleEndS, target);
      if (next) {
        next.startS = target;
        next.cycleEndS = Math.max(next.cycleEndS, target + this.minBar());
      }
    }
    this.save(parts);
  }

  private minBar(): number {
    const d = this.downbeats();
    return d.length > 1 ? (d[d.length - 1] - d[0]) / (d.length - 1) : 1;
  }

  /** O downbeat mais próximo de um instante (o próprio instante se não há beats). */
  private snap(seconds: number): number {
    let best: number | null = null;
    for (const d of this.downbeats()) {
      if (best == null || Math.abs(d - seconds) < Math.abs(best - seconds)) {
        best = d;
      }
    }
    return best ?? seconds;
  }

  /** O downbeat estritamente antes (-1) ou depois (+1) de uma borda; a borda pode não estar num downbeat. */
  private neighbourDownbeat(boundary: number, direction: -1 | 1): number | null {
    const d = this.downbeats();
    if (d.length === 0) {
      return boundary + direction * 2;
    }
    const eps = 0.02;
    if (direction < 0) {
      for (let i = d.length - 1; i >= 0; i--) {
        if (d[i] < boundary - eps) {
          return d[i];
        }
      }
      return boundary > 0 ? 0 : null;
    }
    for (const t of d) {
      if (t > boundary + eps) {
        return t;
      }
    }
    return boundary < this.duration() ? this.duration() : null;
  }

  private nextLabel(parts: SectionPart[]): string {
    const used = new Set(parts.map((p) => p.label));
    for (let i = 0; i < 26; i++) {
      const letter = String.fromCharCode(65 + i);
      if (!used.has(letter)) {
        return letter;
      }
    }
    return 'parte ' + (parts.length + 1);
  }

  private save(parts: SectionPart[]): void {
    const body: SectionRequest[] = parts.map((p) => ({
      startS: p.startS, endS: p.endS, label: p.label, cycleEndS: p.cycleEndS, repeats: p.repeats,
    }));
    this.saving.set(true);
    this.error.set(null);
    this.http.put<SectionsDto>(`/api/tracks/${this.trackId()}/sections`, body).subscribe({
      next: () => {
        this.saving.set(false);
        this.sections.reload();
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(e?.error?.detail ?? e?.message ?? 'falha ao salvar');
      },
    });
  }

  protected readonly formatTime = formatTime;
}
