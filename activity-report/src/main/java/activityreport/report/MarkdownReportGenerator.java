package activityreport.report;

import activityreport.model.ActionCategory;
import activityreport.model.Activity;

import java.util.*;

/**
 * Markdown report generator working with grouped activities
 */
public class MarkdownReportGenerator {

    public static String generate(List<ActivityGroup> groups) {
        StringBuilder report = new StringBuilder();

        // Organize groups by project
        Map<String, List<ActivityGroup>> byProject = new LinkedHashMap<>();
        byProject.put("General", new ArrayList<>()); // Always empty, manually filled

        List<ActivityGroup> miscGroups = new ArrayList<>();

        for (ActivityGroup group : groups) {
            Activity primary = group.primary();
            ActionCategory actionCategory = primary.actionCategory();
            String project = (String) primary.metadata().get("project");

            // Only CODE activities go to projects, others go to Misc
            if (actionCategory == ActionCategory.CODE && project != null && !project.isEmpty()) {
                byProject.computeIfAbsent(project, k -> new ArrayList<>()).add(group);
            } else {
                miscGroups.add(group);
            }
        }

        // Generate General section (always empty)
        report.append("# General\n\n");
        report.append("(To be filled manually)\n\n");
        report.append("----\n\n");

        // Generate Project sections
        for (Map.Entry<String, List<ActivityGroup>> entry : byProject.entrySet()) {
            String project = entry.getKey();
            if ("General".equals(project)) {
                continue; // Already handled
            }

            List<ActivityGroup> projectGroups = entry.getValue();
            if (projectGroups.isEmpty()) {
                continue;
            }

            report.append(String.format("# Project: %s\n\n", project));

            for (ActivityGroup group : projectGroups) {
                formatGroup(report, group);
            }

            report.append("----\n\n");
        }

        // Generate Misc section (reviews, discussions)
        if (!miscGroups.isEmpty()) {
            report.append("# Misc\n\n");
            report.append("Reviews, triage, discussions\n\n");

            for (ActivityGroup group : miscGroups) {
                formatGroup(report, group);
            }
        }

        return report.toString();
    }

    private static void formatGroup(StringBuilder report, ActivityGroup group) {
        Activity primary = group.primary();

        // Primary activity line with link
        if (primary.url() != null && !primary.url().isEmpty()) {
            report.append(String.format("* [%s](%s)", primary.title(), primary.url()));
        } else {
            report.append(String.format("* %s", primary.title()));
        }

        // Add description if present
        if (primary.description() != null && !primary.description().isEmpty()) {
            report.append("\n");
            String[] lines = primary.description().split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    report.append(String.format("  %s\n", line));
                }
            }
        }

        // Add secondary activities as sub-items
        if (!group.secondary().isEmpty()) {
            for (Activity secondary : group.secondary()) {
                report.append("  - ");
                if (secondary.url() != null && !secondary.url().isEmpty()) {
                    report.append(String.format("[%s](%s)", secondary.title(), secondary.url()));
                } else {
                    report.append(secondary.title());
                }
                report.append("\n");
            }
        }

        report.append("\n");
    }
}
