import { Component, computed, input } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { HarmonicProfile, PedalPassage } from '../api/models';
import { DegreeBars } from '../shared/degree-bars';
import { Heatmap } from '../shared/heatmap';
import { KEY_RELATION_ORDER, formatTime, noteName, percent, relationHint, relationLabel } from '../shared/music';

/** Perfil harmônico de um artista ou álbum: eixo A, graus, matriz de transição, relações, timbre. */
@Component({
  selector: 'app-profile',
  imports: [RouterLink, Heatmap, DegreeBars],
  templateUrl: './profile.html',
})
export class Profile {
  readonly scope = input.required<'artists' | 'albums'>();
  readonly id = input.required<string>();

  readonly profile = httpResource<HarmonicProfile>(() => `/api/collection/${this.scope()}/${this.id()}/profile`);
  readonly pedals = httpResource<PedalPassage[]>(() =>
    this.scope() === 'artists' ? `/api/collection/artists/${this.id()}/pedal-passages` : undefined);

  readonly keyRelationRows = computed(() => {
    const p = this.profile.value();
    return p ? KEY_RELATION_ORDER.filter((r) => p.keyRelations[r]).map((r) => ({ relation: r, ...p.keyRelations[r] })) : [];
  });

  readonly relationRows = computed(() => {
    const p = this.profile.value();
    return p ? Object.entries(p.relations).map(([relation, s]) => ({ relation, ...s }))
      .sort((a, b) => b.bySegment - a.bySegment) : [];
  });

  readonly degreeSeries = computed(() => {
    const p = this.profile.value();
    return p ? [{ name: 'por segmento', values: p.degrees.bySegment }, { name: 'por duração', values: p.degrees.byDuration }] : [];
  });

  readonly topTransitions = computed(() => {
    const p = this.profile.value();
    if (!p) {
      return [];
    }
    const rows: { from: string; to: string; count: number; share: number }[] = [];
    p.transitions.counts.forEach((row, from) => row.forEach((c, to) => {
      if (c > 0) {
        rows.push({ from: p.degreeLabels[from], to: p.degreeLabels[to], count: c, share: c / p.transitions.total });
      }
    }));
    return rows.sort((a, b) => b.count - a.count).slice(0, 10);
  });

  readonly timbreStems = computed(() => {
    const rows = this.profile.value()?.timbreByAlbum ?? [];
    return [...new Set(rows.map((r) => r.stem))];
  });

  protected readonly percent = percent;
  protected readonly formatTime = formatTime;
  protected readonly noteName = noteName;
  protected readonly relationLabel = relationLabel;
  protected readonly relationHint = relationHint;
}
