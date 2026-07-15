package org.example.deokgilserver.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record S3Properties(String region, String accessKey, String secretKey, S3 s3) {

    public record S3(String bucket) {
    }
}
