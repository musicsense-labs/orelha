package dev.rifflab.catalog;

import dev.rifflab.analysis.AnalysisRun;
import dev.rifflab.analysis.AnalysisRunRepository;
import dev.rifflab.analysis.BeatRepository;
import dev.rifflab.common.NotFoundException;
import dev.rifflab.extraction.DataPaths;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tracks")
public class TrackController {

    private final TrackRepository tracks;
    private final TrackService service;
    private final DataPaths dataPaths;
    private final AnalysisRunRepository runs;
    private final ImportService importer;
    private final BeatRepository beats;

    TrackController(TrackRepository tracks, TrackService service, DataPaths dataPaths, AnalysisRunRepository runs,
                    ImportService importer, BeatRepository beats) {
        this.tracks = tracks;
        this.service = service;
        this.dataPaths = dataPaths;
        this.runs = runs;
        this.importer = importer;
        this.beats = beats;
    }

    private TrackResponse response(Track track) {
        return TrackResponse.of(track, runs.findFirstByTrackIdOrderByIdDesc(track.getId()).orElse(null));
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<TrackResponse> list(@RequestParam(required = false) Long albumId) {
        List<Track> result = albumId == null ? tracks.findAll() : tracks.findByAlbumIdOrderByTrackNoAsc(albumId);
        Map<Long, AnalysisRun> latest = runs.findLatestPerTrack().stream()
                .collect(Collectors.toMap(r -> r.getTrack().getId(), r -> r));
        return result.stream().map(t -> TrackResponse.of(t, latest.get(t.getId()))).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    TrackResponse get(@PathVariable Long id) {
        return response(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    TrackResponse create(@Valid @RequestBody TrackRequest req) {
        return response(service.register(req));
    }

    /** Enfileira um novo run para a faixa; devolve o id do run para polling em /api/analysis/runs/{id}. */
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

    // --- importação de pasta: preview editável + confirmação -----------------------------------

    /** Upload de pasta: guarda em staging e devolve a pré-visualização (nada entra no catálogo ainda). */
    @PostMapping(value = "/import/stage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportReport.Preview stage(@RequestPart("files") List<MultipartFile> files) {
        return importer.stageUploads(files);
    }

    public record ImportPathRequest(@jakarta.validation.constraints.NotBlank String path, boolean recursive) {
    }

    /** Pasta do servidor: pré-visualização (os arquivos ficarão no lugar). */
    @PostMapping("/import-path/preview")
    ImportReport.Preview previewPath(@Valid @RequestBody ImportPathRequest req) {
        return importer.previewDirectory(Path.of(req.path()), req.recursive());
    }

    /** Cadastra os itens como a UI os editou (staging → biblioteca; servidor → no lugar). */
    @PostMapping("/import/confirm")
    ImportReport confirmImport(@Valid @RequestBody ImportReport.Confirmation confirmation) {
        return importer.confirm(confirmation);
    }

    /** Cancelou a pré-visualização de um upload: apaga o staging. */
    @DeleteMapping("/import/stage/{stagingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discardStaging(@PathVariable String stagingId) {
        importer.discardStaging(stagingId);
    }

    /** Atalho sem edição: upload de pasta importado inteiro. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportReport importUploads(@RequestPart("files") List<MultipartFile> files) {
        return importer.importUploads(files);
    }

    /** Atalho sem edição: pasta do servidor importada inteira. */
    @PostMapping("/import-path")
    ImportReport importPath(@Valid @RequestBody ImportPathRequest req) {
        return importer.importDirectory(Path.of(req.path()), req.recursive());
    }

    /** O arquivo de áudio da faixa, para o player da UI. Spring MVC responde a Range (206) para seek. */
    @GetMapping("/{id}/audio")
    @Transactional(readOnly = true)
    ResponseEntity<Resource> audio(@PathVariable Long id) {
        Track track = find(id);
        Path path = Path.of(track.getAudioPath());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Audio of track", id);
        }
        return audioResponse(path);
    }

    public record BeatResponse(BigDecimal timeS, int beatNo, Integer barNo, boolean downbeat) {
    }

    /** Beats e downbeats do run canônico: a grade do metrônomo e das barras da timeline. */
    @GetMapping("/{id}/beats")
    @Transactional(readOnly = true)
    List<BeatResponse> beats(@PathVariable Long id) {
        AnalysisRun run = find(id).getCanonicalRun();
        if (run == null) {
            return List.of();
        }
        return beats.findByRunIdOrderByBeatNo(run.getId()).stream()
                .map(b -> new BeatResponse(b.getTimeS(), b.getBeatNo(), b.getBarNo(), b.isDownbeat()))
                .toList();
    }

    /** Nomes dos stems disponíveis no run canônico (vazio para runs anteriores ao extrator 0.3.0). */
    @GetMapping("/{id}/stems")
    @Transactional(readOnly = true)
    List<String> stems(@PathVariable Long id) {
        Map<String, String> stems = stemsOf(find(id));
        return stems.keySet().stream().sorted().toList();
    }

    /** Um stem (wav) do run canônico, com Range para o player multi-stem. */
    @GetMapping("/{id}/stems/{name}")
    @Transactional(readOnly = true)
    ResponseEntity<Resource> stem(@PathVariable Long id, @PathVariable String name) {
        String containerPath = stemsOf(find(id)).get(name);
        Path path = containerPath == null ? null : dataPaths.toHost(containerPath);
        if (path == null || !Files.isRegularFile(path)) {
            throw new NotFoundException("Stem " + name + " of track", id);
        }
        return audioResponse(path);
    }

    private static Map<String, String> stemsOf(Track track) {
        AnalysisRun run = track.getCanonicalRun();
        return run == null || run.getStems() == null ? Map.of() : run.getStems();
    }

    private static ResponseEntity<Resource> audioResponse(Path path) {
        return ResponseEntity.ok()
                .contentType(audioType(path))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(new FileSystemResource(path));
    }

    private static MediaType audioType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (name.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        if (name.endsWith(".flac")) {
            return MediaType.parseMediaType("audio/flac");
        }
        if (name.endsWith(".ogg") || name.endsWith(".opus")) {
            return MediaType.parseMediaType("audio/ogg");
        }
        if (name.endsWith(".m4a") || name.endsWith(".aac")) {
            return MediaType.parseMediaType("audio/mp4");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public record CanonicalRunRequest(@jakarta.validation.constraints.NotNull Long runId) {
    }

    /** Troca o run que responde pela faixa (comparar extratores/modelos sem sobrescrever nada). */
    @PutMapping("/{id}/canonical-run")
    @Transactional
    TrackResponse setCanonicalRun(@PathVariable Long id, @Valid @RequestBody CanonicalRunRequest req) {
        return response(service.setCanonicalRun(id, req.runId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        tracks.delete(find(id));
    }

    private Track find(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("Track", id));
    }
}
