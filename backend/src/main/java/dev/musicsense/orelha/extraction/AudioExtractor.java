package dev.musicsense.orelha.extraction;

import java.nio.file.Path;

/**
 * Fronteira com a camada de extração (Python). Um adapter por serviço; o domínio só vê
 * {@link ExtractionResult}, nunca o JSON de um extrator específico.
 */
public interface AudioExtractor {

    /** Nome gravado em analysis_run.extractor_name. */
    String name();

    /** Síncrono e demorado (minutos por faixa): chamado pelo worker, nunca por um request HTTP. */
    ExtractionResult analyze(Path audio, String audioSha256);
}
