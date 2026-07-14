package org.example.deokgilserver.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 행사장 위경도를 DB에 평문으로 저장하지 않기 위한 AES-GCM 컬럼 암호화.
 * DB가 통째로 유출되더라도(백업 파일 노출, SQL 인젝션 등) 좌표만큼은 별도 키 없이는 못 읽게 막는다.
 *
 * Hibernate가 @Component로 등록된 AttributeConverter를 Spring 빈으로 자동 인식해서
 * 생성자 주입을 쓸 수 있다(Spring Boot의 SpringBeanContainer 연동, 별도 설정 불필요).
 *
 * ⚠ 스키마 변경 필요: 이 컨버터를 적용하려면 latitude/longitude 컬럼 타입을
 * DECIMAL(10,7) -> VARCHAR(255) 로 바꿔야 한다(암호문은 Base64 문자열이라 더 이상 숫자가 아님).
 * ddl-auto=validate 환경이라 이 변경은 애플리케이션이 자동으로 해주지 않으므로, 실제 DB에
 * 마이그레이션을 직접 적용해야 한다.
 */
@Converter
@Component
public class EncryptedBigDecimalConverter implements AttributeConverter<BigDecimal, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;

    public EncryptedBigDecimalConverter(@Value("${location.encryption-key}") String base64Key) {
        // Base64로 인코딩된 256bit(32byte) AES 키. openssl rand -base64 32 로 생성해서
        // LOCATION_ENCRYPTION_KEY에 설정할 것 — 이 키를 잃어버리면 저장된 좌표는 복구 불가능하다.
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.toPlainString().getBytes(StandardCharsets.UTF_8));

            // IV는 비밀이 아니라 복호화에 필요한 값이라, 암호문 앞에 그대로 붙여서 같이 저장한다
            // (GCM은 매 암호화마다 새 IV를 써야 하므로 키만으로는 복호화가 불가능하다).
            ByteBuffer combined = ByteBuffer.allocate(iv.length + cipherText.length);
            combined.put(iv).put(cipherText);
            return Base64.getEncoder().encodeToString(combined.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("좌표 암호화에 실패했습니다.", e);
        }
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new BigDecimal(new String(plainText, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("좌표 복호화에 실패했습니다. LOCATION_ENCRYPTION_KEY가 바뀌지 않았는지 확인하세요.", e);
        }
    }
}
