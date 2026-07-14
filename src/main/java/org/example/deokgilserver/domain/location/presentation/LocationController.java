package org.example.deokgilserver.domain.location.presentation;

import jakarta.validation.Valid;
import org.example.deokgilserver.domain.location.presentation.dto.request.LocationObfuscationRequest;
import org.example.deokgilserver.domain.location.presentation.dto.response.LocationObfuscationResponse;
import org.example.deokgilserver.domain.location.service.LocationObfuscator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocationController {

    private final LocationObfuscator locationObfuscator;

    public LocationController(LocationObfuscator locationObfuscator) {
        this.locationObfuscator = locationObfuscator;
    }

    @PostMapping("/api/locations/obfuscate")
    public LocationObfuscationResponse obfuscateLocation(@Valid @RequestBody LocationObfuscationRequest request) {
        return locationObfuscator.obfuscate(request);
    }
}
