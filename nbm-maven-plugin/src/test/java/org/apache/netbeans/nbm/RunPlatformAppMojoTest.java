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
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 *
 */
@MojoTest
class RunPlatformAppMojoTest {

    @Test
    @InjectMojo(goal = "run-platform", pom = "src/test/resources/unit/run-platform-jar/plugin-config.xml")
    void testInvalidPackaging(RunPlatformAppMojo runPlatformAppMojo) throws Exception {
        assertNotNull(runPlatformAppMojo);
        MojoFailureException incorrectpackaging = assertThrows(MojoFailureException.class, () -> runPlatformAppMojo.execute());
        assertEquals("The nbm:run-platform goal shall be used within a NetBeans Application project only ('nbm-application' packaging)", incorrectpackaging.getMessage());
    }

    @Test
    @InjectMojo(goal = "run-platform", pom = "src/test/resources/unit/run-platform-nbm-application/plugin-config.xml")
    void testValidPackaging(RunPlatformAppMojo runPlatformAppMojo) throws Exception {
        assertNotNull(runPlatformAppMojo);
        String branding = "mybrand";
        Map<String, Object> variablesAndValuesFromObject = MojoExtension.getVariablesAndValuesFromObject(runPlatformAppMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        File brandingfolder = new File(output, branding);
        brandingfolder.mkdirs();
        File userfolder = new File(output, "usertest");
        userfolder.createNewFile();
        File dummyLauncher = new File(MojoExtension.getBasedir(), "target/test-classes/unit/build-mac-nbm-application/dummyLauncher");
        dummyLauncher.createNewFile();
        MojoExtension.setVariableValueToObject(runPlatformAppMojo, "brandingToken", branding);
        MojoExtension.setVariableValueToObject(runPlatformAppMojo, "netbeansUserdir", userfolder);
        File fileapp = new File(MojoExtension.getBasedir(), "target/test-classes/unit/build-mac-nbm-applicationa");
        fileapp.mkdirs();
        MojoExecutionException executionfailure = assertThrows(MojoExecutionException.class, () -> runPlatformAppMojo.execute());
        assertEquals("Failed executing NetBeans", executionfailure.getMessage());
    }

}
