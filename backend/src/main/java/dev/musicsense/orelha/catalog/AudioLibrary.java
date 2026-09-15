package dev.musicsense.orelha.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Onde os arquivos de áudio moram e como {@code track.audio_path} os referencia.
 * <p>
 * Arquivos dentro de {@code orelha.library.dir} são gravados como caminho relativo à biblioteca, com
 * {@code /} como separador ({@code 25/Evil Woman.mp3}): mover a pasta do projeto ou trocar de máquina
 * não quebra o acervo. Arquivos cadastrados por path fora da biblioteca continuam absolutos.
 */
@Component
public class AudioLibrary {

    private final Path dir;

    public AudioLibrary(@Value("${orelha.library.dir:../data/audio}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    /** Raiz da biblioteca (absoluta, normalizada). */
    public Path dir() {
        return dir;
    }

    /** Valor a persistir em {@code audio_path} para um arquivo já no disco. */
    public String store(Path audio) {
        Path absolute = audio.toAbsolutePath().normalize();
        if (absolute.startsWith(dir)) {
            return dir.relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString();
    }

    /** Caminho absoluto do arquivo a partir do valor persistido. */
    public Path resolve(String stored) {
        Path path = Path.of(stored);
        return path.isAbsolute() ? path : dir.resolve(path).normalize();
    }

    public Path resolve(Track track) {
        return resolve(track.getAudioPath());
    }
}
