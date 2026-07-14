package org.example.deokgilserver.common.weather;

import org.example.deokgilserver.common.location.Coordinate;

import java.time.LocalDateTime;

public interface WeatherClient {

    WeatherCondition getForecast(Coordinate coordinate, LocalDateTime targetTime);
}
