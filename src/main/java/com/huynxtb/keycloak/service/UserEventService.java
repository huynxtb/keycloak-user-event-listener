package com.huynxtb.keycloak.service;

import org.jboss.logging.Logger;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class UserEventService {

    private static final Logger log = Logger.getLogger(UserEventService.class);
    private final HttpClient httpClient;
    private final URI webhookUri;

    /**
     * Creates the service, reading the webhook URL from the WEBHOOK_URL env var.
     */
    public UserEventService() {
        String url = System.getenv("WEBHOOK_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Environment variable WEBHOOK_URL must be set");
        }
        this.webhookUri = URI.create(url);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Posts the given JSON payload to the configured webhook.
     */
    public void postEvent(String jsonPayload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(webhookUri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                log.infof("Webhook POST succeeded (%d): %s", status, resp.body());
            } else {
                log.errorf("Webhook POST failed with HTTP %d: %s", status, resp.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error sending event to webhook", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
