package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.harmony.KeyMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/** Análise humana de uma faixa (TheoryTab transcrito pelo dono): tonalidade e seções com numerais. */
@Entity
@Table(name = "reference_analysis")
public class ReferenceAnalysis {

    /** Uma seção como o dono a colou: rótulo ("Verse") e progressão em numerais ("I V vi IV"). */
    public record Section(String label, String progression) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false, unique = true)
    private Track track;

    @Column(nullable = false)
    private String source = "THEORYTAB";

    private String url;

    @Column(name = "tonic_pc")
    private Integer tonicPc;

    @Enumerated(EnumType.STRING)
    private KeyMode mode;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Section> sections;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ReferenceAnalysis() {
    }

    public ReferenceAnalysis(Track track) {
        this.track = track;
    }

    public void update(String url, Integer tonicPc, KeyMode mode, String rawText, List<Section> sections) {
        this.url = url;
        this.tonicPc = tonicPc;
        this.mode = mode;
        this.rawText = rawText;
        this.sections = sections;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Track getTrack() {
        return track;
    }

    public String getSource() {
        return source;
    }

    public String getUrl() {
        return url;
    }

    public Integer getTonicPc() {
        return tonicPc;
    }

    public KeyMode getMode() {
        return mode;
    }

    public String getRawText() {
        return rawText;
    }

    public List<Section> getSections() {
        return sections;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
