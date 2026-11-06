package activityreport.util;

import java.nio.file.Path;

public class OpenCommandBuilder {

    private OpenCommandBuilder() {
    }

    /**
     * Substitute {file} and {dir} placeholders in a command template.
     */
    public static String substituteCommand(String template, Path filePath) {
        return template
                .replace("{file}", filePath.toString())
                .replace("{dir}", filePath.getParent().toString());
    }
}
