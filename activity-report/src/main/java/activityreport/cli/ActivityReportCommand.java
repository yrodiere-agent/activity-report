package activityreport.cli;

import activityreport.config.AppConfig;
import activityreport.model.Activity;
import activityreport.model.ActivityProvider;
import activityreport.providers.GitHubProvider;
import activityreport.providers.JiraProvider;
import activityreport.providers.ZulipProvider;
import activityreport.report.AIProcessor;
import activityreport.report.MarkdownReportGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Command(
    name = "report",
    mixinStandardHelpOptions = true,
    version = "report 1.0",
    description = "Generate activity reports from GitHub, JIRA, Zulip, and other sources"
)
public class ActivityReportCommand implements Runnable {

    @Option(names = {"-c", "--config"}, description = "Path to configuration file (default: ~/.activity-report/config.yaml)")
    private String configPath;

    @Option(names = {"-d", "--days"}, description = "Number of days to look back (default: 7)")
    private int days = 7;

    @Option(names = {"--start-date"}, description = "Start date (ISO format: YYYY-MM-DD)")
    private String startDateStr;

    @Option(names = {"--end-date"}, description = "End date (ISO format: YYYY-MM-DD)")
    private String endDateStr;

    @Option(names = {"--no-ai"}, description = "Disable AI processing and use simple markdown generation")
    private boolean noAi = false;

    @Override
    public void run() {
        try {
            System.err.println("Activity Report Generator");
            System.err.println("=========================\n");

            // Load configuration
            System.err.println("Loading configuration...");
            String effectiveConfigPath = configPath != null ? configPath :
                System.getProperty("user.home") + "/.activity-report/config.yaml";
            var config = AppConfig.loadConfig(effectiveConfigPath);
            System.err.println("Configuration loaded successfully.\n");

            // Determine date range
            Instant startDate, endDate;
            if (startDateStr != null && endDateStr != null) {
                startDate = LocalDate.parse(startDateStr).atStartOfDay(ZoneId.systemDefault()).toInstant();
                endDate = LocalDate.parse(endDateStr).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();
            } else {
                endDate = Instant.now();
                startDate = endDate.minus(Duration.ofDays(days));
            }

            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault());
            System.err.println("Fetching activities from " + formatter.format(startDate) +
                             " to " + formatter.format(endDate) + "\n");

            // Initialize providers
            List<ActivityProvider> providers = new ArrayList<>();

            if (config.providers().github().map(g -> g.enabled()).orElse(false)) {
                var githubProvider = new GitHubProvider(config);
                if (githubProvider.isConfigured()) {
                    providers.add(githubProvider);
                }
            }

            if (config.providers().jira().map(j -> j.enabled()).orElse(false)) {
                var jiraProvider = new JiraProvider(config);
                if (jiraProvider.isConfigured()) {
                    providers.add(jiraProvider);
                }
            }

            if (config.providers().zulip().map(z -> z.enabled()).orElse(false)) {
                var zulipProvider = new ZulipProvider(config);
                if (zulipProvider.isConfigured()) {
                    providers.add(zulipProvider);
                }
            }

            if (providers.isEmpty()) {
                System.err.println("Error: No providers are configured and enabled.");
                System.exit(1);
                return;
            }

            // Fetch activities from all providers
            List<Activity> allActivities = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (ActivityProvider provider : providers) {
                System.err.println("Fetching from " + provider.getName() + "...");
                try {
                    List<Activity> activities = provider.fetchActivities(startDate, endDate);
                    allActivities.addAll(activities);
                    System.err.println("  Found " + activities.size() + " activities");
                } catch (Exception e) {
                    String errorMsg = "  Error: " + e.getMessage();
                    System.err.println(errorMsg);
                    errors.add(provider.getName() + ": " + e.getMessage());
                }
            }

            System.err.println("\nTotal activities found: " + allActivities.size());

            if (allActivities.isEmpty()) {
                System.out.println("# Activity Report\n");
                System.out.println("No activities found for the specified date range.\n");
                if (!errors.isEmpty()) {
                    System.out.println("## Errors\n");
                    for (String error : errors) {
                        System.out.println("- " + error + "\n");
                    }
                }
                return;
            }

            // Generate report
            System.err.println("Generating report...\n");

            String report;
            if (noAi) {
                report = MarkdownReportGenerator.generateSimple(allActivities, startDate, endDate);
            } else {
                AIProcessor aiProcessor = new AIProcessor(config);
                report = aiProcessor.generateGroupedReport(allActivities, startDate, endDate);
            }

            // Output the report
            System.out.println(report);

            // Report any errors at the end
            if (!errors.isEmpty()) {
                System.err.println("\nWarning: Some providers encountered errors:");
                for (String error : errors) {
                    System.err.println("  - " + error);
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
