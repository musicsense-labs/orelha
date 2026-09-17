import { Injectable, computed } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { AccessInfo } from '../api/models';

/**
 * Quem está usando a UI e por onde: um único GET /api/access por carga da página (é também a "entrada"
 * que a auditoria registra). Um recurso em erro (backend antigo) vale como acesso local e sem privilégios.
 */
@Injectable({ providedIn: 'root' })
export class Access {
  readonly info = httpResource<AccessInfo>(() => '/api/access');
  readonly value = computed(() => (this.info.hasValue() ? this.info.value() : null));
  readonly remote = computed(() => this.value()?.remote ?? false);
  readonly email = computed(() => this.value()?.email ?? null);
  readonly admin = computed(() => this.value()?.admin ?? false);
  readonly importFolderAllowed = computed(() => this.value()?.importFolderAllowed ?? true);
}
