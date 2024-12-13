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
import java.io.IOException;
import java.util.Map;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Assert;

/**
 *
 */
public class BuildMacMojoMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/build-mac-jar/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        BuildMacMojo buildmacMojo = (BuildMacMojo) lookupMojo("build-mac", pom);
        assertNotNull(buildmacMojo);
        MojoExecutionException incorrectpackaging = Assert.assertThrows(MojoExecutionException.class, () -> buildmacMojo.execute());
        assertEquals("This goal only makes sense on project with 'nbm-application' packaging.", incorrectpackaging.getMessage());
    }

    public void testValidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/build-mac-nbm-application/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        String branding = "mybrand";
        createDummyApp("project-build-mac-nbm-application", branding);

        BuildMacMojo buildmacMojo = (BuildMacMojo) lookupMojo("build-mac", pom);
        assertNotNull(buildmacMojo);
        Map<String, Object> variablesAndValuesFromObject = getVariablesAndValuesFromObject(buildmacMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        File brandingfolder = new File(output, branding);
        brandingfolder.mkdirs();
        File dummyLauncher = new File(getBasedir(), "target/test-classes/unit/build-mac-nbm-application/dummyLauncher");
        dummyLauncher.createNewFile();
        setVariableValueToObject(buildmacMojo, "brandingToken", branding);
        setVariableValueToObject(buildmacMojo, "macLauncherFile", dummyLauncher);
        File fileapp = new File(getBasedir(), "target/test-classes/unit/build-mac-nbm-applicationa");
        fileapp.mkdirs();

        buildmacMojo.execute();
    }

    private void createDummyApp(String folder, String branding) throws IOException {
        File file = new File(getBasedir(), "target/test-harness/" + folder + "/" + branding + ".app");
        file.mkdirs();
        File bin = new File(file, "Contents/Resources/mybrand/bin");
        bin.mkdirs();
        File exe1 = new File(bin, branding + ".exe");
        File exe2 = new File(bin, branding + "64.exe");
        exe1.createNewFile();
        exe2.createNewFile();
    }
}
