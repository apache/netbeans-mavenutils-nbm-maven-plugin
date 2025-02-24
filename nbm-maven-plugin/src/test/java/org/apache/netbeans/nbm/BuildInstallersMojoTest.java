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
package org.apache.netbeans.nbm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Assert;

/**
 *
 */
public class BuildInstallersMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/build-installer-jar/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        BuildInstallersMojo buildInstallerMojo = (BuildInstallersMojo) lookupMojo("build-installers", pom);
        assertNotNull(buildInstallerMojo);
        MojoExecutionException incorrectpackaging = Assert.assertThrows(MojoExecutionException.class, () -> buildInstallerMojo.execute());
        assertEquals("This goal only makes sense on project with 'nbm-application' packaging.", incorrectpackaging.getMessage());
    }

    public void testValidPackagingEmptySelection() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/build-installer-nbm-application/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        BuildInstallersMojo buildInstallerMojo = (BuildInstallersMojo) lookupMojo("build-installers", pom);
        assertNotNull(buildInstallerMojo);
        buildInstallerMojo.execute();
    }

    public void testValidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/build-installer-nbm-application/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        String branding = "mybrand";
        createDummyZipApp("project-build-installer-nbm-application", branding);

        BuildInstallersMojo buildInstallerMojo = (BuildInstallersMojo) lookupMojo("build-installers", pom);
        assertNotNull(buildInstallerMojo);
        Map<String, Object> variablesAndValuesFromObject = getVariablesAndValuesFromObject(buildInstallerMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        setVariableValueToObject(buildInstallerMojo, "installerOsLinux", true);
        setVariableValueToObject(buildInstallerMojo, "finalName", "foo");
        setVariableValueToObject(buildInstallerMojo, "brandingToken", branding);
        File fileapp = new File(getBasedir(), "target/test-classes/unit/build-installer-nbm-applicationa");
        fileapp.mkdirs();
        setVariableValueToObject(buildInstallerMojo, "basedir", fileapp);

        buildInstallerMojo.execute();
    }

    public void testValidPackagingZip() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/build-installer-nbm-application-zip/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        String branding = "mybrand";
        createDummyZipApp("project-build-installer-nbm-application-zip", branding);
        BuildInstallersMojo buildInstallerMojo = (BuildInstallersMojo) lookupMojo("build-installers", pom);
        assertNotNull(buildInstallerMojo);
        Map<String, Object> variablesAndValuesFromObject = getVariablesAndValuesFromObject(buildInstallerMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        setVariableValueToObject(buildInstallerMojo, "installerOsLinux", true);
        setVariableValueToObject(buildInstallerMojo, "installerOsMacosx", true);
        setVariableValueToObject(buildInstallerMojo, "installerOsWindows", true);
        setVariableValueToObject(buildInstallerMojo, "installerOsSolaris", true);
        setVariableValueToObject(buildInstallerMojo, "finalName", "foo");
        setVariableValueToObject(buildInstallerMojo, "brandingToken", branding);
        File fileapp = new File(getBasedir(), "target/test-classes/unit/build-installer-nbm-application-zipa");
        fileapp.mkdirs();
        setVariableValueToObject(buildInstallerMojo, "basedir", fileapp);

        buildInstallerMojo.execute();
    }

    private void createDummyZipApp(String folder, String branding) throws IOException {
        File file = new File(getBasedir(), "target/test-harness/" + folder + "/foo.zip");
        file.getParentFile().mkdirs();
        file.createNewFile();
        try (FileOutputStream fos = new FileOutputStream(file); ZipOutputStream zos = new ZipOutputStream(fos);) {
            ZipEntry ze = new ZipEntry(branding + "/" + "dummy.txt");
            zos.putNextEntry(ze);
            zos.write("test".getBytes(), 0, "test".getBytes().length);
            zos.closeEntry();
        }
    }
}
