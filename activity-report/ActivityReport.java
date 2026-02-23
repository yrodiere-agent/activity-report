///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS io.quarkus.platform:quarkus-bom:3.17.7@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkus:quarkus-config-yaml
//DEPS org.kohsuke:github-api:1.321
//DEPS com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2
//JAVAC_OPTIONS -parameters
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager

//SOURCES src/main/java/activityreport/model/Activity.java
//SOURCES src/main/java/activityreport/model/ActivityProvider.java
//SOURCES src/main/java/activityreport/config/AppConfig.java
//SOURCES src/main/java/activityreport/providers/GitHubProvider.java
//SOURCES src/main/java/activityreport/providers/JiraProvider.java
//SOURCES src/main/java/activityreport/providers/ZulipProvider.java
//SOURCES src/main/java/activityreport/report/MarkdownReportGenerator.java
//SOURCES src/main/java/activityreport/report/AIProcessor.java
//SOURCES src/main/java/activityreport/cli/ActivityReportCommand.java

import activityreport.cli.ActivityReportCommand;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

@QuarkusMain
public class ActivityReport implements QuarkusApplication {

    @Override
    public int run(String... args) {
        return new CommandLine(new ActivityReportCommand()).execute(args);
    }

    public static void main(String... args) {
        Quarkus.run(ActivityReport.class, args);
    }
}
