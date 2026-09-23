package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.extraction.DataPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Varre {@code <host>/stems/<sha>/} e {@code <host>/features/<sha>.parquet} e apaga o que não pertence a
 * faixa nenhuma. Sobra desse tipo aparece quando o extrator grava antes do run ser persistido e o run acaba
 * abandonado ou falho — o {@code TrackRemoval} só sabe apagar o que está registrado no banco.
 *
 * <p>Três travas, porque isto apaga arquivo sozinho: (1) acervo vazio aborta a varredura — banco fora do ar
 * não pode virar faxina geral; (2) só entra o que não foi tocado há mais de {@code orelha.data.orphan-min-age},
 * para nunca disputar com uma análise em andamento; (3) nada fora de {@code stems}/{@code features} é olhado.
 * O identificador é o SHA do áudio, o mesmo que o extrator usa para nomear.
 */
@Component
public class OrphanSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrphanSweeper.class);

    /** O que a varredura achou (e apagou, quando não é ensaio). */
    public record Report(int stemFolders, int featureFiles, long bytes, boolean dryRun, String skippedReason) {

        public boolean skipped() {
            return skippedReason != null;
        }

        static Report skipped(String reason) {
            return new Report(0, 0, 0, false, reason);
        }
    }

    private final TrackRepository tracks;
    private final DataPaths dataPaths;
    private final boolean enabled;
    private final Duration minAge;

    OrphanSweeper(TrackRepository tracks, DataPaths dataPaths,
                  @Value("${orelha.data.orphan-sweep-enabled:true}") boolean enabled,
                  @Value("${orelha.data.orphan-min-age:1h}") Duration minAge) {
        this.tracks = tracks;
        this.dataPaths = dataPaths;
        this.enabled = enabled;
        this.minAge = minAge;
    }

    @Scheduled(initialDelayString = "${orelha.data.orphan-sweep-initial-delay:PT5M}",
            fixedDelayString = "${orelha.data.orphan-sweep-interval:PT6H}")
    void sweepScheduled() {
        if (!enabled) {
            return;
        }
        Report report = sweep(false);
        if (report.skipped()) {
            log.warn("varredura de órfãos pulada: {}", report.skippedReason());
        } else if (report.stemFolders() > 0 || report.featureFiles() > 0) {
            log.info("órfãos apagados: {} pastas de stems, {} parquets, {} MB",
                    report.stemFolders(), report.featureFiles(), report.bytes() / (1024 * 1024));
        }
    }

    /** {@code dryRun} conta sem apagar — é o que o administrador vê antes de mandar limpar. */
    @Transactional(readOnly = true)
    public Report sweep(boolean dryRun) {
        Set<String> alive = tracks.findAllAudioSha256();
        if (alive.isEmpty()) {
            return Report.skipped("o acervo está vazio; nada é apagado enquanto não houver faixa alguma");
        }
        Instant deadline = Instant.now().minus(minAge);
        List<Path> stems = orphans(dataPaths.hostRoot().resolve("stems"), alive, deadline, true);
        List<Path> features = orphans(dataPaths.hostRoot().resolve("features"), alive, deadline, false);
        long bytes = stems.stream().mapToLong(OrphanSweeper::sizeOf).sum()
                + features.stream().mapToLong(OrphanSweeper::sizeOf).sum();
        if (!dryRun) {
            stems.forEach(OrphanSweeper::delete);
            features.forEach(OrphanSweeper::delete);
        }
        return new Report(stems.size(), features.size(), bytes, dryRun, null);
    }

    /**
     * Entradas de um diretório cujo nome (sem {@code .parquet}) não é o SHA de nenhuma faixa e que estão
     * paradas há tempo suficiente. Diretório inexistente devolve lista vazia.
     */
    private static List<Path> orphans(Path dir, Set<String> alive, Instant deadline, boolean directories) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> out = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                if (Files.isDirectory(entry) != directories) {
                    continue;
                }
                String name = entry.getFileName().toString();
                if (!directories && !name.endsWith(".parquet")) {
                    continue;   // arquivo que não é nosso: não é a varredura que decide sobre ele
                }
                String sha = directories ? name : name.substring(0, name.length() - ".parquet".length());
                if (alive.contains(sha)) {
                    continue;
                }
                if (lastTouched(entry).isAfter(deadline)) {
                    continue;   // recente demais: pode ser uma análise em andamento
                }
                out.add(entry);
            }
        } catch (IOException e) {
            log.warn("não deu para listar {}: {}", dir, e.getMessage());
        }
        return out;
    }

    /** O instante mais recente entre a entrada e o que ela contém (a pasta não muda quando o arquivo muda). */
    private static Instant lastTouched(Path path) {
        try {
            if (!Files.isDirectory(path)) {
                return Files.getLastModifiedTime(path).toInstant();
            }
            try (Stream<Path> walk = Files.walk(path)) {
                return walk.map(p -> {
                    try {
                        return Files.getLastModifiedTime(p).toInstant();
                    } catch (IOException e) {
                        return Instant.EPOCH;
                    }
                }).max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
            }
        } catch (IOException e) {
            return Instant.now();   // na dúvida, trata como recente e não apaga
        }
    }

    private static long sizeOf(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void delete(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("não apagou o órfão {}: {}", path, e.getMessage());
        }
    }
}
