package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.common.AdminProperties;
import dev.musicsense.orelha.lyrics.LrcImporter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manutenção do disco pelo administrador. A varredura de órfãos roda sozinha de tempos em tempos; aqui ela
 * pode ser olhada ({@code dryRun}, o padrão) ou disparada na hora — útil depois de um run abandonado, que é
 * quando a sobra aparece.
 */
@RestController
@EnableConfigurationProperties(AdminProperties.class)
@RequestMapping("/api/admin")
public class MaintenanceController {

    private final OrphanSweeper sweeper;
    private final AdminProperties admin;
    private final LrcImporter lrc;

    MaintenanceController(OrphanSweeper sweeper, AdminProperties admin, LrcImporter lrc) {
        this.sweeper = sweeper;
        this.admin = admin;
        this.lrc = lrc;
    }

    /**
     * Procura o .lrc ao lado do áudio de cada faixa e grava os versos. Faixas novas já fazem isso ao entrar;
     * isto é para o acervo que veio antes (ou para quando o arquivo aparece depois).
     */
    @PostMapping("/lrc-scan")
    LrcImporter.Report lrcScan(HttpServletRequest request, @RequestParam(defaultValue = "true") boolean onlyMissing) {
        if (!admin.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só o administrador varre o acervo.");
        }
        return lrc.scan(onlyMissing);
    }

    @PostMapping("/orphans")
    OrphanSweeper.Report orphans(HttpServletRequest request, @RequestParam(defaultValue = "true") boolean dryRun) {
        if (!admin.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só o administrador faz a faxina do disco.");
        }
        return sweeper.sweep(dryRun);
    }
}
