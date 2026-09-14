package dev.rifflab.extraction.riffextractor;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** rifflab.extractor.url / rifflab.extractor.timeout (application.yml). */
@ConfigurationProperties("rifflab.extractor")
public record RiffExtractorProperties(String url, Duration timeout) {
}
