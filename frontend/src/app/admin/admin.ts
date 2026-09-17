import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ActorSummary, AuditEvent, Track } from '../api/models';
import { Access } from '../shared/access';

/**
 * Aba do administrador: quem entrou e o que fez (auditoria gravada pelo backend em cada entrada, faixa
 * aberta e ação). Só o dono vê; para os outros o backend responde 403 e a tela diz isso.
 */
@Component({
  selector: 'app-admin',
  imports: [RouterLink],
  templateUrl: './admin.html',
  styleUrl: './admin.scss',
})
export class Admin {
  readonly access = inject(Access);
  private readonly http = inject(HttpClient);

  readonly actor = signal<string>('');
  readonly users = httpResource<ActorSummary[]>(() => '/api/admin/audit/users');
  readonly events = httpResource<AuditEvent[]>(() =>
    `/api/admin/audit?limit=200${this.actor() ? '&actor=' + encodeURIComponent(this.actor()) : ''}`);
  readonly tracks = httpResource<Track[]>(() => '/api/tracks');
  readonly more = signal<AuditEvent[]>([]);
  readonly loadingMore = signal(false);

  readonly forbidden = computed(() => this.events.error() != null && (this.events.error() as { status?: number })?.status === 403);
  readonly all = computed(() => [...(this.events.hasValue() ? this.events.value() : []), ...this.more()]);
  readonly titles = computed(() => {
    const map = new Map<number, string>();
    for (const t of this.tracks.hasValue() ? this.tracks.value() : []) {
      map.set(t.id, t.title);
    }
    return map;
  });

  filter(actor: string): void {
    this.more.set([]);
    this.actor.set(actor);
  }

  loadMore(): void {
    const last = this.all()[this.all().length - 1];
    if (!last) {
      return;
    }
    this.loadingMore.set(true);
    const params: Record<string, string> = { limit: '200', before: String(last.id) };
    if (this.actor()) {
      params['actor'] = this.actor();
    }
    this.http.get<AuditEvent[]>('/api/admin/audit', { params }).subscribe({
      next: (page) => {
        this.more.update((m) => [...m, ...page]);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false),
    });
  }

  /** O que a pessoa fez, em português, a partir de método e caminho. */
  describe(e: AuditEvent): string {
    const track = e.trackId != null ? (this.titles().get(e.trackId) ?? `faixa ${e.trackId}`) : null;
    if (e.kind === 'ENTER') {
      return 'entrou';
    }
    if (e.kind === 'OPEN_TRACK') {
      return `abriu ${track}`;
    }
    const p = e.path;
    const what =
      p.endsWith('/lyrics') ? 'editou a letra' :
      p.endsWith('/key') ? 'trocou a tonalidade' :
      p.endsWith('/sections') ? 'editou as partes' :
      p.endsWith('/sections/derive') ? 're-derivou as partes' :
      p.endsWith('/canonical-run') ? 'trocou o run canônico' :
      p.endsWith('/analyze') ? 'pediu re-análise' :
      p.endsWith('/reference') ? (e.method === 'DELETE' ? 'apagou a referência' : 'gravou a referência') :
      p.endsWith('/upload') ? 'enviou uma faixa' :
      p.includes('/import') ? 'importou pasta' :
      e.method === 'DELETE' && track ? 'apagou' :
      p.startsWith('/api/artists') ? 'mexeu em artista' :
      p.startsWith('/api/albums') ? 'mexeu em álbum' :
      `${e.method} ${p}`;
    return track ? `${what} · ${track}` : what;
  }

  when(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }
}
