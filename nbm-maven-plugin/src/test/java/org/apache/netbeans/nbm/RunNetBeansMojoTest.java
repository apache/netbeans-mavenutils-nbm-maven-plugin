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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Assert;

/**
 *
 */
public class RunNetBeansMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/run-ide-jar/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        RunNetBeansMojo runNetBeansMojo = (RunNetBeansMojo) lookupMojo("run-ide", pom);
        assertNotNull(runNetBeansMojo);
        File userdir = new File(getBasedir(), "target/test-classes/unit/run-ide-jar/userdir");
        setVariableValueToObject(runNetBeansMojo, "netbeansUserdir", userdir);
        File clusterdir = new File(getBasedir(), "target/test-classes/unit/run-ide-jar/clusterdir");
        setVariableValueToObject(runNetBeansMojo, "clusterBuildDir", clusterdir);
        MojoExecutionException incorrectpackaging = Assert.assertThrows(MojoExecutionException.class, () -> runNetBeansMojo.execute());
        assertEquals("No clusters to include in execution found. Please run the nbm:cluster or nbm:cluster-app goals before this one.", incorrectpackaging.getMessage());
    }

    public void testValidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/run-ide-nbm-application/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        RunNetBeansMojo runNetBeansMojo = (RunNetBeansMojo) lookupMojo("run-ide", pom);
        assertNotNull(runNetBeansMojo);
        File userdir = new File(getBasedir(), "target/test-classes/unit/run-ide-nbm-application/userdir");
        setVariableValueToObject(runNetBeansMojo, "netbeansUserdir", userdir);
        File clusterdir = new File(getBasedir(), "target/test-classes/unit/run-ide-nbm-application/clusterdir");
        clusterdir.mkdirs();
        File aclusterdir = new File(clusterdir, "acluster");
        aclusterdir.mkdirs();
        File bclusterdir = new File(clusterdir, "bcluster.test");
        bclusterdir.createNewFile();
        setVariableValueToObject(runNetBeansMojo, "clusterBuildDir", clusterdir);
        MojoExecutionException executionfailure = Assert.assertThrows(MojoExecutionException.class, () -> runNetBeansMojo.execute());
        assertEquals("Failed executing NetBeans", executionfailure.getMessage());
    }

}
