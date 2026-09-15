package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.AudioLibrary;
import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.common.NotFoundException;
import dev.musicsense.orelha.extraction.ChordEvents;
import dev.musicsense.orelha.extraction.ExtractionResult;
import dev.musicsense.orelha.extraction.ExtractionResult.NoteEvent;
import dev.musicsense.orelha.extraction.ExtractionResult.ChordEvent;
import dev.musicsense.orelha.harmony.Chord;
import dev.musicsense.orelha.harmony.HarmonicNormalizer;
import dev.musicsense.orelha.harmony.Key;
import dev.musicsense.orelha.harmony.KeyMode;
import dev.musicsense.orelha.harmony.NormalizedChord;
import dev.musicsense.orelha.harmony.PowerChordDetector;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * De {@link ExtractionResult} até harmonic_annotation: persiste o bruto, decide POWER pelo chroma
 * grave do stem de guitarra, funde segmentos, normaliza e anota. Tudo numa transação — sem I/O
 * externo aqui dentro. Re-anotar com outra tonalidade ({@link #reannotate}) nunca re-extrai.
 */
@Service
public class AnalysisPipeline {

    private final AnalysisRunRepository runs;
    private final EntityManager em;
    private final HarmonicNormalizer normalizer = new HarmonicNormalizer();
    private final PowerChordDetector powerChords;
    private final AudioLibrary library;
    private final SectionService sections;
    private final SectionRepository sectionRepository;
    private final KeySegmentRepository keys;

    AnalysisPipeline(AnalysisRunRepository runs, EntityManager em, PowerChordDetector powerChords, AudioLibrary library,
                     SectionService sections, SectionRepository sectionRepository, KeySegmentRepository keys) {
        this.runs = runs;
        this.em = em;
        this.powerChords = powerChords;
        this.library = library;
        this.sections = sections;
        this.sectionRepository = sectionRepository;
        this.keys = keys;
    }

    public record Input(String audioPath, String audioSha256) {
    }

    @Transactional(readOnly = true)
    public Input load(long runId) {
        AnalysisRun run = find(runId);
        Track track = run.getTrack();
        return new Input(library.resolve(track).toString(), track.getAudioSha256());
    }

    @Transactional
    public void persist(long runId, ExtractionResult result) {
        AnalysisRun run = find(runId);
        Track track = run.getTrack();
        run.setExtractorVersion(result.provenance().version());
        run.setModelNames(result.provenance().models());
        run.setFeaturesPath(result.featuresPath());
        run.setStems(result.stems() == null || result.stems().isEmpty() ? null : result.stems());
        track.setDurationS(result.audio().durationS());
        track.setSampleRate(result.audio().sampleRate());

        em.persist(new TrackAnalysis(run,
                result.tempo() == null ? null : result.tempo().bpm(),
                result.tempo() == null ? null : result.tempo().timeSignature(),
                result.audio().integratedLufs()));

        persistBeats(run, result);
        List<NoteEvent> bassNotes = result.bassNotes();
        bassNotes.forEach(n -> em.persist(new BassNote(run, n.startS(), n.endS(), n.midi(), n.velocity())));
        result.vocalNotes().forEach(n -> em.persist(new VocalNote(run, n.startS(), n.endS(), n.midi(), n.velocity())));
        result.timbre().forEach(t -> em.persist(new TimbreSummary(run, t.stemModel(), t.stem(), t.centroidMean(),
                t.centroidStd(), t.flatnessMean(), t.rolloffP95(), t.rmsMean())));

        // POWER é decidido antes da fusão: dois E5 rotulados E e Em pelo modelo viram um segmento só.
        List<ChordEvent> events = ChordEvents.mergeConsecutive(result.chords().stream()
                .map(e -> new ChordEvent(e.startS(), e.endS(), powerChords.reclassify(e.chord(), e.chromaLow()),
                        e.chroma(), e.chromaLow(), e.confidence()))
                .toList());
        List<ChordSegment> segments = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            ChordEvent e = events.get(i);
            ChordSegment segment = new ChordSegment(run, i, e.startS(), e.endS(), e.chord().rootPc(),
                    e.chord().quality(), e.chord().bassPc(), e.confidence(), e.chroma(), e.chromaLow());
            em.persist(segment);
            segments.add(segment);
        }

        if (result.key() != null) {
            KeySegment keySegment = new KeySegment(run, BigDecimal.ZERO, result.audio().durationS(),
                    result.key().tonicPc(), result.key().mode(), result.key().confidence(), KeySource.EXTRACTOR);
            em.persist(keySegment);
            annotate(segments, keySegment, bassNotes);
        }
        em.flush();
        sections.derive(run);   // partes por repetição: independem da tonalidade, não re-derivam no override
        inheritManualOverrides(run, track.getCanonicalRun(), segments, bassNotes);

        if (track.getCanonicalRun() == null) {
            track.setCanonicalRun(run);
        }
    }

    /**
     * O que o dono corrigiu no run canônico anterior (tonalidade MANUAL, partes MANUAL) vale para a
     * re-análise: é copiado para o run novo, senão reprocessar apagaria o trabalho dele.
     */
    private void inheritManualOverrides(AnalysisRun run, AnalysisRun previous, List<ChordSegment> segments,
                                        List<NoteEvent> bassNotes) {
        if (previous == null || previous.getId().equals(run.getId())) {
            return;
        }
        keys.findPreferred(previous.getId())
                .filter(k -> k.getSource() == KeySource.MANUAL)
                .ifPresent(k -> {
                    KeySegment manual = new KeySegment(run, k.getStartS(), k.getEndS(), k.getTonicPc(), k.getMode(), null,
                            KeySource.MANUAL);
                    em.persist(manual);
                    annotate(segments, manual, bassNotes);
                });
        List<Section> manualSections = sectionRepository.findByRunIdAndSourceOrderByPosition(previous.getId(),
                SectionSource.MANUAL);
        if (!manualSections.isEmpty()) {
            sections.replaceManual(run, manualSections.stream()
                    .map(s -> new SectionService.SectionEdit(s.getStartS(), s.getEndS(), s.getLabel(), s.getCycleEndS(),
                            s.getRepeats()))
                    .toList());
        }
    }

    /**
     * Tonalidade atribuída pelo dono: grava um key_segment MANUAL cobrindo o run inteiro e deriva uma
     * nova leitura dos segmentos já extraídos. A leitura feita com a tonalidade do extrator permanece.
     */
    @Transactional
    public KeySegment reannotate(long runId, int tonicPc, KeyMode mode) {
        AnalysisRun run = find(runId);
        BigDecimal duration = run.getTrack().getDurationS();
        KeySegment keySegment = new KeySegment(run, BigDecimal.ZERO, duration == null ? BigDecimal.ZERO : duration,
                tonicPc, mode, null, KeySource.MANUAL);
        em.persist(keySegment);

        List<ChordSegment> segments = em.createQuery(
                        "select s from ChordSegment s where s.run.id = :runId order by s.seqNo", ChordSegment.class)
                .setParameter("runId", runId).getResultList();
        List<NoteEvent> bassNotes = em.createQuery(
                        "select b from BassNote b where b.run.id = :runId order by b.startS", BassNote.class)
                .setParameter("runId", runId).getResultList().stream()
                .map(b -> new NoteEvent(b.getStartS(), b.getEndS(), b.getMidiPitch(), b.getVelocity()))
                .toList();
        annotate(segments, keySegment, bassNotes);
        return keySegment;
    }

    private void persistBeats(AnalysisRun run, ExtractionResult result) {
        Integer bar = null;
        int beatNo = 0;
        for (ExtractionResult.BeatEvent beat : result.beats()) {
            boolean downbeat = beat.position() == 1;
            if (downbeat) {
                bar = bar == null ? 1 : bar + 1;
            }
            em.persist(new Beat(run, beatNo++, beat.timeS(), bar, downbeat));
        }
    }

    private void annotate(List<ChordSegment> segments, KeySegment keySegment, List<NoteEvent> bassNotes) {
        Key key = new Key(keySegment.getTonicPc(), keySegment.getMode());
        List<Chord> chords = segments.stream().map(ChordSegment::chord).toList();
        List<NormalizedChord> normalized = normalizer.normalize(chords, key);
        for (int i = 0; i < segments.size(); i++) {
            NormalizedChord n = normalized.get(i);
            ChordSegment s = segments.get(i);
            em.persist(new HarmonicAnnotation(s, HarmonicNormalizer.VERSION, keySegment,
                    n.degreeInterval(), n.degreeLabel(), n.keyRelation().name(), n.isInverted(),
                    effectiveBassPc(s.getStartS(), s.getEndS(), bassNotes),
                    n.fromPrevious() == null ? null : n.fromPrevious().relation().name()));
        }
    }

    /** Classe de altura do baixo que mais tempo soa dentro do intervalo; null sem notas. */
    static Integer effectiveBassPc(BigDecimal start, BigDecimal end, List<NoteEvent> bassNotes) {
        Map<Integer, Double> weight = new HashMap<>();
        for (NoteEvent note : bassNotes) {
            double overlap = Math.min(note.endS().doubleValue(), end.doubleValue())
                    - Math.max(note.startS().doubleValue(), start.doubleValue());
            if (overlap > 0) {
                weight.merge(note.midi() % 12, overlap, Double::sum);
            }
        }
        return weight.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private AnalysisRun find(long runId) {
        return runs.findById(runId).orElseThrow(() -> new NotFoundException("AnalysisRun", runId));
    }
}
