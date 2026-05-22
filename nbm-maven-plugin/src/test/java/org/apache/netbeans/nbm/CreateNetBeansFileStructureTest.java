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

import org.apache.netbeans.nbm.stubs.ArtifactHandlerManagerStub;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class CreateNetBeansFileStructureTest {

    @Test
    void testWriteExternal() throws Exception {

        String version = "6.1.0";  // TODO must be in local repo downloaded by other means -> fix this test!

        String localRepository = System.getProperty("localRepository");
        String path = "org/junit/jupiter/junit-jupiter/" + version + "/junit-jupiter-" + version + ".jar";
        File junitFile = new File(new File(localRepository), path);
        Artifact a = new DefaultArtifact("org.junit.jupiter", "junit-jupiter", "jar", version).setFile(junitFile);
        StringWriter w = new StringWriter();
        CreateNetBeansFileStructure.writeExternal(new Artifacts(new ArtifactHandlerManagerStub()), new PrintWriter(w), a);
        assertEquals(
                "CRC:1362182011\n"
                + "SIZE:6375\n"
                + "URL:m2:/org.junit.jupiter:junit-jupiter:" + version + ":jar\n"
                + "URL:http://repo.maven.apache.org/maven2/org/junit/jupiter/junit-jupiter/" + version + "/junit-jupiter-" + version + ".jar\n",
                w.toString()
        );
    }

}
