package dev.rifflab.harmony;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.rifflab.harmony.KeyMode.DORIAN;
import static dev.rifflab.harmony.KeyMode.MAJOR;
import static dev.rifflab.harmony.KeyMode.MINOR;
import static dev.rifflab.harmony.KeyMode.MIXOLYDIAN;
import static dev.rifflab.harmony.KeyMode.PHRYGIAN;
import static dev.rifflab.harmony.KeyMode.PHRYGIAN_DOMINANT;
import static dev.rifflab.harmony.KeyRelation.AMBIGUOUS;
import static dev.rifflab.harmony.KeyRelation.BORROWED;
import static dev.rifflab.harmony.KeyRelation.CHROMATIC;
import static dev.rifflab.harmony.KeyRelation.DIATONIC;
import static dev.rifflab.harmony.KeyRelation.SECONDARY_DOMINANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Portão da Onda 1: a classificação bate com a análise manual do dono do projeto?
 * Cada caso é uma linha da lista fornecida em 2026-09-13, transcrita para a convenção do normalizer
 * (numerais relativos à escala maior; power chords em caixa alta neutra). Onde a leitura do dono
 * exigiu ajuste de regra ou escolha de referência, o comentário diz qual.
 */
class ProgressionClassificationTest {

    /** Eixo A abreviado: D diatônico, B emprestado, S dominante secundária, C cromático, A ambíguo (power). */
    private static final Map<Character, KeyRelation> RELATIONS = Map.of(
            'D', DIATONIC, 'B', BORROWED, 'S', SECONDARY_DOMINANT, 'C', CHROMATIC, 'A', AMBIGUOUS);

    record Progression(String name, Key key, String chords, String labels, String relations,
                       Map<Integer, ChordRelation> transitions) {
    }

    private static Progression p(String name, int tonic, KeyMode mode, String chords, String labels,
                                 String relations, Object... transitions) {
        Map<Integer, ChordRelation> t = new java.util.HashMap<>();
        for (int i = 0; i < transitions.length; i += 2) {
            t.put((Integer) transitions[i], (ChordRelation) transitions[i + 1]);
        }
        return new Progression(name, new Key(tonic, mode), chords, labels, relations, t);
    }

    private static final int C = 0, CS = 1, D = 2, E = 4, F = 5, FS = 6, G = 7, A = 9, B = 11;

    static Stream<Progression> progressions() {
        return Stream.of(
                // Dono: "i5 ♭VII5 i5 ♭III5, tudo diatônico". Power chord: díade diatônica → AMBIGUOUS, label neutro.
                p("War Pigs riff", E, MINOR, "E5 D5 E5 G5", "I5 ♭VII5 I5 ♭III5", "AAAA",
                        1, ChordRelation.WHOLE_TONE, 3, ChordRelation.MEDIANT),
                // "E7 = dominante do menor harmônico" → diatônico em MINOR.
                p("Sabbath Bloody Sabbath", A, MINOR, "Am F G E7", "i ♭VI ♭VII V7", "DDDD",
                        1, ChordRelation.LEITTONWECHSEL, 3, ChordRelation.CHROMATIC_MEDIANT),
                // "B = mediante cromático (não resolve em Em, vai pra C); Cm = iv emprestado".
                p("Creep", G, MAJOR, "G B C Cm", "I III IV iv", "DCDB",
                        1, ChordRelation.CHROMATIC_MEDIANT, 3, ChordRelation.PARALLEL),
                // "E7 = V/vi" sem resolver em Am: regra DOM7-fora-do-campo = dominante secundária.
                p("Don't Look Back in Anger (verso)", C, MAJOR, "C G Am E7 F G C", "I V vi III7 IV V I", "DDDSDDD",
                        3, ChordRelation.FIFTH_UP, 4, ChordRelation.SEMITONE),
                p("Don't Look Back in Anger (pré-refrão)", C, MAJOR, "F Fm C", "IV iv I", "DBD",
                        1, ChordRelation.PARALLEL),
                p("Zombie", E, MINOR, "Em C G D", "i ♭VI ♭III ♭VII", "DDDD", 1, ChordRelation.LEITTONWECHSEL),
                p("Californication", A, MINOR, "Am F C G", "i ♭VI ♭III ♭VII", "DDDD", 2, ChordRelation.FIFTH_UP),
                p("All Along the Watchtower", CS, MINOR, "C#m B A B", "i ♭VII ♭VI ♭VII", "DDDD"),
                // "i5 iv5 ♭III5 ♭VI5, eólio em power chords" → díades diatônicas, AMBIGUOUS.
                p("Smells Like Teen Spirit", F, MINOR, "F5 Bb5 Ab5 Db5", "I5 IV5 ♭III5 ♭VI5", "AAAA"),
                // "♭VII mixolídio, regra do rock" → BORROWED (cabe na menor paralela).
                p("Sweet Child O' Mine (verso)", D, MAJOR, "D C G D", "I ♭VII IV I", "DBDD",
                        2, ChordRelation.FIFTH_UP, 3, ChordRelation.FIFTH_UP),
                p("Comfortably Numb (verso)", B, MINOR, "Bm A G Em", "i ♭VII ♭VI iv", "DDDD"),
                p("Comfortably Numb (refrão)", D, MAJOR, "D A C G", "I V ♭VII IV", "DDBD"),
                // "F#7 = V harmônico; E = IV dórico (ou V/♭VII)" → E cabe em B maior: BORROWED.
                p("Hotel California", B, MINOR, "Bm F#7 A E G D Em F#7", "i V7 ♭VII IV ♭VI ♭III iv V7", "DDDBDDDD",
                        2, ChordRelation.CHROMATIC_MEDIANT, 4, ChordRelation.CHROMATIC_MEDIANT),
                // Dono: "A7 = V/V que nunca resolve". A7sus4 não tem terça nem cabe no vocabulário:
                // reduzido a Asus4, que é diatônico em Sol. Divergência registrada no portão.
                p("Wish You Were Here", G, MAJOR, "Em7 G Em7 G Em7 A7sus4 Em7 A7sus4 G",
                        "vi7 I vi7 I vi7 IIsus4 vi7 IIsus4 I", "DDDDDDDDD", 1, ChordRelation.RELATIVE),
                // "só o E é da tonalidade, o resto vem de E menor" (A é IV de E maior).
                p("Hey Joe", E, MAJOR, "C G D A E", "♭VI ♭III ♭VII IV I", "BBBDD",
                        1, ChordRelation.FIFTH_UP, 2, ChordRelation.FIFTH_UP, 3, ChordRelation.FIFTH_UP, 4, ChordRelation.FIFTH_UP),
                // "D/F# = IV dórico" → BORROWED; baixo cromático fica em BassRole (testado à parte).
                p("Stairway to Heaven (intro)", A, MINOR, "Am Am/G# C/G D/F# Fmaj7 G Am",
                        "i i ♭III IV ♭VImaj7 ♭VII i", "DDDBDDD", 1, ChordRelation.SAME),
                p("Knockin' on Heaven's Door", G, MAJOR, "G D Am7 G D C", "I V ii7 I V IV", "DDDDDD"),
                // "G7 = IV dórico; A7 = V harmônico" → referência DORIAN com dominante da harmônica.
                p("Come Together (verso)", D, DORIAN, "Dm7 A7 G7", "i7 V7 IV7", "DDD"),
                p("Come Together (refrão)", D, MAJOR, "Bm A G A", "vi V IV V", "DDDD"),
                p("Stand By Me (Oasis)", G, MAJOR, "G D Am7 C", "I V ii7 IV", "DDDD"),
                p("Don't Go Away (verso)", A, MINOR, "Am G Fadd9 Dm", "i ♭VII ♭VI iv", "DDDD"),
                p("Don't Go Away (pré)", A, MINOR, "Dm F G", "iv ♭VI ♭VII", "DDD"),
                p("Don't Go Away (refrão)", C, MAJOR, "C G Am G F G", "I V vi V IV V", "DDDDDD"),
                p("Don't Go Away (ponte)", C, MAJOR, "F Fm", "IV iv", "DB", 1, ChordRelation.PARALLEL),
                // "F# é power chord no disco, terça ambígua" → F#5: AMBIGUOUS, transição MEDIANT.
                // "Bb = mediante cromático" → eixo B (G → Bb); eixo A: ♭VI emprestado.
                p("Lithium (verso)", D, MAJOR, "D F#5 Bm G Bb C A C", "I III5 vi IV ♭VI ♭VII V ♭VII", "DADDBBDB",
                        1, ChordRelation.MEDIANT, 4, ChordRelation.CHROMATIC_MEDIANT, 7, ChordRelation.CHROMATIC_MEDIANT),
                p("Lithium (ponte)", D, MAJOR, "G Bb", "IV ♭VI", "DB", 1, ChordRelation.CHROMATIC_MEDIANT),
                // "F = mediante cromático sobre tônica maior; sem V".
                p("Heart-Shaped Box", A, MAJOR, "A F D", "I ♭VI IV", "DBD",
                        1, ChordRelation.CHROMATIC_MEDIANT, 2, ChordRelation.CHROMATIC_MEDIANT),
                // "E (blues)": referência MIXOLYDIAN (I7 diatônico); "G = ♭III de blues (mediante por baixo)".
                p("Purple Haze", E, MIXOLYDIAN, "E7#9 G A", "I7 ♭III IV", "DBD",
                        1, ChordRelation.CHROMATIC_MEDIANT, 2, ChordRelation.WHOLE_TONE),
                // "F# G = I ♭II, par frígio" com tônica maior → PHRYGIAN_DOMINANT.
                p("White Rabbit (verso)", FS, PHRYGIAN_DOMINANT, "F# G F# G", "I ♭II I ♭II", "DDDD",
                        1, ChordRelation.SEMITONE),
                p("White Rabbit (refrão)", A, MAJOR, "A B A E", "I II I V", "DCDD"),
                // "♭II em power chord; frígio, não harmônico".
                p("Wherever I May Roam (riff)", E, PHRYGIAN, "E5 F5", "I5 ♭II5", "AA", 1, ChordRelation.SEMITONE),
                p("Wherever I May Roam (refrão)", E, PHRYGIAN, "E5 G5 A5 F5", "I5 ♭III5 IV5 ♭II5", "AAAA"),
                // "D = IV dórico, E = V harmônico; dois modos menores na mesma volta".
                p("House of the Rising Sun", A, MINOR, "Am C D F Am C E", "i ♭III IV ♭VI i ♭III V", "DDBDDDD"),
                // "cadência andaluza; A = V harmônico".
                p("Sultans of Swing", D, MINOR, "Dm C Bb A", "i ♭VII ♭VI V", "DDDD", 3, ChordRelation.SEMITONE),
                // "V só como B5, mas funciona como dominante" → díade diatônica, AMBIGUOUS.
                p("Seven Nation Army", E, MINOR, "E5 G5 E5 D5 C5 B5", "I5 ♭III5 I5 ♭VII5 ♭VI5 V5", "AAAAAA"),
                // "Db5 = ♭5 de passagem (blue note), não ♭II" → ♭V em modo de terça menor, CHROMATIC.
                p("Smoke on the Water (riff)", G, MINOR, "G5 Bb5 C5 G5 Bb5 Db5 C5",
                        "I5 ♭III5 IV5 I5 ♭III5 ♭V5 IV5", "AAAAACA"),
                p("Smoke on the Water (verso)", G, MINOR, "Gm F C", "i ♭VII IV", "DDB"),
                // "D/F# = IV dórico invertido; depois V harmônico".
                p("While My Guitar Gently Weeps (verso)", A, MINOR, "Am Am/G D/F# Fmaj7 Am G C E",
                        "i i IV ♭VImaj7 i ♭VII ♭III V", "DDBDDDDD"),
                p("While My Guitar Gently Weeps (ponte)", A, MAJOR, "A C#m F#m C F#m Bm E",
                        "I iii vi ♭III vi ii V", "DDDBDDD", 3, ChordRelation.TRITONE),
                // "C7 = V/IV; D7 = V/V".
                p("Something (1ª frase)", C, MAJOR, "C Cmaj7 C7 F D7 G", "I Imaj7 I7 IV II7 V", "DDSDSD"),
                // "cliché em Am (A G# G); D9 = V/V (vai para F); Eb = ♭III, mediante antes do G".
                p("Something (2ª frase)", C, MAJOR, "Am Am(maj7) Am7 D9 F Eb G", "vi vimM7 vi7 II7 IV ♭III V", "DCDSDBD",
                        5, ChordRelation.WHOLE_TONE, 6, ChordRelation.CHROMATIC_MEDIANT),
                // Riff de Kashmir é pedal + linha cromática, não sequência de acordes (query da Onda 3); só a ponte.
                p("Kashmir (ponte)", D, MAJOR, "G A D", "IV V I", "DDD")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("progressions")
    void classifiesLikeTheOwner(Progression p) {
        List<NormalizedChord> out = new HarmonicNormalizer().normalize(ChordSymbol.parseAll(p.chords()), p.key());

        assertThat(out).extracting(NormalizedChord::degreeLabel)
                .as("graus de %s", p.name())
                .containsExactly(p.labels().split(" "));

        List<KeyRelation> expectedRelations = p.relations().chars()
                .mapToObj(c -> RELATIONS.get((char) c)).toList();
        assertThat(out).extracting(NormalizedChord::keyRelation)
                .as("eixo A de %s", p.name())
                .containsExactlyElementsOf(expectedRelations);

        p.transitions().forEach((index, relation) ->
                assertThat(out.get(index).fromPrevious().relation())
                        .as("transição %d de %s", index, p.name())
                        .isEqualTo(relation));
    }

    @ParameterizedTest(name = "bass line: {0}")
    @MethodSource("bassLines")
    void bassRolesOfInvertedChords(String name, Key key, String chords, List<BassRole> roles) {
        List<NormalizedChord> out = new HarmonicNormalizer().normalize(ChordSymbol.parseAll(chords), key);
        assertThat(out).extracting(NormalizedChord::bassRole).containsExactlyElementsOf(roles);
        assertThat(out).extracting(NormalizedChord::isInverted)
                .containsExactlyElementsOf(roles.stream().map(r -> r != BassRole.UNKNOWN && r != BassRole.ROOT).toList());
    }

    static Stream<Object[]> bassLines() {
        return Stream.of(
                // "cromatismo descendente no baixo (A G# G F# F)"
                new Object[]{"Stairway", new Key(A, MINOR), "Am Am/G# C/G D/F# Fmaj7",
                        Arrays.asList(BassRole.UNKNOWN, BassRole.NON_CHORD_TONE, BassRole.FIFTH, BassRole.THIRD, BassRole.UNKNOWN)},
                // "i/♭7 IV/6" (baixo A G F# F)
                new Object[]{"While My Guitar Gently Weeps", new Key(A, MINOR), "Am Am/G D/F# Fmaj7",
                        Arrays.asList(BassRole.UNKNOWN, BassRole.NON_CHORD_TONE, BassRole.THIRD, BassRole.UNKNOWN)}
        );
    }
}
