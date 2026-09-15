package dev.musicsense.orelha.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tudo que o HarmonicNormalizer deriva de um {@link ChordSegment}, versionado por normalizer.
 * Re-derivar cria novas linhas; nunca reescreve o segmento bruto.
 * function_class e relation_from_prev ficam como texto até a Onda 1 fechar o vocabulário (P1).
 */
@Entity
@Table(name = "harmonic_annotation")
public class HarmonicAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chord_segment_id", nullable = false)
    private ChordSegment segment;

    @Column(name = "normalizer_version", nullable = false)
    private String normalizerVersion;

    /** Tonalidade de referência usada na derivação. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "key_segment_id")
    private KeySegment keySegment;

    /** (root_pc − tonic_pc) mod 12; null quando o segmento não tem fundamental. */
    @Column(name = "degree_interval")
    private Integer degreeInterval;

    /** Numeral romano renderizado: 'I', 'bVI', 'vii°'. */
    @Column(name = "degree_label")
    private String degreeLabel;

    @Column(name = "function_class", nullable = false)
    private String functionClass;

    @Column(name = "is_inverted", nullable = false)
    private boolean inverted;

    /** Baixo efetivo, derivado do stem (bass_note), não do rótulo do extrator. */
    @Column(name = "effective_bass_pc")
    private Integer effectiveBassPc;

    /** Relação com o segmento anterior: P, L, R, HEXATONIC_POLE, ... */
    @Column(name = "relation_from_prev")
    private String relationFromPrev;

    protected HarmonicAnnotation() {
    }

    public HarmonicAnnotation(ChordSegment segment, String normalizerVersion, KeySegment keySegment,
                              Integer degreeInterval, String degreeLabel, String functionClass, boolean inverted,
                              Integer effectiveBassPc, String relationFromPrev) {
        this.segment = segment;
        this.normalizerVersion = normalizerVersion;
        this.keySegment = keySegment;
        this.degreeInterval = degreeInterval;
        this.degreeLabel = degreeLabel;
        this.functionClass = functionClass;
        this.inverted = inverted;
        this.effectiveBassPc = effectiveBassPc;
        this.relationFromPrev = relationFromPrev;
    }

    public Long getId() {
        return id;
    }

    public ChordSegment getSegment() {
        return segment;
    }

    public String getNormalizerVersion() {
        return normalizerVersion;
    }

    public KeySegment getKeySegment() {
        return keySegment;
    }

    public Integer getDegreeInterval() {
        return degreeInterval;
    }

    public String getDegreeLabel() {
        return degreeLabel;
    }

    public String getFunctionClass() {
        return functionClass;
    }

    public boolean isInverted() {
        return inverted;
    }

    public Integer getEffectiveBassPc() {
        return effectiveBassPc;
    }

    public String getRelationFromPrev() {
        return relationFromPrev;
    }
}
