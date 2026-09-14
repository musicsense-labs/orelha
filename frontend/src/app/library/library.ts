import { Component, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Album, Artist, Track } from '../api/models';
import { formatTime } from '../shared/music';

/** Catálogo: artistas → álbuns → faixas, com o estado de análise de cada faixa. */
@Component({
  selector: 'app-library',
  imports: [RouterLink],
  templateUrl: './library.html',
})
export class Library {
  readonly artists = httpResource<Artist[]>(() => '/api/artists');
  readonly albums = httpResource<Album[]>(() => '/api/albums');
  readonly tracks = httpResource<Track[]>(() => '/api/tracks');

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

  protected readonly formatTime = formatTime;
}
