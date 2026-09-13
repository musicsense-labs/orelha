package dev.rifflab.analysis;

/** Ciclo de vida de um run; a própria tabela analysis_run é a fila. */
public enum RunStatus {
    QUEUED, RUNNING, DONE, FAILED
}
