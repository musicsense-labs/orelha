package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.analysis.AnalysisRun;
import dev.musicsense.orelha.analysis.KeySegment;
import dev.musicsense.orelha.analysis.KeySegmentRepository;
import dev.musicsense.orelha.analysis.SectionService;
import dev.musicsense.orelha.analysis.SectionsResponse;
import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.harmony.ChordQuality;
import dev.musicsense.orelha.reference.ReferenceComparer.OurPart;
import dev.musicsense.orelha.reference.ReferenceComparer.SectionMatch;
import dev.musicsense.orelha.reference.RomanNumeralParser.Degree;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Guarda a análise humana colada pelo dono e a compara com a nossa (run canônico): tonalidade
 * preferida e partes com a progressão de um ciclo, reduzidas a fundamental + família da tríade.
 */
@Service
public class ReferenceService {

    private final ReferenceAnalysisRepository references;
    private final SectionService sections;
    private final KeySegmentRepository keys;

    ReferenceService(ReferenceAnalysisRepository references, SectionService sections, KeySegmentRepository keys) {
        this.references = references;
        this.sections = sections;
        this.keys = keys;
    }

    @Transactional(readOnly = true)
    public Optional<ReferenceAnalysis> find(long trackId) {
        return references.findByTrackId(trackId);
    }

    @Transactional
    public ReferenceAnalysis save(Track track, String rawText, String url) {
        ReferenceText.Parsed parsed = ReferenceText.parse(rawText);
        ReferenceAnalysis ref = references.findByTrackId(track.getId()).orElseGet(() -> new ReferenceAnalysis(track));
        ref.update(url == null || url.isBlank() ? null : url.trim(), parsed.tonicPc(), parsed.mode(), rawText,
                parsed.sections());
        return references.save(ref);
    }

    @Transactional
    public void delete(long trackId) {
        references.findByTrackId(trackId).ifPresent(references::delete);
    }

    /** null quando não há referência ou run canônico. */
    @Transactional(readOnly = true)
    public ComparisonResponse compare(Track track) {
        ReferenceAnalysis ref = references.findByTrackId(track.getId()).orElse(null);
        AnalysisRun run = track.getCanonicalRun();
        if (ref == null || run == null) {
            return null;
        }
        KeySegment key = keys.findPreferred(run.getId()).orElse(null);
        Integer tonic = key == null ? null : key.getTonicPc();
        List<OurPart> ours = new ArrayList<>();
        for (SectionsResponse.Part p : sections.read(run)) {
            List<String> keysOfPart = new ArrayList<>();
            for (SectionsResponse.Chord c : p.chords()) {
                String k = keyOf(c, tonic);
                if (k != null) {
                    keysOfPart.add(k);
                }
            }
            ours.add(new OurPart(p.label(), keysOfPart));
        }
        List<SectionMatch> matches = new ArrayList<>();
        for (ReferenceAnalysis.Section s : ref.getSections()) {
            List<Degree> degrees = RomanNumeralParser.parse(s.progression());
            matches.add(ReferenceComparer.match(s.label(), ReferenceComparer.keysOf(degrees), ours));
        }
        double similarity = matches.stream().mapToDouble(SectionMatch::sequenceSimilarity).average().orElse(0);
        double coverage = matches.stream().mapToDouble(SectionMatch::vocabularyCoverage).average().orElse(0);
        ComparisonResponse.KeyView refKey = new ComparisonResponse.KeyView(ref.getTonicPc(), ref.getMode(), ref.getSource());
        ComparisonResponse.KeyView ourKey = key == null ? null
                : new ComparisonResponse.KeyView(key.getTonicPc(), key.getMode(), key.getSource().name());
        boolean tonicMatches = ref.getTonicPc() != null && tonic != null && ref.getTonicPc().equals(tonic);
        boolean modeMatches = tonicMatches && key != null && ref.getMode() != null
                && ref.getMode().isMajorThird() == key.getMode().isMajorThird();
        return new ComparisonResponse(track.getId(), run.getId(), refKey, ourKey, tonicMatches, modeMatches,
                similarity, coverage, matches, ours.stream().map(OurPart::label).toList());
    }

    /** "fundamental:família" do acorde nosso relativo à tônica preferida; null sem fundamental ou sem tônica. */
    static String keyOf(SectionsResponse.Chord c, Integer tonic) {
        if (c.rootPc() == null || tonic == null || c.quality() == null || !c.quality().hasRoot()) {
            return null;
        }
        ChordQuality.TriadFamily family = c.quality().triadFamily();
        String fam = switch (Objects.requireNonNull(family)) {
            case MINOR -> "MINOR";
            case DIMINISHED -> "DIMINISHED";
            case AUGMENTED -> "AUGMENTED";
            default -> "MAJOR";   // MAJOR, e NONE (power chord, sus) reduz a maior — como no SectionDeriver
        };
        return ((c.rootPc() - tonic) % 12 + 12) % 12 + ":" + fam;
    }
}
