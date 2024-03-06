package org.apache.netbeans.nbm.repository;

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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.netbeans.nbm.utils.AbstractNetbeansMojo;
import org.apache.netbeans.nbm.utils.ExamineManifest;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Input;
import org.apache.tools.ant.taskdefs.PathConvert;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * A goal for identifying NetBeans modules from the installation and populating
 * the local repository with them. Optionally you can also deploy to a remote
 * repository.
 * <p>
 * If you are looking for an existing remote repository for NetBeans artifacts,
 * check out <a href="https://search.maven.org/">Maven Central</a>.
 * <a href="https://netbeans.apidesign.org/maven2/">https://netbeans.apidesign.org/maven2/</a>,
 * contains contains API artifacts for older NetBeans releases.
 * <a href="https://repository.apache.org/content/groups/snapshots">https://repository.apache.org/content/groups/snapshots</a>
 * may also be used for <code>dev-SNAPSHOT</code> artifacts if you wish to test
 * development builds.
 * </p>
 * <p>
 * See this <a href="repository.html">HOWTO</a> on how to generate the NetBeans
 * binaries required by this goal.
 * </p>
 *
 * @author Milos Kleint
 */
@Mojo(name = "populate", aggregator = true, requiresProject = false)
public class PopulateRepositoryMojo
        extends AbstractNetbeansMojo {

    private static final String GROUP_API = ".api";
    private static final String GROUP_IMPL = ".modules";
    private static final String GROUP_EXTERNAL = ".external";
    private static final String GROUP_CLUSTER = ".cluster";

    /**
     * a prefix for groupId of generated content, eg. for org.netbeans value
     * will generate org.netbeans.cluster groupId for clusters and
     * org.netbeans.modules for module artifacts.
     *
     * @since 1.2
     */
    @Parameter(property = "groupIdPrefix", defaultValue = "org.netbeans")
    private String groupIdPrefix;

    /**
     * an url where to deploy the NetBeans artifacts. Optional, if not
     * specified, the artifacts will be only installed in local repository, if
     * you need to give credentials to access remote repo, the id of the server
     * is hardwired to "netbeans".
     */
    @Parameter(property = "deployUrl")
    private String deployUrl;

    /**
     * an string id representing the server
     */
    @Parameter(defaultValue = "netbeans", property = "deployId")
    private String deployId;

    /**
     * By default the generated metadata is installed in local repository.
     * Setting this parameter to false will avoid installing the bits. Only
     * meaningful together with a defined "deployUrl" parameter.
     *
     * @since 3.0
     */
    @Parameter(defaultValue = "false", property = "skipInstall")
    private boolean skipLocalInstall;

    /**
     * Location of NetBeans installation
     */
    @Parameter(property = "netbeansInstallDirectory", required = true)
    protected File netbeansInstallDirectory;

    /**
     * If you want to install/deploy also NetBeans api javadocs, download the
     * javadoc zip file from netbeans.org expand it to a directory, it should
     * contain multiple zip files. Define this parameter as absolute path to the
     * zip files folder.
     *
     */
    @Parameter(property = "netbeansJavadocDirectory")
    protected File netbeansJavadocDirectory;

    /**
     * Assumes a folder with &lt;code-name-base&gt;.zip files containing sources
     * for modules.
     */
    @Parameter(property = "netbeansSourcesDirectory")
    protected File netbeansSourcesDirectory;

    /**
     * If defined, will match the nbm files found in the designated folder with
     * the modules and upload the nbm file next to the module jar in local and
     * remote repositories.
     *
     * Assumes a folder with &lt;code-name-base&gt;.nbm files containing nbm
     * files for modules.
     *
     * @since 3.0
     */
    @Parameter(property = "netbeansNbmDirectory", required = true)
    protected File netbeansNbmDirectory;

    /**
     * When specified, will force all modules to have the designated version.
     * Good when depending on releases. Then you would for example specify
     * RELEASE50 in this parameter and all modules get this version in the
     * repository. If not defined, the maven version is derived from the
     * OpenIDE-Module-Specification-Version manifest attribute.
     * <p>
     * Highly Recommended!
     * </p>
     */
    @Parameter(property = "forcedVersion")
    protected String forcedVersion;

    /**
     * When specified it points to a file containing a merge of all
     * binaries-list sha1;coordinate;module Any dependencies not found this way,
     * will be generated with a unique id under the org.netbeans.external
     * groupId.
     * <p/>
     * @since 1.16
     */
    @Parameter(property = "externallist")
    private File externallist;

    /**
     * Whether to create cluster POMs in the {@code org.netbeans.cluster} group.
     * Only meaningful when {@code forcedVersion} is defined.
     *
     * @since 3.7
     */
    @Parameter(defaultValue = "true", property = "defineCluster")
    private boolean defineCluster;

    /**
     * Optional remote repository to use for inspecting remote dependencies.
     * This may be used to populate just part of an installation, when base
     * modules are already available in Maven format. Currently only supported
     * when {@code forcedVersion} is defined.
     *
     * @since 3.7
     */
    @Parameter(property = "dependencyRepositoryUrl")
    private String dependencyRepositoryUrl;

    /**
     * Repository ID to use when inspecting remote dependencies. Only meaningful
     * when {@code dependencyRepositoryUrl} is defined.
     *
     * @since 3.7
     */
    @Parameter(defaultValue = "temp", property = "dependencyRepositoryId")
    private String dependencyRepositoryId;

    /**
     * Colon separated artefact coordinate groupId:artefactId:version that
     * represent parent to be used
     *
     * @since 1.4
     */
    @Parameter(property = "parentGAV", required = false)
    private String parentGAV;

    /**
     * Maven session.
     */
    @Parameter(required = true, readonly = true, defaultValue = "${session}")
    protected MavenSession session;

    /**
     * Repository system.
     */
    @Component
    protected RepositorySystem repositorySystem;

    /**
     * Maven ArtifactHandlerManager
     *
     */
    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    // parent handler in case we have one
    private Parent artefactParent = null;

    @Override
    public void execute()
            throws MojoExecutionException {
        getLog().info("Populate repository with NetBeans modules");
        Project antProject = antProject();
        RemoteRepository deploymentRepository = null;

        if (parentGAV != null) {
            // populate artefactParent
            artefactParent = new Parent();
            String[] split = parentGAV.split(":");
            if (split.length != 3) {
                throw new MojoExecutionException(
                        "parentGAV should respect the following format groupId:artefactId:version");
            }
            artefactParent.setGroupId(split[0]);
            artefactParent.setArtifactId(split[1]);
            artefactParent.setVersion(split[2]);
        }

        if (deployUrl != null) {
            deploymentRepository = repositorySystem.newDeploymentRepository(session.getRepositorySession(), new RemoteRepository.Builder(deployId, "default", deployUrl).build());
        } else if (skipLocalInstall) {
            throw new MojoExecutionException(
                    "When skipping install to local repository, one shall define the deployUrl parameter");
        }

        if (netbeansInstallDirectory == null) {
            Input input = (Input) antProject.createTask("input");
            input.setMessage("Please enter NetBeans installation directory:");
            input.setAddproperty("installDir");
            try {
                input.execute();
            } catch (BuildException e) {
                getLog().error("Cannot run ant:input");
                throw new MojoExecutionException(e.getMessage(), e);
            }
            String prop = antProject.getProperty("installDir");
            netbeansInstallDirectory = new File(prop);
        }

        File rootDir = netbeansInstallDirectory;
        if (!rootDir.exists()) {
            getLog().error("NetBeans installation doesn't exist.");
            throw new MojoExecutionException("NetBeans installation doesn't exist.");
        }
        getLog().info("Copying NetBeans artifacts from " + netbeansInstallDirectory);

        PathConvert convert = (PathConvert) antProject.createTask("pathconvert");
        convert.setPathSep(",");
        convert.setProperty("netbeansincludes");
        FileSet set = new FileSet();
        set.setDir(rootDir);
        set.createInclude().setName("**/modules/*.jar");
        set.createInclude().setName("*/core/*.jar");
        set.createInclude().setName("platform*/lib/*.jar");

        convert.createPath().addFileset(set);
        try {
            convert.execute();
        } catch (BuildException e) {
            getLog().error("Cannot run ant:pathconvert");
            throw new MojoExecutionException(e.getMessage(), e);
        }

        String prop = antProject.getProperty("netbeansincludes");
        StringTokenizer tok = new StringTokenizer(prop, ",");
        Map<ModuleWrapper, Artifact> moduleDefinitions = new HashMap<>();
        Map<String, Collection<ModuleWrapper>> clusters = new HashMap<>();
        while (tok.hasMoreTokens()) {
            String token = tok.nextToken();
            File module = new File(token);
            String clust = module.getAbsolutePath().substring(rootDir.getAbsolutePath().length() + 1);
            clust = clust.substring(0, clust.indexOf(File.separator));
            ExamineManifest examinator = new ExamineManifest(getLog());
            examinator.setPopulateDependencies(true);
            examinator.setJarFile(module);
            examinator.checkFile();
            if (examinator.isNetBeansModule() || examinator.isOsgiBundle()) {
                //TODO get artifact id from the module's manifest?
                String artifact = module.getName().substring(0, module.getName().indexOf(".jar"));
                if ("boot".equals(artifact)) {
                    artifact = "org-netbeans-bootstrap";
                }
                if ("core".equals(artifact)) {
                    artifact = "org-netbeans-core-startup";
                }
                if ("core-base".equals(artifact)) {
                    artifact = "org-netbeans-core-startup-base";
                }
                String version = forcedVersion == null ? examinator.getSpecVersion() : forcedVersion;
                String group = groupIdPrefix + (examinator.isOsgiBundle() ? GROUP_EXTERNAL : examinator.hasPublicPackages() ? GROUP_API : GROUP_IMPL);
                Artifact art = createArtifact(artifact, version, group);
                ModuleWrapper wr = new ModuleWrapper(artifact, version, group, examinator, module);
                if (examinator.isOsgiBundle()) {
                    Dependency dep = findExternal(module);
                    if (dep != null) {

                        art = createArtifact(dep.getArtifactId(), dep.getVersion(), dep.getGroupId());
                        group = dep.getGroupId();
                        version = dep.getVersion();
                        wr = new ModuleWrapperMaven(artifact, version, group, examinator, module, dep);
                    }
                }
                wr.setCluster(clust);
                moduleDefinitions.put(wr, art);
                Collection<ModuleWrapper> col = clusters.get(clust);
                if (col == null) {
                    col = new ArrayList<>();
                    clusters.put(clust, col);
                }
                col.add(wr);
            }
        }

        File javadocRoot = null;
        if (netbeansJavadocDirectory != null) {
            javadocRoot = netbeansJavadocDirectory;
            if (!javadocRoot.exists()) {
                javadocRoot = null;
                throw new MojoExecutionException(
                        "The netbeansJavadocDirectory parameter doesn't point to an existing folder");
            }
        }
        File sourceRoot = null;
        if (netbeansSourcesDirectory != null) {
            sourceRoot = netbeansSourcesDirectory;
            if (!sourceRoot.exists()) {
                sourceRoot = null;
                throw new MojoExecutionException(
                        "The netbeansSourceDirectory parameter doesn't point to an existing folder");
            }
        }

        File nbmRoot = null;
        if (netbeansNbmDirectory != null) {
            nbmRoot = netbeansNbmDirectory;
            if (!nbmRoot.exists()) {
                nbmRoot = null;
                throw new MojoExecutionException(
                        "The nbmDirectory parameter doesn't point to an existing folder");
            }
        }
        List<ModuleWrapper> wrapperList = new ArrayList<>(moduleDefinitions.keySet());
        // artifact that we need to populate
        Map<ModuleWrapper, Artifact> tobePopulated = new HashMap<>();
        // external artefacts
        Map<ModuleWrapper, Artifact> oncentralWrapper = new HashMap<>();
        // triage
        for (Map.Entry<ModuleWrapper, Artifact> entry : moduleDefinitions.entrySet()) {
            if (entry.getKey() instanceof ModuleWrapperMaven) {
                oncentralWrapper.put(entry.getKey(), entry.getValue());
            } else {
                tobePopulated.put(entry.getKey(), entry.getValue());
            }
        }
        List<ExternalsWrapper> externals = new ArrayList<>();
        int count = tobePopulated.size() + 1;
        int index = 0;

        try {
            for (Map.Entry<ModuleWrapper, Artifact> elem : tobePopulated.entrySet()) {
                ModuleWrapper man = elem.getKey();
                Artifact art = elem.getValue();
                index = index + 1;
                getLog().info("Processing " + index + "/" + count);
                File pom = createMavenProject(man, wrapperList, externals);
                Artifact pomArt = new SubArtifact(art, "", "pom", pom);
                File javadoc = null;
                Artifact javadocArt = null;
                if (javadocRoot != null) {
                    File zip = new File(javadocRoot, art.getArtifactId() + ".zip");
                    if (zip.exists()) {
                        javadoc = zip;
                        javadocArt = createAttachedArtifact(art, javadoc, "jar", "javadoc");
                    }
                }
                File source = null;
                Artifact sourceArt = null;
                if (sourceRoot != null) {
                    File zip = new File(sourceRoot, art.getArtifactId() + ".zip");
                    if (zip.exists()) {
                        source = zip;
                        sourceArt = createAttachedArtifact(art, source, "jar", "sources");
                    }
                }
                File nbm = null;
                Artifact nbmArt = null;
                if (nbmRoot != null) {
                    File zip = new File(nbmRoot, art.getArtifactId() + ".nbm");

                    if (!zip.exists()) {
                        zip = new File(nbmRoot,
                                man.getCluster() + File.separator + art.getArtifactId() + ".nbm");
                    }
                    if (zip.exists()) {
                        nbm = zip;
                        nbmArt = createAttachedArtifact(art, nbm, "nbm-file", null);
                        if (nbmArt.getExtension().equals("nbm-file")) {
                            // Maven 2.x compatibility.
                            nbmArt = createAttachedArtifact(art, nbm, "nbm", null);
                        }
                        assert nbmArt.getExtension().equals("nbm");
                    }
                }
                File moduleJar = man.getFile();
                File moduleJarMinusCP = null;
                if (!man.getModuleManifest().getClasspath().isEmpty()) {
                    try {
                        moduleJarMinusCP = Files.createTempFile(man.getArtifact(), ".jar").toFile();
                        moduleJarMinusCP.deleteOnExit();
                        try (InputStream is = Files.newInputStream(moduleJar.toPath())) {
                            try (OutputStream os = Files.newOutputStream(moduleJarMinusCP.toPath())) {
                                JarInputStream jis = new JarInputStream(is);
                                Manifest mani = new Manifest(jis.getManifest());
                                mani.getMainAttributes().remove(Attributes.Name.CLASS_PATH);
                                if (!man.deps.isEmpty()) { // MNBMODULE-132
                                    StringBuilder b = new StringBuilder();
                                    for (Dependency dep : man.deps) {
                                        if (b.length() > 0) {
                                            b.append(' ');
                                        }
                                        b.append(dep.getGroupId()).append(':').append(dep.getArtifactId())
                                                .append(':').append(dep.getVersion());
                                        if (dep.getClassifier() != null) {
                                            b.append(":").append(dep.getClassifier());
                                        }
                                    }
                                    mani.getMainAttributes().putValue("Maven-Class-Path", b.toString());
                                } else {
                                    getLog().warn("did not find any external artifacts for " + man.getModule());
                                }
                                JarOutputStream jos = new JarOutputStream(os, mani);
                                JarEntry entry;
                                while ((entry = jis.getNextJarEntry()) != null) {
                                    if (entry.getName().matches("META-INF/.+[.]SF")) {
                                        throw new IOException("cannot handle signed JARs");
                                    }
                                    jos.putNextEntry(entry);
                                    byte[] buf = new byte[(int) entry.getSize()];
                                    int read = jis.read(buf, 0, buf.length);
                                    if (read != buf.length) {
                                        throw new IOException("read wrong amount");
                                    }
                                    jos.write(buf);
                                }
                                jos.close();
                            }
                        }
                    } catch (IOException x) {
                        getLog().warn("Could not process " + moduleJar + ": " + x, x);
                        moduleJarMinusCP.delete();
                        moduleJarMinusCP = null;
                    }
                }
                try {
                    if (!skipLocalInstall) {
                        install(pomArt.setFile(pom));
                        install(art.setFile(moduleJarMinusCP != null ? moduleJarMinusCP : moduleJar));
                        if (javadoc != null) {
                            install(javadocArt.setFile(javadoc));
                        }
                        if (source != null) {
                            install(sourceArt.setFile(source));
                        }
                        if (nbm != null) {
                            install(nbmArt.setFile(nbm));
                        }
                    }
                    try {
                        if (deploymentRepository != null) {
                            DeployRequest deployRequest = new DeployRequest();
                            deployRequest.setRepository(deploymentRepository);
                            deployRequest.setTrace(RequestTrace.newChild(null, "nb-repository-plugin"));
                            deployRequest.addArtifact(art.setFile(moduleJarMinusCP != null ? moduleJarMinusCP : moduleJar));
                            if (pom != null) {
                                deployRequest.addArtifact(pomArt.setFile(pom));
                            }
                            if (javadoc != null) {
                                deployRequest.addArtifact(javadocArt.setFile(javadoc));
                            }
                            if (source != null) {
                                deployRequest.addArtifact(sourceArt.setFile(source));
                            }
                            if (nbm != null) {
                                deployRequest.addArtifact(nbmArt.setFile(nbm));
                            }
                            repositorySystem.deploy(session.getRepositorySession(), deployRequest);
                        }
                    } catch (DeploymentException ex) {
                        throw new MojoExecutionException("Error Deploying artifact", ex);
                    }
                } finally {
                    if (moduleJarMinusCP != null) {
                        moduleJarMinusCP.delete();
                    }
                }
            }
        } finally {
            /*if ( searcher != null )
            {
                try
                {
                    searcher.close();
                }
                catch ( IOException ex )
                {
                    getLog().error( ex );
                }
            }*/
        }

        //process collected non-recognized external jars..
        if (!externals.isEmpty()) {
            index = 0;
            count = externals.size();
            for (ExternalsWrapper ex : externals) {
                Artifact art = createArtifact(ex.getArtifact(), ex.getVersion(), ex.getGroupid());
                index = index + 1;
                getLog().info("Processing external " + index + "/" + count);
                File pom = createExternalProject(ex);
                Artifact pomArt = new SubArtifact(art, "", "pom", pom);
                if (!skipLocalInstall) {
                    install(pomArt.setFile(pom));
                    install(art.setFile(ex.getFile()));
                }
                try {
                    if (deploymentRepository != null) {
                        DeployRequest deployRequest = new DeployRequest();
                        deployRequest.setRepository(deploymentRepository);
                        deployRequest.setTrace(RequestTrace.newChild(null, "nb-repository-plugin"));
                        deployRequest.addArtifact(pomArt.setFile(pom));
                        deployRequest.addArtifact(art.setFile(ex.getFile()));

                        repositorySystem.deploy(session.getRepositorySession(), deployRequest);
                    }
                } catch (DeploymentException exc) {
                    throw new MojoExecutionException("Error Deploying artifact", exc);
                }
            }
        }

        if (!defineCluster) {
            getLog().info("Not creating cluster POMs.");
        } else if (forcedVersion == null) {
            getLog().warn("Version not specified, cannot create cluster POMs.");
        } else {
            for (Map.Entry<String, Collection<ModuleWrapper>> elem : clusters.entrySet()) {
                String cluster = stripClusterName(elem.getKey());
                Collection<ModuleWrapper> modules = elem.getValue();
                getLog().info("Processing cluster " + cluster);
                Artifact art = createClusterArtifact(cluster, forcedVersion);
                File pom = createClusterProject(art, modules);
                if (!skipLocalInstall) {
                    install(art.setFile(pom));
                }
                try {
                    if (deploymentRepository != null) {
                        DeployRequest deployRequest = new DeployRequest();
                        deployRequest.setRepository(deploymentRepository);
                        deployRequest.setTrace(RequestTrace.newChild(null, "nb-repository-plugin"));
                        deployRequest.addArtifact(art.setFile(pom));

                        repositorySystem.deploy(session.getRepositorySession(), deployRequest);
                    }
                } catch (DeploymentException ex) {
                    throw new MojoExecutionException("Error Deploying artifact", ex);
                }
            }

        }
    }

    void install(Artifact art)
            throws MojoExecutionException {
        try {
            InstallRequest installRequest = new InstallRequest();
            installRequest.addArtifact(art);
            installRequest.setTrace(RequestTrace.newChild(null, "nb-repository-plugin"));
            repositorySystem.install(session.getRepositorySession(), installRequest);
        } catch (InstallationException e) {
            // TODO: install exception that does not give a trace
            throw new MojoExecutionException("Error installing artifact", e);
        }
    }

    //performs the same tasks as the MavenProjectHelper
    Artifact createAttachedArtifact(Artifact primary, File file, String type, String classifier) {
        assert type != null;

        ArtifactHandler handler;

        handler = artifactHandlerManager.getArtifactHandler(type);

        if (handler == null) {
            getLog().warn("No artifact handler for " + type);
            handler = artifactHandlerManager.getArtifactHandler("jar");
        }

        return new SubArtifact(primary, classifier, handler.getExtension(), null, file);
    }

    private File createMavenProject(ModuleWrapper wrapper, List<ModuleWrapper> wrapperList,
            List<ExternalsWrapper> externalsList)
            throws MojoExecutionException {
        Model mavenModel = new Model();

        mavenModel.setGroupId(wrapper.getGroup());
        mavenModel.setArtifactId(wrapper.getArtifact());
        mavenModel.setVersion(wrapper.getVersion());
        mavenModel.setPackaging("jar");
        mavenModel.setModelVersion("4.0.0");
        if (artefactParent != null) {
            mavenModel.setParent(artefactParent);
        }
        ExamineManifest man = wrapper.getModuleManifest();
        List<Dependency> deps = new ArrayList<>();
        if (!man.getDependencyTokens().isEmpty()) {
            for (String elem : man.getDependencyTokens()) {
                // create pseudo wrapper
                ModuleWrapper wr = new ModuleWrapper(elem);
                int index = wrapperList.indexOf(wr);
                if (index > -1) {
                    wr = wrapperList.get(index);
                    Dependency dep;
                    if (wr instanceof ModuleWrapperMaven) {
                        dep = ((ModuleWrapperMaven) wr).getDep();
                    } else {
                        dep = new Dependency();
                        dep.setArtifactId(wr.getArtifact());
                        dep.setGroupId(wr.getGroup());
                        dep.setVersion(wr.getVersion());
                    }
                    dep.setType("jar");
                    //we don't want the API modules to depend on non-api ones..
                    // otherwise the transitive dependency mechanism pollutes your classpath..
                    if (wrapper.getModuleManifest().hasPublicPackages()
                            && !wr.getModuleManifest().hasPublicPackages()) {
                        dep.setScope("runtime");
                    }
                    deps.add(dep);
                } else if (dependencyRepositoryUrl != null) {
                    Dependency dep = new Dependency();
                    dep.setType("jar");
                    String artifactId = elem.replace('.', '-');
                    dep.setArtifactId(artifactId);
                    if (forcedVersion == null) {
                        throw new MojoExecutionException("Cannot use dependencyRepositoryUrl without forcedVersion");
                    }
                    dep.setVersion(forcedVersion);
                    List<RemoteRepository> repos = repositorySystem.newResolutionRepositories(session.getRepositorySession(), Collections.singletonList(new RemoteRepository.Builder(dependencyRepositoryId, "default", dependencyRepositoryUrl).build()));
                    ArtifactRequest artifactRequest = new ArtifactRequest();
                    artifactRequest.setRequestContext("nb-repository-plugin");
                    artifactRequest.setRepositories(repos);
                    artifactRequest.setTrace(RequestTrace.newChild(null, "nb-repository-plugin"));
                    ArtifactResult artifactResult;
                    try {
                        artifactRequest.setArtifact(new DefaultArtifact(groupIdPrefix + GROUP_API, artifactId, "", "pom", forcedVersion));
                        artifactResult = repositorySystem.resolveArtifact(session.getRepositorySession(), artifactRequest);
                        dep.setGroupId(groupIdPrefix + GROUP_API);
                    } catch (ArtifactResolutionException x) {
                        try {
                            artifactRequest.setArtifact(new DefaultArtifact(groupIdPrefix + GROUP_IMPL, artifactId, "", "pom", forcedVersion));
                            artifactResult = repositorySystem.resolveArtifact(session.getRepositorySession(), artifactRequest);
                            dep.setGroupId(groupIdPrefix + GROUP_IMPL);
                            if (wrapper.getModuleManifest().hasPublicPackages()) {
                                dep.setScope("runtime");
                            }
                        } catch (ArtifactResolutionException x2) {
                            try {
                                artifactRequest.setArtifact(new DefaultArtifact(groupIdPrefix + GROUP_EXTERNAL, artifactId, "", "pom", forcedVersion));
                                artifactResult = repositorySystem.resolveArtifact(session.getRepositorySession(), artifactRequest);
                                dep.setGroupId(groupIdPrefix + GROUP_EXTERNAL);
                                if (wrapper.getModuleManifest().hasPublicPackages()) {
                                    dep.setScope("runtime");
                                }
                            } catch (ArtifactResolutionException x3) {
                                getLog().warn(x3);
                                throw new MojoExecutionException("No module found for dependency '" + elem + "'", x);
                            }

                        }

                    }
                    deps.add(dep);
                } else {
                    getLog().warn("No module found for dependency '" + elem + "'");
                }
            }
        }
        //need some generic way to handle Classpath: items.
        //how to figure the right version?
        String cp = wrapper.getModuleManifest().getClasspath();
        if (!cp.isEmpty()) {
            StringTokenizer tok = new StringTokenizer(cp);
            while (tok.hasMoreTokens()) {
                String path = tok.nextToken();
                File f = new File(wrapper.getFile().getParentFile(), path);
                if (f.exists()) {
                    Dependency dep = findExternal(f);
                    if (dep != null) {
                        deps.add(dep);
                        // XXX MNBMODULE-170: repack NBM with *.external
                    } else {
                        ExternalsWrapper ex = new ExternalsWrapper();
                        ex.setFile(f);
                        String artId = f.getName();
                        if (artId.endsWith(".jar")) {
                            artId = artId.substring(0, artId.length() - ".jar".length());
                        }
                        ex.setVersion(wrapper.getVersion());
                        ex.setArtifact(artId);
                        ex.setGroupid(groupIdPrefix + GROUP_EXTERNAL);
                        externalsList.add(ex);
                        dep = new Dependency();
                        dep.setArtifactId(artId);
                        dep.setGroupId(groupIdPrefix + GROUP_EXTERNAL);
                        dep.setVersion(wrapper.getVersion());
                        dep.setType("jar");
                        deps.add(dep);
                    }
                }
            }
        }

        wrapper.deps = deps;
        mavenModel.setDependencies(deps);
        FileWriter writer = null;
        File fil = null;
        try {
            MavenXpp3Writer xpp = new MavenXpp3Writer();
            fil = Files.createTempFile("maven", ".pom").toFile();
            fil.deleteOnExit();
            writer = new FileWriter(fil);
            xpp.write(writer, mavenModel);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException io) {
                    throw new UncheckedIOException(io);
                }
            }
        }
        return fil;
    }

    private Dependency findExternal(File f) {
        if (externallist == null) {
            return null;
        }
        try {
            List<String> content512 = Files.readAllLines(externallist.toPath());
            MessageDigest shaDig = MessageDigest.getInstance("SHA1");

            try (InputStream is = new FileInputStream(f); OutputStream os = new DigestOutputStream(new NullOutputStream(), shaDig);) {
                IOUtil.copy(is, os);
            }
            String sha1 = encode(shaDig.digest()).toUpperCase();
            for (String string : content512) {
                if (string.startsWith("#")) {
                    continue;
                }
                String[] split = string.split(";");
                if (split[0].equals(sha1) && split[1].contains(":")) {
                    Dependency dep = splitDependencyString(split[1]);
                    getLog().info("found match " + dep.getGroupId() + ":" + dep.getArtifactId() + ":" + dep.getVersion() + " for " + f.getName());
                    return dep;
                }
            }
            getLog().info("no repository match for " + f.getName() + f.getAbsolutePath() + " with sha " + sha1);
        } catch (Exception x) {
            getLog().error(x);
        }
        return null;
    }

    static Dependency splitDependencyString(String split) {
        String[] splits = split.split(":");
        Dependency dep = new Dependency();
        dep.setArtifactId(splits[1]);
        dep.setGroupId(splits[0]);
        dep.setVersion(splits[2]);
        dep.setType("jar");
        dep.setClassifier("");
        if (splits.length > 3) {
            String[] split2 = splits[3].split("@");
            if (split2.length > 1) {
                dep.setClassifier(split2[0]);
                dep.setType(split2[1]);
            } else {
                dep.setClassifier(splits[3]);
            }
        }
        return dep;
    }

    File createExternalProject(ExternalsWrapper wrapper) {
        Model mavenModel = new Model();

        mavenModel.setGroupId(wrapper.getGroupid());
        mavenModel.setArtifactId(wrapper.getArtifact());
        mavenModel.setVersion(wrapper.getVersion());
        mavenModel.setPackaging("jar");
        mavenModel.setModelVersion("4.0.0");
        if (artefactParent != null) {
            mavenModel.setParent(artefactParent);
        }
        mavenModel.setName(
                "Maven definition for " + wrapper.getFile().getName() + " - external part of NetBeans module.");
        mavenModel.setDescription(
                "POM and identification for artifact that was not possible to uniquely identify as a maven dependency.");
        FileWriter writer = null;
        File fil = null;
        try {
            MavenXpp3Writer xpp = new MavenXpp3Writer();
            fil = Files.createTempFile("maven", ".pom").toFile();
            fil.deleteOnExit();
            writer = new FileWriter(fil);
            xpp.write(writer, mavenModel);
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException io) {
                    io.printStackTrace();
                }
            }
        }
        return fil;

    }

    private File createClusterProject(Artifact cluster, Collection<ModuleWrapper> mods) {
        Model mavenModel = new Model();

        mavenModel.setGroupId(cluster.getGroupId());
        mavenModel.setArtifactId(cluster.getArtifactId());
        mavenModel.setVersion(cluster.getVersion());
//        mavenModel.setPackaging("nbm-application");
        mavenModel.setPackaging("pom");
        mavenModel.setModelVersion("4.0.0");
        if (artefactParent != null) {
            mavenModel.setParent(artefactParent);
        }
        List<Dependency> deps = new ArrayList<>();
        for (ModuleWrapper wr : mods) {
            Dependency dep = new Dependency();
            if (wr.getModuleManifest().isNetBeansModule()) {
                dep.setArtifactId(wr.getArtifact());
                dep.setGroupId(wr.getGroup());
                dep.setVersion(wr.getVersion());
                dep.setType("nbm-file");
            } else if (wr instanceof ModuleWrapperMaven) {
                ModuleWrapperMaven mwr = (ModuleWrapperMaven) wr;
                dep.setArtifactId(mwr.getDep().getArtifactId());
                dep.setGroupId(mwr.getDep().getGroupId());
                dep.setVersion(mwr.getDep().getVersion());
                dep.setClassifier(mwr.getDep().getClassifier());
                dep.setScope(mwr.getDep().getScope());
            } else {
                dep.setArtifactId(wr.getArtifact());
                dep.setGroupId(wr.getGroup());
                dep.setVersion(wr.getVersion());
            }
            deps.add(dep);
        }
        mavenModel.setDependencies(deps);
//
//
//        Build build = new Build();
//        Plugin plg = new Plugin();
//        plg.setGroupId("org.codehaus.mojo");
//        plg.setArtifactId("nbm-maven-plugin");
//        plg.setVersion("2.7-SNAPSHOT");
//        plg.setExtensions(true);
//        build.addPlugin(plg);
//        mavenModel.setBuild(build);

        File fil = null;
        try (FileWriter writer = new FileWriter(fil)) {
            MavenXpp3Writer xpp = new MavenXpp3Writer();
            fil = Files.createTempFile("maven", ".pom").toFile();
            fil.deleteOnExit();
            xpp.write(writer, mavenModel);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return fil;
    }

    Artifact createArtifact(String artifact, String version, String group) {
        return new DefaultArtifact(group, artifact, "jar", version);
    }

    private Artifact createClusterArtifact(String artifact, String version) {
        return new DefaultArtifact(groupIdPrefix + GROUP_CLUSTER, artifact, "pom", version);
    }

    private static final Pattern PATTERN_CLUSTER = Pattern.compile("([a-zA-Z]+)[0-9\\.]*");

    static String stripClusterName(String key) {
        Matcher m = PATTERN_CLUSTER.matcher(key);
        if (m.matches()) {
            return m.group(1);
        }
        return key;
    }

    private static class ExternalsWrapper {

        private File file;

        private String artifact;

        private String groupid;

        public String getArtifact() {
            return artifact;
        }

        public void setArtifact(String artifact) {
            this.artifact = artifact;
        }

        public File getFile() {
            return file;
        }

        public void setFile(File file) {
            this.file = file;
        }

        public String getGroupid() {
            return groupid;
        }

        public void setGroupid(String groupid) {
            this.groupid = groupid;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
        private String version;

    }

    private static class ModuleWrapperMaven extends ModuleWrapper {

        private final Dependency dep;

        ModuleWrapperMaven(String art, String ver, String grp, ExamineManifest manifest, File fil, Dependency de) {
            super(art, ver, grp, manifest, fil);
            this.dep = de;
        }

        public Dependency getDep() {
            return dep;
        }
    }

    private static class ModuleWrapper {

        ExamineManifest man;

        private String artifact;

        private String version;

        private String group;

        private File file;

        private String cluster;

        String module;

        List<Dependency> deps;

        ModuleWrapper(String module) {
            this.module = module;
        }

        ModuleWrapper(String art, String ver, String grp, ExamineManifest manifest, File fil) {
            man = manifest;
            artifact = art;
            version = ver;
            group = grp;
            file = fil;
        }

        @Override
        public int hashCode() {
            return getModule().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ModuleWrapper && getModule().equals(((ModuleWrapper) obj).getModule());
        }

        public String getModule() {
            return module != null ? module : getModuleManifest().getModule();
        }

        public ExamineManifest getModuleManifest() {
            return man;
        }

        private String getArtifact() {
            return artifact;
        }

        private String getVersion() {
            return version;
        }

        private String getGroup() {
            return group;
        }

        private File getFile() {
            return file;
        }

        void setCluster(String clust) {
            cluster = clust;
        }

        String getCluster() {
            return cluster;
        }
    }

    private static class NullOutputStream
            extends OutputStream {

        @Override
        public void write(int b)
                throws IOException {
        }
    }

    /**
     * Encodes a 128 bit or 160-bit byte array into a String.
     *
     * @param binaryData Array containing the digest
     * @return Encoded hex string, or null if encoding failed
     */
    static String encode(byte[] binaryData) {
        int bitLength = binaryData.length * 8;
        if (bitLength != 128 && bitLength != 160) {
            throw new IllegalArgumentException(
                    "Unrecognised length for binary data: " + bitLength + " bits");
        }
        return String.format("%0" + bitLength / 4 + "x", new BigInteger(1, binaryData));
    }
}
