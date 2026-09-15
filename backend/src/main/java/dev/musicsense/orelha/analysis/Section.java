package dev.musicsense.orelha.analysis;

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

/** Uma parte da música num run (A, B, "refrão"…), com o ciclo que a define quando há repetição. */
@Entity
@Table(name = "section")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SectionSource source;

    @Column(nullable = false)
    private int position;

    @Column(name = "start_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal startS;

    @Column(name = "end_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal endS;

    @Column(name = "cycle_end_s", nullable = false, precision = 9, scale = 3)
    private BigDecimal cycleEndS;

    @Column(nullable = false)
    private int repeats;

    @Column(nullable = false, length = 64)
    private String label;

    protected Section() {
    }

    public Section(AnalysisRun run, SectionSource source, int position, BigDecimal startS, BigDecimal endS,
                   BigDecimal cycleEndS, int repeats, String label) {
        this.run = run;
        this.source = source;
        this.position = position;
        this.startS = startS;
        this.endS = endS;
        this.cycleEndS = cycleEndS;
        this.repeats = repeats;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public AnalysisRun getRun() {
        return run;
    }

    public SectionSource getSource() {
        return source;
    }

    public int getPosition() {
        return position;
    }

    public BigDecimal getStartS() {
        return startS;
    }

    public BigDecimal getEndS() {
        return endS;
    }

    public BigDecimal getCycleEndS() {
        return cycleEndS;
    }

    public int getRepeats() {
        return repeats;
    }

    public String getLabel() {
        return label;
    }
}
