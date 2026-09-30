package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.lyrics.LyricMerger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Classifica cada nota do stem de voz pela letra (Java puro, sem I/O).
 *
 * <p>O demucs deixa solos de guitarra e sintetizadores no stem de voz, e o basic-pitch transcreve tudo
 * como se fosse canto. A letra dá a evidência para separar: as palavras com tempo (nota sob palavra = canto
 * com texto) e os trechos (dentro de um trecho, sem palavra = vocalise). A regra:
 * <ol>
 *   <li>nota que sobrepõe uma palavra com probabilidade ≥ {@code wordMinProbability} (ou sem probabilidade:
 *       texto afirmado pelo .lrc ou pelo dono), com folga de {@code wordToleranceS} em cada ponta:
 *       {@link VocalNoteKind#LEXICAL};</li>
 *   <li>senão, nota dentro de um trecho: {@link VocalNoteKind#NON_LEXICAL};</li>
 *   <li>senão {@link VocalNoteKind#LIKELY_LEAK}.</li>
 * </ol>
 * A letra é a mesma que a tela mostra — a fundida com o .lrc ({@link LyricMerger}) — senão o piano roll
 * escondia como vazamento as notas sob as palavras que o ASR não ouviu e o .lrc trouxe. Trecho sem nenhuma
 * palavra crível é alucinação (um "You" a 0,06 no fim de um solo, "Oh" a 0,01 sob guitarra) e não conta.
 * É classificação, não descarte: vocalises que a letra ignora caem em LIKELY_LEAK e a UI só as esconde por
 * padrão. Sem letra alguma, toda nota é LEXICAL — não há evidência contra.
 */
public final class VocalNoteClassifier {

    /** Nota (instante, em segundos) e o resultado, na ordem das notas de entrada. */
    public record Classified(BigDecimal startS, BigDecimal endS, int midi, Integer velocity, VocalNoteKind kind) {
    }

    private final double wordToleranceS;
    private final double wordMinProbability;

    public VocalNoteClassifier(double wordToleranceS, double wordMinProbability) {
        this.wordToleranceS = wordToleranceS;
        this.wordMinProbability = wordMinProbability;
    }

    public List<Classified> classify(List<VocalNote> notes, List<LyricMerger.Segment> segments) {
        List<Classified> out = new ArrayList<>(notes.size());
        for (VocalNote n : notes) {
            out.add(new Classified(n.getStartS(), n.getEndS(), n.getMidiPitch(), n.getVelocity(), kindOf(n, segments)));
        }
        return out;
    }

    VocalNoteKind kindOf(VocalNote note, List<LyricMerger.Segment> segments) {
        if (segments.isEmpty()) {
            return VocalNoteKind.LEXICAL;
        }
        double start = note.getStartS().doubleValue();
        double end = note.getEndS().doubleValue();
        boolean inSegment = false;
        for (LyricMerger.Segment s : segments) {
            if (!credible(s) || !overlaps(start, end, s.startS(), s.endS(), 0)) {
                continue;
            }
            for (LyricMerger.Word w : s.words()) {
                if (believable(w) && overlaps(start, end, w.startS(), w.endS(), wordToleranceS)) {
                    return VocalNoteKind.LEXICAL;
                }
            }
            inSegment = true;
        }
        return inSegment ? VocalNoteKind.NON_LEXICAL : VocalNoteKind.LIKELY_LEAK;
    }

    /** Ao menos uma palavra crível; senão o trecho é alucinação sobre ruído. */
    private boolean credible(LyricMerger.Segment s) {
        return s.words().stream().anyMatch(this::believable);
    }

    private boolean believable(LyricMerger.Word w) {
        return w.probability() == null || w.probability() >= wordMinProbability;
    }

    private static boolean overlaps(double aStart, double aEnd, double bStart, double bEnd, double tolerance) {
        return aStart < bEnd + tolerance && bStart - tolerance < aEnd;
    }
}
