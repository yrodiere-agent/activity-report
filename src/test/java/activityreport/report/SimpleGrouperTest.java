package activityreport.report;

import activityreport.model.ActionCategory;
import activityreport.model.Activity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleGrouperTest {

    @Test
    void testGroupingWhenContentUrlsReferenceParent() {
        // This test shows that grouping WORKS when contentUrls include the parent URL
        Activity pr = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Add feature X",
            "Description",
            "https://github.com/repo/pull/123",
            Instant.now(),
            List.of()
        );

        Activity review = new Activity(
            "GitHub",
            "review",
            ActionCategory.REVIEW,
            "Reviewed PR",
            "Description",
            "https://github.com/repo/pull/123#review-456",
            Instant.now(),
            List.of("https://github.com/repo/pull/123") // This references the parent!
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(pr, review));

        assertThat(groups)
            .filteredOn(g -> !g.secondary().isEmpty())
            .as("Expected 1 group when contentUrls reference parent")
            .hasSize(1);
    }

    @Test
    void testGroupingWithJiraReferencingGitHubPR() {
        // Realistic scenario: JIRA issue references a GitHub PR
        Activity pr = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Add feature X",
            "Description",
            "https://github.com/repo/pull/123",
            Instant.now(),
            List.of(
                "https://github.com/repo/pull/123#issuecomment-456",
                "https://github.com/repo/pull/123#discussion_r789"
            )
        );

        Activity jiraIssue = new Activity(
            "JIRA",
            "issue",
            ActionCategory.CODE,
            "PROJ-123: Implement feature",
            "Description",
            "https://jira.example.com/browse/PROJ-123",
            Instant.now(),
            List.of(
                "https://github.com/repo/pull/123" // JIRA references the GitHub PR
            )
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(pr, jiraIssue));

        // This DOES group because JIRA's contentUrls include the PR's main URL
        assertThat(groups)
            .filteredOn(g -> !g.secondary().isEmpty())
            .as("Should group when JIRA contentUrls reference the GitHub PR")
            .hasSize(1);
    }

    @Test
    void testNoGroupingWhenUrlsAreRelatedButDontOverlap() {
        // Scenario that WON'T group: URLs are related (same PR) but don't overlap
        Activity pr = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Add feature X",
            "Description",
            "https://github.com/repo/pull/123",
            Instant.now(),
            List.of(
                "https://github.com/repo/pull/123#issuecomment-456"
            )
        );

        Activity jiraIssue = new Activity(
            "JIRA",
            "issue",
            ActionCategory.CODE,
            "PROJ-123: Implement feature",
            "Description",
            "https://jira.example.com/browse/PROJ-123",
            Instant.now(),
            List.of(
                // JIRA extracted a DIFFERENT comment URL, not the PR's main URL
                "https://github.com/repo/pull/123#issuecomment-789"
            )
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(pr, jiraIssue));

        // No grouping: URLs don't overlap (different comment IDs)
        assertThat(groups)
            .filteredOn(g -> !g.secondary().isEmpty())
            .as("No grouping when URLs are related but don't exactly match")
            .isEmpty();
    }

    @Test
    void testPrimaryPrefersCodeTargetingDefaultBranch() {
        Activity prFeatureBranch = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Backport fix to 1.x",
            "Description",
            "https://github.com/repo/pull/100",
            Instant.now(),
            List.of("https://github.com/repo/pull/200"),
            null,
            new HashMap<>(Map.of())
        );

        Activity prDefaultBranch = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Add feature X",
            "Description",
            "https://github.com/repo/pull/200",
            Instant.now(),
            List.of("https://github.com/repo/pull/100"),
            null,
            new HashMap<>(Map.of("targetsDefaultBranch", true))
        );

        // Even though prFeatureBranch comes first, the one targeting default branch should be primary
        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(prFeatureBranch, prDefaultBranch));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).primary()).isSameAs(prDefaultBranch);
        assertThat(groups.get(0).secondary()).containsExactly(prFeatureBranch);
    }

    @Test
    void testPrimaryFallsBackToAnyCodeWhenNoneTargetDefaultBranch() {
        Activity pr1 = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Backport fix to 1.x",
            "Description",
            "https://github.com/repo/pull/100",
            Instant.now(),
            List.of("https://github.com/repo/pull/200")
        );

        Activity review = new Activity(
            "GitHub",
            "review",
            ActionCategory.REVIEW,
            "Reviewed PR",
            "Description",
            "https://github.com/repo/pull/200",
            Instant.now(),
            List.of("https://github.com/repo/pull/100")
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(review, pr1));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).primary()).isSameAs(pr1);
    }

    @Test
    void testNoGroupingWithCompletelyDifferentUrls() {
        // Activities with no URL overlap shouldn't group
        Activity pr = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Add feature X",
            "Description",
            "https://github.com/repo/pull/123",
            Instant.now()
        );

        Activity issue = new Activity(
            "GitHub",
            "issue",
            ActionCategory.DISCUSS,
            "Bug report",
            "Description",
            "https://github.com/repo/issues/456",
            Instant.now()
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(pr, issue));

        assertThat(groups).hasSize(2)
            .allMatch(g -> g.secondary().isEmpty());
    }
}
