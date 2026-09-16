package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.reference.ReferenceComparer.OurPart;
import dev.musicsense.orelha.reference.ReferenceComparer.SectionMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReferenceComparerTest {

    @Test
    void identicalProgressionScoresOne() {
        SectionMatch m = ReferenceComparer.match("Verse", List.of("0:MAJOR", "7:MAJOR", "9:MINOR", "5:MAJOR"),
                List.of(new OurPart("A", List.of("0:MAJOR", "7:MAJOR", "9:MINOR", "5:MAJOR"))));
        assertThat(m.ourLabel()).isEqualTo("A");
        assertThat(m.sequenceSimilarity()).isEqualTo(1.0);
        assertThat(m.vocabularyCoverage()).isEqualTo(1.0);
        assertThat(m.missingKeys()).isEmpty();
    }

    @Test
    void consecutiveRepeatsDoNotCount() {
        assertThat(ReferenceComparer.dedupe(List.of("0:MAJOR", "0:MAJOR", "5:MAJOR", "5:MAJOR")))
                .containsExactly("0:MAJOR", "5:MAJOR");
    }

    @Test
    void picksTheClosestPartAndReportsWhatIsMissing() {
        // Referência: I III IV iv (Creep). Nossa parte B tem I III IV (faltou o iv); A é outra coisa.
        SectionMatch m = ReferenceComparer.match("Chorus", List.of("0:MAJOR", "4:MAJOR", "5:MAJOR", "5:MINOR"),
                List.of(new OurPart("A", List.of("9:MINOR", "5:MAJOR", "0:MAJOR", "7:MAJOR")),
                        new OurPart("B", List.of("0:MAJOR", "4:MAJOR", "5:MAJOR"))));
        assertThat(m.ourLabel()).isEqualTo("B");
        assertThat(m.sequenceSimilarity()).isCloseTo(0.75, within(1e-9));
        assertThat(m.vocabularyCoverage()).isCloseTo(0.75, within(1e-9));
        assertThat(m.missingKeys()).containsExactly("5:MINOR");
    }

    @Test
    void noPartsAtAll() {
        SectionMatch m = ReferenceComparer.match("Verse", List.of("0:MAJOR"), List.of());
        assertThat(m.ourLabel()).isNull();
        assertThat(m.sequenceSimilarity()).isZero();
    }
}
