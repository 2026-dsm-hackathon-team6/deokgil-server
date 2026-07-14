package org.example.deokgilserver.common.security;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedBigDecimalConverterTest {

    // 실제 환경변수와 무관하게, 이 테스트만을 위한 임의의 256bit 키를 매번 새로 만든다.
    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void 암호화한_값을_복호화하면_원래_값과_같다() {
        EncryptedBigDecimalConverter converter = new EncryptedBigDecimalConverter(randomBase64Key());
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
        EncryptedBigDecimalConverter converter = new EncryptedBigDecimalConverter(randomBase64Key());
        BigDecimal value = new BigDecimal("127.0982");

        String first = converter.convertToDatabaseColumn(value);
        String second = converter.convertToDatabaseColumn(value);

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualByComparingTo(value);
        assertThat(converter.convertToEntityAttribute(second)).isEqualByComparingTo(value);
    }

    @Test
    void null_값은_그대로_null로_변환된다() {
        EncryptedBigDecimalConverter converter = new EncryptedBigDecimalConverter(randomBase64Key());

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void 다른_키로_복호화하면_실패한다() {
        EncryptedBigDecimalConverter encryptor = new EncryptedBigDecimalConverter(randomBase64Key());
        EncryptedBigDecimalConverter decryptorWithWrongKey = new EncryptedBigDecimalConverter(randomBase64Key());
        String encrypted = encryptor.convertToDatabaseColumn(new BigDecimal("37.5"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> decryptorWithWrongKey.convertToEntityAttribute(encrypted));
    }
}
