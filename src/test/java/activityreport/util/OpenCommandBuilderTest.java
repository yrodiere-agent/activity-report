package activityreport.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OpenCommandBuilderTest {

    @Test
    void substitutesBothPlaceholders() {
        Path file = Path.of("/data/reports/report_2024-01-01_to_2024-01-07.md");
        String result = OpenCommandBuilder.substituteCommand("code {dir} && open {file}", file);
        assertEquals("code /data/reports && open /data/reports/report_2024-01-01_to_2024-01-07.md", result);
    }

    @Test
    void substitutesFileOnly() {
        Path file = Path.of("/tmp/report.md");
        String result = OpenCommandBuilder.substituteCommand("xdg-open {file}", file);
        assertEquals("xdg-open /tmp/report.md", result);
    }

    @Test
    void substitutesDirOnly() {
        Path file = Path.of("/home/user/reports/weekly.md");
        String result = OpenCommandBuilder.substituteCommand("code {dir}", file);
        assertEquals("code /home/user/reports", result);
    }

    @Test
    void noPlaceholders() {
        Path file = Path.of("/tmp/report.md");
        String result = OpenCommandBuilder.substituteCommand("echo done", file);
        assertEquals("echo done", result);
    }

    @Test
    void multipleSamePlaceholder() {
        Path file = Path.of("/data/report.md");
        String result = OpenCommandBuilder.substituteCommand("echo {file} {file}", file);
        assertEquals("echo /data/report.md /data/report.md", result);
    }
}
