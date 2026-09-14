import { Routes } from '@angular/router';
import { Compare } from './compare/compare';
import { Library } from './library/library';
import { Profile } from './profile/profile';
import { Timeline } from './timeline/timeline';

export const routes: Routes = [
  { path: '', component: Library, title: 'riff-lab' },
  { path: 'tracks/:id', component: Timeline, title: 'Timeline — riff-lab' },
  { path: 'artists/:id', component: Profile, data: { scope: 'artists' }, title: 'Artista — riff-lab' },
  { path: 'albums/:id', component: Profile, data: { scope: 'albums' }, title: 'Álbum — riff-lab' },
  { path: 'compare', component: Compare, title: 'Comparar — riff-lab' },
  { path: '**', redirectTo: '' },
];
