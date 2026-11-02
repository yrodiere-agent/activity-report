package activityreport.report;

import activityreport.model.ActionCategory;
import activityreport.model.Activity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests demonstrating cross-provider grouping via URL extraction.
 * Substantive activities (CODE, REVIEW) stay in separate groups;
 * minor activities (DISCUSS, CHORE) are absorbed into the best substantive group.
 */
class CrossProviderGroupingTest {

    @Test
    void testDiscussGroupsWithSubstantiveAcrossProviders() {
        Activity githubPR = new Activity(
            "GitHub - github.com",
            "pull_request",
            ActionCategory.CODE,
            "owner/repo#123: Implement feature",
            "",
            "https://github.com/owner/repo/pull/123",
            Instant.now()
        );

        Activity zulipTopic = new Activity(
            "Zulip - example.zulipchat.com",
            "topic",
            ActionCategory.DISCUSS,
            "development / Sprint planning",
            "",
            "https://example.zulipchat.com/#narrow/stream/development/topic/Sprint.20planning",
            Instant.now(),
            List.of("https://github.com/owner/repo/pull/123")
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(
            List.of(githubPR, zulipTopic)
        );

        assertEquals(1, groups.size());
        assertEquals(ActionCategory.CODE, groups.get(0).primary().actionCategory());
        assertEquals(1, groups.get(0).secondary().size());
    }

    @Test
    void testTwoCodeActivitiesStaySeparateAcrossProviders() {
        Activity githubPR = new Activity(
            "GitHub - github.com",
            "pull_request",
            ActionCategory.CODE,
            "owner/repo#123: Implement feature",
            "",
            "https://github.com/owner/repo/pull/123",
            Instant.now()
        );

        Activity jiraIssue = new Activity(
            "JIRA - company",
            "issue",
            ActionCategory.CODE,
            "PROJ-456: Feature implementation",
            "",
            "https://jira.example.com/browse/PROJ-456",
            Instant.now(),
            List.of("https://github.com/owner/repo/pull/123")
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(
            List.of(githubPR, jiraIssue)
        );

        assertEquals(2, groups.size(), "Two CODE activities should stay in separate groups");
        assertTrue(groups.stream().allMatch(g -> g.secondary().isEmpty()));
    }

    @Test
    void testZulipDiscussJoinsCodeWhenReferencingMultipleSystems() {
        Activity githubPR = new Activity(
            "GitHub - github.com",
            "pull_request",
            ActionCategory.CODE,
            "owner/repo#123: Fix bug",
            "",
            "https://github.com/owner/repo/pull/123",
            Instant.now()
        );

        Activity jiraIssue = new Activity(
            "JIRA - company",
            "issue",
            ActionCategory.DISCUSS,
            "BUG-789: Fix production issue",
            "",
            "https://jira.example.com/browse/BUG-789",
            Instant.now()
        );

        // Zulip topic (DISCUSS) references both GitHub and JIRA
        Activity zulipTopic = new Activity(
            "Zulip - example.zulipchat.com",
            "topic",
            ActionCategory.DISCUSS,
            "incidents / Production hotfix",
            "",
            "https://example.zulipchat.com/#narrow/stream/incidents/topic/Production.20hotfix",
            Instant.now(),
            List.of(
                "https://github.com/owner/repo/pull/123",
                "https://jira.example.com/browse/BUG-789"
            )
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(
            List.of(githubPR, jiraIssue, zulipTopic)
        );

        // Zulip (DISCUSS) joins the CODE activity (best priority)
        ActivityGroup codeGroup = groups.stream()
            .filter(g -> g.primary().actionCategory() == ActionCategory.CODE)
            .findFirst()
            .orElseThrow();
        assertThat(codeGroup.secondary())
            .anyMatch(a -> a.source().contains("Zulip"));
    }
}
