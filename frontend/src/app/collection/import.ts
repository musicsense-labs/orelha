import { Component, computed, inject, output, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface ImportItem {
  key: string;
  file: string;
  artist: string;
  album: string;
  year: number | null;
  title: string;
  trackNo: number | null;
  fromTags: boolean;
  duplicate: boolean;
}

export interface ImportPreview {
  stagingId: string | null;
  items: ImportItem[];
}

export interface ImportReport {
  imported: { trackId: number; file: string; artist: string; album: string; title: string; trackNo: number | null; fromTags: boolean }[];
  skipped: { file: string; reason: string }[];
}

/** Linha da pré-visualização como a UI a edita. */
interface Row extends ImportItem {
  include: boolean;
}

const AUDIO = /\.(mp3|wav|flac|ogg|m4a|aac|aiff|aif)$/i;
const BATCH = 5;

/**
 * Importar uma pasta em dois passos: pré-visualização editável (tags lidas, duplicatas marcadas)
 * e confirmação. Pelo navegador os arquivos ficam em staging até confirmar; por caminho do servidor
 * ficam onde estão. Artista/álbum/título/número são o que o usuário deixar na tabela.
 */
@Component({
  selector: 'app-import',
  templateUrl: './import.html',
})
export class Import {
  private readonly http = inject(HttpClient);

  readonly done = output<void>();

  readonly files = signal<File[]>([]);
  readonly serverPath = signal('');
  readonly recursive = signal(true);
  readonly running = signal(false);
  readonly progress = signal<{ sent: number; total: number } | null>(null);
  readonly error = signal<string | null>(null);

  /** Pré-visualização em edição; stagingId != null quando veio de upload. */
  readonly stagingId = signal<string | null>(null);
  readonly rows = signal<Row[]>([]);
  readonly report = signal<ImportReport | null>(null);

  readonly audioFiles = computed(() => this.files().filter((f) => AUDIO.test(f.name)));
  readonly selectedCount = computed(() => this.rows().filter((r) => r.include).length);
  readonly hasPreview = computed(() => this.rows().length > 0);

  onFolder(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.files.set(Array.from(input.files ?? []));
    this.report.set(null);
  }

  // --- passo 1: preview ---------------------------------------------------------------------

  async stageFolder(): Promise<void> {
    const files = this.audioFiles();
    if (files.length === 0 || this.running()) {
      return;
    }
    await this.discard();
    this.running.set(true);
    this.error.set(null);
    this.report.set(null);
    try {
      // Upload em lotes; o staging de cada lote é confirmado separadamente, mas a tabela é uma só.
      const previews: ImportPreview[] = [];
      for (let i = 0; i < files.length; i += BATCH) {
        this.progress.set({ sent: i, total: files.length });
        const form = new FormData();
        for (const f of files.slice(i, i + BATCH)) {
          // O nome enviado carrega o caminho relativo da pasta: é o fallback de artista/álbum sem tags.
          form.append('files', f, (f as File & { webkitRelativePath?: string }).webkitRelativePath || f.name);
        }
        previews.push(await firstValueFrom(this.http.post<ImportPreview>('/api/tracks/import/stage', form)));
      }
      this.progress.set({ sent: files.length, total: files.length });
      this.stagingBatches = previews.map((p) => p.stagingId!).filter(Boolean);
      this.stagingId.set(this.stagingBatches[0] ?? null);
      this.rows.set(previews.flatMap((p) => p.items).map((i) => ({ ...i, include: !i.duplicate })));
      this.files.set([]);
    } catch (e: unknown) {
      this.error.set(detail(e));
    } finally {
      this.running.set(false);
    }
  }

  async previewServerPath(): Promise<void> {
    const path = this.serverPath().trim();
    if (!path || this.running()) {
      return;
    }
    await this.discard();
    this.running.set(true);
    this.error.set(null);
    this.report.set(null);
    this.progress.set(null);
    try {
      const preview = await firstValueFrom(this.http.post<ImportPreview>('/api/tracks/import-path/preview',
        { path, recursive: this.recursive() }));
      this.stagingBatches = [];
      this.stagingId.set(null);
      this.rows.set(preview.items.map((i) => ({ ...i, include: !i.duplicate })));
    } catch (e: unknown) {
      this.error.set(detail(e));
    } finally {
      this.running.set(false);
    }
  }

  // --- passo 2: edição ----------------------------------------------------------------------

  update(index: number, patch: Partial<Row>): void {
    this.rows.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  }

  swapArtistTitle(index: number): void {
    const r = this.rows()[index];
    this.update(index, { artist: r.title, title: r.artist });
  }

  applyToAll(field: 'artist' | 'album' | 'year', index: number): void {
    const value = this.rows()[index][field];
    this.rows.update((rows) => rows.map((r) => (r.include ? { ...r, [field]: value } : r)));
  }

  toggleAll(include: boolean): void {
    this.rows.update((rows) => rows.map((r) => ({ ...r, include })));
  }

  // --- passo 3: confirm ---------------------------------------------------------------------

  async confirm(): Promise<void> {
    const selected = this.rows().filter((r) => r.include);
    if (selected.length === 0 || this.running()) {
      return;
    }
    this.running.set(true);
    this.error.set(null);
    const merged: ImportReport = { imported: [], skipped: [] };
    try {
      if (this.stagingBatches.length === 0) {
        const report = await firstValueFrom(this.http.post<ImportReport>('/api/tracks/import/confirm',
          { stagingId: null, items: selected.map(strip) }));
        merged.imported.push(...report.imported);
        merged.skipped.push(...report.skipped);
      } else {
        // Cada lote de upload tem seu staging: confirma os itens que pertencem a ele.
        for (const id of this.stagingBatches) {
          const items = selected.filter((r) => r.key.includes(id)).map(strip);
          const report = await firstValueFrom(this.http.post<ImportReport>('/api/tracks/import/confirm',
            { stagingId: id, items }));
          merged.imported.push(...report.imported);
          merged.skipped.push(...report.skipped);
        }
      }
      this.report.set(merged);
      this.rows.set([]);
      this.stagingBatches = [];
      this.stagingId.set(null);
      this.done.emit();
    } catch (e: unknown) {
      this.error.set(detail(e));
    } finally {
      this.running.set(false);
    }
  }

  /** Cancela a pré-visualização; uploads em staging são apagados no servidor. */
  async discard(): Promise<void> {
    for (const id of this.stagingBatches) {
      try {
        await firstValueFrom(this.http.delete(`/api/tracks/import/stage/${id}`));
      } catch {
        // staging já sumiu: nada a fazer
      }
    }
    this.stagingBatches = [];
    this.stagingId.set(null);
    this.rows.set([]);
  }

  private stagingBatches: string[] = [];

  protected readonly numberOrNull = numberOrNull;
}

function strip(r: Row): ImportItem {
  const { include, ...item } = r;
  void include;
  return { ...item, year: numberOrNull(item.year), trackNo: numberOrNull(item.trackNo) };
}

function numberOrNull(v: unknown): number | null {
  if (v === null || v === undefined || v === '') {
    return null;
  }
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function detail(e: unknown): string {
  return (e as { error?: { detail?: string } })?.error?.detail ?? String(e);
}
