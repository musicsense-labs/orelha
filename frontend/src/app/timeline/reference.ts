import { Component, computed, inject, input, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { SlicePipe } from '@angular/common';
import { Comparison, Reference } from '../api/models';
import { keyName, percent } from '../shared/music';

/** Slug no padrão das URLs do TheoryTab ("The Beatles" → "the-beatles"). */
export function theorytabSlug(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

/**
 * Análise humana de referência (TheoryTab, transcrita pelo dono) e a comparação com a nossa análise.
 * O TheoryTab não tem API para a análise por música, então o caminho é abrir a página, colar a
 * tonalidade e as seções em numerais aqui, e o backend compara seção a seção com as nossas partes.
 */
@Component({
  selector: 'app-reference',
  imports: [SlicePipe],
  templateUrl: './reference.html',
  styleUrl: './reference.scss',
})
export class ReferencePanel {
  readonly trackId = input.required<string>();
  readonly title = input('');
  readonly artist = input('');

  private readonly http = inject(HttpClient);

  readonly reference = httpResource<Reference>(() => `/api/tracks/${this.trackId()}/reference`);
  readonly comparison = httpResource<Comparison | null>(() => `/api/tracks/${this.trackId()}/reference/compare`);
  readonly open = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  /** Recurso em erro (backend antigo, 404) vale como "sem referência", não como falha da tela. */
  readonly ref = computed(() => (this.reference.hasValue() ? this.reference.value() : null));
  readonly has = computed(() => !!this.ref()?.rawText);
  readonly cmp = computed(() => (this.comparison.hasValue() ? this.comparison.value() : null));

  readonly viewUrl = computed(() =>
    `https://www.hooktheory.com/theorytab/view/${theorytabSlug(this.artist())}/${theorytabSlug(this.title())}`);
  readonly artistUrl = computed(() => {
    const slug = theorytabSlug(this.artist());
    return `https://www.hooktheory.com/theorytab/artists/${slug.charAt(0) || 'a'}/${slug}`;
  });

  readonly placeholder = 'tonalidade: G major\nIntro: I III IV iv\nVerse: I III IV iv\nChorus: I III IV iv\nBridge: ...';

  keyLabel(k: { tonicPc: number | null; mode: string | null } | null | undefined): string {
    return k && k.tonicPc != null && k.mode ? keyName(k.tonicPc, k.mode as never) : '—';
  }

  percent(v: number): string {
    return percent(v);
  }

  save(text: string, url: string): void {
    if (!text.trim()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.http.put<Reference>(`/api/tracks/${this.trackId()}/reference`, { text, url }).subscribe({
      next: () => {
        this.saving.set(false);
        this.open.set(false);
        this.reference.reload();
        this.comparison.reload();
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(e?.error?.detail ?? e?.message ?? 'falha ao salvar');
      },
    });
  }

  remove(): void {
    this.saving.set(true);
    this.http.delete(`/api/tracks/${this.trackId()}/reference`).subscribe({
      next: () => {
        this.saving.set(false);
        this.reference.reload();
        this.comparison.reload();
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(e?.error?.detail ?? e?.message ?? 'falha ao apagar');
      },
    });
  }
}
