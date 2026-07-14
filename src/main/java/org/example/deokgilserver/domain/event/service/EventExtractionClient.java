package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.domain.event.presentation.dto.response.ExtractEventResponse;

public interface EventExtractionClient {

    ExtractEventResponse extract(String eventUrl);
}
