package dev.rifflab.catalog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Fallback sem tags: só o caminho decide (o arquivo não existe, então jaudiotagger não lê nada). */
class AudioTagsTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Black Sabbath/Paranoid/01 War Pigs.mp3            | Black Sabbath    | Paranoid | War Pigs      | 1",
            "Black Sabbath/Paranoid/03 - Planet Caravan.flac   | Black Sabbath    | Paranoid | Planet Caravan| 3",
            "Music/Valerie - The Zutons (youtube).mp3          | Valerie          | Music    | The Zutons    |",
            "Music/Radiohead - Creep (Remastered) [Audio].mp3  | Radiohead        | Music    | Creep         |",
            "Music/Born to Run.mp3                             | Desconhecido     | Music    | Born to Run   |",
            "Deep Purple/Machine Head/Smoke on the Water.ogg   | Deep Purple      | Machine Head | Smoke on the Water |",
    })
    void guessesFromPathWhenThereAreNoTags(String path, String artist, String album, String title, Integer trackNo) {
        AudioTags tags = AudioTags.read(Path.of("Z:/nope/" + path), Path.of(path));
        assertThat(tags.artist()).isEqualTo(artist);
        assertThat(tags.album()).isEqualTo(album);
        assertThat(tags.title()).isEqualTo(title);
        assertThat(tags.trackNo()).isEqualTo(trackNo);
        assertThat(tags.fromTags()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Creep (Remastered) (Audio)      | Creep",
            "Creep [Official Video]          | Creep",
            "(Don't Fear) The Reaper         | (Don't Fear) The Reaper",
            "Paranoid                        | Paranoid",
    })
    void cleansTrailingBrackets(String raw, String expected) {
        assertThat(AudioTags.cleanTitle(raw)).isEqualTo(expected);
    }
}
