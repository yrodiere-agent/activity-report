package activityreport.config;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.OptionalInt;

/**
 * Custom ConfigSource factory that loads YAML configuration from XDG-compliant locations.
 * Looks for config.yaml in:
 * 1. $XDG_CONFIG_HOME/activity-report/config.yaml (if XDG_CONFIG_HOME is set)
 * 2. ~/.config/activity-report/config.yaml (default)
 */
public class XdgYamlConfigSourceFactory implements ConfigSourceFactory {

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        Path configPath = findConfigFile();

        if (configPath != null && Files.exists(configPath)) {
            try {
                YamlConfigSource yamlSource = new YamlConfigSource(configPath.toUri().toURL(), 275);
                return Collections.singletonList(yamlSource);
            } catch (IOException e) {
                System.err.println("Warning: Failed to load configuration from " + configPath + ": " + e.getMessage());
            }
        } else {
            System.err.println("Warning: Configuration file not found. Looked in: " +
                (configPath != null ? configPath : getDefaultConfigPath()));
            System.err.println("Please create a configuration file. See config.yaml.example for reference.");
        }

        return Collections.emptyList();
    }

    @Override
    public OptionalInt getPriority() {
        // Priority 275 is between application.properties (250) and system properties (300)
        // This allows command-line/env vars to override config file, but config file overrides application.properties
        return OptionalInt.of(275);
    }

    /**
     * Find the configuration file in XDG-compliant locations.
     * Returns the first existing file, or the default path if none exist.
     */
    private Path findConfigFile() {
        Path configPath = getDefaultConfigPath();

        if (Files.exists(configPath)) {
            return configPath;
        }

        return configPath; // Return default path even if it doesn't exist (for error messages)
    }

    /**
     * Get the default configuration file path following XDG Base Directory Specification.
     */
    private Path getDefaultConfigPath() {
        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
        if (xdgConfigHome != null && !xdgConfigHome.isEmpty()) {
            return Paths.get(xdgConfigHome, "activity-report", "config.yaml");
        }
        return Paths.get(System.getProperty("user.home"), ".config", "activity-report", "config.yaml");
    }
}
