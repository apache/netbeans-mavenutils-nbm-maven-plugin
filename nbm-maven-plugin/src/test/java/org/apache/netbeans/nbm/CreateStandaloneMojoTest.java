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
import org.junit.jupiter.api.Test;

/**
 *
 */
@MojoTest
class CreateStandaloneMojoTest {

    @Test
    @InjectMojo(goal = "standalone-zip", pom = "src/test/resources/unit/standalone-zip-simple/plugin-config.xml")

    void testInvalidPackaging(CreateStandaloneMojo createStandaloneMojo) throws Exception {
        String branding = "mybrand";

        MojoExtension.setVariableValueToObject(createStandaloneMojo, "brandingToken", branding);

        Map<String, Object> variablesAndValuesFromObject = MojoExtension.getVariablesAndValuesFromObject(createStandaloneMojo);
        File output = new File(variablesAndValuesFromObject.get("outputDirectory").toString());
        output.mkdirs();
        File outputbrand = new File(output, branding);
        outputbrand.mkdirs();
        File outputbin = new File(outputbrand, "bin");
        outputbin.mkdirs();
        createStandaloneMojo.execute();// 
        //  assertNotNull(createclusterappMojo);
        //  MojoExecutionException assertThrows = assertThrows(MojoExecutionException.class, () -> createclusterappMojo.execute());
        //  assertEquals("This goal only makes sense on project with nbm-application packaging", assertThrows.getMessage());
    }

}
