package dev.musicsense.orelha.extraction.orelhaextractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.musicsense.orelha.extraction.ExtractionResult;
import dev.musicsense.orelha.extraction.ExtractionResult.NoteEvent;
import dev.musicsense.orelha.extraction.ExtractionResult.ChordEvent;
import dev.musicsense.orelha.harmony.Chord;
import dev.musicsense.orelha.harmony.ChordQuality;
import dev.musicsense.orelha.harmony.KeyMode;
import dev.musicsense.orelha.harmony.PowerChordDetector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: a fixture é a resposta real do container orelha-extractor 0.6.0 sobre o WAV sintético
 * de {@code extractor/app/testaudio.py} (Am F C G × 2, 120 BPM, baixo na fundamental). Nada de
 * container em teste; se o contrato mudar, regrave a fixture e este teste conta o que mudou.
 */
class OrelhaExtractorAdapterTest {

    static ExtractionResult result;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = OrelhaExtractorAdapterTest.class.getResourceAsStream("/fixtures/orelha-extractor/progression.json")) {
            result = OrelhaExtractorAdapter.toResult(new ObjectMapper().readValue(in, OrelhaExtractorResponse.class));
        }
    }

    @Test
    void provenanceNamesEveryModel() {
        assertThat(result.provenance().name()).isEqualTo("orelha-extractor");
        assertThat(result.provenance().version()).isEqualTo("0.6.0");
        assertThat(result.stems()).containsOnlyKeys("bass", "drums", "other", "vocals");
        assertThat(result.stems().get("bass")).startsWith("/data/stems/").endsWith("/bass.ogg");   // Opus em Ogg
        assertThat(result.provenance().models()).containsEntry("stems_codec", "opus@128k");
        assertThat(result.provenance().models()).containsKeys("chords", "beats", "key", "stems", "bass", "lyrics", "timbre");
        assertThat(result.provenance().models().get("lyrics")).startsWith("faster-whisper/");
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
        // Voz: o WAV sintético não tem canto, mas o demucs deixa vazamento no stem e o basic-pitch transcreve
        // algo; o contrato é só a forma (notas na tessitura de voz, 80–1100 Hz ≈ E2–C6) e a ordem.
        assertThat(result.vocalNotes()).isNotEmpty();
        assertThat(result.vocalNotes()).allSatisfy(n -> {
            assertThat(n.midi()).isBetween(40, 84);
            assertThat(n.endS()).isGreaterThan(n.startS());
        });
        assertThat(result.vocalNotes()).isSortedAccordingTo(java.util.Comparator.comparing(NoteEvent::startS));
        // Letra: sem canto no WAV sintético o ASR não devolve trecho algum (e o idioma detectado é lixo, sem
        // confiança); o contrato é a forma — objeto presente, lista vazia, nunca null.
        assertThat(result.lyrics()).isNotNull();
        assertThat(result.lyrics().segments()).isEmpty();
        assertThat(result.lyrics().languageConfidence()).isLessThan(0.5f);
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
