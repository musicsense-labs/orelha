package dev.rifflab.analysis;

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

@Entity
@Table(name = "beat")
public class Beat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun run;

    @Column(name = "beat_no", nullable = false)
    private int beatNo;

    @Column(name = "time_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal timeS;

    @Column(name = "bar_no")
    private Integer barNo;

    @Column(name = "is_downbeat", nullable = false)
    private boolean downbeat;

    protected Beat() {
    }

    public Beat(AnalysisRun run, int beatNo, BigDecimal timeS, Integer barNo, boolean downbeat) {
        this.run = run;
        this.beatNo = beatNo;
        this.timeS = timeS;
        this.barNo = barNo;
        this.downbeat = downbeat;
    }

    public Long getId() {
        return id;
    }

    public AnalysisRun getRun() {
        return run;
    }

    public int getBeatNo() {
        return beatNo;
    }

    public BigDecimal getTimeS() {
        return timeS;
    }

    public Integer getBarNo() {
        return barNo;
    }

    public boolean isDownbeat() {
        return downbeat;
    }
}
