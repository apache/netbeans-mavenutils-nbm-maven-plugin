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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

/**
 *
 */
public class CreateNbmMojoTest extends AbstractMojoTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testInvalidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/nbm-pom/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateNbmMojo createnbmmojo = (CreateNbmMojo) lookupMojo("nbm", pom);
        assertNotNull(createnbmmojo);
        createnbmmojo.execute();
    }

    public void testValidPackaging() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/nbm-nbm/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateNbmMojo createnbmmojo = (CreateNbmMojo) lookupMojo("nbm", pom);
        assertNotNull(createnbmmojo);
        setVariableValueToObject(createnbmmojo, "codeNameBase", "my.group.my-artifact");
        setVariableValueToObject(createnbmmojo, "finalName", "foo");
        File dummyHelper = new File(getBasedir(), "target/test-classes/unit/nbm-nbm/notexisting.help");
        setVariableValueToObject(createnbmmojo, "nbmJavahelpSource", dummyHelper);
        createDummyJarApp("project-nbm-nbm");
        createnbmmojo.execute();
    }

    public void testValidPackagingWithContent() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/nbm-nbm-withcontent/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateNbmMojo createnbmmojo = (CreateNbmMojo) lookupMojo("nbm", pom);
        assertNotNull(createnbmmojo);
        setVariableValueToObject(createnbmmojo, "codeNameBase", "my.group.my-artifact");
        setVariableValueToObject(createnbmmojo, "finalName", "foo");
        File dummyHelper = new File(getBasedir(), "target/test-classes/unit/nbm-nbm-withcontent/notexisting.help");
        setVariableValueToObject(createnbmmojo, "nbmJavahelpSource", dummyHelper);
        createDummyJarApp("project-nbm-nbm-withcontent");
        createnbmmojo.execute();
    }

    public void testValidPackagingWithContentSite() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/nbm-nbm-withcontent-site/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateNbmMojo createnbmmojo = (CreateNbmMojo) lookupMojo("nbm", pom);
        assertNotNull(createnbmmojo);
        setVariableValueToObject(createnbmmojo, "distributionUrl", "https://bit.netbeans.org/testme");
        setVariableValueToObject(createnbmmojo, "codeNameBase", "my.group.my-artifact");
        setVariableValueToObject(createnbmmojo, "finalName", "foo");
        File dummyHelper = new File(getBasedir(), "target/test-classes/unit/nbm-nbm-withcontent-site/notexisting.help");
        setVariableValueToObject(createnbmmojo, "nbmJavahelpSource", dummyHelper);
        createDummyJarApp("project-nbm-nbm-withcontent-site");
        createnbmmojo.execute();
    }

    public void testValidPackagingWithContentSiteAlt() throws Exception {
        File pom = new File(getBasedir(), "target/test-classes/unit/nbm-nbm-withcontent-site/plugin-config.xml");
        assertNotNull(pom);
        assertTrue(pom.exists());
        CreateNbmMojo createnbmmojo = (CreateNbmMojo) lookupMojo("nbm", pom);
        assertNotNull(createnbmmojo);
        setVariableValueToObject(createnbmmojo, "distributionUrl", "id::default::url");
        setVariableValueToObject(createnbmmojo, "codeNameBase", "my.group.my-artifact");
        setVariableValueToObject(createnbmmojo, "finalName", "foo");
        File dummyHelper = new File(getBasedir(), "target/test-classes/unit/nbm-nbm-withcontent-site/notexisting.help");
        setVariableValueToObject(createnbmmojo, "nbmJavahelpSource", dummyHelper);
        createDummyJarApp("project-nbm-nbm-withcontent-site");
        createnbmmojo.execute();
    }

    private void createDummyJarApp(String folder) throws IOException {
        File file = new File(getBasedir(), "target/test-harness/" + folder + "/target/foo.jar");
        file.getParentFile().mkdirs();
        file.createNewFile();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module"), "test");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module-Name"), "test");
        manifest.getMainAttributes().put(new Attributes.Name("OpenIDE-Module-Specification-Version"), "test");
        try (FileOutputStream fos = new FileOutputStream(file); JarOutputStream jos = new JarOutputStream(fos, manifest);) {
            JarEntry jarAdd = new JarEntry("entry1");
            jos.putNextEntry(jarAdd);
            jos.closeEntry();
        }
    }

}
