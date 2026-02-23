package activityreport.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Main configuration interface using Quarkus ConfigMapping
 */
@ConfigMapping
public interface AppConfig {
    Providers providers();
    Optional<Ai> ai();

    interface Providers {
        Optional<Github> github();
        Optional<Jira> jira();
        Optional<Zulip> zulip();
    }

    interface Github {
        boolean enabled();
        List<GithubInstance> instances();

        interface GithubInstance {
            String name();
            Optional<String> url();
            Optional<String> username();
            String token();
        }
    }

    interface Jira {
        boolean enabled();
        List<JiraInstance> instances();

        interface JiraInstance {
            String name();
            String url();
            String email();
            String token();
        }
    }

    interface Zulip {
        boolean enabled();
        List<ZulipInstance> instances();

        interface ZulipInstance {
            String url();
            String email();
            @io.smallrye.config.WithName("api_key")
            String apiKey();
        }
    }

    interface Ai {
        Optional<String> url();
        Optional<String> model();
    }

    /**
     * Helper to load configuration from external YAML file
     */
    static AppConfig loadConfig(String configPath) throws IOException {
        var path = Paths.get(configPath);

        if (!Files.exists(path)) {
            throw new IOException("Configuration file not found: " + configPath +
                "\nPlease create a configuration file. See config.yaml.example for reference.");
        }

        try {
            // Create YAML config source from file
            var yamlSource = new YamlConfigSource(path.toUri().toURL());

            // Build SmallRye config with the YAML source and environment variables support
            var config = new SmallRyeConfigBuilder()
                .withSources(yamlSource)
                .withDefaultValue("ai.url", "http://localhost:8000/v1")
                .withDefaultValue("ai.model", "auto")
                .build();

            // Map to AppConfig interface
            var appConfig = config.getConfigMapping(AppConfig.class);

            // Validate configuration
            validateConfig(appConfig);

            return appConfig;
        } catch (Exception e) {
            throw new IOException("Failed to load configuration: " + e.getMessage(), e);
        }
    }

    private static void validateConfig(AppConfig config) throws IOException {
        if (config.providers() == null) {
            throw new IOException("No providers configured");
        }

        // Check if at least one provider is enabled
        boolean hasEnabledProvider = false;

        if (config.providers().github().isPresent() && config.providers().github().get().enabled()) {
            hasEnabledProvider = true;
            if (config.providers().github().get().instances() == null ||
                config.providers().github().get().instances().isEmpty()) {
                throw new IOException("GitHub is enabled but no instances are configured");
            }
        }

        if (config.providers().jira().isPresent() && config.providers().jira().get().enabled()) {
            hasEnabledProvider = true;
            if (config.providers().jira().get().instances() == null ||
                config.providers().jira().get().instances().isEmpty()) {
                throw new IOException("JIRA is enabled but no instances are configured");
            }
        }

        if (config.providers().zulip().isPresent() && config.providers().zulip().get().enabled()) {
            hasEnabledProvider = true;
            if (config.providers().zulip().get().instances() == null ||
                config.providers().zulip().get().instances().isEmpty()) {
                throw new IOException("Zulip is enabled but no instances are configured");
            }
        }

        if (!hasEnabledProvider) {
            throw new IOException("No providers are enabled. Please enable at least one provider in the configuration.");
        }
    }
}
