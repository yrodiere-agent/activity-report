package activityreport.report;

import activityreport.config.AppConfig;
import activityreport.model.Activity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Processor for generating intelligent, grouped activity reports
 */
public class AIProcessor {
    private final String aiUrl;
    private final String modelName;
    private final ObjectMapper mapper;

    public AIProcessor(AppConfig config) {
        this.aiUrl = config.ai()
            .flatMap(ai -> ai.url())
            .orElse("http://localhost:8000/v1");
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());

        // Auto-detect model if not specified
        var configuredModel = config.ai()
            .flatMap(ai -> ai.model())
            .orElse("auto");
        if (configuredModel.equals("auto")) {
            this.modelName = detectModel();
        } else {
            this.modelName = configuredModel;
        }

        if (this.modelName != null) {
            System.err.println("Using AI model: " + this.modelName);
        }
    }

    private String detectModel() {
        try {
            var httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            var request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(aiUrl + "/models"))
                .header("Accept", "application/json")
                .GET()
                .build();

            var response = httpClient.send(
                request,
                java.net.http.HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                var root = mapper.readTree(response.body());
                var data = root.get("data");
                if (data != null && data.isArray() && !data.isEmpty()) {
                    var model = data.get(0).get("id").asText();
                    System.err.println("Auto-detected AI model: " + model);
                    return model;
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not auto-detect AI model: " + e.getMessage());
        }
        return null;
    }

    public String generateGroupedReport(List<Activity> activities, Instant startDate, Instant endDate) {
        if (modelName == null) {
            System.err.println("AI model not available, falling back to simple markdown");
            return MarkdownReportGenerator.generateSimple(activities, startDate, endDate);
        }

        try {
            // Prepare activities as JSON
            String activitiesJson = serializeActivities(activities);

            // Build prompt
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
                .withZone(ZoneId.systemDefault());

            String prompt = """
                You are analyzing a developer's activity from %s to %s.

                Your task is to create a concise, achievement-oriented activity report. \
                Group related activities into coherent achievements. For example, an issue, pull request, \
                and commits related to the same feature should be grouped together as one achievement.

                Guidelines:
                - Focus on WHAT was accomplished, not just listing actions
                - Group related items (e.g., issue + PR + commits = one achievement)
                - Use clear, professional language
                - Include relevant links from the activities
                - Be concise but informative

                Activities data (JSON):
                %s

                Generate a markdown report with:
                1. A brief summary paragraph (2-3 sentences)
                2. Main achievements grouped by theme (use ## headers)
                3. Include links to relevant issues/PRs where available
                4. Keep it professional and concise

                Return ONLY the markdown report, nothing else.
                """.formatted(
                    dateFormatter.format(startDate),
                    dateFormatter.format(endDate),
                    activitiesJson
                );

            // Make API call
            return callAIModel(prompt);

        } catch (Exception e) {
            System.err.println("Warning: AI processing failed: " + e.getMessage());
            System.err.println("Falling back to simple markdown generation");
            return MarkdownReportGenerator.generateSimple(activities, startDate, endDate);
        }
    }

    private String serializeActivities(List<Activity> activities) throws Exception {
        // Create simplified JSON representation of activities
        List<Map<String, Object>> simplified = activities.stream()
            .sorted(Comparator.comparing(Activity::timestamp).reversed())
            .map(activity -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("source", activity.source());
                map.put("type", activity.type());
                map.put("title", activity.title());
                if (activity.description() != null && !activity.description().isEmpty()) {
                    // Truncate very long descriptions
                    String desc = activity.description();
                    if (desc.length() > 500) {
                        desc = desc.substring(0, 500) + "...";
                    }
                    map.put("description", desc);
                }
                if (activity.url() != null && !activity.url().isEmpty()) {
                    map.put("url", activity.url());
                }
                map.put("timestamp", activity.timestamp().toString());
                return map;
            })
            .collect(Collectors.toList());

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(simplified);
    }

    private String callAIModel(String prompt) throws Exception {
        var httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        // Build request body
        var requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("model", modelName);
        requestBody.put("messages", List.of(
            Map.of("role", "user", "content", prompt)
        ));
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);

        var requestJson = mapper.writeValueAsString(requestBody);

        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(aiUrl + "/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestJson))
            .timeout(Duration.ofMinutes(2))
            .build();

        var response = httpClient.send(
            request,
            java.net.http.HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException("AI API returned status " + response.statusCode() + ": " + response.body());
        }

        // Parse response
        var root = mapper.readTree(response.body());
        var choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            var message = choices.get(0).get("message");
            if (message != null) {
                return message.get("content").asText();
            }
        }

        throw new IOException("Unexpected AI API response format");
    }
}
