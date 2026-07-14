package org.example.deokgilserver.domain.schedule.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleType;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.GenerateScheduleRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ClaudeScheduleExtractionClient implements ScheduleExtractionClient {

    private static final String SYSTEM_PROMPT = """
            사용자가 참여할 행사 정보와 목적, 우선순위, 이동 수단이 주어집니다.
            행사 시작 시간과 종료 시간 사이(포함)에서만 일정을 배치하고, 일정끼리 시간이 겹치지 않게 하세요.
            일정 종류(type)는 반드시 다음 중 하나여야 합니다: MOVE, WAITING, GOODS, VISIT, PERFORMANCE, RETURN, ETC.
            시간은 ISO-8601(yyyy-MM-dd'T'HH:mm:ss) 형식으로 작성하세요.
            """;

    private final AnthropicClient anthropicClient;

    public ClaudeScheduleExtractionClient(@Value("${claude.api.key}") String claudeApiKey) {
        this.anthropicClient = AnthropicOkHttpClient.builder()
                .apiKey(claudeApiKey)
                .build();
    }

    @Override
    public List<GeneratedSchedule> generate(Event event, GenerateScheduleRequest request) {
        // 행사명/장소/목적 등은 사용자 입력 또는 외부 URL에서 AI로 추출된 값이라 신뢰할 수 없다.
        // system(SYSTEM_PROMPT)이 아니라 user 메시지에 담아, 여기 섞여 들어온 지시문이
        // system 프롬프트의 시간 범위/겹침 방지 규칙을 덮어쓰지 못하게 한다. 다만 이 규칙은
        // "지켜달라는 요청"일 뿐 강제(guardrail)가 아니다 — ScheduleServiceImpl.generate()는
        // 현재 AI 응답을 코드로 재검증하지 않고 그대로 저장하므로(범위/겹침 체크는 update() 쪽인
        // applyUpdate/validateNoOverlap에만 있음), 여기서 어긋난 시간대가 나오면 그대로 저장된다.
        String userMessage = """
                행사명: %s
                행사 시작: %s
                행사 종료: %s
                행사 장소: %s
                목적: %s
                우선순위: %s
                이동 수단: %s
                """.formatted(
                event.getTitle(), event.getStartAt(), event.getEndAt(),
                event.getPlaceName() == null ? "미정" : event.getPlaceName(),
                request.purpose(),
                formatPriorities(request.priorities()),
                request.transportation()
        );

        StructuredMessageCreateParams<GeneratedScheduleList> params = MessageCreateParams.builder()
                .model("claude-opus-4-8")
                .maxTokens(2048L)
                .system(SYSTEM_PROMPT)
                .outputConfig(GeneratedScheduleList.class)
                .addUserMessage(userMessage)
                .build();

        StructuredMessage<GeneratedScheduleList> response;
        try {
            response = anthropicClient.messages().create(params);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }

        if (response.stopReason().filter(reason -> reason.equals(com.anthropic.models.messages.StopReason.REFUSAL)).isPresent()) {
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }

        GeneratedScheduleList generated = response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(com.anthropic.models.messages.StructuredTextBlock::text)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_GENERATION_FAILED));

        if (generated.schedules() == null || generated.schedules().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }

        try {
            return generated.schedules().stream()
                    .map(item -> new GeneratedSchedule(
                            ScheduleType.valueOf(item.type()),
                            item.title(),
                            LocalDateTime.parse(item.startAt()),
                            LocalDateTime.parse(item.endAt())
                    ))
                    .toList();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }
    }

    private String formatPriorities(List<ScheduleType> priorities) {
        if (priorities == null || priorities.isEmpty()) {
            return "없음";
        }
        return priorities.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    private record GeneratedScheduleList(
            @JsonPropertyDescription("생성된 일정 목록") List<GeneratedScheduleItem> schedules
    ) {
    }

    private record GeneratedScheduleItem(
            @JsonPropertyDescription("일정 종류: MOVE, WAITING, GOODS, VISIT, PERFORMANCE, RETURN, ETC 중 하나") String type,
            @JsonPropertyDescription("일정 제목") String title,
            @JsonPropertyDescription("시작 시간, ISO-8601 형식") String startAt,
            @JsonPropertyDescription("종료 시간, ISO-8601 형식") String endAt
    ) {
    }
}
