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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.netbeans.nbm.utils.ExamineManifest;

/**
 * A dependency node visitor that collects visited nodes that are known
 * libraries or are children of known libraries
 *
 * @author Milos Kleint
 */
public class CollectLibrariesNodeVisitor implements DependencyNodeVisitor {

    /**
     * The collected list of nodes.
     */
    private final List<Artifact> nodes;

    private Map<String, Artifact> artifacts;

    private Map<Artifact, ExamineManifest> examinerCache;

    private List<String> explicitLibs;

    private final Log log;

    private MojoExecutionException throwable;

    private DependencyNode root;

    private Set<String> duplicates;

    private Set<String> conflicts;

    private Set<String> includes;

    private final boolean useOsgiDependencies;

    /**
     * Creates a dependency node visitor that collects visited nodes for further
     * processing.
     *
     * @param explicitLibraries list of explicit libraries
     * @param runtimeArtifacts list of runtime artifacts
     * @param examinerCache cache of netbeans manifest for artifacts
     * @param log mojo logger
     * @param root dependency to start collect with
     * @param useOsgiDependencies whether to allow osgi dependencies or not
     */
    public CollectLibrariesNodeVisitor(List<String> explicitLibraries,
            List<Artifact> runtimeArtifacts, Map<Artifact, ExamineManifest> examinerCache,
            Log log, DependencyNode root, boolean useOsgiDependencies) {
        nodes = new ArrayList<>();
        artifacts = new HashMap<>();
        for (Artifact a : runtimeArtifacts) {
            artifacts.put(a.getDependencyConflictId(), a);
        }
        this.examinerCache = examinerCache;
        this.explicitLibs = explicitLibraries;
        this.log = log;
        this.root = root;
        this.useOsgiDependencies = useOsgiDependencies;
        duplicates = new HashSet<String>();
        conflicts = new HashSet<String>();
        includes = new HashSet<String>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
                //ignore non-runtime stuff..
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
            if (AbstractNbmMojo.matchesLibrary(artifact, explicitLibs, depExaminator, log, useOsgiDependencies)) {
                if (depExaminator.isNetBeansModule()) {
                    log.warn("You are using a NetBeans Module as a Library (classpath extension): " + artifact.getId());
                }

                nodes.add(artifact);
                includes.add(artifact.getDependencyConflictId());
                // if a library, iterate to it's child nodes.
                return true;
            }
        } catch (MojoExecutionException mojoExecutionException) {
            throwable = mojoExecutionException;
        }
        //don't bother iterating to childs if the current node is not a library.
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean endVisit(DependencyNode node) {
        if (throwable != null) {
            return false;
        }
        if (node == root) {
            if (!nodes.isEmpty()) {
                log.info("Adding on module's Class-Path:");
                for (Artifact inc : nodes) {
                    log.info("    " + inc.getId());
                }
            }
        }
        return true;
    }

    /**
     * Gets the list of collected dependency nodes.
     *
     * @return the list of collected dependency nodes
     * @throws MojoExecutionException if a throwable is set
     */
    public List<Artifact> getArtifacts() throws MojoExecutionException {
        if (throwable != null) {
            throw throwable;
        }
        return nodes;
    }
}
