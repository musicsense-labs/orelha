package dev.musicsense.orelha.extraction.orelhaextractor;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** orelha.extractor.url / orelha.extractor.timeout (application.yml). */
@ConfigurationProperties("orelha.extractor")
public record OrelhaExtractorProperties(String url, Duration timeout) {
}
