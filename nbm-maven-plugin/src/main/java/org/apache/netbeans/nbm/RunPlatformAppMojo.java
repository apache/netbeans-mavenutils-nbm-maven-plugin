package org.apache.netbeans.nbm;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Run a branded application on top of NetBeans Platform. To be used with
 * projects with nbm-application packaging only and the project needs to be
 * built first.
 *
 * @author Milos Kleint
 *
 */
@Mojo(name = "run-platform", requiresDependencyResolution = ResolutionScope.RUNTIME)
public final class RunPlatformAppMojo extends AbstractMojo {

    /**
     * The branding token for the application based on NetBeans platform.
     */
    @Parameter(required = true, property = "netbeans.branding.token")
    private String brandingToken;
    /**
     * output directory where the NetBeans application is created.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}")
    private File outputDirectory;

    /**
     * NetBeans user directory for the executed instance.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/userdir", property = "netbeans.userdir")
    private File netbeansUserdir;
    /**
     * additional command line arguments passed to the application. can be used
     * to debug the IDE.
     */
    @Parameter(property = "netbeans.run.params")
    private String additionalArguments;

    /**
     * Attach a debugger to the application JVM. If set to "true", the process
     * will suspend and wait for a debugger to attach on port 5005. If set to
     * some other string, that string will be appended to the
     * <code>additionalArguments</code>, allowing you to configure arbitrary
     * debug-ability options (without overwriting the other options specified
     * through the <code>additionalArguments</code> parameter).
     *
     * @since 3.11
     */
    @Parameter(property = "netbeans.run.params.debug")
    private String debugAdditionalArguments;

    /**
     * The Maven Project.
     *
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     *
     * @throws MojoExecutionException if an unexpected problem occurs
     * @throws MojoFailureException if an expected problem occurs
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!"nbm-application".equals(project.getPackaging())) {
            throw new MojoFailureException("The nbm:run-platform goal shall be used within a NetBeans Application project only ('nbm-application' packaging)");
        }

        netbeansUserdir.mkdirs();

        File appbasedir = new File(outputDirectory, brandingToken);

        if (!appbasedir.exists()) {
            throw new MojoExecutionException("The directory that shall contain built application, doesn't exist ("
                    + appbasedir.getAbsolutePath() + ")\n Please invoke 'mvn install' on the project first");
        }

        boolean windows = Os.isFamily("windows");

        Commandline cmdLine = new Commandline();
        File exec;
        if (windows) {
            exec = new File(appbasedir, "bin" + brandingToken + "_w.exe");
            if (!exec.exists()) { // Was removed as of nb 6.7
                exec = new File(appbasedir, "bin\\" + brandingToken + ".exe");
                // if jdk is 32 or 64-bit
                String jdkHome = System.getenv("JAVA_HOME");
                if (jdkHome != null) {
                    /* Detect whether the JDK is 32-bit or 64-bit. Since Oracle has "no plans to ship 32-bit builds of
                    JDK 9" [1] or beyond, assume 64-bit unless we can positively identify the JDK as 32-bit. The file
                    below is confirmed to exist on 32-bit Java 8, Java 9, and Java 10 [2], and confirmed _not_ to exist
                    on 64-bit Oracle Java 10 nor on OpenJDK 8, 9, or 10.

                    [1] Mark Reinhold on 2017-09-25
                        https://twitter.com/mreinhold/status/912311207935090689
                    [2] Downloaded from https://www.azul.com/downloads/zulu/zulu-windows on 2018-09-05. */
                    if (!new File(jdkHome, "jre\\bin\\JavaAccessBridge-32.dll").exists()
                            && // 32-bit Java 8
                            !new File(jdkHome, "\\bin\\javaaccessbridge-32.dll").exists()) // 32-bit Java 9 or 10
                    {
                        File exec64 = new File(appbasedir, "bin\\" + brandingToken + "64.exe");
                        if (exec64.isFile()) {
                            exec = exec64;
                        }
                    }
                }
                cmdLine.addArguments(new String[]{
                    "--console", "suppress"
                });
            }
        } else {
            exec = new File(appbasedir, "bin/" + brandingToken);
        }

        cmdLine.setExecutable(exec.getAbsolutePath());

        try {

            List<String> args = new ArrayList<>();
            args.add("--userdir");
            args.add(netbeansUserdir.getAbsolutePath());
            args.add("-J-Dnetbeans.logger.console=true");
            args.add("-J-ea");
            args.add("--branding");
            args.add(brandingToken);

            // use JAVA_HOME if set
            if (System.getenv("JAVA_HOME") != null) {
                args.add("--jdkhome");
                args.add(System.getenv("JAVA_HOME"));
            }

            cmdLine.addArguments(args.toArray(new String[0]));
            cmdLine.addArguments(CommandLineUtils.translateCommandline(additionalArguments));
            cmdLine.addArguments(CommandLineUtils.translateCommandline(getDebugAdditionalArguments()));
            getLog().info("Executing: " + cmdLine);
            StreamConsumer out = new StreamConsumer() {

                @Override
                public void consumeLine(String line) {
                    getLog().info(line);
                }
            };
            CommandLineUtils.executeCommandLine(cmdLine, out, out);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed executing NetBeans", e);
        }
    }

    private String getDebugAdditionalArguments() {
        if ("true".equals(debugAdditionalArguments)) {
            return "-Xdebug -Xnoagent -Djava.compiler=NONE "
                    + "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005";
        }
        return debugAdditionalArguments;
    }
}
