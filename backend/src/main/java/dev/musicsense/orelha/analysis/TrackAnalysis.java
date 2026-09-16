package dev.musicsense.orelha.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Valores globais da faixa devolvidos pela extração (um por run). */
@Entity
@Table(name = "track_analysis")
public class TrackAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false, unique = true)
    private AnalysisRun run;

    @Column(precision = 6, scale = 2)
    private BigDecimal bpm;

    @Column(name = "time_signature")
    private String timeSignature;

    @Column(name = "integrated_lufs", precision = 6, scale = 2)
    private BigDecimal integratedLufs;

    /** Idioma detectado pelo ASR da letra (ISO 639-1) e a confiança; null sem letra (extrator < 0.6.0). */
    @Column(name = "lyrics_language")
    private String lyricsLanguage;

    @Column(name = "lyrics_language_confidence")
    private Float lyricsLanguageConfidence;

    protected TrackAnalysis() {
    }

    public TrackAnalysis(AnalysisRun run, BigDecimal bpm, String timeSignature, BigDecimal integratedLufs) {
        this.run = run;
        this.bpm = bpm;
        this.timeSignature = timeSignature;
        this.integratedLufs = integratedLufs;
    }

    public Long getId() {
        return id;
    }

    public void setLyricsLanguage(String language, Float confidence) {
        this.lyricsLanguage = language;
        this.lyricsLanguageConfidence = confidence;
    }

    public String getLyricsLanguage() {
        return lyricsLanguage;
    }

    public Float getLyricsLanguageConfidence() {
        return lyricsLanguageConfidence;
    }

    public AnalysisRun getRun() {
        return run;
    }

    public BigDecimal getBpm() {
        return bpm;
    }

    public String getTimeSignature() {
        return timeSignature;
    }

    public BigDecimal getIntegratedLufs() {
        return integratedLufs;
    }
}
