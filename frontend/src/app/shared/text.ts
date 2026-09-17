/** Texto para comparação: sem acentos, sem caixa ("Ré Menor" → "re menor"). */
export function fold(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
