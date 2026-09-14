package dev.rifflab.corpus;

import dev.rifflab.corpus.CorpusMetrics.PedalPassage;
import dev.rifflab.corpus.CorpusService.Comparison;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/corpus")
public class CorpusController {

    private final CorpusService corpus;

    CorpusController(CorpusService corpus) {
        this.corpus = corpus;
    }

    @GetMapping("/artists/{id}/profile")
    HarmonicProfile artistProfile(@PathVariable Long id) {
        return corpus.artistProfile(id);
    }

    @GetMapping("/albums/{id}/profile")
    HarmonicProfile albumProfile(@PathVariable Long id) {
        return corpus.albumProfile(id);
    }

    @GetMapping("/compare")
    Comparison compare(@RequestParam Long a, @RequestParam Long b) {
        return corpus.compareArtists(a, b);
    }

    /** Passagens em que o baixo fica parado enquanto a harmonia se move pela relação dada. */
    @GetMapping("/artists/{id}/pedal-passages")
    List<PedalPassage> pedalPassages(@PathVariable Long id,
                                     @RequestParam(defaultValue = "CHROMATIC_MEDIANT") String relation) {
        return corpus.artistPedalPassages(id, relation);
    }
}
