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
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.FileSet;
import org.codehaus.plexus.archiver.util.DefaultFileSet;

public class MavenInfraUtils {

    private MavenInfraUtils() {
        // utility class
    }

    /**
     * Builds a {@link FileSet} over the given directory.
     *
     * <p>
     * Entry names stay relative to {@code jarDirectory} (empty prefix).
     * Optional Ant-style include/exclude path patterns are applied by the
     * directory scanner; empty directories are included or skipped according to
     * the archiver's {@code includeEmptyDirs} setting.</p>
     *
     * @param jarDirectory base directory to scan
     * @param archiver the archiver this file set is added to, used for its
     * {@code includeEmptyDirs} setting
     * @param includes Ant-style include patterns, or {@code null} to include
     * all files
     * @param excludes Ant-style exclude patterns, or {@code null} for none
     * @return a {@link FileSet} covering {@code jarDirectory}
     */
    static FileSet getFileSet(File jarDirectory, Archiver archiver, String[] includes, String[] excludes) {
        return DefaultFileSet.fileSet(jarDirectory).prefixed("").includeExclude(includes, excludes).includeEmptyDirs(archiver.getIncludeEmptyDirs());
    }

    /**
     * Returns this project's dependency artifacts in all scopes, including
     * transitive dependencies.
     */
    static List<Artifact> getRuntimeArtifacts(MavenProject project) {
        return project.getRuntimeArtifacts();
    }

    /**
     * Returns this project's runtime-scope dependencies (compile + runtime),
     * including transitive dependencies, as a list of artifacts.
     */
    static Set<Artifact> getDependencyArtifacts(MavenProject project) {
        return project.getDependencyArtifacts();
    }
}
