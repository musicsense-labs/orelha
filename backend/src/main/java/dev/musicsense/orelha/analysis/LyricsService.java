package dev.musicsense.orelha.analysis;

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

    LyricsService(LyricSegmentRepository lyrics, VocalNoteRepository vocalNotes, BeatRepository beats,
                  TrackAnalysisRepository analyses, LyricsProperties properties, EntityManager em) {
        this.lyrics = lyrics;
        this.vocalNotes = vocalNotes;
        this.beats = beats;
        this.analyses = analyses;
        this.properties = properties;
        this.classifier = properties.classifier();
        this.em = em;
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

    /** Notas de voz do run classificadas pela letra preferida. */
    @Transactional(readOnly = true)
    public List<VocalNoteClassifier.Classified> classifiedVocalNotes(AnalysisRun run) {
        return classifier.classify(vocalNotes.findByRunIdOrderByStartS(run.getId()), read(run));
    }

    /** A letra preferida com compasso e ataque de nota por palavra. */
    @Transactional(readOnly = true)
    public LyricsResponse response(AnalysisRun run) {
        List<LyricSegment> found = read(run);
        List<Beat> grid = beats.findByRunIdOrderByBeatNo(run.getId());
        List<VocalNote> notes = vocalNotes.findByRunIdOrderByStartS(run.getId());
        double tolerance = properties.wordToleranceS();
        List<LyricsResponse.Segment> segments = found.stream()
                .map(s -> new LyricsResponse.Segment(s.getStartS(), s.getEndS(), s.getText(), s.getNoSpeechProb(),
                        barAt(grid, s.getStartS()), s.getWords().stream()
                        .map(w -> {
                            LyricAligner.Onset onset = LyricAligner.nearestOnset(w.getStartS(), notes, tolerance);
                            BigDecimal at = onset == null ? w.getStartS() : onset.startS();
                            return new LyricsResponse.Word(w.getStartS(), w.getEndS(), w.getText(), w.getProbability(),
                                    barAt(grid, at), onset == null ? null : onset.startS(),
                                    onset == null ? null : onset.midi());
                        })
                        .toList()))
                .toList();
        LyricSource source = found.isEmpty() ? null : found.get(0).getSource();
        return analyses.findByRunId(run.getId())
                .map(a -> new LyricsResponse(run.getId(), source, a.getLyricsLanguage(), a.getLyricsLanguageConfidence(),
                        segments))
                .orElseGet(() -> new LyricsResponse(run.getId(), source, null, null, segments));
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
