package org.example.deokgilserver.common.security;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractEncryptedBigDecimalConverterTest {

    // 실제 환경변수와 무관하게, 이 테스트만을 위한 임의의 256bit 키를 매번 새로 만든다.
    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static AbstractEncryptedBigDecimalConverter converterWithAad(String key, String aad) {
        return new AbstractEncryptedBigDecimalConverter(key) {
            @Override
            protected byte[] aad() {
                return aad.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        };
    }

    @Test
    void 암호화한_값을_복호화하면_원래_값과_같다() {
        AbstractEncryptedBigDecimalConverter converter = converterWithAad(randomBase64Key(), "event.latitude");
        BigDecimal original = new BigDecimal("37.5121");

        String encrypted = converter.convertToDatabaseColumn(original);
        BigDecimal decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(encrypted).isNotEqualTo(original.toPlainString());
        assertThat(decrypted).isEqualByComparingTo(original);
    }

    @Test
    void 같은_값을_두번_암호화하면_매번_다른_암호문이_나온다() {
        // GCM은 매 호출마다 새 IV를 써야 한다 - 암호문이 같다면 IV가 재사용됐다는 뜻이고,
        // 이는 GCM의 기밀성/무결성 보장을 깨뜨리는 심각한 구현 버그다.
        AbstractEncryptedBigDecimalConverter converter = converterWithAad(randomBase64Key(), "event.latitude");
        BigDecimal value = new BigDecimal("127.0982");

        String first = converter.convertToDatabaseColumn(value);
        String second = converter.convertToDatabaseColumn(value);

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualByComparingTo(value);
        assertThat(converter.convertToEntityAttribute(second)).isEqualByComparingTo(value);
    }

    @Test
    void null_값은_그대로_null로_변환된다() {
        AbstractEncryptedBigDecimalConverter converter = converterWithAad(randomBase64Key(), "event.latitude");

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void 다른_키로_복호화하면_실패한다() {
        AbstractEncryptedBigDecimalConverter encryptor = converterWithAad(randomBase64Key(), "event.latitude");
        AbstractEncryptedBigDecimalConverter decryptorWithWrongKey = converterWithAad(randomBase64Key(), "event.latitude");
        String encrypted = encryptor.convertToDatabaseColumn(new BigDecimal("37.5"));

        assertThatThrownBy(() -> decryptorWithWrongKey.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 다른_필드의_암호문을_가져와도_복호화가_실패한다() {
        // latitude 암호문을 longitude 컬럼에 그대로 옮겨 심는 공격(또는 실수로 인한 필드 뒤바뀜)을
        // 시뮬레이션한다 — AAD가 필드별로 고정돼 있어야 여기서 걸러진다.
        String key = randomBase64Key();
        AbstractEncryptedBigDecimalConverter latitudeConverter = converterWithAad(key, "event.latitude");
        AbstractEncryptedBigDecimalConverter longitudeConverter = converterWithAad(key, "event.longitude");

        String latitudeCipherText = latitudeConverter.convertToDatabaseColumn(new BigDecimal("37.5"));

        assertThatThrownBy(() -> longitudeConverter.convertToEntityAttribute(latitudeCipherText))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void AAD_없이_암호화된_레거시_암호문도_복호화된다() {
        // AAD 도입 이전에 저장된 데이터를 흉내낸다: updateAAD 없이 암호화.
        String key = randomBase64Key();
        AbstractEncryptedBigDecimalConverter legacyEncryptor = new AbstractEncryptedBigDecimalConverter(key) {
            @Override
            protected byte[] aad() {
                return new byte[0];
            }

            @Override
            public String convertToDatabaseColumn(BigDecimal attribute) {
                // aad()가 빈 배열이라도 updateAAD(new byte[0])는 호출되므로, 진짜 "AAD 미적용"
                // 레거시 암호문을 재현하려면 이 테스트에서는 아예 AAD 없이 만든 값으로 검증한다.
                return super.convertToDatabaseColumn(attribute);
            }
        };
        AbstractEncryptedBigDecimalConverter currentConverter = converterWithAad(key, "event.latitude");

        String legacyCipherText = legacyEncryptor.convertToDatabaseColumn(new BigDecimal("37.5"));
        BigDecimal decrypted = currentConverter.convertToEntityAttribute(legacyCipherText);

        assertThat(decrypted).isEqualByComparingTo(new BigDecimal("37.5"));
    }

    @Test
    void 키_길이가_16_24_32바이트가_아니면_생성자에서_즉시_실패한다() {
        byte[] invalidKey = new byte[10];
        new SecureRandom().nextBytes(invalidKey);
        String base64Key = Base64.getEncoder().encodeToString(invalidKey);

        assertThatThrownBy(() -> converterWithAad(base64Key, "event.latitude"))
                .isInstanceOf(IllegalStateException.class);
    }
}
