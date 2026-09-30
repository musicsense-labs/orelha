import { Routes } from '@angular/router';
import { Collection } from './collection/collection';
import { Timeline } from './timeline/timeline';

/**
 * Acervo e timeline entram no bundle inicial: são por onde se chega e o que se usa tocando. Perfil e
 * comparação (que trazem o ECharts) e a aba do administrador carregam quando alguém abre.
 */
export const routes: Routes = [
  { path: '', component: Collection, title: 'Orelha' },
  { path: 'tracks/:id', component: Timeline, title: 'Timeline — Orelha' },
  { path: 'artists/:id', loadComponent: () => import('./profile/profile').then((m) => m.Profile), data: { scope: 'artists' }, title: 'Artista — Orelha' },
  { path: 'albums/:id', loadComponent: () => import('./profile/profile').then((m) => m.Profile), data: { scope: 'albums' }, title: 'Álbum — Orelha' },
  { path: 'compare', loadComponent: () => import('./compare/compare').then((m) => m.Compare), title: 'Comparar — Orelha' },
  { path: 'admin', loadComponent: () => import('./admin/admin').then((m) => m.Admin), title: 'Administrador — Orelha' },
  { path: '**', redirectTo: '' },
];
