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
