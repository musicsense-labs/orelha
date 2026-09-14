package dev.rifflab.analysis;

import dev.rifflab.catalog.Track;
import dev.rifflab.common.NotFoundException;
import dev.rifflab.extraction.ChordEvents;
import dev.rifflab.extraction.ExtractionResult;
import dev.rifflab.extraction.ExtractionResult.BassNoteEvent;
import dev.rifflab.extraction.ExtractionResult.ChordEvent;
import dev.rifflab.harmony.Chord;
import dev.rifflab.harmony.HarmonicNormalizer;
import dev.rifflab.harmony.Key;
import dev.rifflab.harmony.NormalizedChord;
import dev.rifflab.harmony.PowerChordDetector;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * De {@link ExtractionResult} até harmonic_annotation: persiste o bruto, decide POWER pelo chroma,
 * funde segmentos, normaliza e anota. Tudo numa transação — sem I/O externo aqui dentro.
 */
@Service
public class AnalysisPipeline {

    private final AnalysisRunRepository runs;
    private final EntityManager em;
    private final HarmonicNormalizer normalizer = new HarmonicNormalizer();
    private final PowerChordDetector powerChords;

    AnalysisPipeline(AnalysisRunRepository runs, EntityManager em, PowerChordDetector powerChords) {
        this.runs = runs;
        this.em = em;
        this.powerChords = powerChords;
    }

    public record Input(String audioPath, String audioSha256) {
    }

    @Transactional(readOnly = true)
    public Input load(long runId) {
        AnalysisRun run = find(runId);
        Track track = run.getTrack();
        return new Input(track.getAudioPath(), track.getAudioSha256());
    }

    @Transactional
    public void persist(long runId, ExtractionResult result) {
        AnalysisRun run = find(runId);
        Track track = run.getTrack();
        run.setExtractorVersion(result.provenance().version());
        run.setModelNames(result.provenance().models());
        run.setFeaturesPath(result.featuresPath());
        track.setDurationS(result.audio().durationS());
        track.setSampleRate(result.audio().sampleRate());

        em.persist(new TrackAnalysis(run,
                result.tempo() == null ? null : result.tempo().bpm(),
                result.tempo() == null ? null : result.tempo().timeSignature(),
                result.audio().integratedLufs()));

        KeySegment keySegment = null;
        if (result.key() != null) {
            keySegment = new KeySegment(run, BigDecimal.ZERO, result.audio().durationS(),
                    result.key().tonicPc(), result.key().mode(), result.key().confidence(), KeySource.EXTRACTOR);
            em.persist(keySegment);
        }

        persistBeats(run, result);
        result.bassNotes().forEach(n -> em.persist(new BassNote(run, n.startS(), n.endS(), n.midi(), n.velocity())));
        result.timbre().forEach(t -> em.persist(new TimbreSummary(run, t.stemModel(), t.stem(), t.centroidMean(),
                t.centroidStd(), t.flatnessMean(), t.rolloffP95(), t.rmsMean())));

        List<ChordEvent> events = ChordEvents.mergeConsecutive(result.chords().stream()
                .map(e -> new ChordEvent(e.startS(), e.endS(), powerChords.reclassify(e.chord(), e.chroma()),
                        e.chroma(), e.confidence()))
                .toList());
        List<ChordSegment> segments = new ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            ChordEvent e = events.get(i);
            ChordSegment segment = new ChordSegment(run, i, e.startS(), e.endS(), e.chord().rootPc(),
                    e.chord().quality(), e.chord().bassPc(), e.confidence(), e.chroma());
            em.persist(segment);
            segments.add(segment);
        }

        if (keySegment != null) {
            annotate(segments, events, keySegment, result.bassNotes());
        }

        if (track.getCanonicalRun() == null) {
            track.setCanonicalRun(run);
        }
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

    private void annotate(List<ChordSegment> segments, List<ChordEvent> events, KeySegment keySegment,
                          List<BassNoteEvent> bassNotes) {
        Key key = new Key(keySegment.getTonicPc(), keySegment.getMode());
        List<Chord> chords = events.stream().map(ChordEvent::chord).toList();
        List<NormalizedChord> normalized = normalizer.normalize(chords, key);
        for (int i = 0; i < segments.size(); i++) {
            NormalizedChord n = normalized.get(i);
            ChordEvent e = events.get(i);
            em.persist(new HarmonicAnnotation(segments.get(i), HarmonicNormalizer.VERSION, keySegment,
                    n.degreeInterval(), n.degreeLabel(), n.keyRelation().name(), n.isInverted(),
                    effectiveBassPc(e, bassNotes),
                    n.fromPrevious() == null ? null : n.fromPrevious().relation().name()));
        }
    }

    /** Classe de altura do baixo que mais tempo soa dentro do segmento; null sem notas. */
    static Integer effectiveBassPc(ChordEvent segment, List<BassNoteEvent> bassNotes) {
        Map<Integer, Double> weight = new HashMap<>();
        for (BassNoteEvent note : bassNotes) {
            double overlap = Math.min(note.endS().doubleValue(), segment.endS().doubleValue())
                    - Math.max(note.startS().doubleValue(), segment.startS().doubleValue());
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
