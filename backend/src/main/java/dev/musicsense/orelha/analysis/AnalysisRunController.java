package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.common.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis/runs")
public class AnalysisRunController {

    private final AnalysisRunRepository runs;

    AnalysisRunController(AnalysisRunRepository runs) {
        this.runs = runs;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    AnalysisRunResponse get(@PathVariable Long id) {
        return AnalysisRunResponse.of(runs.findById(id).orElseThrow(() -> new NotFoundException("AnalysisRun", id)));
    }
}
