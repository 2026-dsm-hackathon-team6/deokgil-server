package org.example.deokgilserver.domain.checklist.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.weather.WeatherCondition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClaudeChecklistExtractionClient implements ChecklistExtractionClient {

    private static final String SYSTEM_PROMPT = """
            사용자가 참여할 행사명과 그 날의 날씨가 주어집니다.
            해당 행사에 실제로 챙기면 좋은 준비물 목록을 한국어로 간단히 나열하세요.
            신분증, 지갑처럼 어떤 외출에나 해당하는 일반적인 항목은 제외하고,
            행사 성격과 날씨에 특화된 항목만 포함하세요.
            """;

    private final AnthropicClient anthropicClient;

    public ClaudeChecklistExtractionClient(@Value("${claude.api.key}") String claudeApiKey) {
        this.anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(claudeApiKey)
                .build();
    }

    @Override
    public List<String> generateItems(String eventTitle, WeatherCondition weather) {
        // eventTitle은 사용자가 직접 입력하거나 외부 페이지에서 AI로 추출된 값이라 신뢰할 수 없는
        // 문자열이다. system(SYSTEM_PROMPT)이 아니라 user 메시지 쪽에 넣어서, 프롬프트 인젝션
        // 문구가 섞여 들어와도 지시사항(system) 자체를 덮어쓰지 못하게 한다. 이 클라이언트는
        // 툴 호출/외부 액션이 전혀 없는 순수 텍스트 생성이라 인젝션에 성공해도 최악의 경우
        // 이상한 준비물 목록이 나오는 정도이고, 다른 시스템에 영향을 주진 못한다.
        String userMessage = "행사명: %s\n날씨: %s".formatted(eventTitle, weather.label());

        StructuredMessageCreateParams<GeneratedChecklist> params = MessageCreateParams.builder()
                .model("claude-opus-4-8")
                .maxTokens(1024L)
                .system(SYSTEM_PROMPT)
                .outputConfig(GeneratedChecklist.class)
                .addUserMessage(userMessage)
                .build();

        StructuredMessage<GeneratedChecklist> response;
        try {
            response = anthropicClient.messages().create(params);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }

        if (response.stopReason().filter(reason -> reason.equals(com.anthropic.models.messages.StopReason.REFUSAL)).isPresent()) {
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }

        GeneratedChecklist generated = response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(com.anthropic.models.messages.StructuredTextBlock::text)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_GENERATION_FAILED));

        if (generated.items() == null || generated.items().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }

        return generated.items();
    }

    private record GeneratedChecklist(
            @JsonPropertyDescription("추천 준비물 목록") List<String> items
    ) {
    }
}
