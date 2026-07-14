package org.example.deokgilserver.common.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    // 사용자가 지정한 외부 URL(행사 페이지)을 서버가 대신 요청하는 용도라, 느리거나 응답을
    // 끝내지 않는 서버가 요청 처리 스레드/커넥션을 오래 붙잡지 못하도록 각 단계에 짧은
    // 시간제한을 둔다.
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int READ_WRITE_TIMEOUT_SECONDS = 5;
    private static final int RESPONSE_TIMEOUT_SECONDS = 5;

    // 응답 본문을 무제한으로 메모리에 적재하지 않도록 WebClient 코덱 단계에서 상한을 둔다.
    // 애플리케이션 코드(ClaudeEventExtractionClient)의 문자열 길이 제한과 별개로, 네트워크
    // 레벨에서 더 큰 응답이 오면 메모리에 다 쌓기 전에 예외로 끊어낸다.
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                .followRedirect(false)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies);
    }
}
