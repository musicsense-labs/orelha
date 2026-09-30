package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.common.NotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** As séries nota a nota e a grade de beats do run canônico: o que as lanes da timeline e o metrônomo leem. */
@RestController
@RequestMapping("/api/tracks/{id}")
public class NotesController {

    /** {@code kind} só nas notas de voz ({@link VocalNoteKind}); null no baixo. */
    public record NoteResponse(BigDecimal startS, BigDecimal endS, int midi, Integer velocity, VocalNoteKind kind) {
    }

    public record BeatResponse(BigDecimal timeS, int beatNo, Integer barNo, boolean downbeat) {
    }

    private final TrackRepository tracks;
    private final BassNoteRepository bassNotes;
    private final BeatRepository beats;
    private final LyricsService lyrics;

    NotesController(TrackRepository tracks, BassNoteRepository bassNotes, BeatRepository beats, LyricsService lyrics) {
        this.tracks = tracks;
        this.bassNotes = bassNotes;
        this.beats = beats;
        this.lyrics = lyrics;
    }

    /** Linha de baixo nota a nota (basic-pitch no stem de baixo). */
    @GetMapping("/bass-notes")
    @Transactional(readOnly = true)
    List<NoteResponse> bassNotes(@PathVariable Long id) {
        return fromCanonical(id, run -> bassNotes.findByRunIdOrderByStartS(run.getId()).stream()
                .map(n -> new NoteResponse(n.getStartS(), n.getEndS(), n.getMidiPitch(), n.getVelocity(), null))
                .toList());
    }

    /** Notas da voz classificadas pela letra: com texto, sem texto (vocalise) ou provável vazamento. */
    @GetMapping("/vocal-notes")
    @Transactional(readOnly = true)
    List<NoteResponse> vocalNotes(@PathVariable Long id) {
        return fromCanonical(id, run -> lyrics.classifiedVocalNotes(run).stream()
                .map(n -> new NoteResponse(n.startS(), n.endS(), n.midi(), n.velocity(), n.kind()))
                .toList());
    }

    /** Beats e downbeats: a grade do metrônomo e das linhas de compasso. */
    @GetMapping("/beats")
    @Transactional(readOnly = true)
    List<BeatResponse> beats(@PathVariable Long id) {
        return fromCanonical(id, run -> beats.findByRunIdOrderByBeatNo(run.getId()).stream()
                .map(b -> new BeatResponse(b.getTimeS(), b.getBeatNo(), b.getBarNo(), b.isDownbeat()))
                .toList());
    }

    /** Sem análise concluída, lista vazia: a tela mostra vazio, nunca inventa. */
    private <T> List<T> fromCanonical(Long id, Function<AnalysisRun, List<T>> read) {
        AnalysisRun run = tracks.findById(id).orElseThrow(() -> new NotFoundException("Track", id)).getCanonicalRun();
        return run == null ? List.of() : read.apply(run);
    }
}
