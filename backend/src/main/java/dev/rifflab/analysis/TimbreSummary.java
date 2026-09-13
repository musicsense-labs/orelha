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

/** Agregados tímbricos de um stem. As séries por frame ficam no Parquet do run. */
@Entity
@Table(name = "timbre_summary")
public class TimbreSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_run_id", nullable = false)
    private AnalysisRun run;

    /** Modelo de separação: 'htdemucs', 'htdemucs_6s'... Decide se "guitar" existe ou é "other". */
    @Column(name = "stem_model", nullable = false)
    private String stemModel;

    @Column(name = "stem_name", nullable = false)
    private String stemName;

    @Column(name = "centroid_mean")
    private Float centroidMean;

    @Column(name = "centroid_std")
    private Float centroidStd;

    @Column(name = "flatness_mean")
    private Float flatnessMean;

    @Column(name = "rolloff_p95")
    private Float rolloffP95;

    @Column(name = "rms_mean")
    private Float rmsMean;

    protected TimbreSummary() {
    }

    public TimbreSummary(AnalysisRun run, String stemModel, String stemName, Float centroidMean, Float centroidStd,
                         Float flatnessMean, Float rolloffP95, Float rmsMean) {
        this.run = run;
        this.stemModel = stemModel;
        this.stemName = stemName;
        this.centroidMean = centroidMean;
        this.centroidStd = centroidStd;
        this.flatnessMean = flatnessMean;
        this.rolloffP95 = rolloffP95;
        this.rmsMean = rmsMean;
    }

    public Long getId() {
        return id;
    }

    public AnalysisRun getRun() {
        return run;
    }

    public String getStemModel() {
        return stemModel;
    }

    public String getStemName() {
        return stemName;
    }

    public Float getCentroidMean() {
        return centroidMean;
    }

    public Float getCentroidStd() {
        return centroidStd;
    }

    public Float getFlatnessMean() {
        return flatnessMean;
    }

    public Float getRolloffP95() {
        return rolloffP95;
    }

    public Float getRmsMean() {
        return rmsMean;
    }
}
