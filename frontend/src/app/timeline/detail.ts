import { Component, ElementRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BassTab, Beat, KeyInfo, Lyrics, Note, Segment } from '../api/models';
import { readFlag, saveFlag } from '../shared/prefs';
import { KEY_RELATION_COLORS, chordName, formatTime, noteName, relationHint, relationLabel } from '../shared/music';
import { Fifths } from './fifths';

/**
 * O painel do instante, sob a timeline: acorde, tempo, compasso, baixo, voz e letra em células de largura
 * fixa — a grade não se mexe quando o conteúdo muda, para dar para ler enquanto a música toca. Sem segmento
 * sob o cursor ou o playhead, as células mostram "—".
 *
 * A letra é corrigível aqui: clicar numa palavra pausa (pede à timeline) e abre a edição; ao gravar, a
 * timeline recarrega a letra e as notas de voz, cuja classificação segue a letra.
 */
@Component({
  selector: 'app-detail',
  imports: [Fifths],
  templateUrl: './detail.html',
  styleUrl: './detail.scss',
  host: { class: 'card', '[class.circle]': 'showCircle()' },
})
export class DetailPanel {
  readonly trackId = input.required<number>();
  /** O segmento sob o cursor, ou sob o playhead quando o cursor não está na timeline. */
  readonly segment = input<Segment | null>(null);
  /** O segmento vem do cursor (e não do playhead): o tempo mostrado é o do início dele. */
  readonly hovering = input(false);
  readonly time = input(0);
  readonly beats = input<Beat[]>([]);
  readonly timeSignature = input<string | null>(null);
  readonly musicKey = input<KeyInfo | null>(null);
  readonly bassNotes = input<Note[]>([]);
  /** As notas de voz que a timeline mostra (sem o vazamento, salvo pedido). */
  readonly vocalNotes = input<Note[]>([]);
  /** Presente só no modo tab: a célula BAIXO mostra corda e casa. */
  readonly bassTab = input<BassTab | null>(null);
  readonly lyrics = input<Lyrics | null>(null);

  /** A edição de uma palavra pausa o áudio, para a linha não mudar debaixo do cursor. */
  readonly pause = output<void>();
  /** A letra foi gravada (ou revertida): quem mostra letra e notas de voz recarrega. */
  readonly lyricsSaved = output<void>();

  private readonly http = inject(HttpClient);

  /** Ciclo das quintas na célula ACORDE (desligado por padrão; preferência guardada no navegador). */
  readonly showCircle = signal(readFlag('orelha.timeline.circle'));
  /** Palavra da linha atual em edição (índice do trecho e da palavra). */
  readonly editingWord = signal<{ seg: number; word: number } | null>(null);
  readonly savingLyrics = signal(false);
  readonly lyricsError = signal<string | null>(null);
  private readonly wordInput = viewChild<ElementRef<HTMLInputElement>>('wordInput');

  readonly lyricSegments = computed(() => this.lyrics()?.segments ?? []);

  readonly currentBar = computed(() => {
    const t = this.time();
    let bar: number | null = null;
    for (const b of this.beats()) {
      if (b.timeS > t) {
        break;
      }
      bar = b.barNo;
    }
    return bar;
  });

  /**
   * Nota do baixo (stem) no instante: em execução ("on") ou a última que soou ("off"), até a próxima
   * começar — a UI mostra a primeira em negrito e a segunda leve, para o nome não piscar.
   */
  readonly bassNow = computed(() => noteAt(this.bassNotes(), this.time()));
  readonly vocalNow = computed(() => noteAt(this.vocalNotes(), this.time()));

  /** Corda e casa da nota de baixo que soa agora (ou da última). */
  readonly tabNow = computed(() => {
    const tab = this.bassTab();
    if (!tab) {
      return null;
    }
    const t = this.time();
    let last: (typeof tab.notes)[number] | null = null;
    for (const n of tab.notes) {
      if (n.startS > t) {
        break;
      }
      last = n;
    }
    return last ? { string: tab.strings[last.string], fret: last.fret, shifted: last.octaveShifted } : null;
  });

  /**
   * Trecho da letra no instante e as palavras dele, com a que está sendo cantada marcada — pelo ataque da nota
   * de voz alinhada, quando há (o ASR marca a consoante; a nota, a vogal).
   */
  readonly currentLine = computed(() => {
    const t = this.time();
    const segments = this.lyricSegments();
    const index = segments.findIndex((s) => s.startS <= t && t < s.endS);
    if (index < 0) {
      return null;
    }
    const seg = segments[index];
    const words = seg.words.map((w) => ({ text: w.text, on: (w.noteStartS ?? w.startS) <= t && t < w.endS + 0.12 }));
    return { index, seg, words };
  });

  constructor() {
    effect(() => {
      const el = this.wordInput()?.nativeElement;
      if (this.editingWord() && el) {
        el.focus();
        el.select();
      }
    });
  }

  setShowCircle(on: boolean): void {
    this.showCircle.set(on);
    saveFlag('orelha.timeline.circle', on);
  }

  color(s: Segment): string {
    return KEY_RELATION_COLORS[s.keyRelation] ?? '#ccc';
  }

  /** Cifra com o baixo da harmonia na notação acorde/baixo, quando o baixo efetivo não é a fundamental. */
  labelWithBass(s: Segment): string {
    const slash = s.effectiveBassPc != null && s.effectiveBassPc !== s.rootPc ? s.effectiveBassPc : null;
    return chordName(s.rootPc, s.quality, slash);
  }

  // --- correção da letra ------------------------------------------------------------------------

  startEditWord(seg: number, word: number): void {
    this.pause.emit();
    this.editingWord.set({ seg, word });
  }

  /** Grava a palavra corrigida: vazia remove; com espaços divide o tempo dela entre as novas palavras. */
  commitWord(seg: number, word: number, raw: string): void {
    if (!this.editingWord()) {
      return;   // o blur depois do Enter/Escape
    }
    this.editingWord.set(null);
    const segments = this.lyricSegments();
    const target = segments[seg]?.words[word];
    if (!target) {
      return;
    }
    const parts = raw.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 1 && parts[0] === target.text) {
      return;
    }
    const span = (target.endS - target.startS) / Math.max(parts.length, 1);
    const replacement = parts.map((text, i) => ({
      startS: target.startS + i * span, endS: target.startS + (i + 1) * span, text,
    }));
    const edited = segments
      .map((s, i) => {
        if (i !== seg) {
          return s;
        }
        const words = [...s.words.slice(0, word), ...replacement, ...s.words.slice(word + 1)];
        return { ...s, words, text: words.map((w) => w.text).join(' ') };
      })
      .filter((s) => s.words.length > 0);
    this.saveLyrics(edited);
  }

  /** Descarta a correção: volta à transcrição do extrator. */
  revertLyrics(): void {
    this.saveLyrics([]);
  }

  private saveLyrics(segments: { startS: number; endS: number; text: string; words: { startS: number; endS: number; text: string }[] }[]): void {
    const body = segments.map((s) => ({
      startS: s.startS, endS: s.endS, text: s.text,
      words: s.words.map((w) => ({ startS: w.startS, endS: w.endS, text: w.text })),
    }));
    this.savingLyrics.set(true);
    this.lyricsError.set(null);
    this.http.put(`/api/tracks/${this.trackId()}/lyrics`, body).subscribe({
      next: () => {
        this.savingLyrics.set(false);
        this.lyricsSaved.emit();
      },
      error: (e) => {
        this.savingLyrics.set(false);
        this.lyricsError.set(e?.error?.detail ?? e?.message ?? 'falha ao salvar');
      },
    });
  }

  protected readonly formatTime = formatTime;
  protected readonly relationLabel = relationLabel;
  protected readonly relationHint = relationHint;
  protected readonly noteName = noteName;
}

/** A nota que soa em t (a mais forte, se várias) ou, sem nenhuma, a última que terminou antes de t. */
function noteAt(notes: Note[], t: number): { name: string; state: 'on' | 'off' } | null {
  let sounding: Note | null = null;
  let last: Note | null = null;
  for (const n of notes) {
    if (n.startS > t) {
      break;
    }
    if (t < n.endS) {
      if (sounding == null || (n.velocity ?? 0) > (sounding.velocity ?? 0)) {
        sounding = n;
      }
    } else if (last == null || n.endS > last.endS) {
      last = n;
    }
  }
  const n = sounding ?? last;
  return n ? { name: noteName(n.midi % 12) + (Math.floor(n.midi / 12) - 1), state: sounding ? 'on' : 'off' } : null;
}
