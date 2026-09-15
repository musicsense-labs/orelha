package dev.musicsense.orelha.catalog;

import dev.musicsense.orelha.analysis.AnalysisQueue;
import dev.musicsense.orelha.catalog.ImportReport.Confirmation;
import dev.musicsense.orelha.catalog.ImportReport.Imported;
import dev.musicsense.orelha.catalog.ImportReport.Item;
import dev.musicsense.orelha.catalog.ImportReport.Preview;
import dev.musicsense.orelha.catalog.ImportReport.Skipped;
import dev.musicsense.orelha.extraction.AudioExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;
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
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Importação em lote em dois passos: <em>preview</em> (lê tags, marca duplicatas, nada é gravado no
 * catálogo) e <em>confirm</em> (cadastra os itens como a UI os editou e enfileira a análise).
 * Uploads ficam num diretório de staging entre os dois passos; arquivos do servidor ficam no lugar.
 * Cada faixa confirmada é uma transação própria: um arquivo ruim não derruba a pasta inteira.
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
    private final Path stagingDir;

    ImportService(ArtistRepository artists, AlbumRepository albums, TrackRepository tracks, AnalysisQueue queue,
                  AudioExtractor extractor, PlatformTransactionManager transactionManager,
                  @Value("${orelha.library.dir:../data/audio}") String libraryDir,
                  @Value("${orelha.staging.dir:../data/staging}") String stagingDir) {
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
        this.queue = queue;
        this.extractor = extractor;
        this.tx = new TransactionTemplate(transactionManager);
        this.libraryDir = Path.of(libraryDir).toAbsolutePath().normalize();
        this.stagingDir = Path.of(stagingDir).toAbsolutePath().normalize();
    }

    // --- passo 1: preview -------------------------------------------------------------------------

    /** Pasta já no disco do servidor. */
    public Preview previewDirectory(Path directory, boolean recursive) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        List<Path> files;
        try (Stream<Path> walk = recursive ? Files.walk(directory) : Files.list(directory)) {
            files = walk.filter(Files::isRegularFile).filter(p -> isAudio(p.getFileName().toString())).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path root = directory.toAbsolutePath();
        List<Item> items = new ArrayList<>();
        for (Path file : files) {
            Path abs = file.toAbsolutePath();
            items.add(item(abs.toString(), root.relativize(abs).toString(), abs, abs));
        }
        return new Preview(null, items);
    }

    /**
     * Upload de pasta pela UI: grava em staging/&lt;id&gt;/&lt;caminho relativo&gt; (o nome original traz
     * "Artista/Álbum/01 x.mp3", que vale como fallback) e devolve a pré-visualização.
     */
    public Preview stageUploads(List<MultipartFile> uploads) {
        String stagingId = UUID.randomUUID().toString();
        Path dir = stagingDir.resolve(stagingId);
        List<Item> items = new ArrayList<>();
        for (MultipartFile upload : uploads) {
            String relative = upload.getOriginalFilename() == null ? "" : upload.getOriginalFilename().replace('\\', '/');
            String name = relative.substring(relative.lastIndexOf('/') + 1);
            if (!isAudio(name)) {
                continue;
            }
            Path target = safeStagingPath(dir, relative);
            try {
                Files.createDirectories(target.getParent());
                try (InputStream in = upload.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            items.add(item(target.toString(), relative, target, Path.of(relative)));
        }
        return new Preview(stagingId, items);
    }

    private Item item(String key, String displayName, Path content, Path nominalPath) {
        AudioTags tags = AudioTags.read(content, nominalPath);
        boolean duplicate = tracks.existsByAudioSha256(TrackService.sha256(content));
        return new Item(key, displayName, tags.artist(), tags.album(), tags.year(), tags.title(), tags.trackNo(),
                tags.fromTags(), duplicate);
    }

    // --- passo 2: confirm -------------------------------------------------------------------------

    /** Cadastra os itens como vieram da UI. Itens de staging são movidos para a biblioteca; o staging é apagado. */
    public ImportReport confirm(Confirmation confirmation) {
        Path staging = confirmation.stagingId() == null ? null : stagingDir.resolve(requireStagingId(confirmation.stagingId()));
        List<Imported> imported = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        for (Item item : confirmation.items()) {
            Path source = Path.of(item.key()).toAbsolutePath().normalize();
            if (staging != null && !source.startsWith(staging)) {
                skipped.add(new Skipped(item.file(), "fora do staging"));
                continue;
            }
            if (!Files.isRegularFile(source)) {
                skipped.add(new Skipped(item.file(), "arquivo não encontrado"));
                continue;
            }
            importOne(source, item, staging == null, imported, skipped);
        }
        if (staging != null) {
            FileSystemUtils.deleteRecursively(staging.toFile());
        }
        return new ImportReport(imported, skipped);
    }

    /** Descarta um staging sem importar (o usuário cancelou a pré-visualização). */
    public void discardStaging(String stagingId) {
        FileSystemUtils.deleteRecursively(stagingDir.resolve(requireStagingId(stagingId)).toFile());
    }

    /** Atalhos sem edição: preview + confirmar tudo que não é duplicata. */
    public ImportReport importDirectory(Path directory, boolean recursive) {
        return confirm(all(previewDirectory(directory, recursive)));
    }

    public ImportReport importUploads(List<MultipartFile> uploads) {
        return confirm(all(stageUploads(uploads)));
    }

    private static Confirmation all(Preview preview) {
        return new Confirmation(preview.stagingId(), preview.items());
    }

    private void importOne(Path content, Item item, boolean keepInPlace, List<Imported> imported, List<Skipped> skipped) {
        try {
            String sha = TrackService.sha256(content);
            if (tracks.existsByAudioSha256(sha)) {
                skipped.add(new Skipped(item.file(), "já importado (mesmo conteúdo)"));
                return;
            }
            AudioTags tags = new AudioTags(item.artist().strip(), item.album().strip(), item.year(), item.title().strip(),
                    item.trackNo(), item.fromTags());
            Registered r = tx.execute(status -> register(tags, content, keepInPlace, sha));
            imported.add(new Imported(r.track().getId(), item.file(), r.artist(), r.album(), r.track().getTitle(),
                    r.track().getTrackNo(), item.fromTags()));
        } catch (Exception e) {
            skipped.add(new Skipped(item.file(), e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /** Nomes como ficaram no catálogo (artista/álbum reusados podem diferir do item em caixa). */
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
        Path audio = keepInPlace ? content : moveIntoLibrary(content, album, tags.title());
        Track track = tracks.save(new Track(album, tags.title(), trackNo, audio.toAbsolutePath().toString(), sha));
        queue.enqueue(track, extractor.name());
        return new Registered(track, artist.getName(), album.getTitle());
    }

    private Path moveIntoLibrary(Path content, Album album, String title) {
        try {
            Path dir = libraryDir.resolve(String.valueOf(album.getId()));
            Files.createDirectories(dir);
            Path target = uniquePath(dir, TrackService.sanitize(title), extensionOf(content.getFileName().toString()));
            Files.move(content, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Caminho relativo do upload sob o staging: segmentos "." e ".." são descartados (nomes como "N.I.B..mp3" ficam intactos). */
    private Path safeStagingPath(Path dir, String relative) {
        Path target = dir;
        for (String segment : relative.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                continue;
            }
            target = target.resolve(TrackService.sanitizeSegment(segment));
        }
        target = target.normalize();
        if (target.equals(dir) || !target.startsWith(dir)) {
            throw new IllegalArgumentException("Invalid upload path: " + relative);
        }
        return target;
    }

    private static String requireStagingId(String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid staging id");
        }
        return id;
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
