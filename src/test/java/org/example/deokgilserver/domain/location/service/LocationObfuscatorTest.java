package org.example.deokgilserver.domain.location.service;

import org.example.deokgilserver.domain.location.presentation.dto.request.LocationObfuscationRequest;
import org.example.deokgilserver.domain.location.presentation.dto.response.LocationObfuscationResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocationObfuscatorTest {

    private final LocationObfuscator locationObfuscator = new LocationObfuscator();

    @Test
    void 좌표를_소수점_셋째_자리로_반올림한다() {
        LocationObfuscationRequest request = new LocationObfuscationRequest(37.512345, 127.098765);

        LocationObfuscationResponse response = locationObfuscator.obfuscate(request);

        assertThat(response.latitude()).isEqualTo(37.512);
        assertThat(response.longitude()).isEqualTo(127.099);
    }

    @Test
    void 반올림_기준을_충족하면_올림된다() {
        LocationObfuscationRequest request = new LocationObfuscationRequest(37.5125, -127.0985);

        LocationObfuscationResponse response = locationObfuscator.obfuscate(request);

        assertThat(response.latitude()).isEqualTo(37.513);
        assertThat(response.longitude()).isEqualTo(-127.099);
    }

    @Test
    void 경계값도_정상적으로_처리한다() {
        LocationObfuscationRequest request = new LocationObfuscationRequest(90.0, -180.0);

        LocationObfuscationResponse response = locationObfuscator.obfuscate(request);

        assertThat(response.latitude()).isEqualTo(90.0);
        assertThat(response.longitude()).isEqualTo(-180.0);
    }
}
