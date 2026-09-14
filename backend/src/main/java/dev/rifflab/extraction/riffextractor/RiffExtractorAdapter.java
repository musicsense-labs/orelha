package dev.rifflab.extraction.riffextractor;

import dev.rifflab.extraction.AudioExtractor;
import dev.rifflab.extraction.ExtractionResult;
import dev.rifflab.extraction.ExtractionResult.AudioInfo;
import dev.rifflab.extraction.ExtractionResult.BassNoteEvent;
import dev.rifflab.extraction.ExtractionResult.BeatEvent;
import dev.rifflab.extraction.ExtractionResult.ChordEvent;
import dev.rifflab.extraction.ExtractionResult.KeyEstimate;
import dev.rifflab.extraction.ExtractionResult.Provenance;
import dev.rifflab.extraction.ExtractionResult.Tempo;
import dev.rifflab.extraction.ExtractionResult.TimbreStat;
import dev.rifflab.extraction.HarteLabel;
import dev.rifflab.harmony.KeyMode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/** Traduz o JSON do riff-extractor para {@link ExtractionResult}. Sem interpretação musical. */
@Component
public class RiffExtractorAdapter implements AudioExtractor {

    public static final String NAME = "riff-extractor";

    private final RiffExtractorClient client;

    RiffExtractorAdapter(RiffExtractorClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ExtractionResult analyze(Path audio, String audioSha256) {
        return toResult(client.analyze(audio, audioSha256));
    }

    static ExtractionResult toResult(RiffExtractorResponse r) {
        return new ExtractionResult(
                new Provenance(r.extractor().name(), r.extractor().version(), r.extractor().models()),
                new AudioInfo(r.audio().durationS(), r.audio().sampleRate(), r.audio().integratedLufs()),
                r.key() == null ? null : new KeyEstimate(r.key().tonicPc(), mode(r.key().mode()), r.key().confidence()),
                r.tempo() == null ? null : new Tempo(r.tempo().bpm(), r.tempo().timeSignature()),
                orEmpty(r.beats()).stream().map(b -> new BeatEvent(b.timeS(), b.position())).toList(),
                orEmpty(r.chords()).stream()
                        .map(c -> new ChordEvent(c.startS(), c.endS(), HarteLabel.parse(c.label()), c.chroma(),
                                c.chromaLow(), null))
                        .toList(),
                orEmpty(r.bassNotes()).stream()
                        .map(n -> new BassNoteEvent(n.startS(), n.endS(), n.midi(), n.velocity())).toList(),
                orEmpty(r.timbre()).stream()
                        .map(t -> new TimbreStat(t.stemModel(), t.stem(), t.centroidMean(), t.centroidStd(),
                                t.flatnessMean(), t.rolloffP95(), t.rmsMean()))
                        .toList(),
                r.featuresPath());
    }

    /** O madmom só conhece 'major' e 'minor'; modos são atribuídos depois (MANUAL/DERIVED). */
    private static KeyMode mode(String mode) {
        return switch (mode.toLowerCase()) {
            case "major" -> KeyMode.MAJOR;
            case "minor" -> KeyMode.MINOR;
            default -> throw new IllegalArgumentException("Unknown key mode from extractor: " + mode);
        };
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
