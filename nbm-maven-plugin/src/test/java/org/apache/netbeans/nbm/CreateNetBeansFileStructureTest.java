package org.apache.netbeans.nbm;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.netbeans.nbm.stubs.ArtifactHandlerManagerStub;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

public class CreateNetBeansFileStructureTest extends AbstractMojoTestCase {

    public void testWriteExternal() throws Exception {

        String version = "4.13.2";  // TODO must be in local repo downloaded by other means -> fix this test!

        String localRepository = System.getProperty("localRepository");
        String path = "junit/junit/" + version + "/junit-" + version + ".jar";
        File junitFile  = new File(new File(localRepository), path);
        Artifact a = new DefaultArtifact("junit", "junit", "jar", version).setFile(junitFile);
        StringWriter w = new StringWriter();
        CreateNetBeansFileStructure.writeExternal(new Artifacts(new ArtifactHandlerManagerStub()), new PrintWriter(w), a);
        assertEquals(
                "CRC:1161534166\n"
                + "SIZE:384581\n"
                + "URL:m2:/junit:junit:" + version + ":jar\n"
                + "URL:http://repo.maven.apache.org/maven2/junit/junit/" + version + "/junit-" + version + ".jar\n",
                w.toString()
        );
    }

}
