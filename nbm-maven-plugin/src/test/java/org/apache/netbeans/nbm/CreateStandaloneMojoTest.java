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
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import static org.codehaus.plexus.PlexusTestCase.getBasedir;
import static org.junit.Assert.assertThrows;
import org.mockito.Mockito;

/**
 *
 */
public class CreateStandaloneMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/standalone-zip-simple/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateStandaloneMojo createStandaloneMojo = (CreateStandaloneMojo) lookupMojo("standalone-zip", pom);
        setVariableValueToObject(createStandaloneMojo, "brandingToken", "mybrand");
        Map<String, Object> variablesAndValuesFromObject = getVariablesAndValuesFromObject(createStandaloneMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        File outputbrand = new File(output, "mybrand");
        outputbrand.mkdirs();
        File outputbin = new File(outputbrand, "bin");
        outputbin.mkdirs();
        createStandaloneMojo.execute();// 
        //  assertNotNull(createclusterappMojo);
        //  MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclusterappMojo.execute());
        //  assertEquals("This goal only makes sense on project with nbm-application packaging", assertThrows.getMessage());
    }

}
