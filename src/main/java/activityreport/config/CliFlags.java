package activityreport.config;

/**
 * Detects CLI flags from process arguments at config-load time,
 * before PicoCLI has parsed them.
 */
public final class CliFlags {

    private static volatile Boolean helpOrVersionRequested;

    private CliFlags() {
    }

    public static boolean isHelpOrVersionRequested() {
        if (helpOrVersionRequested == null) {
            helpOrVersionRequested = detect();
        }
        return helpOrVersionRequested;
    }

    private static boolean detect() {
        try {
            var argsOpt = ProcessHandle.current().info().arguments();
            if (argsOpt.isPresent()) {
                for (String arg : argsOpt.get()) {
                    if ("-h".equals(arg) || "--help".equals(arg)
                            || "-V".equals(arg) || "--version".equals(arg)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Fall through — resolve as usual if we can't inspect args
        }
        return false;
    }
}
