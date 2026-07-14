package org.example.deokgilserver.domain.checklist.service;

import org.example.deokgilserver.common.weather.WeatherCondition;

import java.util.List;

public interface ChecklistExtractionClient {

    List<String> generateItems(String eventTitle, WeatherCondition weather);
}
