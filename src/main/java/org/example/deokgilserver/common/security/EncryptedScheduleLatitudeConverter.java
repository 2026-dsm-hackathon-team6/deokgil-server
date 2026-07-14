package org.example.deokgilserver.common.security;

import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * ⚠ 스키마 변경 필요: 이 컨버터를 적용하려면 latitude 컬럼 타입을 DECIMAL(10,7) -> VARCHAR(255)로
 * 바꿔야 한다(암호문은 Base64 문자열이라 더 이상 숫자가 아님). ddl-auto=validate 환경이라 이
 * 변경은 애플리케이션이 자동으로 해주지 않으므로, 실제 DB에 마이그레이션을 직접 적용해야 한다.
 */
@Converter
@Component
public class EncryptedScheduleLatitudeConverter extends AbstractEncryptedBigDecimalConverter {

    private static final byte[] AAD = "schedule.latitude".getBytes(StandardCharsets.UTF_8);

    public EncryptedScheduleLatitudeConverter(@Value("${location.encryption-key}") String base64Key) {
        super(base64Key);
    }

    @Override
    protected byte[] aad() {
        return AAD;
    }
}
