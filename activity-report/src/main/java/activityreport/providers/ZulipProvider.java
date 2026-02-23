package activityreport.providers;

import activityreport.config.AppConfig;
import activityreport.model.Activity;
import activityreport.model.ActivityProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Zulip Activity Provider supporting multiple instances
 */
public class ZulipProvider implements ActivityProvider {
    private final List<ZulipInstance> instances;

    private record ZulipInstance(String url, String email, String apiKey) {}

    public ZulipProvider(AppConfig config) {
        this.instances = new ArrayList<>();

        config.providers().zulip().ifPresent(zulip -> {
            if (zulip.enabled() && zulip.instances() != null) {
                for (var instance : zulip.instances()) {
                    instances.add(new ZulipInstance(
                        instance.url(),
                        instance.email(),
                        instance.apiKey()
                    ));
                }
            }
        });
    }

    @Override
    public String getName() {
        return "Zulip (all instances)";
    }

    @Override
    public boolean isConfigured() {
        return !instances.isEmpty();
    }

    @Override
    public List<Activity> fetchActivities(Instant startDate, Instant endDate) throws Exception {
        List<Activity> allActivities = new ArrayList<>();

        for (ZulipInstance instance : instances) {
            try {
                allActivities.addAll(fetchFromInstance(instance, startDate, endDate));
            } catch (Exception e) {
                System.err.println("Warning: Error fetching from Zulip instance " + instance.url + ": " + e.getMessage());
            }
        }

        return allActivities;
    }

    private List<Activity> fetchFromInstance(ZulipInstance instance, Instant startDate, Instant endDate) throws Exception {
        List<Activity> activities = new ArrayList<>();

        // Get current user ID first
        var httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        var auth = Base64.getEncoder().encodeToString(
            (instance.email + ":" + instance.apiKey).getBytes()
        );

        // Get current user
        var userRequest = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(instance.url + "/api/v1/users/me"))
            .header("Authorization", "Basic " + auth)
            .GET()
            .build();

        var userResponse = httpClient.send(
            userRequest,
            java.net.http.HttpResponse.BodyHandlers.ofString()
        );

        if (userResponse.statusCode() != 200) {
            throw new IOException("Zulip API returned status " + userResponse.statusCode());
        }

        var mapper = new ObjectMapper();
        var userRoot = mapper.readTree(userResponse.body());
        int userId = userRoot.get("user_id").asInt();

        // Fetch messages sent by this user
        var narrow = String.format("[{\"operator\":\"sender\",\"operand\":%d}]", userId);
        var messagesUrl = instance.url + "/api/v1/messages?anchor=newest&num_before=1000&num_after=0&narrow=" +
            java.net.URLEncoder.encode(narrow, java.nio.charset.StandardCharsets.UTF_8);

        var messagesRequest = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(messagesUrl))
            .header("Authorization", "Basic " + auth)
            .GET()
            .build();

        var messagesResponse = httpClient.send(
            messagesRequest,
            java.net.http.HttpResponse.BodyHandlers.ofString()
        );

        if (messagesResponse.statusCode() != 200) {
            throw new IOException("Zulip messages API returned status " + messagesResponse.statusCode());
        }

        var messagesRoot = mapper.readTree(messagesResponse.body());
        var messages = messagesRoot.get("messages");

        if (messages != null && messages.isArray()) {
            for (JsonNode message : messages) {
                long timestamp = message.get("timestamp").asLong();
                Instant messageTime = Instant.ofEpochSecond(timestamp);

                // Skip if outside date range
                if (messageTime.isBefore(startDate) || messageTime.isAfter(endDate)) {
                    continue;
                }

                String subject = message.get("subject").asText();
                String content = message.get("content").asText();
                String messageType = message.get("type").asText();
                int messageId = message.get("id").asInt();

                // Build message URL
                String streamName = messageType.equals("stream") ?
                    message.get("display_recipient").asText() : "private";
                String messageUrl = instance.url + "/#narrow/stream/" + streamName + "/topic/" + subject + "/near/" + messageId;

                Activity activity = new Activity(
                    "Zulip - " + instance.url.replace("https://", "").replace("http://", ""),
                    "message",
                    "Message in " + streamName + ": " + subject,
                    content.length() > 200 ? content.substring(0, 200) + "..." : content,
                    messageUrl,
                    messageTime
                );

                activity.addMetadata("messageType", messageType);
                activities.add(activity);
            }
        }

        return activities;
    }
}
