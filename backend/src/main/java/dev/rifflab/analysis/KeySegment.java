package dev.rifflab.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Tonalidade vigente num trecho. Tonalidade global = um único segmento cobrindo a faixa. */
@Entity
@Table(name = "key_segment")
public class KeySegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun run;

    @Column(name = "start_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal startS;

    @Column(name = "end_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal endS;

    @Column(name = "tonic_pc", nullable = false)
    private int tonicPc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KeyMode mode;

    private Float confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KeySource source;

    protected KeySegment() {
    }

    public KeySegment(AnalysisRun run, BigDecimal startS, BigDecimal endS, int tonicPc, KeyMode mode,
                      Float confidence, KeySource source) {
        this.run = run;
        this.startS = startS;
        this.endS = endS;
        this.tonicPc = tonicPc;
        this.mode = mode;
        this.confidence = confidence;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public AnalysisRun getRun() {
        return run;
    }

    public BigDecimal getStartS() {
        return startS;
    }

    public BigDecimal getEndS() {
        return endS;
    }

    public int getTonicPc() {
        return tonicPc;
    }

    public KeyMode getMode() {
        return mode;
    }

    public Float getConfidence() {
        return confidence;
    }

    public KeySource getSource() {
        return source;
    }
}
