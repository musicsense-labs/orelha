package dev.musicsense.orelha.analysis;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Trecho da letra transcrito por ASR do stem de voz (extrator ≥ 0.6.0). {@code noSpeechProb} é a
 * probabilidade que o modelo dá a "isto não é fala": alta num solo de guitarra que vazou para o stem.
 */
@Entity
@Table(name = "lyric_segment")
public class LyricSegment {

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

    @Column(nullable = false)
    private String text;

    @Column(name = "no_speech_prob")
    private Float noSpeechProb;

    @OneToMany(mappedBy = "segment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startS")
    private List<LyricWord> words = new ArrayList<>();

    protected LyricSegment() {
    }

    public LyricSegment(AnalysisRun run, BigDecimal startS, BigDecimal endS, String text, Float noSpeechProb) {
        this.run = run;
        this.startS = startS;
        this.endS = endS;
        this.text = text;
        this.noSpeechProb = noSpeechProb;
    }

    public LyricWord addWord(BigDecimal startS, BigDecimal endS, String text, Float probability) {
        LyricWord word = new LyricWord(this, startS, endS, text, probability);
        words.add(word);
        return word;
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

    public String getText() {
        return text;
    }

    public Float getNoSpeechProb() {
        return noSpeechProb;
    }

    public List<LyricWord> getWords() {
        return words;
    }
}
