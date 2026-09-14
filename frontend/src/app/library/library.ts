import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { HttpClient, httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Album, Artist, Track } from '../api/models';
import { formatTime } from '../shared/music';
import { Import } from './import';

/**
 * Catálogo: artistas → álbuns → faixas, com o estado do último run; e o formulário de upload,
 * que cria artista/álbum se preciso, envia o arquivo e acompanha a análise até DONE/FAILED.
 */
@Component({
  selector: 'app-library',
  imports: [RouterLink, Import],
  templateUrl: './library.html',
})
export class Library {
  private readonly http = inject(HttpClient);

  /** Muda a cada 5 s enquanto houver run QUEUED/RUNNING: força o reload das faixas. */
  private readonly tick = signal(0);

  readonly artists = httpResource<Artist[]>(() => '/api/artists');
  readonly albums = httpResource<Album[]>(() => '/api/albums');
  readonly tracks = httpResource<Track[]>(() => `/api/tracks?tick=${this.tick()}`);

  readonly tree = computed(() => {
    const albums = this.albums.value() ?? [];
    const tracks = this.tracks.value() ?? [];
    return (this.artists.value() ?? []).map((artist) => ({
      artist,
      albums: albums
        .filter((a) => a.artistId === artist.id)
        .sort((a, b) => (a.year ?? 0) - (b.year ?? 0))
        .map((album) => ({
          album,
          tracks: tracks.filter((t) => t.albumId === album.id).sort((a, b) => (a.trackNo ?? 0) - (b.trackNo ?? 0)),
        })),
    }));
  });

  readonly loading = computed(() => this.artists.isLoading() || this.albums.isLoading() || this.tracks.isLoading());
  readonly error = computed(() => this.artists.error() ?? this.albums.error() ?? this.tracks.error());

  readonly pendingRuns = computed(() =>
    (this.tracks.value() ?? []).filter((t) => t.latestRunStatus === 'QUEUED' || t.latestRunStatus === 'RUNNING').length);

  // --- formulário de upload -------------------------------------------------------------------
  readonly showForm = signal(false);
  readonly showImport = signal(false);
  readonly artistId = signal<number | 'new' | null>(null);
  readonly newArtist = signal({ name: '', country: '', formedYear: '' });
  readonly albumId = signal<number | 'new' | null>(null);
  readonly newAlbum = signal({ title: '', year: '' });
  readonly title = signal('');
  readonly trackNo = signal('');
  readonly file = signal<File | null>(null);
  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);

  readonly albumsOfSelectedArtist = computed(() => {
    const id = this.artistId();
    return typeof id === 'number' ? (this.albums.value() ?? []).filter((a) => a.artistId === id) : [];
  });

  readonly canSubmit = computed(() => {
    const artistOk = typeof this.artistId() === 'number' || (this.artistId() === 'new' && this.newArtist().name.trim() !== '');
    const albumOk = typeof this.albumId() === 'number' || (this.albumId() === 'new' && this.newAlbum().title.trim() !== '');
    return artistOk && albumOk && this.file() != null && !this.submitting();
  });

  constructor() {
    const timer = setInterval(() => {
      if (this.pendingRuns() > 0) {
        this.tick.update((n) => n + 1);
      }
    }, 5000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  onImported(): void {
    this.artists.reload();
    this.albums.reload();
    this.tick.update((n) => n + 1);
  }

  selectArtist(value: string): void {
    this.artistId.set(value === '' ? null : value === 'new' ? 'new' : Number(value));
    this.albumId.set(null);
  }

  selectAlbum(value: string): void {
    this.albumId.set(value === '' ? null : value === 'new' ? 'new' : Number(value));
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.file.set(file);
    if (file && this.title() === '') {
      this.title.set(file.name.replace(/\.[^.]+$/, ''));
    }
  }

  async submit(): Promise<void> {
    if (!this.canSubmit()) {
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    try {
      let artistId = this.artistId();
      if (artistId === 'new') {
        const a = this.newArtist();
        const created = await firstValueFrom(this.http.post<Artist>('/api/artists', {
          name: a.name.trim(), country: a.country.trim() || null, formedYear: a.formedYear ? Number(a.formedYear) : null,
        }));
        artistId = created.id;
      }
      let albumId = this.albumId();
      if (albumId === 'new') {
        const al = this.newAlbum();
        const created = await firstValueFrom(this.http.post<Album>('/api/albums', {
          artistId, title: al.title.trim(), year: al.year ? Number(al.year) : null,
        }));
        albumId = created.id;
      }
      const form = new FormData();
      form.append('file', this.file()!);
      form.append('albumId', String(albumId));
      if (this.title().trim()) {
        form.append('title', this.title().trim());
      }
      if (this.trackNo().trim()) {
        form.append('trackNo', this.trackNo().trim());
      }
      await firstValueFrom(this.http.post<Track>('/api/tracks/upload', form));
      this.file.set(null);
      this.title.set('');
      this.trackNo.set('');
      this.artists.reload();
      this.albums.reload();
      this.tick.update((n) => n + 1);
    } catch (e: unknown) {
      const detail = (e as { error?: { detail?: string } })?.error?.detail;
      this.formError.set(detail ?? String(e));
    } finally {
      this.submitting.set(false);
    }
  }

  protected readonly formatTime = formatTime;
}
