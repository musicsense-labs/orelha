import { Injectable, Signal, computed, signal } from '@angular/core';

/**
 * Em que tipo de tela o Orelha está aberto, pelo que o navegador declara — não pelo user agent (iPad se diz
 * Mac, "ver versão para computador" mente, e uma janela estreita no PC se comporta como celular de qualquer
 * jeito). Os sinais seguem `matchMedia`, então rotação e redimensionamento mudam o valor ao vivo. O CSS
 * usa as mesmas consultas (`max-width: 700px`, `pointer: coarse`); aqui é para o template decidir estrutura.
 */
@Injectable({ providedIn: 'root' })
export class Device {
  /** Tela estreita (≤ 700 px): celular em pé ou janela pequena — o layout é o mesmo. */
  readonly narrow = media('(max-width: 700px)');
  /** Ponteiro grosso (dedo): sem hover nem duplo clique confiáveis, alvos maiores. */
  readonly touch = media('(pointer: coarse)');
  readonly phone = computed(() => this.narrow() && this.touch());
}

function media(query: string): Signal<boolean> {
  const s = signal(false);
  if (typeof matchMedia === 'function') {
    const mq = matchMedia(query);
    s.set(mq.matches);
    mq.addEventListener('change', (e) => s.set(e.matches));
  }
  return s.asReadonly();
}
