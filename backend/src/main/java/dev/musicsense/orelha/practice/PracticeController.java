package dev.musicsense.orelha.practice;

import dev.musicsense.orelha.analysis.AnalysisRun;
import dev.musicsense.orelha.analysis.BassNote;
import dev.musicsense.orelha.analysis.BassNoteRepository;
import dev.musicsense.orelha.analysis.Beat;
import dev.musicsense.orelha.analysis.BeatRepository;
import dev.musicsense.orelha.analysis.TrackAnalysisRepository;
import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.common.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Módulo Practice: a linha de baixo do run canônico como tablatura (corda e casa por nota, {@link TabArranger})
 * e como MIDI quantizado na grade de beats ({@link BassMidiExporter}), para o MuseScore/TuxGuitar gerarem a
 * tab com ritmo. Os dois leem o mesmo {@code bass_note}; a tab é tão boa quanto a transcrição.
 */
@RestController
@RequestMapping("/api/tracks/{id}")
public class PracticeController {

    public record TabNote(BigDecimal startS, BigDecimal endS, int midi, int string, int fret, boolean octaveShifted) {
    }

    /** {@code tuning} = MIDI da corda solta, grave → aguda; {@code strings} = nomes para a UI. */
    public record TabResponse(int[] tuning, List<String> strings, boolean preferOpen, List<TabNote> notes) {
    }

    private final TrackRepository tracks;
    private final BassNoteRepository bassNotes;
    private final BeatRepository beats;
    private final TrackAnalysisRepository analyses;

    PracticeController(TrackRepository tracks, BassNoteRepository bassNotes, BeatRepository beats,
                       TrackAnalysisRepository analyses) {
        this.tracks = tracks;
        this.bassNotes = bassNotes;
        this.beats = beats;
        this.analyses = analyses;
    }

    /** Tablatura de 4 cordas (E A D G); {@code open=false} evita cordas soltas. Vazio sem run canônico. */
    @GetMapping("/bass-tab")
    @Transactional(readOnly = true)
    TabResponse bassTab(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean open) {
        TabArranger arranger = TabArranger.standard4(open);
        List<BassNote> notes = notesOf(id);
        List<TabArranger.Position> positions = arranger.arrange(notes.stream()
                .map(n -> new TabArranger.Note(n.getStartS().doubleValue(), n.getMidiPitch())).toList());
        List<TabNote> out = new java.util.ArrayList<>(notes.size());
        for (int i = 0; i < notes.size(); i++) {
            BassNote n = notes.get(i);
            TabArranger.Position p = positions.get(i);
            out.add(new TabNote(n.getStartS(), n.getEndS(), n.getMidiPitch(), p.string(), p.fret(), p.octaveShifted()));
        }
        return new TabResponse(arranger.tuning(), List.of("E", "A", "D", "G"), open, out);
    }

    /** A linha de baixo em MIDI, quantizada na grade de beats do run (abrir no MuseScore com pauta de tab). */
    @GetMapping(value = "/bass.mid", produces = "audio/midi")
    @Transactional(readOnly = true)
    ResponseEntity<byte[]> bassMidi(@PathVariable Long id) {
        Track track = find(id);
        AnalysisRun run = track.getCanonicalRun();
        List<Double> grid = run == null ? List.of()
                : beats.findByRunIdOrderByBeatNo(run.getId()).stream().map(Beat::getTimeS).map(BigDecimal::doubleValue).toList();
        int beatsPerBar = run == null ? 4 : analyses.findByRunId(run.getId())
                .map(a -> beatsPerBar(a.getTimeSignature())).orElse(4);
        List<BassMidiExporter.Note> notes = notesOf(id).stream()
                .map(n -> new BassMidiExporter.Note(n.getStartS().doubleValue(), n.getEndS().doubleValue(), n.getMidiPitch(),
                        n.getVelocity() == null ? 90 : n.getVelocity()))
                .toList();
        String name = track.getTitle() + " — baixo";
        byte[] bytes = BassMidiExporter.export(name, notes, grid, beatsPerBar);
        String fileName = URLEncoder.encode(name + ".mid", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/midi"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(bytes);
    }

    private List<BassNote> notesOf(Long id) {
        AnalysisRun run = find(id).getCanonicalRun();
        return run == null ? List.of() : bassNotes.findByRunIdOrderByStartS(run.getId());
    }

    /** "4/4" → 4, "6/8" → 6; qualquer outra coisa → 4. */
    static int beatsPerBar(String timeSignature) {
        if (timeSignature == null) {
            return 4;
        }
        int slash = timeSignature.indexOf('/');
        try {
            return slash > 0 ? Integer.parseInt(timeSignature.substring(0, slash).trim()) : 4;
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    private Track find(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("Track", id));
    }
}
