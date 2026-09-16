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

import java.math.BigDecimal;

/** Palavra com tempo dentro de um {@link LyricSegment}; {@code probability} é a confiança do ASR na palavra. */
@Entity
@Table(name = "lyric_word")
public class LyricWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lyric_segment_id", nullable = false)
    private LyricSegment segment;

    @Column(name = "start_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal startS;

    @Column(name = "end_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal endS;

    @Column(nullable = false)
    private String text;

    private Float probability;

    protected LyricWord() {
    }

    LyricWord(LyricSegment segment, BigDecimal startS, BigDecimal endS, String text, Float probability) {
        this.segment = segment;
        this.startS = startS;
        this.endS = endS;
        this.text = text;
        this.probability = probability;
    }

    public Long getId() {
        return id;
    }

    public LyricSegment getSegment() {
        return segment;
    }

    public BigDecimal getStartS() {
        return startS;
    }

    public BigDecimal getEndS() {
        return endS;
    }

    public String getText() {
        return text;
    }

    public Float getProbability() {
        return probability;
    }
}
