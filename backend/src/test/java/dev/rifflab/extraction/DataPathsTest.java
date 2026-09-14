package dev.rifflab.extraction;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataPathsTest {

    private final DataPaths paths = new DataPaths("/data", "C:/riff/data");

    @Test
    void mapsContainerPathsUnderTheRootToTheHost() {
        assertThat(paths.toHost("/data/stems/abc/bass.wav"))
                .isEqualTo(Path.of("C:/riff/data/stems/abc/bass.wav").toAbsolutePath().normalize());
    }

    @Test
    void rejectsPathsOutsideTheRootOrEscapingIt() {
        assertThat(paths.toHost("/tmp/x.wav")).isNull();
        assertThat(paths.toHost("/data/../etc/passwd")).isNull();
        assertThat(paths.toHost("/data/stems/../../secret")).isNull();
        assertThat(paths.toHost(null)).isNull();
    }
}
