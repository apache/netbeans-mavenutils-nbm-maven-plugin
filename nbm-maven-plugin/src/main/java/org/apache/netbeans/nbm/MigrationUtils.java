/*
 * Copyright 2026 The Apache Software Foundation.
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
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.util.DefaultFileSet;

/**
 *
 */
public class MigrationUtils {

    /**
     * replace addDirectory to addFileSet in plexus archiver will be removed 
     */
    static FileSet getFileSet(File jarDirectory, Archiver archiver, String[] includes, String[] excludes) {
        return DefaultFileSet.fileSet(jarDirectory).prefixed("").includeExclude(includes, excludes).includeEmptyDirs(archiver.getIncludeEmptyDirs());
    }

    /**
     * will be removed in maven 4.0
     */
    static List<Artifact> getRuntimeArtifacts(MavenProject project) {
        return project.getRuntimeArtifacts();
    }

}
