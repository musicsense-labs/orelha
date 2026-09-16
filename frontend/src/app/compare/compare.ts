import { Component, computed, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Artist, Comparison } from '../api/models';
import { DegreeBars } from '../shared/degree-bars';
import { Heatmap } from '../shared/heatmap';
import { KEY_RELATION_ORDER, percent, relationHint, relationLabel } from '../shared/music';

/** Dois artistas lado a lado: distâncias, matrizes e distribuições. */
@Component({
  selector: 'app-compare',
  imports: [RouterLink, Heatmap, DegreeBars],
  templateUrl: './compare.html',
})
export class Compare {
  readonly artists = httpResource<Artist[]>(() => '/api/artists');

  readonly a = signal<number | null>(null);
  readonly b = signal<number | null>(null);

  readonly comparison = httpResource<Comparison>(() => {
    const a = this.a();
    const b = this.b();
    return a != null && b != null && a !== b ? `/api/collection/compare?a=${a}&b=${b}` : undefined;
  });

  readonly degreeSeries = computed(() => {
    const c = this.comparison.value();
    return c ? [{ name: c.a.name, values: c.a.degrees.byDuration }, { name: c.b.name, values: c.b.degrees.byDuration }] : [];
  });

  readonly keyRelationRows = computed(() => {
    const c = this.comparison.value();
    return c ? KEY_RELATION_ORDER
      .filter((r) => c.a.keyRelations[r] || c.b.keyRelations[r])
      .map((r) => ({ relation: r, a: c.a.keyRelations[r]?.byDuration ?? 0, b: c.b.keyRelations[r]?.byDuration ?? 0 })) : [];
  });

  select(which: 'a' | 'b', value: string): void {
    const id = value === '' ? null : Number(value);
    (which === 'a' ? this.a : this.b).set(id);
  }

  protected readonly percent = percent;
  protected readonly relationLabel = relationLabel;
  protected readonly relationHint = relationHint;
}
