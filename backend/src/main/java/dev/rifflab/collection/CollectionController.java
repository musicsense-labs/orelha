package dev.rifflab.collection;

import dev.rifflab.collection.CollectionMetrics.PedalPassage;
import dev.rifflab.collection.CollectionService.Comparison;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collection")
public class CollectionController {

    private final CollectionService collection;

    CollectionController(CollectionService collection) {
        this.collection = collection;
    }

    @GetMapping("/artists/{id}/profile")
    HarmonicProfile artistProfile(@PathVariable Long id) {
        return collection.artistProfile(id);
    }

    @GetMapping("/albums/{id}/profile")
    HarmonicProfile albumProfile(@PathVariable Long id) {
        return collection.albumProfile(id);
    }

    @GetMapping("/compare")
    Comparison compare(@RequestParam Long a, @RequestParam Long b) {
        return collection.compareArtists(a, b);
    }

    /** Passagens em que o baixo fica parado enquanto a harmonia se move pela relação dada. */
    @GetMapping("/artists/{id}/pedal-passages")
    List<PedalPassage> pedalPassages(@PathVariable Long id,
                                     @RequestParam(defaultValue = "CHROMATIC_MEDIANT") String relation) {
        return collection.artistPedalPassages(id, relation);
    }
}
