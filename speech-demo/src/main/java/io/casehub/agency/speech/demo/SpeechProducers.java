package io.casehub.agency.speech.demo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SpeechProducers {

    private static final String DEFAULT_MODEL = "claude-haiku-4-5@20251001";

    @Produces
    @ApplicationScoped
    io.casehub.platform.agent.AgentProvider agentProvider() {
        String region    = System.getenv("CLOUD_ML_REGION");
        String projectId = System.getenv("ANTHROPIC_VERTEX_PROJECT_ID");
        if (region == null || projectId == null) {
            throw new IllegalStateException(
                    "CLOUD_ML_REGION and ANTHROPIC_VERTEX_PROJECT_ID required for Vertex AI");
        }
        var httpClient = java.net.http.HttpClient.newHttpClient();
        var gson       = new com.google.gson.Gson();

        return new io.casehub.platform.agent.AgentProvider() {
            @Override
            public io.smallrye.mutiny.Multi<io.casehub.platform.agent.AgentEvent> invoke(
                    io.casehub.platform.agent.AgentSessionConfig config) {
                return io.smallrye.mutiny.Multi.createFrom().item(() -> {
                    String modelId = config.model() != null ? config.model() : DEFAULT_MODEL;
                    String text = callVertex(httpClient, gson, region, projectId, modelId,
                                             config.systemPrompt(), config.userPrompt());
                    return (io.casehub.platform.agent.AgentEvent)
                                   new io.casehub.platform.agent.AgentEvent.TextDelta(text);
                });
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(
                    io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException("Use invoke() for avatar");
            }
        };
    }

    private static String callVertex(java.net.http.HttpClient httpClient, com.google.gson.Gson gson,
                                     String region, String projectId, String modelId,
                                     String systemPrompt, String userPrompt) {
        try {
            String token = getAccessToken();
            String url = "https://" + region + "-aiplatform.googleapis.com/v1/projects/"
                         + projectId + "/locations/" + region
                         + "/publishers/anthropic/models/" + modelId + ":rawPredict";

            var body = new com.google.gson.JsonObject();
            body.addProperty("anthropic_version", "vertex-2023-10-16");
            body.addProperty("max_tokens", 80);
            body.addProperty("system", systemPrompt);
            var messages = new com.google.gson.JsonArray();
            var msg      = new com.google.gson.JsonObject();
            msg.addProperty("role", "user");
            msg.addProperty("content", userPrompt);
            messages.add(msg);
            body.add("messages", messages);

            var request = java.net.http.HttpRequest.newBuilder()
                                                   .uri(java.net.URI.create(url))
                                                   .header("Authorization", "Bearer " + token)
                                                   .header("Content-Type", "application/json")
                                                   .POST(java.net.http.HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                                                   .build();

            var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Vertex API error " + response.statusCode() + ": " + response.body());
            }

            var responseJson = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            return responseJson.getAsJsonArray("content")
                               .get(0).getAsJsonObject()
                               .get("text").getAsString();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Vertex AI call failed: " + e.getMessage(), e);
        }
    }

    private static String getAccessToken() {
        try {
            var credentials = com.google.auth.oauth2.GoogleCredentials.getApplicationDefault()
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get access token via ADC: " + e.getMessage(), e);
        }
    }
}
