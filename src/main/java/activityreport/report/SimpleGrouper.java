package activityreport.report;

import activityreport.model.ActionCategory;
import activityreport.model.Activity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple URL-based activity grouper (fallback when AI is not available).
 * <p>
 * Substantive activities (CODE, REVIEW) each anchor their own group.
 * Minor activities (DISCUSS, CHORE) are absorbed into the best-connected
 * substantive group, or grouped among themselves if unconnected.
 */
public class SimpleGrouper {

    private static int activityPriorityRank(Activity a) {
        return switch (a.actionCategory()) {
            case CODE -> Boolean.TRUE.equals(a.metadata().get("targetsDefaultBranch")) ? 0 : 1;
            case REVIEW -> 2;
            case DISCUSS -> 3;
            case CHORE -> 4;
        };
    }

    private static final Comparator<Activity> ACTIVITY_PRIORITY =
        Comparator.comparingInt(SimpleGrouper::activityPriorityRank);

    private static boolean isMinor(Activity a) {
        return a.actionCategory() == ActionCategory.DISCUSS || a.actionCategory() == ActionCategory.CHORE;
    }

    public static List<ActivityGroup> groupActivities(List<Activity> activities) {
        // Build URL -> Activities map
        Map<String, Set<Activity>> urlToActivities = new HashMap<>();

        for (Activity activity : activities) {
            Set<String> urls = new HashSet<>();
            if (activity.url() != null && !activity.url().isEmpty()) {
                urls.add(activity.url());
            }
            if (activity.contentUrls() != null) {
                urls.addAll(activity.contentUrls());
            }
            for (String url : urls) {
                urlToActivities.computeIfAbsent(url, k -> new HashSet<>()).add(activity);
            }
        }

        // Build direct connections between activities via shared URLs
        Map<Activity, Set<Activity>> connections = new HashMap<>();
        for (Set<Activity> related : urlToActivities.values()) {
            if (related.size() < 2) {
                continue;
            }
            for (Activity a : related) {
                for (Activity b : related) {
                    if (a != b) {
                        connections.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                    }
                }
            }
        }

        // Each substantive activity anchors its own group
        Map<Activity, List<Activity>> groups = new LinkedHashMap<>();
        Set<Activity> assigned = new HashSet<>();
        for (Activity a : activities) {
            if (!isMinor(a)) {
                groups.put(a, new ArrayList<>());
                assigned.add(a);
            }
        }

        // Assign minor activities to the best-connected substantive activity
        for (Activity m : activities) {
            if (!isMinor(m)) {
                continue;
            }
            Set<Activity> connected = connections.getOrDefault(m, Set.of());
            Optional<Activity> bestSubstantive = connected.stream()
                .filter(c -> !isMinor(c))
                .min(ACTIVITY_PRIORITY);

            if (bestSubstantive.isPresent()) {
                groups.get(bestSubstantive.get()).add(m);
                assigned.add(m);
            }
        }

        // Group remaining minor activities among themselves using transitive URL overlap
        List<Activity> unassignedMinor = activities.stream()
            .filter(a -> !assigned.contains(a))
            .toList();

        if (!unassignedMinor.isEmpty()) {
            Map<Activity, Set<Activity>> minorActivityToGroup = new HashMap<>();
            for (Activity m : unassignedMinor) {
                Set<Activity> connected = connections.getOrDefault(m, Set.of());
                for (Activity other : connected) {
                    if (assigned.contains(other) || !isMinor(other)) {
                        continue;
                    }
                    Set<Activity> groupA = minorActivityToGroup.computeIfAbsent(m, k -> new HashSet<>(Set.of(k)));
                    Set<Activity> groupB = minorActivityToGroup.computeIfAbsent(other, k -> new HashSet<>(Set.of(k)));
                    if (groupA != groupB) {
                        Set<Activity> merged = new HashSet<>(groupA);
                        merged.addAll(groupB);
                        for (Activity act : merged) {
                            minorActivityToGroup.put(act, merged);
                        }
                    }
                }
            }

            Set<Set<Activity>> uniqueMinorGroups = new HashSet<>(minorActivityToGroup.values());
            for (Set<Activity> groupSet : uniqueMinorGroups) {
                List<Activity> sorted = groupSet.stream()
                    .sorted(ACTIVITY_PRIORITY)
                    .collect(Collectors.toCollection(ArrayList::new));
                groups.put(sorted.removeFirst(), sorted);
                assigned.addAll(groupSet);
            }

            for (Activity m : unassignedMinor) {
                if (!assigned.contains(m)) {
                    groups.put(m, List.of());
                }
            }
        }

        List<ActivityGroup> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            result.add(new ActivityGroup(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}
