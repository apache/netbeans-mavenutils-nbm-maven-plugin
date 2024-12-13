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
import java.util.Arrays;
import java.util.Map;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import static org.codehaus.plexus.PlexusTestCase.getBasedir;
import org.junit.Assert;
import static org.junit.Assert.assertThrows;
import org.mockito.Mockito;

/**
 *
 */
public class CreateWebstartAppMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/webstart-app-simple/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateWebstartAppMojo createWebstartAppMojo = (CreateWebstartAppMojo) lookupMojo("webstart-app", pom);
        setVariableValueToObject(createWebstartAppMojo, "brandingToken", "mybrand");
        Map<String, Object> variablesAndValuesFromObject = getVariablesAndValuesFromObject(createWebstartAppMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        File outputbrand = new File(output, "mybrand");
        outputbrand.mkdirs();
        File outputbin = new File(outputbrand, "bin");
        outputbin.mkdirs();
        MojoExecutionException incorrectpackaging = Assert.assertThrows(MojoExecutionException.class, () -> createWebstartAppMojo.execute());
        assertEquals("This goal only makes sense on project with nbm-application packaging.", incorrectpackaging.getMessage());
    
    }

}
