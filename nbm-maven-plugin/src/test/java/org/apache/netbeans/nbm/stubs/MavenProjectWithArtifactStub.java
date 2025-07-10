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
package org.apache.netbeans.nbm.stubs;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;

/**
 *
 * @author skygo
 */
public class MavenProjectWithArtifactStub extends MavenProjectStub {

    public MavenProjectWithArtifactStub() {
        setArtifactId("myname");
        setGroupId("foo");
        setVersion("1.2.3.4");
        setArtifact(new ArtifactStub() {
            @Override
            public String getArtifactId() {
                return "myname";
            }

            @Override
            public String getGroupId() {
                return "foo";
            }

            @Override
            public String getVersion() {
                return "1.2.3.4";
            }

            @Override
            public VersionRange getVersionRange() {
                return VersionRange.createFromVersion(getVersion());
            }

        });
    }

    @Override
    public List<Artifact> getRuntimeArtifacts() {
        List<Artifact> arrayList = new ArrayList<>();
        ArtifactStub stub1 =  new ArtifactStub() {
            @Override
            public ArtifactHandler getArtifactHandler() {
                return new DefaultArtifactHandlerStub("type", "classifier");
            }
        };
        stub1.setArtifactId("myname");
        stub1.setGroupId("foo");
        stub1.setVersion("1.2.3.4");
        stub1.setVersionRange(VersionRange.createFromVersion("1.2.3.4"));
        stub1.setType("type");
        arrayList.add(stub1);

        ArtifactStub stub2 =  new ArtifactStub() {
            @Override
            public ArtifactHandler getArtifactHandler() {
                return new DefaultArtifactHandlerStub("type", "classifier");
            }
        };
        stub2.setArtifactId("myname");
        stub2.setGroupId("foo");
        stub2.setVersion("1.2.3.4");
        stub1.setVersionRange(VersionRange.createFromVersion("1.2.3.4"));
        stub2.setType("type");
        arrayList.add(stub2);

        return arrayList;
    }

}
