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
import org.apache.maven.api.plugin.testing.MojoParameter;
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
class BrandingMojoTest {

    private final String BRANDING_SOURCES = "target/nofolder.dummy";
    private final String BRANDING_SOURCES_FOLDER = "target/folder/";

    @Test
    @InjectMojo(goal = "branding", pom = "src/test/resources/unit/basic-branding-jar/plugin-config.xml")
    @MojoParameter(name = "brandingSources", value = "${project.basedir}/" + BRANDING_SOURCES)
    void testInvalidPackaging(BrandingMojo brandingMojo) throws Exception {
        assertNotNull(brandingMojo);
        brandingMojo.execute();
    }

    @Test
    @InjectMojo(goal = "branding", pom = "src/test/resources/unit/basic-branding-jar/plugin-config.xml")
    @MojoParameter(name = "brandingSources", value = "${project.basedir}/" + BRANDING_SOURCES_FOLDER)
    void testInvalidPackagingFolder(BrandingMojo brandingMojo) throws Exception {
        assertNotNull(brandingMojo);
        File specificLocalRepositoryPath = (File) MojoExtension.getVariableValueFromObject(brandingMojo, "brandingSources");
        specificLocalRepositoryPath.mkdirs();
        MojoExecutionException nobrandingmojoexecutionexception = assertThrows(MojoExecutionException.class, () -> brandingMojo.execute());
        assertEquals("brandingToken must be defined for mojo:branding", nobrandingmojoexecutionexception.getMessage());
    }

    @Test
    @InjectMojo(goal = "branding", pom = "src/test/resources/unit/basic-branding-jar/plugin-config.xml")
    @MojoParameter(name = "brandingToken", value = "mybrandingtoken")
    @MojoParameter(name = "brandingSources", value = "${project.basedir}/" + BRANDING_SOURCES_FOLDER)
    void testInvalidPackaging3(BrandingMojo brandingMojo) throws Exception {
        assertNotNull(brandingMojo);
        brandingMojo.execute();
    }

    @Test
    @InjectMojo(goal = "branding", pom = "src/test/resources/unit/basic-branding-nbm/plugin-config.xml")
    @MojoParameter(name = "brandingToken", value = "mybrandingtoken")
    void testValidPackaging3(BrandingMojo brandingMojo) throws Exception {
        assertNotNull(brandingMojo);
        brandingMojo.execute();
    }
}
