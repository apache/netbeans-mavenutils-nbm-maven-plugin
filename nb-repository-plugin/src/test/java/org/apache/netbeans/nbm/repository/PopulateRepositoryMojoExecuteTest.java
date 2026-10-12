/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.netbeans.nbm.repository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

@MojoTest(realRepositorySession = true)
class PopulateRepositoryMojoExecuteTest {

    @Inject
    private MavenSession session;

    @Test
    @InjectMojo(goal = "populate")
    @MojoParameter(name = "netbeansInstallDirectory", value = "${project.basedir}/target/test-classes/unit/netbeansinstall")
    @MojoParameter(name = "forcedVersion", value = "RELEASE00")
    void testPseudoTPL(PopulateRepositoryMojo mojo) throws MojoExecutionException, IOException {

        System.err.println("mojo" + mojo.netbeansInstallDirectory);

        Path netbeansinstallpath = mojo.netbeansInstallDirectory.toPath();

        // cleanup target test files
        try (Stream<Path> paths = Files.walk(netbeansinstallpath)) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
        
        // build a folder for netbeans install directory
        Path platformfolder = netbeansinstallpath.resolve("platform");

        Path dummyfolder = netbeansinstallpath.resolve("dummy");
        Files.createDirectories(platformfolder);

        Path platformcorefolder = platformfolder.resolve("core");
        Files.createDirectories(platformcorefolder);

        Path platformcorebase = platformcorefolder.resolve("core-base.jar");
        Files.createFile(platformcorebase);

        // minimal jar
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("OpenIDE-Module", "org.netbeans.core.startup.base");
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(platformcorebase.toFile()), manifest);
        jos.close();
        Files.createDirectories(dummyfolder);

        mojo.execute();
    }
}
