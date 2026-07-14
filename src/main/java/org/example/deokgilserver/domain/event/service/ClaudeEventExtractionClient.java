package org.example.deokgilserver.domain.event.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.domain.event.presentation.dto.response.ExtractEventResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;

@Component
public class ClaudeEventExtractionClient implements EventExtractionClient {

    private static final int MAX_PAGE_LENGTH = 20_000;
    private static final String SYSTEM_PROMPT = """
            다음은 행사 안내 페이지의 HTML 일부입니다. 이 페이지에서 행사 정보를 추출하세요.
            모르는 값은 null로 남기세요. 날짜는 ISO-8601(yyyy-MM-dd'T'HH:mm:ss) 형식으로 작성하세요.
            """;

    private final WebClient webClient;
    private final AnthropicClient anthropicClient;

    public ClaudeEventExtractionClient(
            WebClient.Builder webClientBuilder,
            @Value("${claude.api.key}") String claudeApiKey
    ) {
        this.webClient = webClientBuilder.build();
        this.anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(claudeApiKey)
                .build();
    }

    @Override
    public ExtractEventResponse extract(String eventUrl) {
        validateUrl(eventUrl);
        String pageContent = fetchPage(eventUrl);
        ExtractedFields extracted = requestExtraction(pageContent);
        return toResponse(extracted, eventUrl);
    }

    private void validateUrl(String eventUrl) {
        try {
            URI uri = new URI(eventUrl);
            if (uri.getScheme() == null || uri.getHost() == null
                    || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
                throw new BusinessException(ErrorCode.INVALID_URL);
            }
        } catch (URISyntaxException e) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
    }

    private String fetchPage(String eventUrl) {
        try {
            String html = webClient.get()
                    .uri(eventUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (html == null || html.isBlank()) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_SITE);
            }
            return html.length() > MAX_PAGE_LENGTH ? html.substring(0, MAX_PAGE_LENGTH) : html;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_SITE);
        }
    }

    private ExtractedFields requestExtraction(String pageContent) {
        StructuredMessageCreateParams<ExtractedFields> params = MessageCreateParams.builder()
                .model("claude-opus-4-8")
                .maxTokens(2048L)
                .system(SYSTEM_PROMPT)
                .outputConfig(ExtractedFields.class)
                .addUserMessage(pageContent)
                .build();

        StructuredMessage<ExtractedFields> response;
        try {
            response = anthropicClient.messages().create(params);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_API_ERROR);
        }

        if (response.stopReason().filter(reason -> reason.equals(com.anthropic.models.messages.StopReason.REFUSAL)).isPresent()) {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
        }

        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(com.anthropic.models.messages.StructuredTextBlock::text)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXTRACTION_FAILED));
    }

    private ExtractEventResponse toResponse(ExtractedFields extracted, String eventUrl) {
        if (extracted.title() == null || extracted.startAt() == null || extracted.endAt() == null) {
            throw new BusinessException(ErrorCode.MISSING_EVENT_INFO);
        }

        try {
            return new ExtractEventResponse(
                    extracted.title(),
                    LocalDateTime.parse(extracted.startAt()),
                    LocalDateTime.parse(extracted.endAt()),
                    extracted.placeName(),
                    extracted.address(),
                    eventUrl
            );
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
        }
    }

    private record ExtractedFields(
            @JsonPropertyDescription("추출된 행사명") String title,
            @JsonPropertyDescription("행사 시작 시간, ISO-8601 형식, 알 수 없으면 null") String startAt,
            @JsonPropertyDescription("행사 종료 시간, ISO-8601 형식, 알 수 없으면 null") String endAt,
            @JsonPropertyDescription("행사장 이름, 알 수 없으면 null") String placeName,
            @JsonPropertyDescription("행사장 주소, 알 수 없으면 null") String address
    ) {
    }
}
