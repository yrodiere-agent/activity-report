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
 * Activities sharing URLs group together, but CODE and REVIEW never mix
 * in the same group.
 */
class CrossProviderGroupingTest {

    @Test
    void testZulipJiraAndGitHubGroupTogether() {
        // All three share the same category family (CODE + CODE + DISCUSS) so they can group
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

        Activity zulipTopic = new Activity(
            "Zulip - example.zulipchat.com",
            "topic",
            ActionCategory.DISCUSS,
            "development / Sprint planning",
            "",
            "https://example.zulipchat.com/#narrow/stream/development/topic/Sprint.20planning",
            Instant.now(),
            List.of(
                "https://example.zulipchat.com/#narrow/stream/development/topic/Sprint.20planning/near/12345",
                "https://jira.example.com/browse/PROJ-456"
            )
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(
            List.of(githubPR, jiraIssue, zulipTopic)
        );

        assertEquals(1, groups.size(), "All three activities should group together (no CODE/REVIEW conflict)");
        assertEquals(2, groups.get(0).secondary().size());
    }

    @Test
    void testCodeAndReviewStaySeparateAcrossProviders() {
        Activity githubPR = new Activity(
            "GitHub - github.com",
            "pull_request",
            ActionCategory.CODE,
            "owner/repo#123: Implement feature",
            "",
            "https://github.com/owner/repo/pull/123",
            Instant.now()
        );

        Activity reviewedPR = new Activity(
            "GitHub - github.com",
            "pull_request",
            ActionCategory.REVIEW,
            "owner/repo#456: Someone else's feature",
            "",
            "https://github.com/owner/repo/pull/456",
            Instant.now(),
            List.of("https://github.com/owner/repo/pull/123")
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(
            List.of(githubPR, reviewedPR)
        );

        assertEquals(2, groups.size(), "CODE and REVIEW should stay in separate groups");
        assertTrue(groups.stream().allMatch(g -> g.secondary().isEmpty()));
    }

    @Test
    void testGitLabAndJiraGrouping() {
        Activity gitlabMR = new Activity(
            "GitLab - gitlab.com",
            "merge_request",
            ActionCategory.CODE,
            "owner/repo!456: Add feature",
            "",
            "https://gitlab.com/owner/repo/-/merge_requests/456",
            Instant.now()
        );

        Activity jiraIssue = new Activity(
            "JIRA - company",
            "issue",
            ActionCategory.CODE,
            "FEAT-123: New feature",
            "",
            "https://jira.example.com/browse/FEAT-123",
            Instant.now(),
            List.of("https://gitlab.com/owner/repo/-/merge_requests/456")
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(gitlabMR, jiraIssue));

        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).secondary().size());
    }
}
