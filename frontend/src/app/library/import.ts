import { Component, computed, inject, output, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface ImportReport {
  imported: { trackId: number; file: string; artist: string; album: string; title: string; trackNo: number | null; fromTags: boolean }[];
  skipped: { file: string; reason: string }[];
}

const AUDIO = /\.(mp3|wav|flac|ogg|m4a|aac|aiff|aif)$/i;
const BATCH = 5;

/**
 * Importar uma pasta: pelo navegador (webkitdirectory, enviado em lotes de 5 com o caminho relativo
 * para o fallback de artista/álbum) ou por um caminho do servidor (arquivos ficam onde estão).
 * Artista, álbum, ano, título e número vêm das tags do arquivo.
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
  readonly report = signal<ImportReport | null>(null);
  readonly error = signal<string | null>(null);

  readonly audioFiles = computed(() => this.files().filter((f) => AUDIO.test(f.name)));

  onFolder(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.files.set(Array.from(input.files ?? []));
    this.report.set(null);
  }

  async uploadFolder(): Promise<void> {
    const files = this.audioFiles();
    if (files.length === 0 || this.running()) {
      return;
    }
    this.running.set(true);
    this.error.set(null);
    const merged: ImportReport = { imported: [], skipped: [] };
    try {
      for (let i = 0; i < files.length; i += BATCH) {
        this.progress.set({ sent: i, total: files.length });
        const form = new FormData();
        for (const f of files.slice(i, i + BATCH)) {
          // O nome enviado carrega o caminho relativo da pasta: é o fallback de artista/álbum sem tags.
          form.append('files', f, (f as File & { webkitRelativePath?: string }).webkitRelativePath || f.name);
        }
        const part = await firstValueFrom(this.http.post<ImportReport>('/api/tracks/import', form));
        merged.imported.push(...part.imported);
        merged.skipped.push(...part.skipped);
      }
      this.progress.set({ sent: files.length, total: files.length });
      this.report.set(merged);
      this.files.set([]);
      this.done.emit();
    } catch (e: unknown) {
      this.error.set((e as { error?: { detail?: string } })?.error?.detail ?? String(e));
    } finally {
      this.running.set(false);
    }
  }

  async importServerPath(): Promise<void> {
    const path = this.serverPath().trim();
    if (!path || this.running()) {
      return;
    }
    this.running.set(true);
    this.error.set(null);
    this.progress.set(null);
    try {
      const report = await firstValueFrom(this.http.post<ImportReport>('/api/tracks/import-path',
        { path, recursive: this.recursive() }));
      this.report.set(report);
      this.done.emit();
    } catch (e: unknown) {
      this.error.set((e as { error?: { detail?: string } })?.error?.detail ?? String(e));
    } finally {
      this.running.set(false);
    }
  }
}
