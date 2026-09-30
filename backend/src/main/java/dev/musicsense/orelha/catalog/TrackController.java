package dev.musicsense.orelha.catalog;

import dev.musicsense.orelha.analysis.AnalysisRun;
import dev.musicsense.orelha.analysis.AnalysisRunRepository;
import dev.musicsense.orelha.common.AdminProperties;
import dev.musicsense.orelha.common.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A faixa como item do acervo: listar, enviar, reprocessar, escolher o run canônico, excluir. Importar pasta
 * fica em {@link ImportController}; o que o player toca, em {@link MediaController}; notas e beats, no
 * {@code NotesController} da análise.
 */
@RestController
@RequestMapping("/api/tracks")
public class TrackController {

    public record CanonicalRunRequest(@NotNull Long runId) {
    }

    private final TrackRepository tracks;
    private final TrackService service;
    private final AnalysisRunRepository runs;
    private final AudioLibrary library;
    private final AdminProperties admin;

    TrackController(TrackRepository tracks, TrackService service, AnalysisRunRepository runs, AudioLibrary library,
                    AdminProperties admin) {
        this.tracks = tracks;
        this.service = service;
        this.runs = runs;
        this.library = library;
        this.admin = admin;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<TrackResponse> list(@RequestParam(required = false) Long albumId) {
        List<Track> result = albumId == null ? tracks.findAll() : tracks.findByAlbumIdOrderByTrackNoAsc(albumId);
        Map<Long, AnalysisRun> latest = runs.findLatestPerTrack().stream()
                .collect(Collectors.toMap(r -> r.getTrack().getId(), r -> r));
        return result.stream().map(t -> TrackResponse.of(t, latest.get(t.getId()), library)).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    TrackResponse get(@PathVariable Long id) {
        return response(find(id));
    }

    /** Enfileira um novo run para a faixa; o anterior fica, e o canônico só muda por escolha do dono. */
    @PostMapping("/{id}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, Long> analyze(@PathVariable Long id) {
        return Map.of("runId", service.enqueueAnalysis(id).getId());
    }

    /** Upload pela UI: multipart com file, albumId, title (opcional: nome do arquivo) e trackNo. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    TrackResponse upload(@RequestPart("file") MultipartFile file, @RequestParam Long albumId,
                         @RequestParam(required = false) String title, @RequestParam(required = false) Integer trackNo) {
        return response(service.upload(albumId, title, trackNo, file));
    }

    /** Troca o run que responde pela faixa (comparar extratores/modelos sem sobrescrever nada). */
    @PutMapping("/{id}/canonical-run")
    @Transactional
    TrackResponse setCanonicalRun(@PathVariable Long id, @Valid @RequestBody CanonicalRunRequest req) {
        return response(service.setCanonicalRun(id, req.runId()));
    }

    /** Só o administrador tira faixas do acervo; vão junto o áudio da biblioteca e os stems. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, HttpServletRequest request) {
        admin.require(request, "exclui faixas do acervo");
        TrackRemoval.delete(service.remove(id));
    }

    private TrackResponse response(Track track) {
        return TrackResponse.of(track, runs.findFirstByTrackIdOrderByIdDesc(track.getId()).orElse(null), library);
    }

    private Track find(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("Track", id));
    }
}
