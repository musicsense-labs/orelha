/**
 * Preferências do visitante guardadas no navegador. Sem storage (aba privada, site bloqueado), a leitura cai
 * no padrão e a escolha vale só nesta visita — nunca é erro da tela.
 */
export function readFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}

export function saveFlag(key: string, on: boolean): void {
  save(key, on ? '1' : '0');
}

/** Número guardado, se passar na validação; senão o padrão. */
export function readNumber(key: string, fallback: number, valid: (n: number) => boolean): number {
  try {
    const n = Number(localStorage.getItem(key));
    return valid(n) ? n : fallback;
  } catch {
    return fallback;
  }
}

export function save(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // sem storage: a escolha vale só nesta visita
  }
}
