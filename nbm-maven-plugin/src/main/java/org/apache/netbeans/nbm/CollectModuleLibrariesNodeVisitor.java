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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.netbeans.nbm.utils.ExamineManifest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

/**
 * A dependency node visitor that collects visited nodes that are known
 * libraries or are children of known libraries
 *
 * @author Milos Kleint
 */
public class CollectModuleLibrariesNodeVisitor extends DependencyVisitorSupport {

    /**
     * The collected list of nodes.
     */
    private final Map<String, List<Artifact>> directNodes;

    private final Map<String, List<Artifact>> transitiveNodes;

    private final Map<String, Artifact> artifacts;

    private final Map<Artifact, ExamineManifest> examinerCache;

    private final DependencyNode root;

    private final Stack<String> currentModule = new Stack<String>();

    private static final String LIB_ID = "!@#$%^&ROOT";

    private final boolean useOSGiDependencies;

    private MojoExecutionException throwable;

    /**
     * Creates a dependency node visitor that collects visited nodes for further
     * processing.
     *
     * @param runtimeArtifacts list of runtime artifacts
     * @param examinerCache cache of netbeans manifest for artifacts
     * @param log mojo logger
     * @param root dependency to start collect with
     * @param useOSGiDependencies whether to allow osgi dependencies or not
     */
    public CollectModuleLibrariesNodeVisitor(Artifacts helper,
            Collection<Artifact> runtimeArtifacts, Map<Artifact, ExamineManifest> examinerCache,
            Log log, DependencyNode root, boolean useOSGiDependencies) {
        super(log, helper);
        directNodes = new HashMap<>();
        transitiveNodes = new HashMap<>();
        artifacts = new HashMap<>();
        for (Artifact a : runtimeArtifacts) {
            artifacts.put(ArtifactIdUtils.toVersionlessId(a), a);
        }
        this.examinerCache = examinerCache;
        this.root = root;
        this.useOSGiDependencies = useOSGiDependencies;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visitEnter(DependencyNode node) {
        if (throwable != null) {
            return false;
        }
        if (root == node) {
            return true;
        }
        try {
            Artifact artifact = node.getArtifact();
            if (!artifacts.containsKey(ArtifactIdUtils.toVersionlessId(artifact))) {
                //ignore non-runtime stuff..
                return false;
            }
            // somehow the transitive artifacts in the  tree are not always resolved?
            artifact = artifacts.get(ArtifactIdUtils.toVersionlessId(artifact));

            ExamineManifest depExaminator = examinerCache.get(artifact);
            if (depExaminator == null) {
                depExaminator = new ExamineManifest(log);
                depExaminator.setArtifactFile(artifact.getFile());
                depExaminator.checkFile();
                examinerCache.put(artifact, depExaminator);
            }
            if (depExaminator.isNetBeansModule() || (useOSGiDependencies && depExaminator.isOsgiBundle())) {
                currentModule.push(ArtifactIdUtils.toVersionlessId(artifact));
                ArrayList<Artifact> arts = new ArrayList<Artifact>();
                arts.add(artifact);
                if (currentModule.size() == 1) {
                    directNodes.put(currentModule.peek(), arts);
                } else {
                    transitiveNodes.put(currentModule.peek(), arts);
                }
                return true;
            }
            if (!currentModule.isEmpty()) {
                ////MNBMODULE-95 we are only interested in the module owned libraries
                if (!currentModule.peek().startsWith(LIB_ID)
                        && matchesLibrary(artifact, node.getDependency().getScope(), Collections.emptyList(), depExaminator,
                                        useOSGiDependencies)) {
                    if (currentModule.size() == 1) {
                        directNodes.get(currentModule.peek()).add(artifact);
                    } else {
                        transitiveNodes.get(currentModule.peek()).add(artifact);
                    }
                    // if a library, iterate to it's child nodes.
                    return true;
                }
            } else {
                //MNBMODULE-95 we check the non-module dependencies to see if they
                // depend on modules/bundles. these bundles are transitive, so
                // we add the root module as the first currentModule to keep
                //any bundle/module underneath it as transitive
                currentModule.push(LIB_ID + ArtifactIdUtils.toVersionlessId(artifact));
            }
        } catch (MojoExecutionException mojoExecutionException) {
            throwable = mojoExecutionException;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean visitLeave(DependencyNode node) {
        if (throwable != null) {
            return false;
        }
        if (!currentModule.empty()
                && (currentModule.peek().equals(ArtifactIdUtils.toVersionlessId(node.getArtifact()))
                || currentModule.peek().equals(LIB_ID + ArtifactIdUtils.toVersionlessId(node.getArtifact())))) {
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
    public Map<String, List<Artifact>> getDeclaredArtifacts()
            throws MojoExecutionException {
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
    public Map<String, List<Artifact>> getTransitiveArtifacts()
            throws MojoExecutionException {
        if (throwable != null) {
            throw throwable;
        }
        return transitiveNodes;
    }
}
