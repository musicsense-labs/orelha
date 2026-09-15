package dev.musicsense.orelha.harmony;

import dev.musicsense.orelha.harmony.SectionDeriver.ChordSpan;
import dev.musicsense.orelha.harmony.SectionDeriver.Section;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SectionDeriverTest {

    private static final double BAR = 2.0;   // 120 BPM em 4/4

    /** Um acorde por compasso, a partir de {@code startBar}. */
    private static List<ChordSpan> barChords(int startBar, String... ids) {
        List<ChordSpan> out = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            out.add(new ChordSpan((startBar + i) * BAR, (startBar + i + 1) * BAR, ids[i]));
        }
        return out;
    }

    private static List<Double> downbeats(int bars) {
        return IntStream.range(0, bars).mapToObj(b -> b * BAR).toList();
    }

    private static List<ChordSpan> concat(List<ChordSpan>... parts) {
        List<ChordSpan> out = new ArrayList<>();
        for (List<ChordSpan> p : parts) {
            out.addAll(p);
        }
        return out;
    }

    @Test
    void verseAndChorusBecomeTwoLetteredPartsWithRepeatCounts() {
        // verso: C Am F G ×2 · refrão: G D Em F ×2 · verso de novo ×2
        List<ChordSpan> chords = concat(
                barChords(0, "C", "Am", "F", "G", "C", "Am", "F", "G"),
                barChords(8, "G", "D", "Em", "F", "G", "D", "Em", "F"),
                barChords(16, "C", "Am", "F", "G", "C", "Am", "F", "G"));

        List<Section> sections = SectionDeriver.derive(chords, downbeats(24));

        assertThat(sections).extracting(Section::label).containsExactly("A", "B", "A");
        assertThat(sections).extracting(Section::repeats).containsExactly(2, 2, 2);
        assertThat(sections.get(0)).isEqualTo(new Section(0, 16, 8, 2, "A"));
        assertThat(sections.get(1).startS()).isEqualTo(16);
        assertThat(sections.get(1).cycleEndS()).isEqualTo(24);
    }

    @Test
    void oneChordPerTwoBarsIsNotFragmentedIntoTinyCycles() {
        // C C Am Am F F G G (harmonia lenta, sem repetição interna) seguido de um refrão que repete
        List<ChordSpan> chords = concat(
                barChords(0, "C", "C", "Am", "Am", "F", "F", "G", "G"),
                barChords(8, "E5", "G5", "E5", "G5", "E5", "G5", "E5", "G5"));

        List<Section> sections = SectionDeriver.derive(chords, downbeats(16));

        assertThat(sections).extracting(Section::label).containsExactly("A", "B");
        assertThat(sections.get(0)).isEqualTo(new Section(0, 16, 16, 1, "A"));
        assertThat(sections.get(1)).isEqualTo(new Section(16, 32, 20, 4, "B"));
    }

    @Test
    void pedalOnOneChordIsOneCycleOfOneBar() {
        List<ChordSpan> chords = List.of(new ChordSpan(0, 16, "E5"));

        List<Section> sections = SectionDeriver.derive(chords, downbeats(8));

        assertThat(sections).containsExactly(new Section(0, 16, 2, 8, "A"));
    }

    @Test
    void twoChordsInOneBarKeepTheirOrderInTheSignature() {
        // | C G | Am F | ×2: a assinatura do compasso preserva a ordem dentro dele
        List<ChordSpan> chords = new ArrayList<>();
        for (int rep = 0; rep < 2; rep++) {
            double t = rep * 4 * BAR;
            chords.add(new ChordSpan(t, t + 1, "C"));
            chords.add(new ChordSpan(t + 1, t + 2, "G"));
            chords.add(new ChordSpan(t + 2, t + 3, "Am"));
            chords.add(new ChordSpan(t + 3, t + 4, "F"));
            chords.add(new ChordSpan(t + 4, t + 5, "C"));
            chords.add(new ChordSpan(t + 5, t + 6, "G"));
            chords.add(new ChordSpan(t + 6, t + 7, "Am"));
            chords.add(new ChordSpan(t + 7, t + 8, "F"));
        }

        List<Section> sections = SectionDeriver.derive(chords, downbeats(8));

        assertThat(sections).containsExactly(new Section(0, 16, 4, 4, "A"));
    }

    @Test
    void silenceAtTheEdgesIsNotAPartButSilenceInsideIs() {
        List<ChordSpan> chords = concat(
                List.of(new ChordSpan(0, 4, null)),
                barChords(2, "C", "F", "C", "F"),
                List.of(new ChordSpan(12, 14, null)),
                barChords(7, "C", "F", "C", "F"),
                List.of(new ChordSpan(22, 26, null)));

        List<Section> sections = SectionDeriver.derive(chords, downbeats(13));

        assertThat(sections).extracting(Section::label).containsExactly("A", "B", "A");
        assertThat(sections.get(0).startS()).isEqualTo(4);
        assertThat(sections.get(2).endS()).isEqualTo(22);
    }

    @Test
    void briefChordGlitchesDoNotChangeTheBarSignature() {
        // G ocupa 5% do compasso: ignorado, o ciclo continua sendo C Am F G
        List<ChordSpan> chords = new ArrayList<>();
        for (int rep = 0; rep < 3; rep++) {
            double t = rep * 4 * BAR;
            chords.add(new ChordSpan(t, t + 1.9, "C"));
            chords.add(new ChordSpan(t + 1.9, t + 2, "G"));
            chords.add(new ChordSpan(t + 2, t + 4, "Am"));
            chords.add(new ChordSpan(t + 4, t + 6, "F"));
            chords.add(new ChordSpan(t + 6, t + 8, "G"));
        }

        List<Section> sections = SectionDeriver.derive(chords, downbeats(12));

        assertThat(sections).containsExactly(new Section(0, 24, 8, 3, "A"));
    }

    @Test
    void noDownbeatsMeansNoSections() {
        assertThat(SectionDeriver.derive(barChords(0, "C", "F"), List.of())).isEmpty();
    }
}
