package dev.rifflab.extraction.riffextractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rifflab.extraction.ExtractionResult;
import dev.rifflab.extraction.ExtractionResult.ChordEvent;
import dev.rifflab.harmony.Chord;
import dev.rifflab.harmony.ChordQuality;
import dev.rifflab.harmony.KeyMode;
import dev.rifflab.harmony.PowerChordDetector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: a fixture é a resposta real do container riff-extractor 0.4.0 sobre o WAV sintético
 * de {@code extractor/app/testaudio.py} (Am F C G × 2, 120 BPM, baixo na fundamental). Nada de
 * container em teste; se o contrato mudar, regrave a fixture e este teste conta o que mudou.
 */
class RiffExtractorAdapterTest {

    static ExtractionResult result;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = RiffExtractorAdapterTest.class.getResourceAsStream("/fixtures/riff-extractor/progression.json")) {
            result = RiffExtractorAdapter.toResult(new ObjectMapper().readValue(in, RiffExtractorResponse.class));
        }
    }

    @Test
    void provenanceNamesEveryModel() {
        assertThat(result.provenance().name()).isEqualTo("riff-extractor");
        assertThat(result.provenance().version()).isEqualTo("0.4.0");
        assertThat(result.stems()).containsOnlyKeys("bass", "drums", "other", "vocals");
        assertThat(result.stems().get("bass")).startsWith("/data/stems/").endsWith("/bass.ogg");   // Opus em Ogg
        assertThat(result.provenance().models()).containsEntry("stems_codec", "opus@128k");
        assertThat(result.provenance().models()).containsKeys("chords", "beats", "key", "stems", "bass", "timbre");
        assertThat(result.provenance().models().get("chords")).startsWith("chordmini/btc_model_best.pth");
        assertThat(result.featuresPath()).endsWith(".parquet");
    }

    @Test
    void audioKeyAndTempo() {
        assertThat(result.audio().durationS()).isEqualByComparingTo("16.0");
        assertThat(result.audio().sampleRate()).isEqualTo(44100);
        assertThat(result.audio().integratedLufs()).isNegative();
        // madmom lê o loop vi–IV–I–V como Dó maior: a referência tonal é decisão nossa, não do extrator.
        assertThat(result.key().tonicPc()).isZero();
        assertThat(result.key().mode()).isEqualTo(KeyMode.MAJOR);
        assertThat(result.key().confidence()).isBetween(0f, 1f);
        assertThat(result.tempo().bpm()).isEqualByComparingTo("120.0");
        assertThat(result.tempo().timeSignature()).isEqualTo("4/4");
    }

    @Test
    void beatsCarryBarPositions() {
        assertThat(result.beats()).hasSize(32);
        assertThat(result.beats().stream().filter(b -> b.position() == 1)).hasSize(8);
        assertThat(result.beats().get(0).timeS()).isEqualByComparingTo("0.0");
        assertThat(result.beats().get(1).timeS()).isEqualByComparingTo("0.5");
    }

    @Test
    void chordsAreHarteLabelsTranslatedToChords() {
        List<Chord> expectedLoop = List.of(Chord.of(9, ChordQuality.MIN), Chord.of(5, ChordQuality.MAJ),
                Chord.of(0, ChordQuality.MAJ), Chord.of(7, ChordQuality.MAJ));
        assertThat(result.chords()).extracting(ChordEvent::chord)
                .containsExactlyElementsOf(List.of(expectedLoop, expectedLoop).stream().flatMap(List::stream).toList());
        assertThat(result.chords().get(0).startS()).isEqualByComparingTo("0");
        assertThat(result.chords().get(7).endS()).isEqualByComparingTo("15.975");
        for (ChordEvent e : result.chords()) {
            assertThat(e.endS()).isGreaterThan(e.startS());
            assertThat(e.confidence()).as("BTC não devolve confiança").isNull();
        }
    }

    @Test
    void mixChromaPeaksAtTheRootAndLowChromaIsDominatedByTheTriad() {
        for (ChordEvent e : result.chords()) {
            float[] chroma = e.chroma();
            assertThat(chroma).hasSize(12);
            int peak = IntStream.range(0, 12).reduce((a, b) -> chroma[a] >= chroma[b] ? a : b).getAsInt();
            assertThat(peak).as("pico do chroma em %s", e.chord()).isEqualTo(e.chord().rootPc());

            float[] low = e.chromaLow();
            assertThat(low).hasSize(12);
            List<Integer> top3 = IntStream.range(0, 12).boxed()
                    .sorted((a, b) -> Float.compare(low[b], low[a])).limit(3).toList();
            List<Integer> triad = IntStream.range(0, 12)
                    .filter(pc -> (e.chord().triad() & (1 << pc)) != 0).boxed().toList();
            assertThat(top3).as("chroma_low de %s", e.chord()).containsExactlyInAnyOrderElementsOf(triad);
        }
    }

    @Test
    void fullTriadsInRealLowChromaAreNotMistakenForPowerChords() {
        PowerChordDetector detector = new PowerChordDetector(PowerChordDetector.DEFAULT_THIRD_RATIO);
        for (ChordEvent e : result.chords()) {
            assertThat(detector.reclassify(e.chord(), e.chromaLow()))
                    .as("%s at %s", e.chord(), e.startS())
                    .isEqualTo(e.chord());
        }
    }

    @Test
    void bassNotesAndTimbrePerStem() {
        assertThat(result.bassNotes()).hasSizeBetween(30, 40);   // basic-pitch varia ±3 notas entre execuções
        assertThat(result.bassNotes().get(0).midi()).isEqualTo(45);               // A1 sob Am
        assertThat(result.bassNotes().get(0).velocity()).isBetween(1, 127);
        assertThat(result.timbre()).extracting(ExtractionResult.TimbreStat::stem)
                .containsExactly("bass", "drums", "other", "vocals");
        assertThat(result.timbre()).allSatisfy(t -> {
            assertThat(t.stemModel()).isEqualTo("htdemucs");
            assertThat(t.centroidMean()).isPositive();
            assertThat(t.rmsMean()).isNotNull();
        });
    }
}
