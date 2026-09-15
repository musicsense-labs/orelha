package dev.musicsense.orelha.harmony;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Transformações neo-riemannianas e relações de terça: casos de manual, sem julgamento de contexto. */
class TransitionTest {

    @ParameterizedTest(name = "{0}{1} → {2}{3} = {4} ({5} common tones)")
    @CsvSource({
            // P / R / L e suas voltas
            "0, MAJ, 0, MIN, PARALLEL, 2",
            "0, MIN, 0, MAJ, PARALLEL, 2",
            "0, MAJ, 9, MIN, RELATIVE, 2",          // C → Am
            "9, MIN, 0, MAJ, RELATIVE, 2",          // Am → C
            "0, MAJ, 4, MIN, LEITTONWECHSEL, 2",    // C → Em
            "4, MIN, 0, MAJ, LEITTONWECHSEL, 2",    // Em → C
            "9, MIN, 5, MAJ, LEITTONWECHSEL, 2",    // Am → F
            "5, MAJ, 9, MIN, LEITTONWECHSEL, 2",    // F → Am
            // Polo hexatônico (L·P·L)
            "0, MAJ, 8, MIN, HEXATONIC_POLE, 0",    // C → A♭m
            "8, MIN, 0, MAJ, HEXATONIC_POLE, 0",    // A♭m → C
            "9, MIN, 1, MAJ, HEXATONIC_POLE, 0",    // Am → D♭
            // Mediantes cromáticos (1 nota comum) — a classe do projeto
            "0, MAJ, 4, MAJ, CHROMATIC_MEDIANT, 1",  // C → E
            "0, MAJ, 8, MAJ, CHROMATIC_MEDIANT, 1",  // C → A♭
            "0, MAJ, 3, MAJ, CHROMATIC_MEDIANT, 1",  // C → E♭
            "0, MAJ, 9, MAJ, CHROMATIC_MEDIANT, 1",  // C → A
            "9, MIN, 0, MIN, CHROMATIC_MEDIANT, 1",  // Am → Cm
            "9, MIN, 6, MIN, CHROMATIC_MEDIANT, 1",  // Am → F♯m
            "0, MAJ, 9, MIN7, RELATIVE, 2",          // 7ª reduzida à tríade: C → Am7 ainda é R
            // Duplamente cromático (0 notas comuns, não polo)
            "0, MAJ, 3, MIN, DOUBLY_CHROMATIC_MEDIANT, 0",  // C → E♭m
            // Mediante com dim/aug reduzidos
            "0, MAJ, 4, DIM, DIATONIC_MEDIANT, 2",   // C → E° (E G B♭ ∩ C E G = E, G)
            // Power chords: terça sem qualificação
            "4, POWER, 0, POWER, MEDIANT, 0",        // E5 → C5
            "4, POWER, 7, POWER, MEDIANT, 0",        // E5 → G5
            // Mesma fundamental
            "0, MAJ, 0, MAJ, SAME, 3",
            "0, MAJ, 0, DOM7, SAME_ROOT, 3",
            "0, MAJ, 0, SUS4, SAME_ROOT, 0",
            // Quintas, trítono, graus conjuntos
            "7, MAJ, 0, MAJ, FIFTH_DOWN, 1",         // G → C
            "0, MAJ, 7, MAJ, FIFTH_UP, 1",           // C → G
            "0, MAJ, 6, MAJ, TRITONE, 0",
            "0, MAJ, 1, MAJ, SEMITONE, 0",
            "0, MAJ, 1, MIN, SEMITONE, 1",           // slide (C → C♯m): fica pesquisável pelas notas comuns
            "0, MAJ, 11, MAJ, SEMITONE, 0",
            "0, MAJ, 10, MAJ, WHOLE_TONE, 0",
            "0, MAJ, 2, MIN, WHOLE_TONE, 0",
    })
    void classifies(int fromRoot, ChordQuality fromQ, int toRoot, ChordQuality toQ, ChordRelation expected, int common) {
        Transition t = Transition.between(Chord.of(fromRoot, fromQ), Chord.of(toRoot, toQ));
        assertThat(t.relation()).isEqualTo(expected);
        assertThat(t.commonTones()).isEqualTo(common);
        assertThat(t.rootInterval()).isEqualTo(PitchClasses.interval(fromRoot, toRoot));
    }
}
