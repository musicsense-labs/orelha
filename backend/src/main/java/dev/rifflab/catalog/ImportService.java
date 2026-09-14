package dev.rifflab.catalog;

import dev.rifflab.analysis.AnalysisQueue;
import dev.rifflab.catalog.ImportReport.Imported;
import dev.rifflab.catalog.ImportReport.Skipped;
import dev.rifflab.extraction.AudioExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Importação em lote: lê as tags de cada arquivo, cria/reusa artista e álbum, cadastra a faixa e
 * enfileira a análise. Arquivo já conhecido (mesmo SHA-256) é pulado. Cada faixa é uma transação
 * própria (TransactionTemplate): um arquivo ruim não derruba a pasta inteira.
 */
@Service
public class ImportService {

    static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "flac", "ogg", "m4a", "aac", "aiff", "aif");

    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final TrackRepository tracks;
    private final AnalysisQueue queue;
    private final AudioExtractor extractor;
    private final TransactionTemplate tx;
    private final Path libraryDir;

    ImportService(ArtistRepository artists, AlbumRepository albums, TrackRepository tracks, AnalysisQueue queue,
                  AudioExtractor extractor, PlatformTransactionManager transactionManager,
                  @Value("${rifflab.library.dir:../data/audio}") String libraryDir) {
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
        this.queue = queue;
        this.extractor = extractor;
        this.tx = new TransactionTemplate(transactionManager);
        this.libraryDir = Path.of(libraryDir).toAbsolutePath().normalize();
    }

    /** Pasta já no disco do servidor: os arquivos ficam onde estão (sem cópia). */
    public ImportReport importDirectory(Path directory, boolean recursive) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        List<Path> files;
        try (Stream<Path> walk = recursive ? Files.walk(directory) : Files.list(directory)) {
            files = walk.filter(Files::isRegularFile).filter(p -> isAudio(p.getFileName().toString())).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Imported> imported = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (Path file : files) {
            importOne(file, file.toAbsolutePath(), directory.toAbsolutePath().relativize(file.toAbsolutePath()).toString(),
                    true, imported, skipped);
        }
        return new ImportReport(imported, skipped);
    }

    /**
     * Upload de pasta pela UI. O nome original pode trazer o caminho relativo ("Artista/Álbum/01 x.mp3"),
     * usado no fallback quando faltam tags; o conteúdo é gravado em rifflab.library.dir/<albumId>/.
     */
    public ImportReport importUploads(List<MultipartFile> uploads) {
        List<Imported> imported = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (MultipartFile upload : uploads) {
            String relative = upload.getOriginalFilename() == null ? "" : upload.getOriginalFilename().replace('\\', '/');
            String name = relative.substring(relative.lastIndexOf('/') + 1);
            if (!isAudio(name)) {
                skipped.add(new Skipped(relative, "não é áudio"));
                continue;
            }
            Path temp = null;
            try {
                temp = Files.createTempFile("riff-import-", "." + extensionOf(name));
                try (InputStream in = upload.getInputStream()) {
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                }
                importOne(temp, Path.of(relative), relative, false, imported, skipped);
            } catch (IOException e) {
                skipped.add(new Skipped(relative, "falha ao gravar: " + e.getMessage()));
            } finally {
                if (temp != null) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                        // temporário; o SO limpa
                    }
                }
            }
        }
        return new ImportReport(imported, skipped);
    }

    /**
     * @param content     arquivo a ler (tags e hash)
     * @param nominalPath caminho para o fallback de pastas/nome
     * @param keepInPlace true = a faixa referencia content onde está; false = copia para a biblioteca
     */
    private void importOne(Path content, Path nominalPath, String displayName, boolean keepInPlace,
                           List<Imported> imported, List<Skipped> skipped) {
        try {
            String sha = TrackService.sha256(content);
            if (tracks.existsByAudioSha256(sha)) {
                skipped.add(new Skipped(displayName, "já importado (mesmo conteúdo)"));
                return;
            }
            AudioTags tags = AudioTags.read(content, nominalPath);
            Registered r = tx.execute(status -> register(tags, content, keepInPlace, sha));
            imported.add(new Imported(r.track().getId(), displayName, r.artist(), r.album(), r.track().getTitle(),
                    r.track().getTrackNo(), tags.fromTags()));
        } catch (Exception e) {
            skipped.add(new Skipped(displayName, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /** Nomes como ficaram no catálogo (artista/álbum reusados podem diferir da tag em caixa). */
    private record Registered(Track track, String artist, String album) {
    }

    private Registered register(AudioTags tags, Path content, boolean keepInPlace, String sha) {
        Artist artist = artists.findFirstByNameIgnoreCase(tags.artist())
                .orElseGet(() -> artists.save(new Artist(tags.artist(), null, null)));
        Album album = albums.findFirstByArtistIdAndTitleIgnoreCase(artist.getId(), tags.album())
                .orElseGet(() -> albums.save(new Album(artist, tags.album(), tags.year())));
        if (album.getYear() == null && tags.year() != null) {
            album.setYear(tags.year());
        }
        Integer trackNo = tags.trackNo();
        if (trackNo != null && tracks.existsByAlbumIdAndTrackNo(album.getId(), trackNo)) {
            trackNo = null;   // número já ocupado neste álbum: cadastra sem número em vez de falhar
        }
        Path audio = keepInPlace ? content : copyIntoLibrary(content, album, tags.title());
        Track track = tracks.save(new Track(album, tags.title(), trackNo, audio.toAbsolutePath().toString(), sha));
        queue.enqueue(track, extractor.name());
        return new Registered(track, artist.getName(), album.getTitle());
    }

    private Path copyIntoLibrary(Path content, Album album, String title) {
        try {
            Path dir = libraryDir.resolve(String.valueOf(album.getId()));
            Files.createDirectories(dir);
            Path target = uniquePath(dir, TrackService.sanitize(title), extensionOf(content.getFileName().toString()));
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean isAudio(String fileName) {
        return AUDIO_EXTENSIONS.contains(extensionOf(fileName));
    }

    private static Path uniquePath(Path dir, String baseName, String extension) {
        Path candidate = dir.resolve(baseName + "." + extension);
        for (int i = 2; Files.exists(candidate); i++) {
            candidate = dir.resolve(baseName + "-" + i + "." + extension);
        }
        return candidate;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
