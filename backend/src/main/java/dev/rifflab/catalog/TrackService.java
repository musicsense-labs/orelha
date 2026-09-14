package dev.rifflab.catalog;

import dev.rifflab.analysis.AnalysisQueue;
import dev.rifflab.analysis.AnalysisRun;
import dev.rifflab.common.NotFoundException;
import dev.rifflab.extraction.AudioExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class TrackService {

    private final TrackRepository tracks;
    private final AlbumRepository albums;
    private final AnalysisQueue queue;
    private final AudioExtractor extractor;

    TrackService(TrackRepository tracks, AlbumRepository albums, AnalysisQueue queue, AudioExtractor extractor) {
        this.tracks = tracks;
        this.albums = albums;
        this.queue = queue;
        this.extractor = extractor;
    }

    /** Cadastra a faixa, fixa a identidade dos bytes e enfileira a primeira análise. */
    @Transactional
    public Track register(TrackRequest req) {
        Album album = albums.findById(req.albumId()).orElseThrow(() -> new NotFoundException("Album", req.albumId()));
        Path audio = Path.of(req.audioPath());
        if (!Files.isRegularFile(audio)) {
            throw new IllegalArgumentException("Audio file not found: " + req.audioPath());
        }
        Track track = tracks.save(new Track(album, req.title(), req.trackNo(), audio.toAbsolutePath().toString(), sha256(audio)));
        queue.enqueue(track, extractor.name());
        return track;
    }

    /** Novo run para a mesma faixa (outro extrator/modelo ou re-execução); nunca sobrescreve. */
    @Transactional
    public AnalysisRun enqueueAnalysis(Long trackId) {
        Track track = tracks.findById(trackId).orElseThrow(() -> new NotFoundException("Track", trackId));
        return queue.enqueue(track, extractor.name());
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
