/*
 * Copyright 2024 The Apache Software Foundation.
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
package org.apache.netbeans.nbm.stubs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

/**
 *
 * @author skygo
 */
public class MavenProjectHarnessArtefactStub extends MavenProjectStub {

    public MavenProjectHarnessArtefactStub() {
        setArtifactId("myname");
        setGroupId("foo");
        setVersion("1.2.3.4");
        setArtifact(new ArtifactStub() {
            @Override
            public String getArtifactId() {
                return "myname";
            }

            @Override
            public String getGroupId() {
                return "foo";
            }

            @Override
            public String getVersion() {
                return "1.2.3.4";
            }

            @Override
            public VersionRange getVersionRange() {
                return VersionRange.createFromVersion(getVersion());
            }

        });
    }

    @Override
    public Set<Artifact> getArtifacts() {
        Set<Artifact> arrayList = new HashSet<>();
        arrayList.add(new ArtifactStub() {
            @Override
            public String getArtifactId() {
                return "myname1";
            }

            @Override
            public String getGroupId() {
                return "foo";
            }

            @Override
            public String getVersion() {
                return "1.2.3.4";
            }

            @Override
            public VersionRange getVersionRange() {
                return VersionRange.createFromVersion(getVersion());
            }

            @Override
            public ArtifactHandler getArtifactHandler() {
                return new DefaultArtifactHandlerStub("type", "classifier");
            }

            @Override
            public String getType() {
                return "jar";
            }

            @Override
            public File getFile() {
                try {
                    return createDummyJarApp(getBasedir(), "cluster-app-complete-harnessa", SCOPE_TEST);
                } catch (IOException ex) {
                    Logger.getLogger(MavenProjectHarnessArtefactStub.class.getName()).log(Level.SEVERE, null, ex);
                }
                return null;
            }

        });
        return arrayList;
    }

    private File createDummyJarApp(File basedir, String folder, String name) throws IOException {
        File file = new File(basedir, "target/test-harness/" + folder + "/target/foo.jar");
        file.getParentFile().mkdirs();
        file.createNewFile();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module"), "test");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module-Name"), "test");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module-Specification-Version"), "test");
        try (FileOutputStream fos = new FileOutputStream(file); JarOutputStream jos = new JarOutputStream(fos, manifest);) {
            JarEntry jarAdd = new JarEntry("entry1");
            jos.putNextEntry(jarAdd);
            jos.closeEntry();
        }
        File filenbm = new File(basedir, "target/test-harness/" + folder + "/target/foo.nbm");
        filenbm.getParentFile().mkdirs();
        filenbm.createNewFile();
        Manifest manifestnbm = new Manifest();
        manifestnbm.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifestnbm.getMainAttributes().put(new Attributes.Name("OpenIDE-Module"), "test");
        manifestnbm.getMainAttributes().put(new Attributes.Name("OpenIDE-Module-Name"), "test");
        manifestnbm.getMainAttributes().put(new Attributes.Name("OpenIDE-Module-Specification-Version"), "test");
        try (FileOutputStream fos = new FileOutputStream(filenbm); JarOutputStream jos = new JarOutputStream(fos, manifestnbm);) {
            JarEntry jarAdd = new JarEntry("entry1");
            jos.putNextEntry(jarAdd);
            jos.closeEntry();
        }
        return filenbm;
    }

}
