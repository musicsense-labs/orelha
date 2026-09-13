package dev.rifflab.catalog;

import dev.rifflab.common.NotFoundException;
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

    TrackService(TrackRepository tracks, AlbumRepository albums) {
        this.tracks = tracks;
        this.albums = albums;
    }

    /** Cadastra a faixa e fixa a identidade dos bytes. Enfileirar a análise é papel da Onda 2. */
    @Transactional
    public Track register(TrackRequest req) {
        Album album = albums.findById(req.albumId()).orElseThrow(() -> new NotFoundException("Album", req.albumId()));
        Path audio = Path.of(req.audioPath());
        if (!Files.isRegularFile(audio)) {
            throw new IllegalArgumentException("Audio file not found: " + req.audioPath());
        }
        return tracks.save(new Track(album, req.title(), req.trackNo(), audio.toAbsolutePath().toString(), sha256(audio)));
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
