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

import org.apache.maven.plugin.logging.Log;
import org.apache.netbeans.nbm.utils.ExamineManifest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.artifact.JavaScopes;

import java.util.List;


/**
 * Visitor support class.
 */
public abstract class DependencyVisitorSupport implements DependencyVisitor {
    protected final Log log;
    protected final Artifacts artifacts;

    public DependencyVisitorSupport(Log log, Artifacts artifacts) {
        this.log = log;
        this.artifacts = artifacts;
    }

    protected boolean matchesLibrary(Artifact artifact, String scope, List<String> libraries, ExamineManifest depExaminator, boolean useOsgiDependencies) {
        String artId = artifact.getArtifactId();
        String grId = artifact.getGroupId();
        String id = grId + ":" + artId;
        boolean explicit = libraries.remove(id);
        if (explicit) {
            log.debug(id + " included as module library, explicitly declared in module descriptor.");
            return explicit;
        }
        if (JavaScopes.PROVIDED.equals(scope) || JavaScopes.SYSTEM.equals(scope)) {
            log.debug(id + " omitted as module library, has scope 'provided/system'");
            return false;
        }
        if ("nbm".equals(artifacts.getArtifactType(artifact).getId())) {
            return false;
        }
        if (depExaminator.isNetBeansModule() || (useOsgiDependencies && depExaminator.isOsgiBundle())) {
            //TODO I can see how someone might want to include an osgi bundle as library, not dependency.
            // I guess it won't matter much in 6.9+, in older versions it could be a problem.
            return false;
        }
        log.debug(id + " included as module library, squeezed through all the filters.");
        return true;
    }
}
