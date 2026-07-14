package org.example.deokgilserver.common.location;

public interface GeocodingClient {

    Coordinate geocode(String address);
}
