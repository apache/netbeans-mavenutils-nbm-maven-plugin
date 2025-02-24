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
import org.junit.Test;

/**
 *
 */
public class BrandingMojoTest extends AbstractMojoTestCase {

    private final String BRANDING_SOURCES = "target/nofolder.dummy";
    private final String BRANDING_SOURCES_FOLDER = "target/folder/";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/basic-branding-jar/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        BrandingMojo brandingMojo = (BrandingMojo) lookupMojo("branding", pom);
        assertNotNull(brandingMojo);
        File specificLocalRepositoryPath = new File(getBasedir() + "/" + BRANDING_SOURCES);
        setVariableValueToObject(brandingMojo, "brandingSources", specificLocalRepositoryPath);
        brandingMojo.execute();
    }

    @Test
    public void testInvalidPackagingFolder() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/basic-branding-jar/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        BrandingMojo brandingMojo = (BrandingMojo) lookupMojo("branding", pom);
        assertNotNull(brandingMojo);
        File specificLocalRepositoryPath = new File(getBasedir() + "/" + BRANDING_SOURCES_FOLDER);
        specificLocalRepositoryPath.mkdirs();
        setVariableValueToObject(brandingMojo, "brandingSources", specificLocalRepositoryPath);
        MojoExecutionException nobrandingmojoexecutionexception = Assert.assertThrows(MojoExecutionException.class, () -> brandingMojo.execute());
        assertEquals("brandingToken must be defined for mojo:branding", nobrandingmojoexecutionexception.getMessage());
    }

    @Test
    public void testInvalidPackaging3() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/basic-branding-jar/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        BrandingMojo brandingMojo = (BrandingMojo) lookupMojo("branding", pom);
        assertNotNull(brandingMojo);
        File specificLocalRepositoryPath = new File(getBasedir() + "/" + BRANDING_SOURCES_FOLDER);
        specificLocalRepositoryPath.mkdirs();
        setVariableValueToObject(brandingMojo, "brandingSources", specificLocalRepositoryPath);
        setVariableValueToObject(brandingMojo, "brandingToken", "mybrandingtoken");
        brandingMojo.execute();
    }

    @Test
    public void testValidPackaging3() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/basic-branding-nbm/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        BrandingMojo brandingMojo = (BrandingMojo) lookupMojo("branding", pom);
        assertNotNull(brandingMojo);
        setVariableValueToObject(brandingMojo, "brandingToken", "mybrandingtoken");
        brandingMojo.execute();
    }
}
