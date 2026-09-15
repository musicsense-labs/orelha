package dev.musicsense.orelha.catalog;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importação de pasta: WAVs sintéticos com tags ID3 escritas pelo jaudiotagger (mesma biblioteca
 * que lê), mais um arquivo sem tags para o fallback de pastas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "orelha.worker.enabled=false")
@Testcontainers
class ImportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static Path libraryDir;
    static Path stagingDir;

    @DynamicPropertySource
    static void dirs(DynamicPropertyRegistry registry) throws IOException {
        libraryDir = Files.createTempDirectory("orelha-library");
        stagingDir = Files.createTempDirectory("orelha-staging");
        registry.add("orelha.library.dir", () -> libraryDir.toString());
        registry.add("orelha.staging.dir", () -> stagingDir.toString());
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    TrackRepository trackRepository;

    @TempDir
    Path tempDir;

    /** WAV PCM válido de alguns milissegundos, com bytes distintos por semente (SHA diferente). */
    static Path wav(Path path, int seed) throws IOException {
        int samples = 2000;
        ByteBuffer buf = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples * 2)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII))
                .put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(44100).putInt(44100 * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples * 2);
        for (int i = 0; i < samples; i++) {
            buf.putShort((short) (Math.sin(i * (0.05 + seed * 0.01)) * 8000));
        }
        Files.createDirectories(path.getParent());
        Files.write(path, buf.array());
        return path;
    }

    static void tag(Path path, Map<FieldKey, String> fields) throws Exception {
        AudioFile audio = AudioFileIO.read(path.toFile());
        Tag tag = audio.getTagOrCreateAndSetDefault();
        for (Map.Entry<FieldKey, String> e : fields.entrySet()) {
            tag.setField(e.getKey(), e.getValue());
        }
        audio.commit();
    }

    @Test
    void importsAServerFolderFromTagsWithFolderFallbackAndSkipsDuplicates() throws Exception {
        Path root = tempDir.resolve("library");
        Path a1 = wav(root.resolve("Black Sabbath/Paranoid/01 War Pigs.wav"), 1);
        tag(a1, Map.of(FieldKey.ARTIST, "Black Sabbath", FieldKey.ALBUM, "Paranoid", FieldKey.YEAR, "1970-09-18",
                FieldKey.TITLE, "War Pigs", FieldKey.TRACK, "1/8"));
        Path a2 = wav(root.resolve("Black Sabbath/Paranoid/02 Paranoid.wav"), 2);
        tag(a2, Map.of(FieldKey.ALBUM_ARTIST, "Black Sabbath", FieldKey.ARTIST, "Ozzy et al.", FieldKey.ALBUM, "paranoid",
                FieldKey.TITLE, "Paranoid", FieldKey.TRACK, "2"));
        wav(root.resolve("Deep Purple/Machine Head/03 - Smoke on the Water.wav"), 3);   // sem tags
        Files.writeString(root.resolve("Deep Purple/Machine Head/cover.txt"), "not audio");

        ResponseEntity<ImportReport> first = rest.postForEntity("/api/tracks/import-path",
                new TrackController.ImportPathRequest(root.toString(), true), ImportReport.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportReport report = first.getBody();
        assertThat(report.skipped()).isEmpty();
        assertThat(report.imported()).hasSize(3);
        assertThat(report.imported()).extracting(ImportReport.Imported::artist)
                .containsExactly("Black Sabbath", "Black Sabbath", "Deep Purple");
        assertThat(report.imported()).extracting(ImportReport.Imported::album)
                .containsExactly("Paranoid", "Paranoid", "Machine Head");   // álbum reusado apesar de "paranoid"
        assertThat(report.imported()).extracting(ImportReport.Imported::title)
                .containsExactly("War Pigs", "Paranoid", "Smoke on the Water");
        assertThat(report.imported()).extracting(ImportReport.Imported::trackNo).containsExactly(1, 2, 3);
        assertThat(report.imported()).extracting(ImportReport.Imported::fromTags).containsExactly(true, true, false);

        ResponseEntity<List<Artist>> artists = rest.exchange("/api/artists", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        assertThat(artists.getBody()).extracting(Artist::getName).contains("Black Sabbath", "Deep Purple");
        ResponseEntity<List<AlbumResponse>> albums = rest.exchange("/api/albums", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        AlbumResponse paranoid = albums.getBody().stream().filter(a -> a.title().equals("Paranoid")).findFirst().orElseThrow();
        assertThat(paranoid.year()).isEqualTo(1970);
        ResponseEntity<List<TrackResponse>> tracks = rest.exchange("/api/tracks?albumId=" + paranoid.id(), HttpMethod.GET,
                null, new ParameterizedTypeReference<>() {
                });
        assertThat(tracks.getBody()).hasSize(2);
        assertThat(tracks.getBody().get(0).audioPath()).isEqualTo(a1.toAbsolutePath().toString());   // no lugar
        assertThat(tracks.getBody()).allSatisfy(t -> assertThat(t.latestRunStatus()).isEqualTo(dev.musicsense.orelha.analysis.RunStatus.QUEUED));

        // Segunda passada: tudo já importado.
        ImportReport again = rest.postForEntity("/api/tracks/import-path",
                new TrackController.ImportPathRequest(root.toString(), true), ImportReport.class).getBody();
        assertThat(again.imported()).isEmpty();
        assertThat(again.skipped()).hasSize(3);
        assertThat(again.skipped()).allSatisfy(s -> assertThat(s.reason()).contains("já importado"));
    }

    @Test
    void importsUploadedFolderCopyingIntoTheLibrary() throws Exception {
        Path src = wav(tempDir.resolve("up/Judas Priest/Painkiller/01 Painkiller.wav"), 7);
        Path noTags = wav(tempDir.resolve("up/Judas Priest/Painkiller/02 Hell Patrol.wav"), 8);
        tag(src, Map.of(FieldKey.ARTIST, "Judas Priest", FieldKey.ALBUM, "Painkiller", FieldKey.TITLE, "Painkiller",
                FieldKey.TRACK, "1", FieldKey.YEAR, "1990"));

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", named(src, "Judas Priest/Painkiller/01 Painkiller.wav"));
        form.add("files", named(noTags, "Judas Priest/Painkiller/02 Hell Patrol.wav"));
        form.add("files", named(noTags, "Judas Priest/Painkiller/notes.txt"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<ImportReport> response = rest.postForEntity("/api/tracks/import", new HttpEntity<>(form, headers),
                ImportReport.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ImportReport report = response.getBody();
        assertThat(report.imported()).extracting(ImportReport.Imported::title).containsExactly("Painkiller", "Hell Patrol");
        assertThat(report.imported()).extracting(ImportReport.Imported::artist).containsOnly("Judas Priest");
        assertThat(report.imported()).extracting(ImportReport.Imported::trackNo).containsExactly(1, 2);
        assertThat(report.skipped()).isEmpty();   // notes.txt é ignorado no staging

        TrackResponse copied = rest.getForObject("/api/tracks/" + report.imported().get(1).trackId(), TrackResponse.class);
        assertThat(copied.audioPath()).endsWith("Hell Patrol.wav");
        assertThat(Path.of(copied.audioPath())).exists().isNotEqualTo(noTags.toAbsolutePath());
        assertThat(Path.of(copied.audioPath())).startsWith(libraryDir);
        // Persistido relativo à biblioteca, com '/': mover a pasta do projeto não quebra o acervo.
        assertThat(trackRepository.findById(copied.id()).orElseThrow().getAudioPath())
                .isEqualTo(copied.albumId() + "/Hell Patrol.wav");
        try (var dirs = Files.list(stagingDir)) {
            assertThat(dirs).as("staging apagado após confirmar").isEmpty();
        }
    }

    @Test
    void previewCanBeEditedBeforeConfirmingAndDuplicatesAreFlagged() throws Exception {
        Path root = tempDir.resolve("rips");
        Path a = wav(root.resolve("Valerie - The Zutons (youtube).wav"), 11);
        Path b = wav(root.resolve("Radiohead - Creep (Remastered).wav"), 12);

        ImportReport.Preview preview = rest.postForEntity("/api/tracks/import-path/preview",
                new TrackController.ImportPathRequest(root.toString(), false), ImportReport.Preview.class).getBody();
        assertThat(preview.stagingId()).isNull();
        assertThat(preview.items()).hasSize(2);
        ImportReport.Item creep = preview.items().get(0);
        ImportReport.Item valerie = preview.items().get(1);
        assertThat(creep.artist()).isEqualTo("Radiohead");           // "Artista - Título", sufixo limpo
        assertThat(creep.title()).isEqualTo("Creep");
        assertThat(valerie.artist()).isEqualTo("Valerie");           // ordem trocada no nome: a UI corrige
        assertThat(valerie.title()).isEqualTo("The Zutons");
        assertThat(preview.items()).allSatisfy(i -> assertThat(i.duplicate()).isFalse());
        assertThat(rest.getForObject("/api/tracks", TrackResponse[].class))
                .as("preview não cadastra nada").noneMatch(t -> t.audioPath().equals(a.toAbsolutePath().toString()));

        // O usuário corrige a linha da Valerie e descarta a do Creep.
        ImportReport.Item fixed = new ImportReport.Item(valerie.key(), valerie.file(), "The Zutons",
                "Tired of Hanging Around", 2006, "Valerie", 3, false, false);
        ImportReport report = rest.postForEntity("/api/tracks/import/confirm",
                new ImportReport.Confirmation(null, List.of(fixed)), ImportReport.class).getBody();
        assertThat(report.imported()).hasSize(1);
        assertThat(report.imported().get(0).artist()).isEqualTo("The Zutons");
        assertThat(report.imported().get(0).album()).isEqualTo("Tired of Hanging Around");
        assertThat(report.imported().get(0).title()).isEqualTo("Valerie");
        assertThat(report.imported().get(0).trackNo()).isEqualTo(3);
        TrackResponse track = rest.getForObject("/api/tracks/" + report.imported().get(0).trackId(), TrackResponse.class);
        assertThat(track.audioPath()).isEqualTo(a.toAbsolutePath().toString());   // no lugar

        // Nova pré-visualização: Valerie agora é duplicata; confirmar mesmo assim é pulado.
        ImportReport.Preview again = rest.postForEntity("/api/tracks/import-path/preview",
                new TrackController.ImportPathRequest(root.toString(), false), ImportReport.Preview.class).getBody();
        assertThat(again.items()).extracting(ImportReport.Item::duplicate).containsExactly(false, true);
        ImportReport forced = rest.postForEntity("/api/tracks/import/confirm",
                new ImportReport.Confirmation(null, again.items()), ImportReport.class).getBody();
        assertThat(forced.imported()).extracting(ImportReport.Imported::title).containsExactly("Creep");
        assertThat(forced.skipped()).extracting(ImportReport.Skipped::reason).containsExactly("já importado (mesmo conteúdo)");
        assertThat(Files.exists(b)).isTrue();
    }

    @Test
    void namesWithDotsKeepTheirExtensionThroughStaging() throws Exception {
        Path src = wav(tempDir.resolve("dots/Black Sabbath/Black Sabbath/04 N.I.B..wav"), 31);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", named(src, "Black Sabbath/Black Sabbath/04 N.I.B..wav"));
        form.add("files", named(src, "../../etc/passwd.wav"));   // traversal: segmentos ".." caem fora
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ImportReport.Preview preview = rest.postForEntity("/api/tracks/import/stage", new HttpEntity<>(form, headers),
                ImportReport.Preview.class).getBody();
        assertThat(preview.items()).hasSize(2);
        assertThat(preview.items().get(0).key()).endsWith("04 N.I.B..wav");
        assertThat(preview.items().get(0).title()).isEqualTo("N.I.B.");
        assertThat(preview.items().get(0).trackNo()).isEqualTo(4);
        assertThat(Path.of(preview.items().get(1).key())).startsWith(stagingDir.resolve(preview.stagingId()))
                .satisfies(p -> assertThat(p.toString()).endsWith("etc" + java.io.File.separator + "passwd.wav"));

        ImportReport report = rest.postForEntity("/api/tracks/import/confirm",
                new ImportReport.Confirmation(preview.stagingId(), List.of(preview.items().get(0))), ImportReport.class).getBody();
        assertThat(report.imported()).hasSize(1);
        TrackResponse track = rest.getForObject("/api/tracks/" + report.imported().get(0).trackId(), TrackResponse.class);
        assertThat(track.title()).isEqualTo("N.I.B.");
        assertThat(track.audioPath()).endsWith(java.io.File.separator + "N.I.B.wav");   // sem ponto duplo, extensão certa
    }

    @Test
    void stagedUploadCanBeDiscarded() throws Exception {
        Path src = wav(tempDir.resolve("disc/x/y/01 Song.wav"), 21);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", named(src, "x/y/01 Song.wav"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ImportReport.Preview preview = rest.postForEntity("/api/tracks/import/stage", new HttpEntity<>(form, headers),
                ImportReport.Preview.class).getBody();
        assertThat(preview.stagingId()).isNotBlank();
        assertThat(preview.items()).hasSize(1);
        Path staged = Path.of(preview.items().get(0).key());
        assertThat(staged).exists().startsWith(stagingDir.resolve(preview.stagingId()));
        assertThat(preview.items().get(0).artist()).isEqualTo("x");
        assertThat(preview.items().get(0).album()).isEqualTo("y");

        rest.delete("/api/tracks/import/stage/" + preview.stagingId());
        assertThat(staged).doesNotExist();
        assertThat(stagingDir.resolve(preview.stagingId())).doesNotExist();

        // Confirmar um staging que não existe mais só pula, não quebra.
        ImportReport report = rest.postForEntity("/api/tracks/import/confirm",
                new ImportReport.Confirmation(preview.stagingId(), preview.items()), ImportReport.class).getBody();
        assertThat(report.imported()).isEmpty();
        assertThat(report.skipped()).extracting(ImportReport.Skipped::reason).containsExactly("arquivo não encontrado");
    }

    /** Resource com o nome relativo que o navegador manda num upload de pasta (webkitRelativePath). */
    private static FileSystemResource named(Path file, String relativeName) {
        return new FileSystemResource(file) {
            @Override
            public String getFilename() {
                return relativeName;
            }
        };
    }
}
