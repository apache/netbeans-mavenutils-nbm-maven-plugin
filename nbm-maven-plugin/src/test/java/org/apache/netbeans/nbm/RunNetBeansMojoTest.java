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
class RunNetBeansMojoTest {

    @Test
    @InjectMojo(goal = "run-ide", pom = "src/test/resources/unit/run-ide-jar/plugin-config.xml")
    void testInvalidPackaging(RunNetBeansMojo runNetBeansMojo) throws Exception {
        assertNotNull(runNetBeansMojo);
        File userdir = new File(MojoExtension.getBasedir(), "target/test-classes/unit/run-ide-jar/userdir");
        MojoExtension.setVariableValueToObject(runNetBeansMojo, "netbeansUserdir", userdir);
        File clusterdir = new File(MojoExtension.getBasedir(), "target/test-classes/unit/run-ide-jar/clusterdir");
        MojoExtension.setVariableValueToObject(runNetBeansMojo, "clusterBuildDir", clusterdir);
        MojoExecutionException incorrectpackaging = assertThrows(MojoExecutionException.class, () -> runNetBeansMojo.execute());
        assertEquals("No clusters to include in execution found. Please run the nbm:cluster or nbm:cluster-app goals before this one.", incorrectpackaging.getMessage());
    }

    @Test
    @InjectMojo(goal = "run-ide", pom = "src/test/resources/unit/run-ide-nbm-application/plugin-config.xml")
    void testValidPackaging(RunNetBeansMojo runNetBeansMojo) throws Exception {
        assertNotNull(runNetBeansMojo);
        File userdir = new File(MojoExtension.getBasedir(), "target/test-classes/unit/run-ide-nbm-application/userdir");
        MojoExtension.setVariableValueToObject(runNetBeansMojo, "netbeansUserdir", userdir);
        File clusterdir = new File(MojoExtension.getBasedir(), "target/test-classes/unit/run-ide-nbm-application/clusterdir");
        clusterdir.mkdirs();
        File aclusterdir = new File(clusterdir, "acluster");
        aclusterdir.mkdirs();
        File bclusterdir = new File(clusterdir, "bcluster.test");
        bclusterdir.createNewFile();
        MojoExtension.setVariableValueToObject(runNetBeansMojo, "clusterBuildDir", clusterdir);
        MojoExecutionException executionfailure = assertThrows(MojoExecutionException.class, () -> runNetBeansMojo.execute());
        assertEquals("Failed executing NetBeans", executionfailure.getMessage());
    }

}
