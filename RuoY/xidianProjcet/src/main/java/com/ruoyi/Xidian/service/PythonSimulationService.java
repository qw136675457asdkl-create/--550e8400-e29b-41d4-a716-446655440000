package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.config.SimulationPythonProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class PythonSimulationService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final Duration requestTimeout;
    private final Duration taskPollingTimeout;
    private final Duration pollInterval;

    public PythonSimulationService(
            ObjectMapper objectMapper,
            SimulationPythonProperties simulationPythonProperties
    ) {
        this.objectMapper = objectMapper;
        this.apiBaseUrl = trimTrailingSlash(simulationPythonProperties.getApiBaseUrl());
        this.requestTimeout = Duration.ofSeconds(simulationPythonProperties.getApiReadTimeoutSeconds());
        this.taskPollingTimeout = Duration.ofSeconds(simulationPythonProperties.getApiTaskTimeoutSeconds());
        this.pollInterval = Duration.ofMillis(simulationPythonProperties.getApiPollIntervalMillis());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(simulationPythonProperties.getApiConnectTimeoutSeconds()))
                .build();
    }

    public JsonNode getHealth() {
        return sendRequest("GET", "/health", null);
    }

    public JsonNode listSimulations() {
        return sendRequest("GET", "/api/simulations", null);
    }

    public JsonNode createSimulation(Object requestBody) {
        try {
            String payloadText = objectMapper.writeValueAsString(requestBody);
            return createSimulation(objectMapper.readTree(payloadText));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize Python simulation request.",
                    exception
            );
        }
    }

    public JsonNode createSimulation(JsonNode requestBody) {
        return sendRequest("POST", "/api/simulations", requestBody);
    }

    public JsonNode getSimulation(String taskId) {
        String encodedTaskId = URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        return sendRequest("GET", "/api/simulations/" + encodedTaskId, null);
    }

    public JsonNode submitAndWait(Object requestBody) {
        JsonNode submitResponse = createSimulation(requestBody);
        String pythonTaskId = readRequiredText(submitResponse, "task_id");
        JsonNode taskResponse = waitForSimulationCompletion(pythonTaskId);
        String status = readText(taskResponse, "status");
        if ("completed".equalsIgnoreCase(status)) {
            return taskResponse;
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, extractMessage(taskResponse));
    }

    public JsonNode waitForSimulationCompletion(String taskId) {
        long deadlineNanos = System.nanoTime() + taskPollingTimeout.toNanos();
        JsonNode latestResponse = getSimulation(taskId);
        while (isTaskRunning(latestResponse)) {
            if (System.nanoTime() >= deadlineNanos) {
                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "Timed out while waiting for Python simulation task: " + taskId
                );
            }
            sleep(pollInterval);
            latestResponse = getSimulation(taskId);
        }
        return latestResponse;
    }

    private JsonNode sendRequest(String method, String path, JsonNode requestBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + path))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json");

            if ("POST".equalsIgnoreCase(method)) {
                String payloadText = objectMapper.writeValueAsString(requestBody);
                builder.header("Content-Type", "application/json");
                builder.POST(HttpRequest.BodyPublishers.ofString(payloadText, StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return handleResponse(response);
        } catch (ConnectException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to connect to Python simulation API: " + apiBaseUrl,
                    exception
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to call Python simulation API: " + exception.getMessage(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Interrupted while calling Python simulation API.",
                    exception
            );
        }
    }

    private JsonNode handleResponse(HttpResponse<String> response) throws IOException {
        String responseBody = response.body() == null ? "" : response.body().trim();
        JsonNode responseJson;
        if (responseBody.isBlank()) {
            responseJson = objectMapper.createObjectNode();
        } else {
            responseJson = objectMapper.readTree(responseBody);
        }

        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return responseJson;
        }

        HttpStatus status = HttpStatus.valueOf(statusCode);
        throw new ResponseStatusException(status, extractMessage(responseJson));
    }

    private boolean isTaskRunning(JsonNode responseJson) {
        String status = readText(responseJson, "status");
        return "queued".equalsIgnoreCase(status) || "running".equalsIgnoreCase(status);
    }

    private String readRequiredText(JsonNode responseJson, String fieldName) {
        String value = readText(responseJson, fieldName);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Python simulation API response missing field: " + fieldName
            );
        }
        return value;
    }

    private String readText(JsonNode responseJson, String fieldName) {
        if (responseJson != null && responseJson.hasNonNull(fieldName)) {
            return responseJson.get(fieldName).asText();
        }
        return null;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Interrupted while waiting for Python simulation task.",
                    exception
            );
        }
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String extractMessage(JsonNode responseJson) {
        if (responseJson.hasNonNull("error")) {
            return responseJson.get("error").asText();
        }
        if (responseJson.hasNonNull("message")) {
            return responseJson.get("message").asText();
        }
        if (responseJson.hasNonNull("detail")) {
            return responseJson.get("detail").asText();
        }
        if (responseJson.hasNonNull("status")) {
            return responseJson.get("status").asText();
        }
        return "Python simulation API failed.";
    }
    private String getFilePath(JsonNode taskResponse, String fileKey) {
        JsonNode filesNode = taskResponse.path("files");
        return filesNode.path(fileKey).asText(null);
    }
}
