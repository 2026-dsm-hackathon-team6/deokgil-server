package org.example.deokgilserver.common.security;

import jakarta.persistence.AttributeConverter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/**
 * 행사장/일정 위경도를 DB에 평문으로 저장하지 않기 위한 AES-GCM 컬럼 암호화의 공통 구현.
 * DB가 통째로 유출되더라도(백업 파일 노출, SQL 인젝션 등) 좌표만큼은 별도 키 없이는 못 읽게 막는다.
 *
 * AAD(Additional Authenticated Data)로 어떤 엔티티·필드의 값인지를 암호문에 인증 결합한다.
 * 이게 없으면 IV+태그가 유효한 암호문을 다른 필드/엔티티 컬럼에 그대로 옮겨 심어도 GCM 검증을
 * 통과해버린다(예: latitude 암호문을 longitude 컬럼에 복사, 또는 Event의 좌표를 Schedule에 복사) —
 * subclass가 고정된 aad()를 제공해 필드별로 암호문이 "그 자리에서만" 유효하도록 한다.
 *
 * 주의(잔존 위험): 이 AAD는 필드/엔티티 종류만 구분하고, 레코드 ID까지는 묶지 않는다. 즉
 * "Event A의 latitude 암호문을 Event B의 latitude 컬럼에 복사"하는 공격은 이 AAD만으로는
 * 막지 못한다 — AttributeConverter는 변환 시점에 엔티티 식별자에 접근할 수 없기 때문이다.
 * 레코드 단위까지 막으려면 JPA 엔티티 리스너(@PrePersist/@PostLoad)로 옮겨 ID를 AAD에
 * 포함시키는 더 큰 리팩터링이 필요하다.
 *
 * 하위 호환: AAD 도입 이전에 저장된 기존 암호문(예: 이미 저장된 Event.latitude/longitude)은
 * AAD 없이 암호화됐으므로, AAD를 붙여 복호화를 시도하면 GCM 태그 불일치로 실패한다. 그런
 * 레코드를 전부 손상 취급하면 안 되므로, AAD 복호화가 실패하면 AAD 없이 한 번 더 시도한다 —
 * 이후 그 엔티티가 다시 저장되면(update) 새 값은 AAD가 붙어서 암호화되므로 자연스럽게 이전된다.
 */
@Slf4j
public abstract class AbstractEncryptedBigDecimalConverter implements AttributeConverter<BigDecimal, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final Set<Integer> VALID_AES_KEY_LENGTHS_BYTES = Set.of(16, 24, 32);

    private final SecretKeySpec secretKey;

    protected AbstractEncryptedBigDecimalConverter(String base64Key) {
        // Base64로 인코딩된 AES 키. openssl rand -base64 32 로 생성해서 LOCATION_ENCRYPTION_KEY에
        // 설정할 것 — 이 키를 잃어버리면 저장된 좌표는 복구 불가능하다.
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (!VALID_AES_KEY_LENGTHS_BYTES.contains(keyBytes.length)) {
            // 잘못된 키 길이를 그대로 두면 Cipher.init()에서 매 암/복호화 시도마다 실패하는데,
            // 그 실패가 나중에(예: 첫 좌표 저장 시점) 발견되면 디버깅 비용이 크다. 애플리케이션
            // 시작 시점에 즉시 실패시켜 배포 파이프라인에서 바로 잡히게 한다.
            throw new IllegalStateException(
                    "LOCATION_ENCRYPTION_KEY의 길이가 올바르지 않습니다(16/24/32바이트여야 함): "
                            + keyBytes.length + "바이트");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 이 컨버터가 암호화하는 대상을 식별하는 고정 문자열(예: "event.latitude"). 같은 값이
     * 암호화·복호화 양쪽에서 AAD로 쓰이므로, 다른 필드/엔티티의 암호문이 이 필드에 들어오면
     * AAD 불일치로 복호화(태그 검증)가 실패한다.
     */
    protected abstract byte[] aad();

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
            cipher.updateAAD(aad());
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
        byte[] combined = Base64.getDecoder().decode(dbData);
        byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
        byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

        try {
            return decrypt(iv, cipherText, true);
        } catch (AEADBadTagException e) {
            log.warn("AAD 포함 복호화 실패, AAD 도입 이전 레거시 암호문으로 간주하고 재시도합니다.");
            try {
                return decrypt(iv, cipherText, false);
            } catch (GeneralSecurityException legacyFailure) {
                throw new IllegalStateException(
                        "좌표 복호화에 실패했습니다. LOCATION_ENCRYPTION_KEY가 바뀌지 않았는지 확인하세요.",
                        legacyFailure);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("좌표 복호화에 실패했습니다.", e);
        }
    }

    private BigDecimal decrypt(byte[] iv, byte[] cipherText, boolean withAad) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        if (withAad) {
            cipher.updateAAD(aad());
        }
        byte[] plainText = cipher.doFinal(cipherText);
        return new BigDecimal(new String(plainText, StandardCharsets.UTF_8));
    }
}
