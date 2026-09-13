package dev.rifflab.analysis;

import dev.rifflab.harmony.ChordQuality;
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

/**
 * Segmento de acorde como o extrator devolveu (já no nosso vocabulário de quality).
 * Só dado bruto: o que o HarmonicNormalizer deriva vive em {@link HarmonicAnnotation}.
 */
@Entity
@Table(name = "chord_segment")
public class ChordSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun run;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Column(name = "start_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal startS;

    @Column(name = "end_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal endS;

    /** null para NO_CHORD / UNKNOWN. */
    @Column(name = "root_pc")
    private Integer rootPc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChordQuality quality;

    /** Baixo rotulado pelo extrator (slash chord), se ele produzir. */
    @Column(name = "bass_pc")
    private Integer bassPc;

    private Float confidence;

    /** Chroma médio do segmento (12 classes, C=0): evidência para o teste de terça. */
    @Column(columnDefinition = "real[]")
    private float[] chroma;

    protected ChordSegment() {
    }

    public ChordSegment(AnalysisRun run, int seqNo, BigDecimal startS, BigDecimal endS, Integer rootPc,
                        ChordQuality quality, Integer bassPc, Float confidence, float[] chroma) {
        this.run = run;
        this.seqNo = seqNo;
        this.startS = startS;
        this.endS = endS;
        this.rootPc = rootPc;
        this.quality = quality;
        this.bassPc = bassPc;
        this.confidence = confidence;
        this.chroma = chroma;
    }

    public Long getId() {
        return id;
    }

    public AnalysisRun getRun() {
        return run;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public BigDecimal getStartS() {
        return startS;
    }

    public BigDecimal getEndS() {
        return endS;
    }

    public Integer getRootPc() {
        return rootPc;
    }

    public ChordQuality getQuality() {
        return quality;
    }

    public Integer getBassPc() {
        return bassPc;
    }

    public Float getConfidence() {
        return confidence;
    }

    public float[] getChroma() {
        return chroma;
    }
}
