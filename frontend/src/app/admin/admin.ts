import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ActorSummary, AuditEvent, Track, WorkerState } from '../api/models';
import { Access } from '../shared/access';
import { errorText } from '../shared/errors';

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
  static readonly PAGE = 100;

  readonly access = inject(Access);
  private readonly http = inject(HttpClient);

  /** Fila de análise: o extrator ocupa a máquina por horas, e nem sempre é hora disso. */
  readonly worker = httpResource<WorkerState>(() => '/api/admin/worker');
  readonly workerBusy = signal(false);
  readonly workerError = signal<string | null>(null);

  readonly actor = signal<string>('');
  /** Página atual (0 = mais recentes) e, por página já visitada, o id antes do qual ela começa (keyset da API). */
  readonly page = signal(0);
  private readonly cursors = signal<(number | undefined)[]>([undefined]);
  readonly users = httpResource<ActorSummary[]>(() => '/api/admin/audit/users');
  readonly events = httpResource<AuditEvent[]>(() => {
    const before = this.cursors()[this.page()];
    return `/api/admin/audit?limit=${Admin.PAGE}${before != null ? '&before=' + before : ''}`
      + (this.actor() ? '&actor=' + encodeURIComponent(this.actor()) : '');
  });
  readonly tracks = httpResource<Track[]>(() => '/api/tracks');

  readonly forbidden = computed(() => this.events.error() != null && (this.events.error() as { status?: number })?.status === 403);
  readonly all = computed(() => (this.events.hasValue() ? this.events.value() : []));
  /** Total (do usuário filtrado ou de todos) vem do resumo por usuário — não há endpoint de contagem. */
  readonly total = computed(() => {
    const users = this.users.hasValue() ? this.users.value() : [];
    return this.actor() ? (users.find((u) => u.actor === this.actor())?.events ?? 0) : users.reduce((n, u) => n + u.events, 0);
  });
  readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / Admin.PAGE)));
  readonly hasNext = computed(() => this.all().length === Admin.PAGE);
  readonly titles = computed(() => {
    const map = new Map<number, string>();
    for (const t of this.tracks.hasValue() ? this.tracks.value() : []) {
      map.set(t.id, t.title);
    }
    return map;
  });

  /** Pausa ou retoma o consumo da fila. A faixa que já está no extrator termina de qualquer forma. */
  async setWorker(enabled: boolean): Promise<void> {
    this.workerBusy.set(true);
    this.workerError.set(null);
    try {
      await firstValueFrom(this.http.post<WorkerState>('/api/admin/worker?enabled=' + enabled, null));
      this.worker.reload();
    } catch (e: unknown) {
      this.workerError.set(errorText(e));
    } finally {
      this.workerBusy.set(false);
    }
  }

  filter(actor: string): void {
    this.actor.set(actor);
    this.page.set(0);
    this.cursors.set([undefined]);
  }

  /** Próxima página = os eventos anteriores ao último desta; a anterior já tem o cursor guardado. */
  next(): void {
    const last = this.all()[this.all().length - 1];
    if (!last || !this.hasNext()) {
      return;
    }
    const cursor = last.id;
    this.cursors.update((c) => { const copy = [...c]; copy[this.page() + 1] = cursor; return copy; });
    this.page.update((p) => p + 1);
  }

  prev(): void {
    this.page.update((p) => Math.max(0, p - 1));
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
      e.method === 'DELETE' && track ? 'excluiu do acervo' :
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
