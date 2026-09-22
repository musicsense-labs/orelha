package dev.musicsense.orelha.metadata;

import dev.musicsense.orelha.metadata.AlbumMatcher.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumMatcherTest {

    @Test
    void boxSetNamesAreReducedToTheAlbum() {
        // Os nomes reais do acervo (rip do box set de 2009).
        assertThat(AlbumMatcher.cleanTitle("Studio Albums Stereo Remastered Box Set (16CD) (2009) 05 - Help (6th August 1965)"))
                .isEqualTo("Help");
        assertThat(AlbumMatcher.cleanTitle("Studio Albums Stereo Remastered Box Set (16CD) (2009) 12 - Abbey Road"))
                .isEqualTo("Abbey Road");
        assertThat(AlbumMatcher.cleanTitle("Studio Albums Stereo Remastered Box Set (16CD) (2009) 10 - White Album (2CD) CD1"))
                .isEqualTo("White Album");
        // Títulos já limpos passam intactos.
        assertThat(AlbumMatcher.cleanTitle("Nevermind")).isEqualTo("Nevermind");
        assertThat(AlbumMatcher.cleanTitle("Born to Run")).isEqualTo("Born to Run");
    }

    @Test
    void theRightReleaseGroupWinsAndCarriesTheFirstReleaseYear() {
        List<Candidate> candidates = List.of(
                new Candidate("m-live", "Help! (live)", "The Beatles", "Album", List.of("Live"), 1996, "live"),
                new Candidate("m-comp", "The Alternate Help!", "The Beatles", "Album", List.of("Compilation"), 1965, null),
                new Candidate("m-other", "Help! (Deluxe)", "The Beatles", "Other", List.of(), null, null),
                new Candidate("m-rg", "Help!", "The Beatles", "Album", List.of("Soundtrack"), 1965, null),
                new Candidate("m-tribute", "Help!", "Beatles Tribute Band", "Album", List.of(), 2004, null));

        List<AlbumMatcher.Scored> ranked = AlbumMatcher.rank(
                "Studio Albums Stereo Remastered Box Set (16CD) (2009) 05 - Help (6th August 1965)", "The Beatles", candidates);

        assertThat(ranked.get(0).candidate().mbid()).isEqualTo("m-rg");
        assertThat(ranked.get(0).candidate().firstReleased()).isEqualTo(1965);
        assertThat(ranked.get(0).score()).isGreaterThan(AlbumMatcher.CONFIDENT);
        // Nada além do álbum de estúdio pode passar do limiar do lote: o tributo (outro artista), a coletânea
        // com o mesmo nome ("The Alternate Help!", secondary-type Compilation), o ao vivo e o primary-type Other.
        assertThat(ranked.stream().filter(s -> !s.candidate().mbid().equals("m-rg")))
                .allSatisfy(s -> assertThat(s.score()).isLessThan(AlbumMatcher.CONFIDENT));
        // Soundtrack não é demérito: Help! e A Hard Day's Night são trilhas.
        assertThat(ranked.get(0).candidate().secondaryTypes()).containsExactly("Soundtrack");
    }

    @Test
    void artistNameIsComparedWithoutArticleAccentOrCase() {
        assertThat(AlbumMatcher.fold("The Beatles")).isEqualTo("beatles");
        assertThat(AlbumMatcher.fold("Os Mutantes")).isEqualTo("mutantes");
        assertThat(AlbumMatcher.fold("Sinéad O'Connor")).isEqualTo("sinead o connor");
        assertThat(AlbumMatcher.similarity("nevermind", "nevermind")).isEqualTo(1);
        // Conter o outro conta pouco quando sobra muito: "Help!" não é "Help! Deluxe Edition Vol. Two".
        assertThat(AlbumMatcher.similarity("help", "help deluxe edition vol two")).isLessThan(0.7);
        assertThat(AlbumMatcher.similarity("abbey road", "abbey road 2")).isGreaterThan(0.8);
        assertThat(AlbumMatcher.nameSimilarity("beatles", "beatles tribute band")).isLessThan(0.7);
        assertThat(AlbumMatcher.similarity("nevermind", "in utero")).isLessThan(0.4);
    }

    @Test
    void anEmptyCandidateListRanksToNothing() {
        assertThat(AlbumMatcher.rank("Nevermind", "Nirvana", List.of())).isEmpty();
    }
}
