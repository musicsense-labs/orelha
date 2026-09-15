import { Routes } from '@angular/router';
import { Compare } from './compare/compare';
import { Collection } from './collection/collection';
import { Profile } from './profile/profile';
import { Timeline } from './timeline/timeline';

export const routes: Routes = [
  { path: '', component: Collection, title: 'Orelha' },
  { path: 'tracks/:id', component: Timeline, title: 'Timeline — Orelha' },
  { path: 'artists/:id', component: Profile, data: { scope: 'artists' }, title: 'Artista — Orelha' },
  { path: 'albums/:id', component: Profile, data: { scope: 'albums' }, title: 'Álbum — Orelha' },
  { path: 'compare', component: Compare, title: 'Comparar — Orelha' },
  { path: '**', redirectTo: '' },
];
