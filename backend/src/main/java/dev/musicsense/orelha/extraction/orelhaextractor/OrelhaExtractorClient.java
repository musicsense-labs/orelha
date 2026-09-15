package dev.musicsense.orelha.extraction.orelhaextractor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.file.Path;

/** HTTP puro contra o container: multipart de entrada, JSON de saída. Bloqueante por desenho. */
@Component
@EnableConfigurationProperties(OrelhaExtractorProperties.class)
class OrelhaExtractorClient {

    private final RestClient rest;

    OrelhaExtractorClient(OrelhaExtractorProperties props) {
        // HTTP/1.1 fixo: o upgrade h2c que o JDK tenta por padrão faz o parser httptools do uvicorn
        // parar no fim dos headers e o multipart chega vazio (422).
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.timeout());
        this.rest = RestClient.builder().baseUrl(props.url()).requestFactory(factory).build();
    }

    OrelhaExtractorResponse analyze(Path audio, String audioSha256) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(audio));
        form.add("audio_sha256", audioSha256);
        return rest.post().uri("/analyze")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(OrelhaExtractorResponse.class);
    }
}
