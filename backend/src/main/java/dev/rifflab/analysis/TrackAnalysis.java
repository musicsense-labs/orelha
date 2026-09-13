package dev.rifflab.analysis;

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
