package org.example.deokgilserver.domain.event.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.net.SsrfProtectedUrlValidator;
import org.example.deokgilserver.domain.event.presentation.dto.response.ExtractEventResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Slf4j
@Component
public class ClaudeEventExtractionClient implements EventExtractionClient {

    private static final int MAX_PAGE_LENGTH = 20_000;
    private static final String SYSTEM_PROMPT = """
            다음은 행사 안내 페이지의 HTML 일부입니다. 이 페이지에서 행사 정보를 추출해
            정해진 JSON 스키마 형식으로만 응답하세요. 설명, 인사말, 마크다운 코드블록 등
            스키마 이외의 텍스트는 절대 포함하지 마세요.

            - <script>, <style> 태그나 내비게이션/푸터 등 행사 내용과 무관한 부분은 무시하세요.
            - 페이지에 JSON-LD(application/ld+json)나 Open Graph 메타 태그로 된 행사 정보가
              있다면 그 값을 우선적으로 신뢰하세요.
            - 모르는 값은 null로 남기고, 절대로 추측해서 지어내지 마세요.
            - 날짜/시간은 ISO-8601 형식으로만 작성하세요. 정확한 시각까지 명시되어 있으면
              yyyy-MM-dd'T'HH:mm:ss 형식으로 채우고, 날짜만 확인되고 시각을 확신할 수
              없으면 시각을 지어내지 말고(00:00:00 포함) yyyy-MM-dd 형식(날짜만)으로
              반환하세요. 날짜조차 알 수 없으면 null로 두세요.
            - "2024년 3월 5일" 같은 한국어 표기나 상대적 표현("다음 주")은 페이지에 명시된
              연도와 함께 계산 가능한 경우에만 변환하고, 연도를 알 수 없으면 null로 두세요.
            """;

    private final WebClient webClient;
    private final AnthropicClient anthropicClient;
    private final SsrfProtectedUrlValidator ssrfUrlValidator;

    public ClaudeEventExtractionClient(
            WebClient.Builder webClientBuilder,
            SsrfProtectedUrlValidator ssrfUrlValidator,
            @Value("${claude.api.key}") String claudeApiKey
    ) {
        this.webClient = webClientBuilder.build();
        this.ssrfUrlValidator = ssrfUrlValidator;
        this.anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(claudeApiKey)
                .build();
    }

    @Override
    public ExtractEventResponse extract(String eventUrl) {
        URI validatedUri = ssrfUrlValidator.validate(eventUrl);
        String pageContent = fetchPage(validatedUri);
        ExtractedFields extracted = requestExtraction(pageContent);
        return toResponse(extracted, eventUrl);
    }

    private String fetchPage(URI eventUri) {
        try {
            String html = webClient.get()
                    .uri(eventUri)
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
            log.warn("행사 페이지 조회 실패: {}", e.getMessage());
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
            log.error("Claude 행사 정보 추출 API 호출 실패", e);
            throw new BusinessException(ErrorCode.AI_API_ERROR);
        }

        StopReason stopReason = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            log.warn("Claude가 행사 정보 추출을 거부했습니다.");
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            log.warn("Claude 응답이 max_tokens에 도달해 잘렸습니다.");
            throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
        }

        ExtractedFields extracted = response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(com.anthropic.models.messages.StructuredTextBlock::text)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXTRACTION_FAILED));

        log.info("Claude 행사 정보 추출 응답: title={}, startAt={}, endAt={}, placeName={}, address={}",
                extracted.title(), extracted.startAt(), extracted.endAt(), extracted.placeName(), extracted.address());

        return extracted;
    }

    private ExtractEventResponse toResponse(ExtractedFields extracted, String eventUrl) {
        if (extracted.title() == null || extracted.startAt() == null || extracted.endAt() == null) {
            throw new BusinessException(ErrorCode.MISSING_EVENT_INFO);
        }

        return new ExtractEventResponse(
                extracted.title(),
                validateDateOrDateTime(extracted.startAt()),
                validateDateOrDateTime(extracted.endAt()),
                extracted.placeName(),
                extracted.address(),
                eventUrl
        );
    }

    // Claude가 시각까지 아는 경우엔 전체 날짜+시각을, 날짜만 아는 경우엔 날짜만 반환하도록
    // 지시했으므로 두 형식 모두 유효하다. 시각을 임의로 채우지 않고(00:00:00 등 지어내지 않음)
    // 원본 문자열을 그대로 전달한다 - 실제 시각 채움 여부는 프론트/사용자가 결정한다.
    private String validateDateOrDateTime(String value) {
        try {
            LocalDateTime.parse(value);
            return value;
        } catch (DateTimeParseException e) {
            try {
                LocalDate.parse(value);
                return value;
            } catch (DateTimeParseException e2) {
                log.warn("Claude 추출 결과 파싱 실패: {}", value);
                throw new BusinessException(ErrorCode.EXTRACTION_FAILED);
            }
        }
    }

    private record ExtractedFields(
            @JsonPropertyDescription("추출된 행사명") String title,
            @JsonPropertyDescription("행사 시작 시간, ISO-8601 형식(yyyy-MM-dd'T'HH:mm:ss, 시각 모르면 yyyy-MM-dd), 알 수 없으면 null") String startAt,
            @JsonPropertyDescription("행사 종료 시간, ISO-8601 형식(yyyy-MM-dd'T'HH:mm:ss, 시각 모르면 yyyy-MM-dd), 알 수 없으면 null") String endAt,
            @JsonPropertyDescription("행사장 이름, 알 수 없으면 null") String placeName,
            @JsonPropertyDescription("행사장 주소, 알 수 없으면 null") String address
    ) {
    }
}
