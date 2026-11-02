package activityreport.report;

import activityreport.model.ActionCategory;
import activityreport.model.Activity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple URL-based activity grouper (fallback when AI is not available).
 * <p>
 * Activities sharing URLs are grouped together via transitive closure,
 * with one constraint: a group may not contain both CODE and REVIEW
 * activities, since those represent different kinds of work.
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

    private static boolean wouldMixCodeAndReview(Set<Activity> group) {
        boolean hasCode = false;
        boolean hasReview = false;
        for (Activity a : group) {
            if (a.actionCategory() == ActionCategory.CODE) hasCode = true;
            else if (a.actionCategory() == ActionCategory.REVIEW) hasReview = true;
            if (hasCode && hasReview) return true;
        }
        return false;
    }

    public static List<ActivityGroup> groupActivities(List<Activity> activities) {
        // Build URL -> Activities map
        Map<String, Set<Activity>> urlToActivities = new HashMap<>();

        for (Activity activity : activities) {
            Set<String> urls = new HashSet<>();

            // Add primary URL
            if (activity.url() != null && !activity.url().isEmpty()) {
                urls.add(activity.url());
            }

            // Add content URLs
            if (activity.contentUrls() != null) {
                urls.addAll(activity.contentUrls());
            }

            // Add to map for each URL
            for (String url : urls) {
                urlToActivities.computeIfAbsent(url, k -> new HashSet<>()).add(activity);
            }
        }

        // Merge groups that share activities (transitive closure),
        // but never merge two groups if the result would contain
        // both CODE and REVIEW activities.
        Map<Activity, Set<Activity>> activityToGroup = new HashMap<>();

        for (Set<Activity> relatedActivities : urlToActivities.values()) {
            if (relatedActivities.size() < 2) {
                continue;
            }

            List<Activity> related = new ArrayList<>(relatedActivities);
            related.sort(ACTIVITY_PRIORITY);
            for (int i = 0; i < related.size(); i++) {
                for (int j = i + 1; j < related.size(); j++) {
                    Activity a = related.get(i);
                    Activity b = related.get(j);

                    Set<Activity> groupA = activityToGroup.get(a);
                    Set<Activity> groupB = activityToGroup.get(b);

                    if (groupA != null && groupA == groupB) {
                        continue;
                    }

                    // Build candidate merged group and check constraint
                    Set<Activity> candidate = new HashSet<>();
                    candidate.add(a);
                    candidate.add(b);
                    if (groupA != null) candidate.addAll(groupA);
                    if (groupB != null) candidate.addAll(groupB);

                    if (wouldMixCodeAndReview(candidate)) {
                        continue;
                    }

                    for (Activity activity : candidate) {
                        activityToGroup.put(activity, candidate);
                    }
                }
            }
        }

        // Create unique groups
        Set<Set<Activity>> uniqueGroups = new HashSet<>(activityToGroup.values());
        List<ActivityGroup> groups = new ArrayList<>();

        for (Set<Activity> groupSet : uniqueGroups) {
            if (groupSet.isEmpty()) {
                continue;
            }

            List<Activity> sorted = groupSet.stream()
                .sorted(ACTIVITY_PRIORITY)
                .collect(Collectors.toCollection(ArrayList::new));

            groups.add(new ActivityGroup(sorted.removeFirst(), sorted));
        }

        // Add ungrouped activities as single-item groups
        Set<Activity> allGrouped = activityToGroup.keySet();
        for (Activity activity : activities) {
            if (!allGrouped.contains(activity)) {
                groups.add(new ActivityGroup(activity, List.of()));
            }
        }

        return groups;
    }
}
