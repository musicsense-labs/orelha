package dev.musicsense.orelha.analysis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Classifica cada nota do stem de voz pela letra transcrita (Java puro, sem I/O).
 *
 * <p>O demucs deixa solos de guitarra e sintetizadores no stem de voz, e o basic-pitch transcreve tudo
 * como se fosse canto. O ASR dá duas evidências para separar: as palavras com tempo (nota sob palavra =
 * canto com texto) e o {@code no_speech_prob} por trecho (alto = o modelo acha que não é fala). A regra:
 * <ol>
 *   <li>nota que sobrepõe uma palavra com probabilidade ≥ {@code wordMinProbability}, com folga de
 *       {@code wordToleranceS} em cada ponta: {@link VocalNoteKind#LEXICAL};</li>
 *   <li>senão, nota dentro de um trecho com {@code no_speech_prob < noSpeechThreshold} (por padrão o limiar
 *       é 1.0: todo trecho devolvido conta, porque canto limpo já pontua ~0,8 e o Whisper só devolve o que
 *       aceitou como fala): {@link VocalNoteKind#NON_LEXICAL} (o ASR ouviu fala ali, mas não alinhou palavra);</li>
 *   <li>senão {@link VocalNoteKind#LIKELY_LEAK}.</li>
 * </ol>
 * Trecho sem nenhuma palavra com probabilidade ≥ {@code wordMinProbability} é alucinação (um "You" a 0,06
 * no fim de um solo, "Oh" a 0,01 sob guitarra) e não conta como fala.
 * É classificação, não descarte: vocalises que o ASR ignora caem em LIKELY_LEAK e a UI só as esconde por
 * padrão. Sem letra alguma (run anterior ao 0.6.0), toda nota é LEXICAL — não há evidência contra.
 */
public final class VocalNoteClassifier {

    /** Nota (instante, em segundos) e o resultado, na ordem das notas de entrada. */
    public record Classified(BigDecimal startS, BigDecimal endS, int midi, Integer velocity, VocalNoteKind kind) {
    }

    private final double noSpeechThreshold;
    private final double wordToleranceS;
    private final double wordMinProbability;

    public VocalNoteClassifier(double noSpeechThreshold, double wordToleranceS, double wordMinProbability) {
        this.noSpeechThreshold = noSpeechThreshold;
        this.wordToleranceS = wordToleranceS;
        this.wordMinProbability = wordMinProbability;
    }

    public List<Classified> classify(List<VocalNote> notes, List<LyricSegment> segments) {
        List<Classified> out = new ArrayList<>(notes.size());
        for (VocalNote n : notes) {
            out.add(new Classified(n.getStartS(), n.getEndS(), n.getMidiPitch(), n.getVelocity(), kindOf(n, segments)));
        }
        return out;
    }

    VocalNoteKind kindOf(VocalNote note, List<LyricSegment> segments) {
        if (segments.isEmpty()) {
            return VocalNoteKind.LEXICAL;
        }
        double start = note.getStartS().doubleValue();
        double end = note.getEndS().doubleValue();
        boolean inSpeech = false;
        for (LyricSegment s : segments) {
            if (!credible(s) || !overlaps(start, end, s.getStartS().doubleValue(), s.getEndS().doubleValue(), 0)) {
                continue;
            }
            for (LyricWord w : s.getWords()) {
                if ((w.getProbability() == null || w.getProbability() >= wordMinProbability)
                        && overlaps(start, end, w.getStartS().doubleValue(), w.getEndS().doubleValue(), wordToleranceS)) {
                    return VocalNoteKind.LEXICAL;
                }
            }
            if (s.getNoSpeechProb() == null || s.getNoSpeechProb() < noSpeechThreshold) {
                inSpeech = true;
            }
        }
        return inSpeech ? VocalNoteKind.NON_LEXICAL : VocalNoteKind.LIKELY_LEAK;
    }

    /** Ao menos uma palavra em que o ASR acredita; senão o trecho é alucinação sobre ruído. */
    private boolean credible(LyricSegment s) {
        return s.getWords().stream().anyMatch(w -> w.getProbability() == null || w.getProbability() >= wordMinProbability);
    }

    private static boolean overlaps(double aStart, double aEnd, double bStart, double bEnd, double tolerance) {
        return aStart < bEnd + tolerance && bStart - tolerance < aEnd;
    }
}
