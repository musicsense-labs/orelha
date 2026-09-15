package dev.musicsense.orelha.catalog;

import dev.musicsense.orelha.analysis.AnalysisQueue;
import dev.musicsense.orelha.analysis.AnalysisRun;
import dev.musicsense.orelha.analysis.RunStatus;
import dev.musicsense.orelha.common.NotFoundException;
import dev.musicsense.orelha.extraction.AudioExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class TrackService {

    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "flac", "ogg", "m4a", "aac", "aiff", "aif");

    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final AnalysisQueue queue;
    private final AudioExtractor extractor;
    private final Path libraryDir;

    TrackService(TrackRepository tracks, AlbumRepository albums, AnalysisQueue queue, AudioExtractor extractor,
                 @Value("${orelha.library.dir:../data/audio}") String libraryDir) {
        this.tracks = tracks;
        this.albums = albums;
        this.queue = queue;
        this.extractor = extractor;
        this.libraryDir = Path.of(libraryDir).toAbsolutePath().normalize();
    }

    /** Cadastra uma faixa a partir de um arquivo já no disco, fixa a identidade dos bytes e enfileira a análise. */
    @Transactional
    public Track register(TrackRequest req) {
        Path audio = Path.of(req.audioPath());
        if (!Files.isRegularFile(audio)) {
            throw new IllegalArgumentException("Audio file not found: " + req.audioPath());
        }
        return register(findAlbum(req.albumId()), req.title(), req.trackNo(), audio);
    }

    /** Upload pela UI: grava em orelha.library.dir/<albumId>/ e segue o mesmo caminho do cadastro. */
    @Transactional
    public Track upload(Long albumId, String title, Integer trackNo, MultipartFile file) {
        Album album = findAlbum(albumId);
        String original = baseNameOf(file.getOriginalFilename());
        String extension = extensionOf(original);
        if (!AUDIO_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported audio file: " + original);
        }
        String baseName = (title == null || title.isBlank()) ? original.substring(0, original.length() - extension.length() - 1) : title;
        Path target = uniquePath(libraryDir.resolve(String.valueOf(album.getId())), sanitize(baseName), extension);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return register(album, baseName, trackNo, target);
    }

    /** Novo run para a mesma faixa (outro extrator/modelo ou re-execução); nunca sobrescreve. */
    @Transactional
    public AnalysisRun enqueueAnalysis(Long trackId) {
        Track track = tracks.findById(trackId).orElseThrow(() -> new NotFoundException("Track", trackId));
        return queue.enqueue(track, extractor.name());
    }

    /** Escolhe qual run responde pela faixa nas queries do acervo; precisa estar DONE e ser dela. */
    @Transactional
    public Track setCanonicalRun(Long trackId, Long runId) {
        Track track = tracks.findById(trackId).orElseThrow(() -> new NotFoundException("Track", trackId));
        AnalysisRun run = queue.find(runId);
        if (!run.getTrack().getId().equals(trackId)) {
            throw new IllegalArgumentException("Run " + runId + " does not belong to track " + trackId);
        }
        if (run.getStatus() != RunStatus.DONE) {
            throw new IllegalStateException("Run " + runId + " is " + run.getStatus() + ", not DONE");
        }
        track.setCanonicalRun(run);
        return track;
    }

    private Track register(Album album, String title, Integer trackNo, Path audio) {
        Track track = tracks.save(new Track(album, title, trackNo, audio.toAbsolutePath().toString(), sha256(audio)));
        queue.enqueue(track, extractor.name());
        return track;
    }

    private Album findAlbum(Long albumId) {
        return albums.findById(albumId).orElseThrow(() -> new NotFoundException("Album", albumId));
    }

    private static Path uniquePath(Path dir, String baseName, String extension) {
        Path candidate = dir.resolve(baseName + "." + extension);
        for (int i = 2; Files.exists(candidate); i++) {
            candidate = dir.resolve(baseName + "-" + i + "." + extension);
        }
        return candidate;
    }

    /** Um segmento de caminho vindo do cliente: só troca caracteres proibidos; a extensão fica intacta. */
    static String sanitizeSegment(String segment) {
        String cleaned = segment.replaceAll("[\\\\:*?\"<>|\\p{Cntrl}]+", "-").strip();
        return cleaned.isBlank() ? "_" : cleaned;
    }

    /** Nome de arquivo seguro: sem caracteres proibidos e sem ponto/espaço no fim ("N.I.B." → "N.I.B"). */
    static String sanitize(String name) {
        String cleaned = name.strip().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "-").replaceAll("\\s+", " ")
                .replaceAll("[. ]+$", "");
        return cleaned.isBlank() ? "track" : cleaned;
    }

    /** Só o nome, sem diretórios do cliente — sem passar por Path, que rejeita ':' e '?' no Windows. */
    private static String baseNameOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int cut = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        return originalFilename.substring(cut + 1);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream digestIn = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"))) {
            digestIn.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digestIn.getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
