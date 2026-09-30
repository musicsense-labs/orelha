package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.lyrics.LrcFile;
import dev.musicsense.orelha.lyrics.LrcLineRepository;
import dev.musicsense.orelha.lyrics.LyricMerger;
import jakarta.persistence.EntityManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Letra de um run: a do extrator (ASR) ou a corrigida pelo dono (MANUAL), que a leitura prefere. Monta a
 * resposta com o compasso e o ataque de nota de cada palavra, e classifica as notas de voz pela letra
 * preferida ({@link VocalNoteClassifier}).
 */
@Service
@EnableConfigurationProperties(LyricsProperties.class)
public class LyricsService {


    public record WordEdit(BigDecimal startS, BigDecimal endS, String text) {
    }

    public record SegmentEdit(BigDecimal startS, BigDecimal endS, String text, List<WordEdit> words) {
    }

    private final LyricSegmentRepository lyrics;
    private final VocalNoteRepository vocalNotes;
    private final BeatRepository beats;
    private final TrackAnalysisRepository analyses;
    private final LyricsProperties properties;
    private final VocalNoteClassifier classifier;
    private final EntityManager em;
    private final LrcLineRepository lrcLines;

    LyricsService(LyricSegmentRepository lyrics, VocalNoteRepository vocalNotes, BeatRepository beats,
                  TrackAnalysisRepository analyses, LyricsProperties properties, EntityManager em,
                  LrcLineRepository lrcLines) {
        this.lyrics = lyrics;
        this.vocalNotes = vocalNotes;
        this.beats = beats;
        this.analyses = analyses;
        this.properties = properties;
        this.classifier = properties.classifier();
        this.em = em;
        this.lrcLines = lrcLines;
    }

    /** Trechos da fonte preferida: MANUAL se o dono corrigiu, senão os do extrator. */
    @Transactional(readOnly = true)
    public List<LyricSegment> read(AnalysisRun run) {
        List<LyricSegment> manual = lyrics.findByRunIdAndSourceOrderByStartS(run.getId(), LyricSource.MANUAL);
        return manual.isEmpty() ? lyrics.findByRunIdAndSourceOrderByStartS(run.getId(), LyricSource.EXTRACTOR) : manual;
    }

    @Transactional(readOnly = true)
    public LyricSource preferredSource(AnalysisRun run) {
        List<LyricSegment> found = read(run);
        return found.isEmpty() ? null : found.get(0).getSource();
    }

    /** Edição do dono: substitui os trechos MANUAL do run; lista vazia volta à transcrição do extrator. */
    @Transactional
    public void replaceManual(AnalysisRun run, List<SegmentEdit> edits) {
        lyrics.deleteByRunIdAndSource(run.getId(), LyricSource.MANUAL);
        em.flush();
        for (SegmentEdit e : edits) {
            if (e.endS().compareTo(e.startS()) <= 0) {
                throw new IllegalArgumentException("Lyric segment '" + e.text() + "' ends before it starts");
            }
            List<WordEdit> words = e.words() == null ? List.of() : e.words();
            String text = e.text() == null || e.text().isBlank()
                    ? String.join(" ", words.stream().map(w -> w.text().trim()).toList())
                    : e.text().trim();
            LyricSegment segment = new LyricSegment(run, LyricSource.MANUAL, e.startS(), e.endS(), text, null);
            for (WordEdit w : words) {
                if (w.text() == null || w.text().isBlank()) {
                    continue;
                }
                segment.addWord(w.startS(), w.endS(), w.text().trim(), null);   // sem probabilidade: o dono afirmou
            }
            em.persist(segment);
        }
    }

    /** Copia a letra MANUAL de um run para outro (re-análise: mesmo áudio, mesmos instantes). */
    @Transactional
    public void inheritManual(AnalysisRun from, AnalysisRun to) {
        List<LyricSegment> manual = lyrics.findByRunIdAndSourceOrderByStartS(from.getId(), LyricSource.MANUAL);
        if (manual.isEmpty()) {
            return;
        }
        replaceManual(to, manual.stream()
                .map(s -> new SegmentEdit(s.getStartS(), s.getEndS(), s.getText(), s.getWords().stream()
                        .map(w -> new WordEdit(w.getStartS(), w.getEndS(), w.getText()))
                        .toList()))
                .toList());
    }

    /**
     * Notas de voz do run classificadas pela mesma letra que a tela mostra — a fundida com o .lrc. Com a
     * transcrição crua, as notas sob as palavras que o ASR não ouviu (e o .lrc trouxe) sumiam como vazamento.
     */
    @Transactional(readOnly = true)
    public List<VocalNoteClassifier.Classified> classifiedVocalNotes(AnalysisRun run) {
        return classifier.classify(vocalNotes.findByRunIdOrderByStartS(run.getId()), merged(run, read(run)).segments());
    }

    /**
     * A letra que vale: o ASR diz quando se canta, o .lrc da faixa diz o quê, e a correção do dono (MANUAL)
     * vence os dois — sobre ela nada é fundido.
     */
    private LyricMerger.Result merged(AnalysisRun run, List<LyricSegment> found) {
        List<LyricMerger.Segment> heard = found.stream()
                .map(s -> new LyricMerger.Segment(s.getStartS().doubleValue(), s.getEndS().doubleValue(), s.getText(),
                        s.getWords().stream()
                                .map(w -> new LyricMerger.Word(w.getStartS().doubleValue(), w.getEndS().doubleValue(),
                                        w.getText(), w.getProbability() == null ? null : w.getProbability().doubleValue()))
                                .toList()))
                .toList();
        boolean fromAsr = !found.isEmpty() && found.get(0).getSource() == LyricSource.EXTRACTOR;
        return fromAsr ? LyricMerger.merge(heard, lrcLines(run)) : new LyricMerger.Result(heard, 0, 0, 0, 0);
    }

    /** A letra preferida com compasso e ataque de nota por palavra. */
    @Transactional(readOnly = true)
    public LyricsResponse response(AnalysisRun run) {
        List<LyricSegment> found = read(run);
        List<Beat> grid = beats.findByRunIdOrderByBeatNo(run.getId());
        List<VocalNote> notes = vocalNotes.findByRunIdOrderByStartS(run.getId());
        double tolerance = properties.wordToleranceS();
        LyricSource source = found.isEmpty() ? null : found.get(0).getSource();
        LyricMerger.Result merged = merged(run, found);

        List<LyricsResponse.Segment> segments = merged.segments().stream()
                .map(s -> new LyricsResponse.Segment(decimal(s.startS()), decimal(s.endS()), s.text(),
                        barAt(grid, decimal(s.startS())), s.words().stream()
                        .map(w -> {
                            BigDecimal start = decimal(w.startS());
                            LyricAligner.Onset onset = LyricAligner.nearestOnset(start, notes, tolerance);
                            BigDecimal at = onset == null ? start : onset.startS();
                            return new LyricsResponse.Word(start, decimal(w.endS()), w.text(),
                                    w.probability() == null ? null : w.probability().floatValue(),
                                    barAt(grid, at), onset == null ? null : onset.startS(),
                                    onset == null ? null : onset.midi());
                        })
                        .toList()))
                .toList();
        LyricsResponse.LrcMerge report = merged.changedAnything()
                ? new LyricsResponse.LrcMerge(merged.corrected(), merged.inserted(), merged.dropped(), merged.kept())
                : null;
        return analyses.findByRunId(run.getId())
                .map(a -> new LyricsResponse(run.getId(), source, a.getLyricsLanguage(), a.getLyricsLanguageConfidence(),
                        segments, report))
                .orElseGet(() -> new LyricsResponse(run.getId(), source, null, null, segments, report));
    }

    /** Os versos do .lrc da faixa deste run (vazio quando o arquivo não veio ao lado do áudio). */
    private List<LrcFile.Line> lrcLines(AnalysisRun run) {
        return lrcLines.findByTrackIdOrderByLineNo(run.getTrack().getId()).stream()
                .map(l -> new LrcFile.Line(l.getStartS().doubleValue(), l.getText()))
                .toList();
    }

    private static BigDecimal decimal(double seconds) {
        return BigDecimal.valueOf(Math.round(seconds * 1000), 3);
    }

    /** Compasso do último beat que não vem depois do instante; null antes do primeiro beat ou sem grade. */
    static Integer barAt(List<Beat> grid, BigDecimal time) {
        Integer bar = null;
        for (Beat b : grid) {
            if (b.getTimeS().compareTo(time) > 0) {
                break;
            }
            bar = b.getBarNo();
        }
        return bar;
    }
}
