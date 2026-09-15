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

/** Nota do stem de baixo transcrita para MIDI. */
@Entity
@Table(name = "bass_note")
public class BassNote {

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

    @Column(name = "midi_pitch", nullable = false)
    private int midiPitch;

    private Integer velocity;

    protected BassNote() {
    }

    public BassNote(AnalysisRun run, BigDecimal startS, BigDecimal endS, int midiPitch, Integer velocity) {
        this.run = run;
        this.startS = startS;
        this.endS = endS;
        this.midiPitch = midiPitch;
        this.velocity = velocity;
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

    public int getMidiPitch() {
        return midiPitch;
    }

    public int getPitchClass() {
        return midiPitch % 12;
    }

    public Integer getVelocity() {
        return velocity;
    }
}
