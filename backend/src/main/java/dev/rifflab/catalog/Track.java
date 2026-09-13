package dev.rifflab.catalog;

import dev.rifflab.analysis.AnalysisRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "track")
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(nullable = false)
    private String title;

    @Column(name = "track_no")
    private Integer trackNo;

    /** Preenchido pela extração; desconhecido no cadastro. */
    @Column(name = "duration_s", precision = 9, scale = 3)
    private BigDecimal durationS;

    @Column(name = "audio_path", nullable = false)
    private String audioPath;

    /** SHA-256 dos bytes do áudio: identidade do que cada run analisou. */
    @Column(name = "audio_sha256", nullable = false, length = 64)
    private String audioSha256;

    @Column(name = "sample_rate")
    private Integer sampleRate;

    /** Run escolhido para as queries de corpus; null enquanto nenhum run terminou. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_run_id")
    private AnalysisRun canonicalRun;

    protected Track() {
    }

    public Track(Album album, String title, Integer trackNo, String audioPath, String audioSha256) {
        this.album = album;
        this.title = title;
        this.trackNo = trackNo;
        this.audioPath = audioPath;
        this.audioSha256 = audioSha256;
    }

    public Long getId() {
        return id;
    }

    public Album getAlbum() {
        return album;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getTrackNo() {
        return trackNo;
    }

    public void setTrackNo(Integer trackNo) {
        this.trackNo = trackNo;
    }

    public BigDecimal getDurationS() {
        return durationS;
    }

    public void setDurationS(BigDecimal durationS) {
        this.durationS = durationS;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public String getAudioSha256() {
        return audioSha256;
    }

    public Integer getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

    public AnalysisRun getCanonicalRun() {
        return canonicalRun;
    }

    public void setCanonicalRun(AnalysisRun canonicalRun) {
        this.canonicalRun = canonicalRun;
    }
}
