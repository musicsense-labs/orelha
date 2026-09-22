import { Component, computed, inject, input, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Album, MusicBrainzCandidate, MusicBrainzCandidates } from '../api/models';
import { errorText } from '../shared/errors';

/**
 * Identidade do álbum no MusicBrainz (dados CC0). O backend busca e pontua; quem escolhe é o dono — nada é
 * gravado sem clique. O que entra é o MBID e o ano da primeira edição, que passa a valer como era; o ano que
 * veio das tags (a edição no disco) continua guardado e aparece ao lado.
 */
@Component({
  selector: 'app-musicbrainz',
  template: `
    <section class="card mb">
      <h2>
        MusicBrainz
        @if (album.value(); as a) {
          @if (a.mbid) {
            <small class="muted">identificado · <a [href]="'https://musicbrainz.org/release-group/' + a.mbid" target="_blank" rel="noreferrer">abrir ↗</a></small>
          }
        }
      </h2>

      @if (album.value(); as a) {
        <p class="years">
          <span><span class="lbl">era</span> <strong>{{ a.effectiveYear ?? '—' }}</strong></span>
          @if (a.firstReleased && a.year && a.firstReleased !== a.year) {
            <span class="muted"><span class="lbl">edição no disco</span> {{ a.year }}</span>
          }
          <span class="muted"><span class="lbl">origem</span> {{ source(a) }}</span>
        </p>

        <div class="row">
          <button type="button" (click)="search()" [disabled]="searching()">
            {{ searching() ? 'procurando…' : (a.mbid ? 'procurar de novo' : 'identificar') }}
          </button>
          @if (a.mbid) {
            <button type="button" (click)="forget()" [disabled]="saving()">desfazer</button>
          }
        </div>

        @if (error(); as e) { <p class="error">{{ e }}</p> }

        @if (result(); as r) {
          @if (r.candidates.length === 0) {
            <p class="muted">Nada encontrado para "{{ r.searchedTitle }}".</p>
          } @else {
            <p class="muted small-note">busca por "{{ r.searchedTitle }}" · o mais parecido primeiro</p>
            <table>
              <thead>
                <tr><th>álbum</th><th>artista</th><th>tipo</th><th class="num">1ª edição</th><th class="num">parecido</th><th></th></tr>
              </thead>
              <tbody>
                @for (c of r.candidates; track c.mbid) {
                  <tr [class.chosen]="c.mbid === a.mbid">
                    <td>
                      <a [href]="'https://musicbrainz.org/release-group/' + c.mbid" target="_blank" rel="noreferrer">{{ c.title }}</a>
                      @if (c.disambiguation) { <small class="muted"> ({{ c.disambiguation }})</small> }
                    </td>
                    <td>{{ c.artist }}</td>
                    <td class="muted">{{ c.primaryType ?? '—' }}</td>
                    <td class="num">{{ c.firstReleased ?? '—' }}</td>
                    <td class="num mono" [class.confident]="c.confident">{{ (c.score * 100).toFixed(0) }}%</td>
                    <td class="num">
                      <button type="button" class="mini" (click)="choose(c)" [disabled]="saving() || c.mbid === a.mbid">
                        {{ c.mbid === a.mbid ? 'atual' : 'é este' }}
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          }
        }
      }
    </section>
  `,
  styles: `
    .mb h2 { display: flex; align-items: baseline; gap: 0.6rem; }
    .mb h2 small { font-size: 0.8rem; font-weight: 400; }
    .years { display: flex; gap: 1.5rem; align-items: baseline; margin: 0.2rem 0 0.6rem; }
    .lbl { font-family: var(--font-mono); font-size: 0.65rem; letter-spacing: 0.18em; text-transform: uppercase; color: var(--muted); margin-right: 0.35rem; }
    .years strong { font-size: 1.35rem; }
    .row { display: flex; gap: 0.5rem; }
    .mono { font-family: var(--font-mono); font-size: 0.8rem; }
    .confident { color: var(--ok); font-weight: 700; }
    tr.chosen td { background: var(--panel); }
    .small-note { font-size: 0.8rem; margin: 0.4rem 0 0.2rem; }
  `,
})
export class MusicBrainz {
  readonly albumId = input.required<string>();
  private readonly http = inject(HttpClient);

  private readonly tick = signal(0);
  readonly album = httpResource<Album>(() => `/api/albums/${this.albumId()}?tick=${this.tick()}`);
  readonly result = signal<MusicBrainzCandidates | null>(null);
  readonly searching = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly configured = computed(() => this.result()?.configured ?? true);

  source(a: Album): string {
    return a.metadataSource === 'MUSICBRAINZ' ? 'MusicBrainz' : a.metadataSource === 'MANUAL' ? 'digitado à mão' : 'tags do arquivo';
  }

  async search(): Promise<void> {
    this.searching.set(true);
    this.error.set(null);
    try {
      this.result.set(await firstValueFrom(
        this.http.get<MusicBrainzCandidates>(`/api/albums/${this.albumId()}/musicbrainz/candidates`)));
    } catch (e: unknown) {
      this.error.set(errorText(e));
    } finally {
      this.searching.set(false);
    }
  }

  async choose(c: MusicBrainzCandidate): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.http.put(`/api/albums/${this.albumId()}/musicbrainz`,
        { mbid: c.mbid, firstReleased: c.firstReleased }));
      this.tick.update((n) => n + 1);
    } catch (e: unknown) {
      this.error.set(errorText(e));
    } finally {
      this.saving.set(false);
    }
  }

  async forget(): Promise<void> {
    this.saving.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.http.delete(`/api/albums/${this.albumId()}/musicbrainz`));
      this.tick.update((n) => n + 1);
    } catch (e: unknown) {
      this.error.set(errorText(e));
    } finally {
      this.saving.set(false);
    }
  }
}
