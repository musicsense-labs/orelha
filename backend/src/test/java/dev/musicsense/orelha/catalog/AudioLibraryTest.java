package dev.musicsense.orelha.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AudioLibraryTest {

    @TempDir
    Path tempDir;

    @Test
    void filesInsideTheLibraryAreStoredRelativeWithForwardSlashes() {
        AudioLibrary library = new AudioLibrary(tempDir.resolve("audio").toString());
        Path file = tempDir.resolve("audio").resolve("25").resolve("Evil Woman.mp3");

        String stored = library.store(file);

        assertThat(stored).isEqualTo("25/Evil Woman.mp3");
        assertThat(library.resolve(stored)).isEqualTo(file.toAbsolutePath().normalize());
    }

    @Test
    void filesOutsideTheLibraryStayAbsolute() {
        AudioLibrary library = new AudioLibrary(tempDir.resolve("audio").toString());
        Path file = tempDir.resolve("Music").resolve("Valerie.mp3");

        String stored = library.store(file);

        assertThat(stored).isEqualTo(file.toAbsolutePath().normalize().toString());
        assertThat(library.resolve(stored)).isEqualTo(file.toAbsolutePath().normalize());
    }

    @Test
    void relativeInputIsResolvedAgainstTheLibraryEvenIfTheLibraryMoved() {
        AudioLibrary before = new AudioLibrary(tempDir.resolve("old").resolve("data").resolve("audio").toString());
        AudioLibrary after = new AudioLibrary(tempDir.resolve("new").resolve("data").resolve("audio").toString());
        String stored = before.store(before.dir().resolve("8").resolve("Misery.mp3"));

        assertThat(after.resolve(stored)).isEqualTo(after.dir().resolve("8").resolve("Misery.mp3"));
    }
}
