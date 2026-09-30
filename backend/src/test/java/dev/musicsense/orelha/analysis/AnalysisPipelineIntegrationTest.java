package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.AlbumRequest;
import dev.musicsense.orelha.catalog.AlbumResponse;
import dev.musicsense.orelha.catalog.ArtistRequest;
import dev.musicsense.orelha.catalog.ArtistResponse;
import dev.musicsense.orelha.catalog.Uploads;
import dev.musicsense.orelha.catalog.TrackResponse;
import dev.musicsense.orelha.extraction.AudioExtractor;
import dev.musicsense.orelha.extraction.ExtractionResult;
import dev.musicsense.orelha.extraction.ExtractionResult.AudioInfo;
import dev.musicsense.orelha.extraction.ExtractionResult.NoteEvent;
import dev.musicsense.orelha.extraction.ExtractionResult.BeatEvent;
import dev.musicsense.orelha.extraction.ExtractionResult.ChordEvent;
import dev.musicsense.orelha.extraction.ExtractionResult.KeyEstimate;
import dev.musicsense.orelha.extraction.ExtractionResult.Provenance;
import dev.musicsense.orelha.extraction.ExtractionResult.Tempo;
import dev.musicsense.orelha.extraction.ExtractionResult.TimbreStat;
import dev.musicsense.orelha.harmony.Chord;
import dev.musicsense.orelha.harmony.ChordQuality;
import dev.musicsense.orelha.harmony.KeyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cadastro → fila → worker → chord_segment com grau → timeline. O extrator é um stub que devolve um
 * resultado conhecido (Am F G E7 em Lá menor, com o E7 tocado como power chord segundo o chroma).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "orelha.worker.enabled=false")
@Testcontainers
class AnalysisPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Simula o bind mount do compose: /data/... do extrator vira este diretório no host. */
    static Path dataRoot;

    @org.springframework.test.context.DynamicPropertySource
    static void dataRoot(org.springframework.test.context.DynamicPropertyRegistry registry) throws IOException {
        dataRoot = Files.createTempDirectory("orelha-data");
        registry.add("orelha.data.host-root", () -> dataRoot.toString());
        registry.add("orelha.library.dir", () -> dataRoot.resolve("library").toString());
    }

    /** O stub declara sempre /data/stems/stub/; recriado a cada teste porque excluir uma faixa apaga a pasta. */
    @org.junit.jupiter.api.BeforeEach
    void stubStems() throws IOException {
        Files.createDirectories(dataRoot.resolve("stems/stub"));
        Files.writeString(dataRoot.resolve("stems/stub/bass.wav"), "bass stem");
    }

    @TestConfiguration
    static class StubExtractor {
        @Bean
        @Primary
        AudioExtractor stub() {
            return new AudioExtractor() {
                @Override
                public String name() {
                    return "stub";
                }

                @Override
                public ExtractionResult analyze(Path audio, String audioSha256) {
                    return FIXTURE;
                }
            };
        }
    }

    static final ExtractionResult FIXTURE = new ExtractionResult(
            new Provenance("stub", "0", Map.of("chords", "hand-written")),
            new AudioInfo(new BigDecimal("8.000"), 44100, new BigDecimal("-14.00")),
            new KeyEstimate(9, KeyMode.MINOR, 0.9f),
            new Tempo(new BigDecimal("120.00"), "4/4"),
            List.of(new BeatEvent(bd(0.0), 1), new BeatEvent(bd(0.5), 2), new BeatEvent(bd(1.0), 3),
                    new BeatEvent(bd(1.5), 4), new BeatEvent(bd(2.0), 1)),
            List.of(
                    chord(0.0, 1.0, 9, ChordQuality.MIN),      // Am
                    chord(1.0, 2.0, 9, ChordQuality.MIN),      // Am de novo: funde
                    chord(2.0, 4.0, 5, ChordQuality.MAJ),      // F
                    chord(4.0, 6.0, 7, ChordQuality.MAJ),      // G
                    chord(6.0, 8.0, 4, ChordQuality.POWER)),   // E5 pronto, como viria de um extrator com classe "5"
            List.of(new NoteEvent(bd(0.0), bd(2.0), 45, 100),        // A sob Am
                    new NoteEvent(bd(2.0), bd(4.0), 41, 100),        // F sob F
                    new NoteEvent(bd(4.0), bd(6.0), 43, 100),        // G sob G
                    new NoteEvent(bd(6.0), bd(8.0), 45, 100)),       // A sob E5: pedal
            List.of(new NoteEvent(bd(0.5), bd(1.5), 64, 90),         // voz: E4 sobre Am, sob a palavra "let"
                    new NoteEvent(bd(2.5), bd(3.5), 65, 90),         // F4 sobre F, dentro do trecho mas sem palavra
                    new NoteEvent(bd(6.0), bd(7.0), 67, 90)),        // G4 sobre E5, fora de qualquer trecho: vazamento
            new ExtractionResult.Lyrics("en", 0.9f, List.of(new ExtractionResult.LyricSegmentEvent(bd(0.4), bd(4.0),
                    "let it", 0.1f, List.of(new ExtractionResult.LyricWordEvent(bd(0.4), bd(1.4), "let", 0.9f),
                            new ExtractionResult.LyricWordEvent(bd(1.6), bd(2.2), "it", 0.8f))))),
            List.of(new TimbreStat("htdemucs", "bass", 400f, 50f, 0.02f, 1800f, 0.1f)),
            Map.of("bass", "/data/stems/stub/bass.wav", "drums", "/data/stems/stub/drums.wav"));

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(3);
    }

    private static ChordEvent chord(double start, double end, int root, ChordQuality quality) {
        float[] chroma = new float[12];
        java.util.Arrays.fill(chroma, 0.5f);
        return new ChordEvent(bd(start), bd(end), Chord.of(root, quality), chroma, null);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AnalysisWorker worker;

    @TempDir
    Path tempDir;

    @Test
    void registeredTrackIsAnalysedIntoAnAnnotatedTimeline() throws IOException {
        Path audio = tempDir.resolve("stub.wav");
        Files.writeString(audio, "stub");
        Long artistId = rest.postForEntity("/api/artists", new ArtistRequest("Stub", null, null), ArtistResponse.class)
                .getBody().id();
        Long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, "Stub", 2026), AlbumResponse.class)
                .getBody().id();
        TrackResponse track = Uploads.upload(rest, albumId, "Stub", 1, audio).getBody();

        // O cadastro enfileirou; antes do worker a timeline é vazia (nada de mock na UI).
        TimelineResponse empty = rest.getForObject("/api/tracks/" + track.id() + "/timeline", TimelineResponse.class);
        assertThat(empty.runId()).isNull();
        assertThat(empty.segments()).isEmpty();

        assertThat(worker.pollOnce()).isPresent();
        assertThat(worker.pollOnce()).isEmpty();

        TrackResponse analysed = rest.getForObject("/api/tracks/" + track.id(), TrackResponse.class);
        assertThat(analysed.canonicalRunId()).isNotNull();
        assertThat(analysed.durationS()).isEqualByComparingTo("8.000");

        ResponseEntity<AnalysisRunResponse> run = rest.getForEntity("/api/analysis/runs/" + analysed.canonicalRunId(),
                AnalysisRunResponse.class);
        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(run.getBody().status()).isEqualTo(RunStatus.DONE);
        assertThat(run.getBody().extractorName()).isEqualTo("stub");
        assertThat(run.getBody().modelNames()).containsEntry("chords", "hand-written");
        assertThat(run.getBody().attempts()).isEqualTo(1);

        TimelineResponse timeline = rest.getForObject("/api/tracks/" + track.id() + "/timeline", TimelineResponse.class);
        assertThat(timeline.key().tonicPc()).isEqualTo(9);
        assertThat(timeline.key().mode()).isEqualTo(KeyMode.MINOR);
        assertThat(timeline.bpm()).isEqualByComparingTo("120.00");
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::degreeLabel)
                .containsExactly("i", "♭VI", "♭VII", "V5");
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::keyRelation)
                .containsExactly("DIATONIC", "DIATONIC", "DIATONIC", "AMBIGUOUS");
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::quality)
                .containsExactly(ChordQuality.MIN, ChordQuality.MAJ, ChordQuality.MAJ, ChordQuality.POWER);
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::relationFromPrev)
                .containsExactly(null, "LEITTONWECHSEL", "WHOLE_TONE", "MEDIANT");
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::effectiveBassPc)
                .containsExactly(9, 5, 7, 9);
        assertThat(timeline.segments().get(0).endS()).isEqualByComparingTo("2.000");   // Am fundido
        assertThat(timeline.key().source()).isEqualTo(KeySource.EXTRACTOR);

        // Linha de baixo nota a nota do run canônico.
        ResponseEntity<List<dev.musicsense.orelha.analysis.NotesController.NoteResponse>> bassLine = rest.exchange(
                "/api/tracks/" + track.id() + "/bass-notes", org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(bassLine.getBody()).extracting(dev.musicsense.orelha.analysis.NotesController.NoteResponse::midi)
                .containsExactly(45, 41, 43, 45);

        // Tablatura (Practice): Lá2 Fá2 Sol2 Lá2 — Fá e Sol cabem na corda D (3ª e 5ª casas) ou na A;
        // a solução tem corda e casa coerentes com a afinação E A D G, e o MIDI sai quantizado nos beats.
        dev.musicsense.orelha.practice.PracticeController.TabResponse tab = rest.getForObject(
                "/api/tracks/" + track.id() + "/bass-tab", dev.musicsense.orelha.practice.PracticeController.TabResponse.class);
        assertThat(tab.tuning()).containsExactly(28, 33, 38, 43);
        assertThat(tab.notes()).hasSize(4);
        assertThat(tab.notes()).allSatisfy(n -> assertThat(tab.tuning()[n.string()] + n.fret()).isEqualTo(n.midi()));
        ResponseEntity<byte[]> midi = rest.getForEntity("/api/tracks/" + track.id() + "/bass.mid", byte[].class);
        assertThat(midi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(midi.getHeaders().getContentType().toString()).isEqualTo("audio/midi");
        assertThat(new String(midi.getBody(), 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("MThd");

        // Notas da voz do run canônico (extrator ≥ 0.5.0), classificadas pela letra (≥ 0.6.0).
        ResponseEntity<List<dev.musicsense.orelha.analysis.NotesController.NoteResponse>> vocals = rest.exchange(
                "/api/tracks/" + track.id() + "/vocal-notes", org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(vocals.getBody()).extracting(dev.musicsense.orelha.analysis.NotesController.NoteResponse::midi)
                .containsExactly(64, 65, 67);
        assertThat(vocals.getBody()).extracting(dev.musicsense.orelha.analysis.NotesController.NoteResponse::kind)
                .containsExactly(VocalNoteKind.LEXICAL, VocalNoteKind.NON_LEXICAL, VocalNoteKind.LIKELY_LEAK);

        // Letra do run canônico, com o compasso de cada palavra pela grade de beats.
        LyricsResponse lyrics = rest.getForObject("/api/tracks/" + track.id() + "/lyrics", LyricsResponse.class);
        assertThat(lyrics.source()).isEqualTo(LyricSource.EXTRACTOR);
        assertThat(lyrics.language()).isEqualTo("en");
        assertThat(lyrics.segments()).hasSize(1);
        assertThat(lyrics.segments().get(0).text()).isEqualTo("let it");
        assertThat(lyrics.segments().get(0).words()).extracting(LyricsResponse.Word::text).containsExactly("let", "it");
        // "let" começa em 0,4 s e a nota E4 em 0,5 s: dentro da folga de 120 ms, o ataque da nota é o instante da palavra.
        assertThat(lyrics.segments().get(0).words().get(0).noteStartS()).isEqualByComparingTo("0.500");
        assertThat(lyrics.segments().get(0).words().get(0).midi()).isEqualTo(64);
        assertThat(lyrics.segments().get(0).words().get(1).noteStartS()).isNull();   // "it" a 1,6 s: nenhuma nota perto
        assertThat(lyrics.segments().get(0).words().get(0).barNo()).isEqualTo(1);
        assertThat(lyrics.segments().get(0).words().get(1).barNo()).isEqualTo(1);   // 1,6 s: ainda no compasso 1 (2,0 s abre o 2)

        // Correção do dono: a lista inteira vira MANUAL, a classificação das notas segue a letra corrigida, e a
        // lista vazia volta à transcrição.
        ResponseEntity<LyricsResponse> corrected = rest.exchange("/api/tracks/" + track.id() + "/lyrics",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(List.of(new LyricsController.SegmentRequest(bd(0.4), bd(4.0), null,
                        List.of(new LyricsController.WordRequest(bd(0.4), bd(1.4), "let"),
                                new LyricsController.WordRequest(bd(2.4), bd(3.6), "be"))))),
                LyricsResponse.class);
        assertThat(corrected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(corrected.getBody().source()).isEqualTo(LyricSource.MANUAL);
        assertThat(corrected.getBody().segments().get(0).text()).isEqualTo("let be");
        assertThat(corrected.getBody().segments().get(0).words().get(1).probability()).isNull();
        vocals = rest.exchange("/api/tracks/" + track.id() + "/vocal-notes", org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(vocals.getBody()).extracting(dev.musicsense.orelha.analysis.NotesController.NoteResponse::kind)
                .containsExactly(VocalNoteKind.LEXICAL, VocalNoteKind.LEXICAL, VocalNoteKind.LIKELY_LEAK);   // F4 agora sob "be"
        ResponseEntity<LyricsResponse> revertedLyrics = rest.exchange("/api/tracks/" + track.id() + "/lyrics",
                org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(List.of()),
                LyricsResponse.class);
        assertThat(revertedLyrics.getBody().source()).isEqualTo(LyricSource.EXTRACTOR);
        assertThat(revertedLyrics.getBody().segments().get(0).text()).isEqualTo("let it");

        // Beats do run canônico, com compasso contado a partir do primeiro downbeat.
        ResponseEntity<List<dev.musicsense.orelha.analysis.NotesController.BeatResponse>> beats = rest.exchange(
                "/api/tracks/" + track.id() + "/beats", org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(beats.getBody()).hasSize(5);
        assertThat(beats.getBody()).extracting(dev.musicsense.orelha.analysis.NotesController.BeatResponse::barNo)
                .containsExactly(1, 1, 1, 1, 2);
        assertThat(beats.getBody()).extracting(dev.musicsense.orelha.analysis.NotesController.BeatResponse::downbeat)
                .containsExactly(true, false, false, false, true);

        // Stems do run canônico: só os que existem no host são servidos.
        ResponseEntity<List<String>> stems = rest.exchange("/api/tracks/" + track.id() + "/stems",
                org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(stems.getBody()).containsExactly("bass", "drums");
        ResponseEntity<byte[]> bass = rest.getForEntity("/api/tracks/" + track.id() + "/stems/bass", byte[].class);
        assertThat(bass.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bass.getHeaders().getContentType().toString()).isEqualTo("audio/wav");
        assertThat(new String(bass.getBody(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("bass stem");
        assertThat(rest.getForEntity("/api/tracks/" + track.id() + "/stems/drums", byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);   // declarado pelo extrator, mas o arquivo não está no host
        assertThat(rest.getForEntity("/api/tracks/" + track.id() + "/stems/vocals", byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Tonalidade atribuída pelo dono: re-anota sem re-extrair e passa a ser a leitura preferida.
        ResponseEntity<KeyController.KeyResponse> override = rest.exchange("/api/tracks/" + track.id() + "/key",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new KeyController.KeyRequest(0, KeyMode.MAJOR)),
                KeyController.KeyResponse.class);
        assertThat(override.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(override.getBody().source()).isEqualTo(KeySource.MANUAL);

        TimelineResponse relative = rest.getForObject("/api/tracks/" + track.id() + "/timeline", TimelineResponse.class);
        assertThat(relative.runId()).isEqualTo(timeline.runId());
        assertThat(relative.key().source()).isEqualTo(KeySource.MANUAL);
        assertThat(relative.key().tonicPc()).isZero();
        assertThat(relative.segments()).extracting(TimelineResponse.Segment::degreeLabel)
                .containsExactly("vi", "IV", "V", "III5");
        assertThat(relative.segments()).extracting(TimelineResponse.Segment::keyRelation)
                .containsExactly("DIATONIC", "DIATONIC", "DIATONIC", "AMBIGUOUS");
        assertThat(relative.segments()).extracting(TimelineResponse.Segment::effectiveBassPc)
                .containsExactly(9, 5, 7, 9);

        // Partes: dois compassos sem repetição viram uma parte "A" só; a progressão segue a tonalidade preferida.
        SectionsResponse derived = rest.getForObject("/api/tracks/" + track.id() + "/sections", SectionsResponse.class);
        assertThat(derived.source()).isEqualTo(SectionSource.DERIVED);
        assertThat(derived.parts()).hasSize(1);
        SectionsResponse.Part a = derived.parts().get(0);
        assertThat(a.label()).isEqualTo("A");
        assertThat(a.startS()).isEqualByComparingTo("0.000");
        assertThat(a.endS()).isEqualByComparingTo("8.000");
        assertThat(a.repeats()).isEqualTo(1);
        assertThat(a.chords()).extracting(SectionsResponse.Chord::degreeLabel).containsExactly("vi", "IV", "V", "III5");
        assertThat(a.chords()).extracting(SectionsResponse.Chord::keyRelation)
                .containsExactly("DIATONIC", "DIATONIC", "DIATONIC", "AMBIGUOUS");

        // O dono divide e nomeia; a edição vence a derivação e a progressão respeita o corte.
        ResponseEntity<SectionsResponse> edited = rest.exchange("/api/tracks/" + track.id() + "/sections",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(List.of(
                        new SectionController.SectionRequest(new BigDecimal("0.000"), new BigDecimal("4.000"), "intro", null, null),
                        new SectionController.SectionRequest(new BigDecimal("4.000"), new BigDecimal("8.000"), "riff", null, 2))),
                SectionsResponse.class);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().source()).isEqualTo(SectionSource.MANUAL);
        assertThat(edited.getBody().parts()).extracting(SectionsResponse.Part::label).containsExactly("intro", "riff");
        assertThat(edited.getBody().parts().get(0).chords()).extracting(SectionsResponse.Chord::degreeLabel)
                .containsExactly("vi", "IV");
        assertThat(edited.getBody().parts().get(1).chords()).extracting(SectionsResponse.Chord::degreeLabel)
                .containsExactly("V", "III5");
        assertThat(edited.getBody().parts().get(1).repeats()).isEqualTo(2);

        // Re-derivar não apaga a edição; lista vazia no PUT volta à derivação.
        assertThat(rest.postForObject("/api/tracks/" + track.id() + "/sections/derive", null, SectionsResponse.class).source())
                .isEqualTo(SectionSource.MANUAL);
        ResponseEntity<SectionsResponse> reverted = rest.exchange("/api/tracks/" + track.id() + "/sections",
                org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(List.of()),
                SectionsResponse.class);
        assertThat(reverted.getBody().source()).isEqualTo(SectionSource.DERIVED);
        assertThat(reverted.getBody().parts()).extracting(SectionsResponse.Part::label).containsExactly("A");

        // Re-análise herda o que o dono corrigiu no run canônico: tonalidade MANUAL (C maior), partes MANUAL e letra MANUAL.
        rest.exchange("/api/tracks/" + track.id() + "/sections", org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(List.of(
                        new SectionController.SectionRequest(new BigDecimal("0.000"), new BigDecimal("8.000"), "tudo", null, null))),
                SectionsResponse.class);
        rest.exchange("/api/tracks/" + track.id() + "/lyrics", org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(List.of(new LyricsController.SegmentRequest(bd(0.4), bd(4.0), "let it be",
                        List.of(new LyricsController.WordRequest(bd(0.4), bd(1.4), "let"))))),
                LyricsResponse.class);
        long reanalysis = ((Number) rest.postForEntity("/api/tracks/" + track.id() + "/analyze", null, Map.class)
                .getBody().get("runId")).longValue();
        worker.pollOnce();
        TimelineResponse inherited = rest.getForObject("/api/tracks/" + track.id() + "/timeline?runId=" + reanalysis,
                TimelineResponse.class);
        assertThat(inherited.key().source()).isEqualTo(KeySource.MANUAL);
        assertThat(inherited.key().tonicPc()).isZero();
        assertThat(inherited.segments()).extracting(TimelineResponse.Segment::degreeLabel)
                .containsExactly("vi", "IV", "V", "III5");
        SectionsResponse inheritedParts = rest.getForObject("/api/tracks/" + track.id() + "/sections?runId=" + reanalysis,
                SectionsResponse.class);
        assertThat(inheritedParts.source()).isEqualTo(SectionSource.MANUAL);
        assertThat(inheritedParts.parts()).extracting(SectionsResponse.Part::label).containsExactly("tudo");
        LyricsResponse inheritedLyrics = rest.getForObject("/api/tracks/" + track.id() + "/lyrics?runId=" + reanalysis,
                LyricsResponse.class);
        assertThat(inheritedLyrics.source()).isEqualTo(LyricSource.MANUAL);
        assertThat(inheritedLyrics.segments().get(0).text()).isEqualTo("let it be");
    }

    @Test
    void keyOverrideWithoutAnalysisIsAConflict() throws IOException {
        Path audio = tempDir.resolve("stub3.wav");
        Files.writeString(audio, "stub3");
        Long artistId = rest.postForEntity("/api/artists", new ArtistRequest("Stub 3", null, null), ArtistResponse.class)
                .getBody().id();
        Long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, "Stub 3", 2026), AlbumResponse.class)
                .getBody().id();
        Long trackId = Uploads.upload(rest, albumId, "Stub 3", 1, audio).getBody().id();

        ResponseEntity<org.springframework.http.ProblemDetail> response = rest.exchange("/api/tracks/" + trackId + "/key",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new KeyController.KeyRequest(7, KeyMode.MAJOR)),
                org.springframework.http.ProblemDetail.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // deixa a fila limpa para os outros testes
        worker.pollOnce();
    }

    @Test
    void auditRecordsEntriesAndActionsAndOnlyTheAdminReadsThem() {
        // Local (sem cabeçalhos do túnel) = administrador: vê a auditoria e aparece nela como "local".
        assertThat(rest.getForObject("/api/access", dev.musicsense.orelha.common.AccessController.AccessInfo.class).admin()).isTrue();
        org.springframework.http.HttpHeaders remote = new org.springframework.http.HttpHeaders();
        remote.set(dev.musicsense.orelha.common.RemoteAccess.EMAIL_HEADER, "amigo@example.com");
        remote.set(dev.musicsense.orelha.common.RemoteAccess.ORIGIN_IP_HEADER, "203.0.113.9");
        ResponseEntity<dev.musicsense.orelha.common.AccessController.AccessInfo> asFriend = rest.exchange("/api/access",
                org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(remote),
                dev.musicsense.orelha.common.AccessController.AccessInfo.class);
        assertThat(asFriend.getBody().admin()).isFalse();
        assertThat(asFriend.getBody().email()).isEqualTo("amigo@example.com");
        assertThat(rest.exchange("/api/admin/audit", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(remote), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<List<dev.musicsense.orelha.audit.AdminController.EventView>> events = rest.exchange(
                "/api/admin/audit?actor=amigo@example.com", org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(events.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(events.getBody()).isNotEmpty();
        assertThat(events.getBody().get(0).kind()).isEqualTo(dev.musicsense.orelha.audit.AuditEvent.Kind.ENTER);
        assertThat(events.getBody().get(0).ip()).isEqualTo("203.0.113.9");
        assertThat(events.getBody().get(0).remote()).isTrue();

        ResponseEntity<List<dev.musicsense.orelha.audit.AuditEventRepository.ActorSummary>> users = rest.exchange(
                "/api/admin/audit/users", org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(users.getBody()).extracting(dev.musicsense.orelha.audit.AuditEventRepository.ActorSummary::actor)
                .contains("amigo@example.com", "local");
    }

    @Test
    void reanalysisCreatesASecondRunWithoutTouchingTheCanonicalOne() throws IOException {
        Path audio = tempDir.resolve("stub2.wav");
        Files.writeString(audio, "stub2");
        Long artistId = rest.postForEntity("/api/artists", new ArtistRequest("Stub 2", null, null), ArtistResponse.class)
                .getBody().id();
        Long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, "Stub 2", 2026), AlbumResponse.class)
                .getBody().id();
        Long trackId = Uploads.upload(rest, albumId, "Stub 2", 1, audio).getBody().id();
        worker.pollOnce();
        Long canonical = rest.getForObject("/api/tracks/" + trackId, TrackResponse.class).canonicalRunId();

        ResponseEntity<Map> accepted = rest.postForEntity("/api/tracks/" + trackId + "/analyze", null, Map.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long secondRun = ((Number) accepted.getBody().get("runId")).longValue();
        worker.pollOnce();

        assertThat(rest.getForObject("/api/tracks/" + trackId, TrackResponse.class).canonicalRunId()).isEqualTo(canonical);
        assertThat(rest.getForObject("/api/analysis/runs/" + secondRun, AnalysisRunResponse.class).status())
                .isEqualTo(RunStatus.DONE);
        TimelineResponse second = rest.getForObject("/api/tracks/" + trackId + "/timeline?runId=" + secondRun,
                TimelineResponse.class);
        assertThat(second.runId()).isEqualTo(secondRun);
        assertThat(second.segments()).hasSize(4);

        // O dono escolhe qual run responde pela faixa.
        ResponseEntity<TrackResponse> switched = rest.exchange("/api/tracks/" + trackId + "/canonical-run",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new dev.musicsense.orelha.catalog.TrackController.CanonicalRunRequest(secondRun)),
                TrackResponse.class);
        assertThat(switched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(switched.getBody().canonicalRunId()).isEqualTo(secondRun);
        assertThat(rest.getForObject("/api/tracks/" + trackId + "/timeline", TimelineResponse.class).runId())
                .isEqualTo(secondRun);

        // Apagar é só do administrador: um usuário remoto comum recebe 403 e a faixa continua lá.
        org.springframework.http.HttpHeaders friend = new org.springframework.http.HttpHeaders();
        friend.set(dev.musicsense.orelha.common.RemoteAccess.EMAIL_HEADER, "amigo@example.com");
        friend.set(dev.musicsense.orelha.common.RemoteAccess.ORIGIN_IP_HEADER, "203.0.113.9");
        assertThat(rest.exchange("/api/tracks/" + trackId, org.springframework.http.HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(friend), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.getForEntity("/api/tracks/" + trackId, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Uma segunda faixa sobre o mesmo arquivo (mesmos bytes → mesma pasta de stems, que o exrquet, que
        // o extrator chaveia por SHA): excluir uma delas não pode levar os arquivos da outra.
        Long twinId = Uploads.upload(rest, albumId, "Stub 2 (cópia)", 2, audio).getBody().id();
        worker.pollOnce();
        ResponseEntity<String> twinDeleted = rest.exchange("/api/tracks/" + twinId, org.springframework.http.HttpMethod.DELETE,
                null, String.class);
        assertThat(twinDeleted.getStatusCode()).as(String.valueOf(twinDeleted.getBody())).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/api/tracks/" + twinId, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Files.exists(dataRoot.resolve("stems/stub/bass.wav"))).isTrue();
        assertThat(rest.getForEntity("/api/tracks/" + trackId + "/stems/bass", byte[].class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Apagar a faixa (local = administrador) leva os runs e tudo que deriva deles (V5: ON DELETE CASCADE).
        ResponseEntity<String> deleted = rest.exchange("/api/tracks/" + trackId, org.springframework.http.HttpMethod.DELETE,
                null, String.class);
        assertThat(deleted.getStatusCode()).as(String.valueOf(deleted.getBody())).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/api/tracks/" + trackId, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/analysis/runs/" + canonical, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/analysis/runs/" + secondRun, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Agora ninguém mais usa os bytes: do disco saem os stems dos runs; o áudio cadastrado por path fora
        // da biblioteca é do dono e fica.
        assertThat(Files.exists(dataRoot.resolve("stems/stub"))).isFalse();
        assertThat(Files.exists(audio)).isTrue();
    }
}
