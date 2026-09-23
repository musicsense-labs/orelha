package dev.musicsense.orelha.lyrics;

import dev.musicsense.orelha.catalog.Track;
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

/** Um verso do .lrc da faixa: quando começa e o texto, como o arquivo trouxe. */
@Entity
@Table(name = "lrc_line")
public class LrcLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "start_s", nullable = false)
    private BigDecimal startS;

    @Column(nullable = false)
    private String text;

    protected LrcLine() {
    }

    public LrcLine(Track track, int lineNo, BigDecimal startS, String text) {
        this.track = track;
        this.lineNo = lineNo;
        this.startS = startS;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public BigDecimal getStartS() {
        return startS;
    }

    public String getText() {
        return text;
    }
}
