package dev.rifflab.analysis;

import dev.rifflab.catalog.Track;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Uma execução de extração sobre uma faixa, com proveniência completa.
 * Uma faixa pode ter vários runs (extratores/modelos diferentes); nunca se sobrescreve.
 */
@Entity
@Table(name = "analysis_run")
public class AnalysisRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    private Track track;

    @Column(name = "extractor_name", nullable = false)
    private String extractorName;

    @Column(name = "extractor_version")
    private String extractorVersion;

    /** Modelo usado por capacidade, ex.: {"chords": "btc-pl", "beats": "beat-transformer"}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_names")
    private Map<String, String> modelNames;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status = RunStatus.QUEUED;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "locked_at")
    private Instant lockedAt;

    private String error;

    /** Caminho do Parquet com as séries por frame; nunca vão para o banco. */
    @Column(name = "features_path")
    private String featuresPath;

    /** Stems persistidos pelo extrator, por nome: caminhos do container (traduzidos por DataPaths). */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> stems;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AnalysisRun() {
    }

    public AnalysisRun(Track track, String extractorName, String extractorVersion, Map<String, String> modelNames) {
        this.track = track;
        this.extractorName = extractorName;
        this.extractorVersion = extractorVersion;
        this.modelNames = modelNames;
    }

    public Long getId() {
        return id;
    }

    public Track getTrack() {
        return track;
    }

    public String getExtractorName() {
        return extractorName;
    }

    public String getExtractorVersion() {
        return extractorVersion;
    }

    public void setExtractorVersion(String extractorVersion) {
        this.extractorVersion = extractorVersion;
    }

    public Map<String, String> getModelNames() {
        return modelNames;
    }

    /** Preenchido na conclusão: só então se sabe qual modelo cada capacidade usou. */
    public void setModelNames(Map<String, String> modelNames) {
        this.modelNames = modelNames;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getFeaturesPath() {
        return featuresPath;
    }

    public void setFeaturesPath(String featuresPath) {
        this.featuresPath = featuresPath;
    }

    public Map<String, String> getStems() {
        return stems;
    }

    public void setStems(Map<String, String> stems) {
        this.stems = stems;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
