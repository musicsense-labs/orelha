package dev.musicsense.orelha.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findByAlbumIdOrderByTrackNoAsc(Long albumId);

    List<Track> findByAlbumArtistId(Long artistId);

    boolean existsByAudioSha256(String audioSha256);

    /** Os bytes de todo o acervo: é o que diz quais pastas de stems e Parquets ainda pertencem a alguém. */
    @Query("select t.audioSha256 from Track t")
    java.util.Set<String> findAllAudioSha256();

    /** Outra faixa com os mesmos bytes: stems e features (por SHA) são dela também. */
    boolean existsByAudioSha256AndIdNot(String audioSha256, Long id);

    /** Outra faixa cadastrada sobre o mesmo arquivo (por path). */
    boolean existsByAudioPathAndIdNot(String audioPath, Long id);

    boolean existsByAlbumIdAndTrackNo(Long albumId, Integer trackNo);
}
