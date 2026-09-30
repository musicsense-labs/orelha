package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.common.AdminProperties;
import dev.musicsense.orelha.lyrics.LrcImporter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manutenção pelo administrador: o consumo da fila e o disco. A varredura de órfãos roda sozinha de tempos em tempos; aqui ela
 * pode ser olhada ({@code dryRun}, o padrão) ou disparada na hora — útil depois de um run abandonado, que é
 * quando a sobra aparece.
 */
@RestController
@EnableConfigurationProperties(AdminProperties.class)
@RequestMapping("/api/admin")
public class MaintenanceController {

    /**
     * Se a fila está sendo consumida, o que falta nela, quantas faixas terminaram falhando e, quando o worker
     * se pausou sozinho, por quê.
     */
    public record WorkerState(boolean enabled, long queued, long running, long failed, String pausedBecause) {
    }

    private final OrphanSweeper sweeper;
    private final AdminProperties admin;
    private final LrcImporter lrc;
    private final AnalysisWorker worker;
    private final AnalysisRunRepository runs;
    private final AnalysisQueue queue;

    MaintenanceController(OrphanSweeper sweeper, AdminProperties admin, LrcImporter lrc,
                          AnalysisWorker worker, AnalysisRunRepository runs, AnalysisQueue queue) {
        this.sweeper = sweeper;
        this.admin = admin;
        this.lrc = lrc;
        this.worker = worker;
        this.runs = runs;
        this.queue = queue;
    }

    @GetMapping("/worker")
    WorkerState worker(HttpServletRequest request) {
        admin.require(request, "mexe na fila de análise");
        return state();
    }

    /**
     * Pausa ou retoma o consumo da fila sem derrubar o Orelha — a extração ocupa a máquina por horas e nem
     * sempre é hora disso. Pausar não aborta a faixa que já está no extrator: ela termina e é gravada.
     * Vale enquanto o processo viver; reiniciar o backend volta ao padrão do ambiente.
     */
    @PostMapping("/worker")
    WorkerState worker(HttpServletRequest request, @RequestParam boolean enabled) {
        admin.require(request, "mexe na fila de análise");
        worker.enabled(enabled);
        return state();
    }

    /** Devolve à fila as faixas cujo último run falhou (queda do disco, extrator fora do ar). */
    @PostMapping("/worker/requeue-failed")
    WorkerState requeueFailed(HttpServletRequest request) {
        admin.require(request, "mexe na fila de análise");
        queue.requeueFailed();
        return state();
    }

    private WorkerState state() {
        return new WorkerState(worker.enabled(), runs.countByStatus(RunStatus.QUEUED), runs.countByStatus(RunStatus.RUNNING),
                runs.countLatestFailed(), worker.pausedBecause());
    }

    /**
     * Procura o .lrc ao lado do áudio de cada faixa e grava os versos. Faixas novas já fazem isso ao entrar;
     * isto é para o acervo que veio antes (ou para quando o arquivo aparece depois).
     */
    @PostMapping("/lrc-scan")
    LrcImporter.Report lrcScan(HttpServletRequest request, @RequestParam(defaultValue = "true") boolean onlyMissing) {
        admin.require(request, "varre o acervo");
        return lrc.scan(onlyMissing);
    }

    @PostMapping("/orphans")
    OrphanSweeper.Report orphans(HttpServletRequest request, @RequestParam(defaultValue = "true") boolean dryRun) {
        admin.require(request, "faz a faxina do disco");
        return sweeper.sweep(dryRun);
    }
}
