package dev.rifflab.extraction.riffextractor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

/** HTTP puro contra o container: multipart de entrada, JSON de saída. Bloqueante por desenho. */
@Component
@EnableConfigurationProperties(RiffExtractorProperties.class)
class RiffExtractorClient {

    private final RestClient rest;

    RiffExtractorClient(RiffExtractorProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(props.timeout());
        this.rest = RestClient.builder().baseUrl(props.url()).requestFactory(factory).build();
    }

    RiffExtractorResponse analyze(Path audio, String audioSha256) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new FileSystemResource(audio));
        form.add("audio_sha256", audioSha256);
        return rest.post().uri("/analyze")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(RiffExtractorResponse.class);
    }
}
