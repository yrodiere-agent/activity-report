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
    void testCodeAndReviewStaySeparate() {
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
            List.of("https://github.com/repo/pull/123")
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(pr, review));

        assertThat(groups)
            .as("CODE and REVIEW should stay in separate groups")
            .hasSize(2)
            .allMatch(g -> g.secondary().isEmpty());
    }

    @Test
    void testTwoCodeActivitiesGroupTogether() {
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
                "https://github.com/repo/pull/123"
            )
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(pr, jiraIssue));

        assertThat(groups)
            .as("Two CODE activities should group together when sharing URLs")
            .hasSize(1);
        assertThat(groups.get(0).secondary()).hasSize(1);
    }

    @Test
    void testDiscussGroupsIntoCode() {
        String sharedUrl = "https://github.com/repo/issues/99";

        Activity pr = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Add feature X",
            "",
            "https://github.com/repo/pull/100",
            Instant.now(),
            List.of(sharedUrl),
            null,
            new HashMap<>(Map.of("targetsDefaultBranch", true))
        );

        Activity discuss = new Activity(
            "GitHub",
            "issue",
            ActionCategory.DISCUSS,
            "Discussion about feature",
            "",
            "https://github.com/repo/issues/10",
            Instant.now(),
            List.of(sharedUrl)
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(pr, discuss));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).primary()).isSameAs(pr);
        assertThat(groups.get(0).secondary()).containsExactly(discuss);
    }

    @Test
    void testChoreGroupsIntoCode() {
        String sharedUrl = "https://github.com/repo/issues/99";

        Activity pr = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Fix dependency",
            "",
            "https://github.com/repo/pull/100",
            Instant.now(),
            List.of(sharedUrl)
        );

        Activity chore = new Activity(
            "GitHub",
            "issue",
            ActionCategory.CHORE,
            "Dependency update",
            "",
            "https://github.com/repo/issues/10",
            Instant.now(),
            List.of(sharedUrl)
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(pr, chore));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).primary()).isSameAs(pr);
        assertThat(groups.get(0).secondary()).containsExactly(chore);
    }

    @Test
    void testCodeAndDiscussGroupButReviewStaysSeparate() {
        String sharedUrl = "https://github.com/repo/issues/99";

        Activity prDefaultBranch = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Add feature X",
            "",
            "https://github.com/repo/pull/200",
            Instant.now(),
            List.of(sharedUrl),
            null,
            new HashMap<>(Map.of("targetsDefaultBranch", true))
        );

        Activity prFeatureBranch = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "Backport fix to 1.x",
            "",
            "https://github.com/repo/pull/100",
            Instant.now(),
            List.of(sharedUrl),
            null,
            new HashMap<>(Map.of())
        );

        Activity review = new Activity(
            "GitHub",
            "review",
            ActionCategory.REVIEW,
            "Reviewed PR",
            "",
            "https://github.com/repo/pull/200#review-1",
            Instant.now(),
            List.of(sharedUrl)
        );

        Activity discuss = new Activity(
            "GitHub",
            "issue",
            ActionCategory.DISCUSS,
            "Discussion",
            "",
            "https://github.com/repo/issues/10",
            Instant.now(),
            List.of(sharedUrl)
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(
            List.of(discuss, prFeatureBranch, review, prDefaultBranch));

        // CODE activities and DISCUSS group together; REVIEW stays separate
        assertThat(groups).hasSize(2);

        ActivityGroup codeGroup = groups.stream()
            .filter(g -> g.primary().actionCategory() == ActionCategory.CODE)
            .findFirst().orElseThrow();
        assertThat(codeGroup.primary()).isSameAs(prDefaultBranch);
        assertThat(codeGroup.secondary()).contains(prFeatureBranch, discuss);

        ActivityGroup reviewGroup = groups.stream()
            .filter(g -> g.primary().actionCategory() == ActionCategory.REVIEW)
            .findFirst().orElseThrow();
        assertThat(reviewGroup.secondary()).isEmpty();
    }

    @Test
    void testNoGroupingWithCompletelyDifferentUrls() {
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

    @Test
    void testConnectedMinorActivitiesGroupTogether() {
        String sharedUrl = "https://github.com/repo/issues/99";

        Activity discuss1 = new Activity(
            "GitHub",
            "issue",
            ActionCategory.DISCUSS,
            "Discussion A",
            "",
            "https://github.com/repo/issues/1",
            Instant.now(),
            List.of(sharedUrl)
        );

        Activity discuss2 = new Activity(
            "GitHub",
            "issue",
            ActionCategory.DISCUSS,
            "Discussion B",
            "",
            "https://github.com/repo/issues/2",
            Instant.now(),
            List.of(sharedUrl)
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(discuss1, discuss2));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).secondary()).hasSize(1);
    }

    @Test
    void testDiscussDoesNotBridgeCodeAndReview() {
        Activity code = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.CODE,
            "My PR",
            "",
            "https://github.com/repo/pull/1",
            Instant.now(),
            List.of("https://github.com/repo/issues/10")
        );

        Activity discuss = new Activity(
            "GitHub",
            "issue",
            ActionCategory.DISCUSS,
            "Issue discussion",
            "",
            "https://github.com/repo/issues/10",
            Instant.now(),
            List.of("https://github.com/repo/pull/2")
        );

        Activity review = new Activity(
            "GitHub",
            "pull_request",
            ActionCategory.REVIEW,
            "Reviewed someone else's PR",
            "",
            "https://github.com/repo/pull/2",
            Instant.now()
        );

        List<ActivityGroup> groups = SimpleGrouper.groupActivities(List.of(code, discuss, review));

        // DISCUSS connects to both CODE and REVIEW via URLs, but merging
        // all three would mix CODE and REVIEW. The DISCUSS joins whichever
        // group it connects to first; the other stays separate.
        assertThat(groups).hasSize(2);
        assertThat(groups.stream().noneMatch(g -> {
            boolean hasCode = g.primary().actionCategory() == ActionCategory.CODE
                || g.secondary().stream().anyMatch(a -> a.actionCategory() == ActionCategory.CODE);
            boolean hasReview = g.primary().actionCategory() == ActionCategory.REVIEW
                || g.secondary().stream().anyMatch(a -> a.actionCategory() == ActionCategory.REVIEW);
            return hasCode && hasReview;
        })).as("No group should contain both CODE and REVIEW").isTrue();
    }
}
