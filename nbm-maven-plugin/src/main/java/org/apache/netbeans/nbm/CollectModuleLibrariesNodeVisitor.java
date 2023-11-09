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
package org.apache.netbeans.nbm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.netbeans.nbm.utils.ExamineManifest;

/**
 * A dependency node visitor that collects visited nodes that are known libraries or are children of known libraries
 *
 * @author Milos Kleint
 */
public class CollectModuleLibrariesNodeVisitor implements DependencyNodeVisitor {

    /**
     * The collected list of nodes.
     */
    private final Map<String, List<Artifact>> directNodes;

    private final Map<String, List<Artifact>> transitiveNodes;

    private Map<String, Artifact> artifacts;

    private Map<Artifact, ExamineManifest> examinerCache;

    private final Log log;

    private MojoExecutionException throwable;

    private DependencyNode root;

    private Stack<String> currentModule = new Stack<String>();
    private static final String LIB_ID = "!@#$%^&ROOT";

    private final boolean useOSGiDependencies;

    /**
     * Creates a dependency node visitor that collects visited nodes for further processing.
     *
     * @param runtimeArtifacts list of runtime artifacts
     * @param examinerCache cache of netbeans manifest for artifacts
     * @param log mojo logger
     * @param root dependency to start collect with
     * @param useOSGiDependencies whether to allow osgi dependencies or not
     */
    public CollectModuleLibrariesNodeVisitor(
            List<Artifact> runtimeArtifacts,
            Map<Artifact, ExamineManifest> examinerCache,
            Log log,
            DependencyNode root,
            boolean useOSGiDependencies) {
        directNodes = new HashMap<String, List<Artifact>>();
        transitiveNodes = new HashMap<String, List<Artifact>>();
        artifacts = new HashMap<String, Artifact>();
        for (Artifact a : runtimeArtifacts) {
            artifacts.put(a.getDependencyConflictId(), a);
        }
        this.examinerCache = examinerCache;
        this.log = log;
        this.root = root;
        this.useOSGiDependencies = useOSGiDependencies;
    }

    /**
     * {@inheritDoc}
     */
    public boolean visit(DependencyNode node) {
        if (throwable != null) {
            return false;
        }
        if (root == node) {
            return true;
        }
        try {
            Artifact artifact = node.getArtifact();
            if (!artifacts.containsKey(artifact.getDependencyConflictId())) {
                // ignore non-runtime stuff..
                return false;
            }
            // somehow the transitive artifacts in the  tree are not always resolved?
            artifact = artifacts.get(artifact.getDependencyConflictId());

            ExamineManifest depExaminator = examinerCache.get(artifact);
            if (depExaminator == null) {
                depExaminator = new ExamineManifest(log);
                depExaminator.setArtifactFile(artifact.getFile());
                depExaminator.checkFile();
                examinerCache.put(artifact, depExaminator);
            }
            if (depExaminator.isNetBeansModule() || (useOSGiDependencies && depExaminator.isOsgiBundle())) {
                currentModule.push(artifact.getDependencyConflictId());
                ArrayList<Artifact> arts = new ArrayList<Artifact>();
                arts.add(artifact);
                if (currentModule.size() == 1) {
                    directNodes.put(currentModule.peek(), arts);
                } else {
                    transitiveNodes.put(currentModule.peek(), arts);
                }
                return true;
            }
            if (currentModule.size() > 0) {
                //// MNBMODULE-95 we are only interested in the module owned libraries
                if (!currentModule.peek().startsWith(LIB_ID)
                        && AbstractNbmMojo.matchesLibrary(
                                artifact, Collections.<String>emptyList(), depExaminator, log, useOSGiDependencies)) {
                    if (currentModule.size() == 1) {
                        directNodes.get(currentModule.peek()).add(artifact);
                    } else {
                        transitiveNodes.get(currentModule.peek()).add(artifact);
                    }
                    // if a library, iterate to it's child nodes.
                    return true;
                }
            } else {
                // MNBMODULE-95 we check the non-module dependencies to see if they
                // depend on modules/bundles. these bundles are transitive, so
                // we add the root module as the first currentModule to keep
                // any bundle/module underneath it as transitive
                currentModule.push(LIB_ID + artifact.getDependencyConflictId());
            }
        } catch (MojoExecutionException mojoExecutionException) {
            throwable = mojoExecutionException;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean endVisit(DependencyNode node) {
        if (throwable != null) {
            return false;
        }
        if (!currentModule.empty()
                && (currentModule.peek().equals(node.getArtifact().getDependencyConflictId())
                        || currentModule
                                .peek()
                                .equals(LIB_ID + node.getArtifact().getDependencyConflictId()))) {
            currentModule.pop();
        }
        return true;
    }

    /**
     * modules declared in the project's pom
     *
     * @return a map of module artifact lists, key is the dependencyConflictId
     * @throws MojoExecutionException if an unexpected problem occurs
     */
    public Map<String, List<Artifact>> getDeclaredArtifacts() throws MojoExecutionException {
        if (throwable != null) {
            throw throwable;
        }
        return directNodes;
    }

    /**
     * modules that were picked up transitively
     *
     * @return a map of module artifact lists, key is the dependencyConflictId
     * @throws MojoExecutionException if an unexpected problem occurs
     */
    public Map<String, List<Artifact>> getTransitiveArtifacts() throws MojoExecutionException {
        if (throwable != null) {
            throw throwable;
        }
        return transitiveNodes;
    }
}
