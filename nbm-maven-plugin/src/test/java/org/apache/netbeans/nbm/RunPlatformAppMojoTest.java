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
import java.util.Map;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Assert;

/**
 *
 */
public class RunPlatformAppMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/run-platform-jar/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        RunPlatformAppMojo runPlatformAppMojo = (RunPlatformAppMojo) lookupMojo("run-platform", pom);
        assertNotNull(runPlatformAppMojo);
        MojoFailureException incorrectpackaging = Assert.assertThrows(MojoFailureException.class, () -> runPlatformAppMojo.execute());
        assertEquals("The nbm:run-platform goal shall be used within a NetBeans Application project only ('nbm-application' packaging)", incorrectpackaging.getMessage());
    }

    public void testValidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/run-platform-nbm-application/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        String branding = "mybrand";

        RunPlatformAppMojo runPlatformAppMojo = (RunPlatformAppMojo) lookupMojo("run-platform", pom);
        assertNotNull(runPlatformAppMojo);
        Map<String, Object> variablesAndValuesFromObject = getVariablesAndValuesFromObject(runPlatformAppMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        File brandingfolder = new File(output, branding);
        brandingfolder.mkdirs();
        File userfolder = new File(output, "usertest");
        userfolder.createNewFile();
        File dummyLauncher = new File(getBasedir(), "target/test-classes/unit/build-mac-nbm-application/dummyLauncher");
        dummyLauncher.createNewFile();
        setVariableValueToObject(runPlatformAppMojo, "brandingToken", branding);
        setVariableValueToObject(runPlatformAppMojo, "netbeansUserdir", userfolder);
        File fileapp = new File(getBasedir(), "target/test-classes/unit/build-mac-nbm-applicationa");
        fileapp.mkdirs();
        MojoExecutionException executionfailure = Assert.assertThrows(MojoExecutionException.class, () -> runPlatformAppMojo.execute());
        assertEquals("Failed executing NetBeans", executionfailure.getMessage());
    }

}
