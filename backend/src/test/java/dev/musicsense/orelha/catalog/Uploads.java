package dev.musicsense.orelha.catalog;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;

/**
 * Cadastra uma faixa como a UI faz — multipart em /api/tracks/upload, que copia o arquivo para a biblioteca.
 * Era o que os testes faziam por {@code POST /api/tracks} com um caminho do servidor, removido em
 * 2026-09-30 porque permitia a qualquer usuário do Access baixar qualquer arquivo da máquina.
 */
public final class Uploads {

    private Uploads() {
    }

    public static ResponseEntity<TrackResponse> upload(TestRestTemplate rest, long albumId, String title, Integer trackNo,
                                                       Path audio) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(audio));
        form.add("albumId", albumId);
        form.add("title", title);
        if (trackNo != null) {
            form.add("trackNo", trackNo);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("/api/tracks/upload", new HttpEntity<>(form, headers), TrackResponse.class);
    }
}
