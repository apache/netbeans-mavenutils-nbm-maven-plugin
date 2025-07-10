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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.archiver.zip.ZipArchiver;

/**
 * Create a standalone application out of the composed clusters of
 * nbm-application
 *
 * @author Johan Andrén
 * @author Milos Kleint
 */
@Mojo(name = "standalone-zip", requiresProject = true, threadSafe = true)
public final class CreateStandaloneMojo extends AbstractMojo {

    /**
     * The branding token for the application based on NetBeans platform.
     */
    @Parameter(property = "netbeans.branding.token", required = true)
    protected String brandingToken;
    /**
     * output directory where the the NetBeans application will be created.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}")
    private File outputDirectory;
    /**
     * Name of the zip artifact produced by the goal (without .zip extension)
     */
    @Parameter(defaultValue = "${project.build.finalName}")
    private String finalName;
    /**
     * The Maven project.
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

        try {
            File nbmBuildDirFile = new File(outputDirectory, brandingToken);

            ZipArchiver archiver = new ZipArchiver();
            DefaultFileSet fs = new DefaultFileSet();
            fs.setDirectory(outputDirectory);
            fs.setIncludes(new String[]{
                brandingToken + "/**",});
            fs.setExcludes(new String[]{
                brandingToken + "/bin/*",});
            archiver.addFileSet(fs);
            File bins = new File(nbmBuildDirFile, "bin");
            for (File bin : bins.listFiles()) {
                archiver.addFile(bin, brandingToken + "/bin/" + bin.getName(), EXEC_FILE_MOD);
            }
            File zipFile = new File(outputDirectory, finalName + ".zip");
            //TODO - somehow check for last modified content to see if we shall be
            //recreating the zip file.
            archiver.setDestFile(zipFile);
            archiver.setForced(false);
            archiver.createArchive();
            project.getArtifact().setFile(zipFile);

        } catch (Exception ex) {
            throw new MojoExecutionException("", ex);
        }

    }
    private static final int EXEC_FILE_MOD = 0755;
}
