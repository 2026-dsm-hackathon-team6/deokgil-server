package org.example.deokgilserver.common.push;

public interface PushNotificationClient {

    void send(String deviceToken, String title, String body);
}
