import { HttpErrorResponse } from '@angular/common/http';

/**
 * Texto de um erro de chamada à API: o `detail` do ProblemDetail (RFC 9457) quando o backend o mandou
 * (404/409 do RestExceptionHandler, 403 das ResponseStatusException), senão a linha do HttpErrorResponse
 * ("Http failure response for /api/…: 500 …" — um 500 cru, rede fora, backend reiniciando), senão o próprio erro.
 */
export function errorText(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    const detail = (e.error as { detail?: unknown } | null)?.detail;
    return typeof detail === 'string' && detail !== '' ? detail : e.message;
  }
  return e instanceof Error ? e.message : String(e);
}
