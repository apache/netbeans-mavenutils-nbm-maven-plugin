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
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugin.MojoExecutionException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 *
 */
@MojoTest
class BuildMacMojoMojoTest {

    @Test
    @InjectMojo(goal = "build-mac", pom = "src/test/resources/unit/build-mac-jar/plugin-config.xml")
    void testInvalidPackaging(BuildMacMojo buildmacMojo) throws Exception {
        assertNotNull(buildmacMojo);
        MojoExecutionException incorrectpackaging = assertThrows(MojoExecutionException.class, () -> buildmacMojo.execute());
        assertEquals("This goal only makes sense on project with 'nbm-application' packaging.", incorrectpackaging.getMessage());
    }

    @Test
    @InjectMojo(goal = "build-mac", pom = "src/test/resources/unit/build-mac-nbm-application/plugin-config.xml")
    void testValidPackaging(BuildMacMojo buildmacMojo) throws Exception {
        String branding = "mybrand";
        createDummyApp("project-build-mac-nbm-application", branding);
        assertNotNull(buildmacMojo);
        Map<String, Object> variablesAndValuesFromObject = MojoExtension.getVariablesAndValuesFromObject(buildmacMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        File brandingfolder = new File(output, branding);
        brandingfolder.mkdirs();
        File dummyLauncher = new File(MojoExtension.getBasedir(), "target/test-classes/unit/build-mac-nbm-application/dummyLauncher");
        dummyLauncher.createNewFile();
        MojoExtension.setVariableValueToObject(buildmacMojo, "brandingToken", branding);
        MojoExtension.setVariableValueToObject(buildmacMojo, "macLauncherFile", dummyLauncher);
        File fileapp = new File(MojoExtension.getBasedir(), "target/test-classes/unit/build-mac-nbm-applicationa");
        fileapp.mkdirs();

        buildmacMojo.execute();
    }

    private void createDummyApp(String folder, String branding) throws IOException {
        File file = new File(MojoExtension.getBasedir(), "target/test-harness/" + folder + "/" + branding + ".app");
        file.mkdirs();
        File bin = new File(file, "Contents/Resources/mybrand/bin");
        bin.mkdirs();
        File exe1 = new File(bin, branding + ".exe");
        File exe2 = new File(bin, branding + "64.exe");
        exe1.createNewFile();
        exe2.createNewFile();
    }
}
