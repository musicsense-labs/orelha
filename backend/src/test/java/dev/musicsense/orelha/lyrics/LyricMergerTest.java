package dev.musicsense.orelha.lyrics;

import dev.musicsense.orelha.lyrics.LyricMerger.Segment;
import dev.musicsense.orelha.lyrics.LyricMerger.Word;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os casos são os do Creep, medidos em 2026-09-23: o ASR trocou "I wish I was special" por "I wish out
 * special" e inventou "I love you" sobre um trecho instrumental; o .lrc tem o texto certo e nenhum verso ali.
 */
class LyricMergerTest {

    private static Word w(double start, String text) {
        return new Word(start, start + 0.3, text, 0.9);
    }

    private static Segment seg(double start, String text, Word... words) {
        return new Segment(start, start + 3, text, List.of(words));
    }

    @Test
    void wrongWordsTakeTheLrcTextAndKeepTheAsrTiming() {
        List<Segment> asr = List.of(seg(49.8, "I wish out special",
                w(49.8, "I"), w(50.2, "wish"), w(50.6, "out"), w(51.0, "special")));
        List<LrcFile.Line> lrc = List.of(new LrcFile.Line(49.8, "I wish I was special"));

        LyricMerger.Result r = LyricMerger.merge(asr, lrc);

        Segment merged = r.segments().get(0);
        assertThat(merged.text()).isEqualTo("I wish I was special");
        assertThat(merged.words()).extracting(Word::text).containsExactly("I", "wish", "I", "was", "special");
        // "I" e "wish" mantêm o tempo que o ASR mediu; nada foi inventado do nada.
        assertThat(merged.words().get(0).startS()).isEqualTo(49.8);
        assertThat(merged.words().get(1).startS()).isEqualTo(50.2);
        assertThat(merged.words()).allSatisfy(word -> assertThat(word.startS()).isBetween(49.0, 53.0));
        assertThat(r.corrected() + r.inserted()).isGreaterThan(0);
    }

    @Test
    void wordsTheAsrNeverHeardGetTimeBetweenTheirNeighbours() {
        List<Segment> asr = List.of(seg(10, "float like feather",
                w(10.0, "float"), w(11.0, "like"), w(12.0, "feather")));
        List<LrcFile.Line> lrc = List.of(new LrcFile.Line(10, "You float like a feather in a beautiful world"));

        LyricMerger.Result r = LyricMerger.merge(asr, lrc);

        List<Word> words = r.segments().get(0).words();
        assertThat(words).extracting(Word::text)
                .containsExactly("You", "float", "like", "a", "feather", "in", "a", "beautiful", "world");
        assertThat(words).isSortedAccordingTo((a, b) -> Double.compare(a.startS(), b.startS()));
        assertThat(words).allSatisfy(word -> assertThat(word.endS()).isGreaterThanOrEqualTo(word.startS()));
        assertThat(r.inserted()).isEqualTo(6);
        // as três que o ASR ouviu mantêm o tempo medido
        assertThat(words.get(1).startS()).isEqualTo(10.0);
        assertThat(words.get(4).startS()).isEqualTo(12.0);
    }

    @Test
    void hallucinationsOverInstrumentalAreDropped() {
        // O ASR inventou um verso aos 2:59, onde o .lrc não tem nada (solo).
        List<Segment> asr = List.of(
                seg(49.8, "I wish I was special", w(49.8, "I"), w(50.2, "wish"), w(50.6, "I"), w(51.0, "was"), w(51.4, "special")),
                seg(179.7, "I love you", w(179.7, "I"), w(180.0, "love"), w(180.3, "you")));
        List<LrcFile.Line> lrc = List.of(new LrcFile.Line(49.8, "I wish I was special"));

        LyricMerger.Result r = LyricMerger.merge(asr, lrc);

        assertThat(r.segments()).hasSize(1);
        assertThat(r.segments().get(0).words()).extracting(Word::text).doesNotContain("love");
        assertThat(r.dropped()).isEqualTo(3);
    }

    @Test
    void assertedTextLosesTheProbabilityButKeptWordsDoNot() {
        List<Segment> asr = List.of(seg(0, "hello word", w(0.0, "hello"), w(0.5, "word")));
        List<LrcFile.Line> lrc = List.of(new LrcFile.Line(0, "hello world"));

        List<Word> words = LyricMerger.merge(asr, lrc).segments().get(0).words();

        assertThat(words.get(0).text()).isEqualTo("hello");
        assertThat(words.get(0).probability()).isEqualTo(0.9);     // o ASR acertou: a confiança dele vale
        assertThat(words.get(1).text()).isEqualTo("world");
        assertThat(words.get(1).probability()).isNull();           // texto afirmado pelo .lrc, não medido
    }

    @Test
    void wordTimesNeverGoBackwardsWithinAVerse() {
        // Caso real de "All Together Now": o ASR marcou "have" antes do carimbo do verso e "Can"/"I" entram
        // interpoladas — sem ordenar, o verso sairia com "have" (11,06) depois de "I" (12,26).
        List<Segment> asr = List.of(seg(10.3, "one two three four can I have a little more",
                w(11.06, "have"), w(11.66, "a"), w(12.52, "little"), w(12.94, "more")));
        List<LrcFile.Line> lrc = List.of(new LrcFile.Line(12.16, "Can I have a little more?"));

        List<Word> words = LyricMerger.merge(asr, lrc).segments().get(0).words();

        assertThat(words).extracting(Word::text).containsExactly("Can", "I", "have", "a", "little", "more?");
        assertThat(words).isSortedAccordingTo((a, b) -> Double.compare(a.startS(), b.startS()));
        assertThat(words).allSatisfy(word -> assertThat(word.endS()).isGreaterThan(word.startS()));
    }

    @Test
    void withoutLrcNothingChanges() {
        List<Segment> asr = List.of(seg(0, "as it came", w(0.0, "as"), w(0.4, "it"), w(0.8, "came")));

        LyricMerger.Result r = LyricMerger.merge(asr, List.of());

        assertThat(r.segments()).isEqualTo(asr);
        assertThat(r.changedAnything()).isFalse();
    }
}
