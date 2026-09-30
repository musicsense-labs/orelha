package dev.musicsense.orelha.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/**
 * Importar pasta em dois passos: pré-visualização editável e confirmação. Nada entra no catálogo antes da
 * confirmação. Só roda no PC do acervo: {@code RemoteImportGuard} barra tudo que começa por
 * {@code /api/tracks/import} quando a requisição vem pelo túnel.
 */
@RestController
@RequestMapping("/api/tracks")
public class ImportController {

    public record ImportPathRequest(@NotBlank String path, boolean recursive) {
    }

    private final ImportService importer;

    ImportController(ImportService importer) {
        this.importer = importer;
    }

    /** Upload de pasta: guarda em staging e devolve a pré-visualização. */
    @PostMapping(value = "/import/stage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportReport.Preview stage(@RequestPart("files") List<MultipartFile> files) {
        return importer.stageUploads(files);
    }

    /** Pasta do servidor: pré-visualização (os arquivos ficarão no lugar). */
    @PostMapping("/import-path/preview")
    ImportReport.Preview previewPath(@Valid @RequestBody ImportPathRequest req) {
        return importer.previewDirectory(Path.of(req.path()), req.recursive());
    }

    /** Cadastra os itens como a UI os editou (staging → biblioteca; servidor → no lugar). */
    @PostMapping("/import/confirm")
    ImportReport confirm(@Valid @RequestBody ImportReport.Confirmation confirmation) {
        return importer.confirm(confirmation);
    }

    /** Cancelou a pré-visualização de um upload: apaga o staging. */
    @DeleteMapping("/import/stage/{stagingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discardStaging(@PathVariable String stagingId) {
        importer.discardStaging(stagingId);
    }
}
