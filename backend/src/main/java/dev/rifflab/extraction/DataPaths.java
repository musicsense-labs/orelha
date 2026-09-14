package dev.rifflab.extraction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Traduz caminhos que o extrator devolve (dentro do container, ex.: /data/stems/<sha>/bass.wav)
 * para o host, onde o compose faz o bind mount (./data). O backend nunca escreve nesses diretórios.
 */
@Component
public class DataPaths {

    private final String containerRoot;
    private final Path hostRoot;

    public DataPaths(@Value("${rifflab.data.container-root:/data}") String containerRoot,
                     @Value("${rifflab.data.host-root:../data}") String hostRoot) {
        this.containerRoot = containerRoot.endsWith("/") ? containerRoot.substring(0, containerRoot.length() - 1) : containerRoot;
        this.hostRoot = Path.of(hostRoot).toAbsolutePath().normalize();
    }

    /** Caminho no host, ou null se o caminho não está sob a raiz do container. */
    public Path toHost(String containerPath) {
        if (containerPath == null || !containerPath.startsWith(containerRoot + "/")) {
            return null;
        }
        Path relative = Path.of(containerPath.substring(containerRoot.length() + 1));
        Path resolved = hostRoot.resolve(relative).normalize();
        return resolved.startsWith(hostRoot) ? resolved : null;
    }
}
