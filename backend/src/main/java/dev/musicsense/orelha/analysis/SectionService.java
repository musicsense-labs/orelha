package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.harmony.ChordQuality;
import dev.musicsense.orelha.harmony.HarmonicNormalizer;
import dev.musicsense.orelha.harmony.SectionDeriver;
import dev.musicsense.orelha.harmony.SectionDeriver.ChordSpan;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Partes de um run: deriva por repetição ({@link SectionDeriver}), guarda a edição do dono (MANUAL)
 * e monta a leitura com a progressão de cada parte colorida pelo eixo A da tonalidade preferida.
 */
@Service
public class SectionService {

    private final SectionRepository sections;
    private final BeatRepository beats;
    private final KeySegmentRepository keys;
    private final HarmonicAnnotationRepository annotations;
    private final EntityManager em;

    SectionService(SectionRepository sections, BeatRepository beats, KeySegmentRepository keys,
                   HarmonicAnnotationRepository annotations, EntityManager em) {
        this.sections = sections;
        this.beats = beats;
        this.keys = keys;
        this.annotations = annotations;
        this.em = em;
    }

    /** Substitui as partes DERIVED do run pela derivação atual. Não toca nas MANUAL. */
    @Transactional
    public List<Section> derive(AnalysisRun run) {
        sections.deleteByRunIdAndSource(run.getId(), SectionSource.DERIVED);
        em.flush();
        List<ChordSegment> segments = em.createQuery(
                        "select s from ChordSegment s where s.run.id = :runId order by s.seqNo", ChordSegment.class)
                .setParameter("runId", run.getId()).getResultList();
        List<ChordSpan> chords = segments.stream()
                .map(s -> new ChordSpan(s.getStartS().doubleValue(), s.getEndS().doubleValue(), chordId(s)))
                .toList();
        List<Double> downbeats = beats.findByRunIdOrderByBeatNo(run.getId()).stream()
                .filter(Beat::isDownbeat)
                .map(b -> b.getTimeS().doubleValue())
                .toList();
        List<Section> result = new ArrayList<>();
        int position = 0;
        for (SectionDeriver.Section s : SectionDeriver.derive(chords, downbeats)) {
            Section section = new Section(run, SectionSource.DERIVED, position++, seconds(s.startS()), seconds(s.endS()),
                    seconds(s.cycleEndS()), s.repeats(), s.label());
            em.persist(section);
            result.add(section);
        }
        return result;
    }

    /** Edição do dono: substitui as partes MANUAL do run; lista vazia volta à derivação. */
    @Transactional
    public List<Section> replaceManual(AnalysisRun run, List<SectionEdit> edits) {
        sections.deleteByRunIdAndSource(run.getId(), SectionSource.MANUAL);
        em.flush();
        List<Section> result = new ArrayList<>();
        int position = 0;
        for (SectionEdit e : edits) {
            if (e.endS().compareTo(e.startS()) <= 0) {
                throw new IllegalArgumentException("Section '" + e.label() + "' ends before it starts");
            }
            BigDecimal cycleEnd = e.cycleEndS() == null || e.cycleEndS().compareTo(e.endS()) > 0 ? e.endS() : e.cycleEndS();
            int repeats = e.repeats() == null || e.repeats() < 1 ? 1 : e.repeats();
            Section section = new Section(run, SectionSource.MANUAL, position++, e.startS(), e.endS(), cycleEnd, repeats,
                    e.label().trim());
            em.persist(section);
            result.add(section);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SectionSource preferredSource(AnalysisRun run) {
        List<Section> found = sections.findPreferred(run.getId());
        return found.isEmpty() ? null : found.get(0).getSource();
    }

    /** As partes da fonte preferida com a progressão de um ciclo, anotada pela tonalidade preferida. */
    @Transactional(readOnly = true)
    public List<SectionsResponse.Part> read(AnalysisRun run) {
        List<Section> found = sections.findPreferred(run.getId());
        if (found.isEmpty()) {
            return List.of();
        }
        KeySegment key = keys.findPreferred(run.getId()).orElse(null);
        List<HarmonicAnnotation> timeline = key == null ? List.of()
                : annotations.findTimeline(run.getId(), HarmonicNormalizer.VERSION, key.getId());
        return found.stream().map(s -> SectionsResponse.Part.of(s, progression(s, timeline))).toList();
    }

    /** Acordes anotados que caem na primeira repetição do ciclo, sem repetição consecutiva. */
    private static List<SectionsResponse.Chord> progression(Section s, List<HarmonicAnnotation> timeline) {
        List<SectionsResponse.Chord> out = new ArrayList<>();
        SectionsResponse.Chord last = null;
        for (HarmonicAnnotation a : timeline) {
            ChordSegment seg = a.getSegment();
            if (seg.getEndS().compareTo(s.getStartS()) <= 0 || seg.getStartS().compareTo(s.getCycleEndS()) >= 0) {
                continue;
            }
            SectionsResponse.Chord chord = SectionsResponse.Chord.of(a);
            if (last == null || !last.sameChord(chord)) {
                out.add(chord);
                last = chord;
            }
        }
        return out;
    }

    /**
     * Identidade do acorde para o teste de repetição: fundamental + família da tríade (maior, menor,
     * diminuta, aumentada). Sétimas, sextas e sus não separam partes — Cmaj7, C7 e Csus2 são "C" aqui.
     * Sus e power chord (sem terça) contam como a família maior só para este teste: o BTC rotula sus2
     * onde a guitarra toca cordas soltas sobre a mesma fundamental, e isso não é outra parte. Não é
     * classificação harmônica; a progressão exibida continua com as qualidades completas.
     */
    static String chordId(ChordSegment s) {
        if (s.getRootPc() == null || !s.getQuality().hasRoot()) {
            return null;
        }
        ChordQuality.TriadFamily family = s.getQuality().triadFamily();
        return s.getRootPc() + ":" + (family == ChordQuality.TriadFamily.NONE ? ChordQuality.TriadFamily.MAJOR : family);
    }

    private static BigDecimal seconds(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    /** Uma parte como o dono a edita; {@code cycleEndS}/{@code repeats} são opcionais (mantêm o "×N" da derivação). */
    public record SectionEdit(BigDecimal startS, BigDecimal endS, String label, BigDecimal cycleEndS, Integer repeats) {
    }
}
