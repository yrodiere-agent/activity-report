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
 * JIRA Activity Provider supporting multiple instances
 */
public class JiraProvider implements ActivityProvider {
    private final List<JiraInstance> instances;

    private record JiraInstance(String name, String url, String email, String token) {}

    public JiraProvider(AppConfig config) {
        this.instances = new ArrayList<>();

        config.providers().jira().ifPresent(jira -> {
            if (jira.enabled() && jira.instances() != null) {
                for (var instance : jira.instances()) {
                    instances.add(new JiraInstance(
                        instance.name(),
                        instance.url(),
                        instance.email(),
                        instance.token()
                    ));
                }
            }
        });
    }

    @Override
    public String getName() {
        return "JIRA (all instances)";
    }

    @Override
    public boolean isConfigured() {
        return !instances.isEmpty();
    }

    @Override
    public List<Activity> fetchActivities(Instant startDate, Instant endDate) throws Exception {
        List<Activity> allActivities = new ArrayList<>();

        for (JiraInstance instance : instances) {
            try {
                allActivities.addAll(fetchFromInstance(instance, startDate, endDate));
            } catch (Exception e) {
                System.err.println("Warning: Error fetching from JIRA instance " + instance.name + ": " + e.getMessage());
            }
        }

        return allActivities;
    }

    private List<Activity> fetchFromInstance(JiraInstance instance, Instant startDate, Instant endDate) throws Exception {
        List<Activity> activities = new ArrayList<>();

        // Create Basic Auth header
        var auth = Base64.getEncoder().encodeToString(
            (instance.email + ":" + instance.token).getBytes()
        );

        // Build JQL query
        long daysAgo = Duration.between(startDate, Instant.now()).toDays();
        var jql = String.format("assignee = currentUser() AND updated >= -%dd ORDER BY updated DESC", daysAgo + 1);

        // Make HTTP request using java.net.http
        var httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        var url = instance.url + "/rest/api/3/search?jql=" +
            java.net.URLEncoder.encode(jql, java.nio.charset.StandardCharsets.UTF_8) +
            "&fields=key,summary,status,created,updated,issuetype&maxResults=100";

        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Authorization", "Basic " + auth)
            .header("Accept", "application/json")
            .GET()
            .build();

        var response = httpClient.send(
            request,
            java.net.http.HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException("JIRA API returned status " + response.statusCode() + ": " + response.body());
        }

        // Parse JSON response
        var mapper = new ObjectMapper();
        var root = mapper.readTree(response.body());
        var issues = root.get("issues");

        if (issues != null && issues.isArray()) {
            for (JsonNode issue : issues) {
                Instant updatedTime = Instant.parse(issue.get("fields").get("updated").asText());

                // Skip if outside date range
                if (updatedTime.isBefore(startDate) || updatedTime.isAfter(endDate)) {
                    continue;
                }

                String key = issue.get("key").asText();
                String summary = issue.get("fields").get("summary").asText();
                String issueType = issue.get("fields").get("issuetype").get("name").asText();
                String status = issue.get("fields").get("status").get("name").asText();
                String issueUrl = instance.url + "/browse/" + key;

                Activity activity = new Activity(
                    "JIRA - " + instance.name,
                    "issue",
                    key + ": " + summary,
                    "Type: " + issueType + ", Status: " + status,
                    issueUrl,
                    updatedTime
                );

                activity.addMetadata("issueType", issueType);
                activity.addMetadata("status", status);
                activities.add(activity);
            }
        }

        return activities;
    }
}
