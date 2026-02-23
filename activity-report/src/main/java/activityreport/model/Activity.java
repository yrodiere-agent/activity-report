package activityreport.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Core activity data model representing a single activity from any provider
 */
public record Activity(
    String source,        // e.g., "GitHub.com", "JIRA - Hibernate"
    String type,          // e.g., "commit", "issue", "pr", "message"
    String title,
    String description,
    String url,
    Instant timestamp,
    Map<String, Object> metadata
) implements Comparable<Activity> {

    public Activity(String source, String type, String title, String description, String url, Instant timestamp) {
        this(source, type, title, description, url, timestamp, new HashMap<>());
    }

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    @Override
    public int compareTo(Activity other) {
        return this.timestamp.compareTo(other.timestamp);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", source, type, title);
    }
}
