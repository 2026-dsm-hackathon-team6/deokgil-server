package org.example.deokgilserver.common.weather;

// AI 프롬프트/응답에 쓸 날씨 상태. 기상청 단기예보(getVilageFcst)의 강수형태(PTY)/하늘상태(SKY)
// 코드값을 우리 서비스가 다루는 단순한 분류로 좁혀서 매핑한다.
public enum WeatherCondition {

    CLEAR("맑음"),
    CLOUDY("흐림"),
    RAIN("비"),
    SNOW("눈"),
    UNKNOWN("알 수 없음");

    private final String label;

    WeatherCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * PTY(강수형태)가 우선이다 — 강수가 있으면 하늘상태(맑음/흐림)는 의미가 없기 때문이다.
     * PTY 코드(단기예보 기준): 0=없음, 1=비, 2=비/눈, 3=눈, 4=소나기.
     * PTY=0(강수없음)일 때만 SKY(1=맑음, 3=구름많음, 4=흐림)로 판단한다.
     * 낙뢰(LGT)·안개는 단기예보(getVilageFcst) 응답에 없는 항목이라 판단 대상에서 제외된다
     * (초단기예보에만 있고, 그마저도 6시간 이내 예보에만 유효해 며칠 뒤 행사에는 못 쓴다).
     */
    public static WeatherCondition fromKmaCodes(String pty, String sky) {
        if (pty != null) {
            switch (pty) {
                case "1", "4", "5", "6" -> {
                    return RAIN;
                }
                case "2" -> {
                    return RAIN; // 비/눈 혼합은 RAIN으로 근사
                }
                case "3", "7" -> {
                    return SNOW;
                }
                default -> {
                    // 0(없음) 등은 아래 SKY 판단으로 넘어간다.
                }
            }
        }
        if (sky != null) {
            return switch (sky) {
                case "1" -> CLEAR;
                case "3", "4" -> CLOUDY;
                default -> UNKNOWN;
            };
        }
        return UNKNOWN;
    }
}
