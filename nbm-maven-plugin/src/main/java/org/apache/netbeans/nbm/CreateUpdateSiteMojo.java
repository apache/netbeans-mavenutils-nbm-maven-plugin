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
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.netbeans.nbm.handlers.NbmFileArtifactHandler;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.archiver.gzip.GZipArchiver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.netbeans.nbbuild.MakeUpdateDesc;

/**
 * Create the NetBeans auto update site definition.
 *
 * @author Milos Kleint
 *
 */
@Mojo(name = "autoupdate",
        defaultPhase = LifecyclePhase.PACKAGE,
        aggregator = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public final class CreateUpdateSiteMojo extends AbstractNbmMojo {

    /**
     * output directory.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}")
    private File outputDirectory;
    /**
     * autoupdate site xml file name.
     */
    @Parameter(defaultValue = "updates.xml", property = "maven.nbm.updatesitexml")
    private String fileName;

    /**
     * A custom distribution base for the nbms in the update site. If NOT
     * defined, the update site will use a simple relative URL, which is
     * generally what you want. Defining it as "auto" will pick up the
     * distribution URL from each NBM, which is generally wrong. See
     * <code>distributionUrl</code> in nbm mojo for what url will be used in
     * that case.
     * <p/>
     * The value is either a direct http protocol based URL that points to the
     * location under which all nbm files are located, or
     * <p/>
     * allows to create an update site based on maven repository content. The
     * resulting autoupdate site document can be uploaded as tar.gz to
     * repository as well as attached artifact to the 'nbm-application' project.
     * <br/>
     * Format: id::layout::url same as in maven-deploy-plugin
     * <br/>
     * with the 'default' and 'legacy' layouts. (maven2 vs maven1 layout)
     * <br/>
     * If the value doesn't contain :: characters, it's assumed to be the flat
     * structure and the value is just the URL.
     *
     * @since 3.0 it's also possible to add remote repository as base
     */
    @Parameter(defaultValue = ".", property = "maven.nbm.customDistBase")
    private String distBase;

    /**
     * List of Ant style patterns on artifact GA (groupID:artifactID) that
     * should be included in the update site. Eg. org.netbeans.* matches all
     * artifacts with any groupID starting with 'org.netbeans.', org.*:api will
     * match any artifact with artifactId of 'api' and groupId starting with
     * 'org.'
     *
     * @since 3.14
     */
    @Parameter
    private List<String> updateSiteIncludes;

    @Inject
    public CreateUpdateSiteMojo(RepositorySystem repositorySystem, MavenProjectHelper mavenProjectHelper, ProjectDependenciesResolver projectDependenciesResolver, Artifacts artifacts) {
        super(repositorySystem, mavenProjectHelper, projectDependenciesResolver, artifacts);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Project antProject = registerNbmAntTasks();
        File nbmBuildDirFile = new File(outputDirectory, "netbeans_site");
        if (!nbmBuildDirFile.exists()) {
            nbmBuildDirFile.mkdirs();
        }

        boolean isRepository = false;
        if ("auto".equals(distBase)) {
            distBase = null;
        }
        RemoteRepository distRepository = getDeploymentRepository(distBase);
        String oldDistBase = null;
        if (distRepository != null) {
            isRepository = true;
        } else {
            if (distBase != null && !distBase.contains("::")) {
                oldDistBase = distBase;
            }
        }

        if ("nbm-application".equals(project.getPackaging())) {
            Collection<Artifact> artifacts = RepositoryUtils.toArtifacts(project.getArtifacts());
            for (Artifact art : artifacts) {
                if (!matchesIncludes(art)) {
                    continue;
                }
                ArtifactResult res = turnJarToNbmFile(art, project);
                if (res.hasConvertedArtifact()) {
                    art = res.getConvertedArtifact();
                }

                if (super.artifacts.getArtifactType(art).getId().equals("nbm-file")) {
                    Copy copyTask = (Copy) antProject.createTask("copy");
                    copyTask.setOverwrite(true);
                    copyTask.setFile(art.getFile());
                    if (!isRepository) {
                        copyTask.setFlatten(true);
                        copyTask.setTodir(nbmBuildDirFile);
                    } else {
                        String path = super.artifacts.pathOf(art);
                        File f = new File(nbmBuildDirFile, path.replace('/', File.separatorChar));
                        copyTask.setTofile(f);
                    }
                    try {
                        copyTask.execute();
                    } catch (BuildException ex) {
                        throw new MojoExecutionException("Cannot merge nbm files into autoupdate site", ex);
                    }

                }
                if (res.isOSGiBundle()) {
                    // TODO check for bundles
                }
            }
            getLog().info("Created NetBeans module cluster(s) at " + nbmBuildDirFile.getAbsoluteFile());

        } else if (!session.getAllProjects().isEmpty()) {

            for (MavenProject proj : session.getAllProjects()) {
                File projOutputDirectory = new File(proj.getBuild().getDirectory());
                if (projOutputDirectory.exists()) {
                    Copy copyTask = (Copy) antProject.createTask("copy");
                    if (!isRepository) {
                        FileSet fs = new FileSet();
                        fs.setDir(projOutputDirectory);
                        fs.createInclude().setName("*.nbm");
                        copyTask.addFileset(fs);
                        copyTask.setOverwrite(true);
                        copyTask.setFlatten(true);
                        copyTask.setTodir(nbmBuildDirFile);
                    } else {
                        boolean has = false;
                        File[] fls = projOutputDirectory.listFiles();
                        if (fls != null) {
                            for (File fl : fls) {
                                if (fl.getName().endsWith(".nbm")) {
                                    copyTask.setFile(fl);
                                    has = true;
                                    break;
                                }
                            }
                        }
                        if (!has) {
                            continue;
                        }
                        ArtifactType npmFile = artifacts.getArtifactType(NbmFileArtifactHandler.NAME);
                        Artifact art = new DefaultArtifact(proj.getGroupId(), proj.getArtifactId(), null, npmFile.getExtension(), proj.getVersion(), npmFile);
                        String path = artifacts.pathOf(art);
                        File f = new File(nbmBuildDirFile, path.replace('/', File.separatorChar));
                        copyTask.setTofile(f);
                    }
                    try {
                        copyTask.execute();
                    } catch (BuildException ex) {
                        throw new MojoExecutionException("Cannot merge nbm files into autoupdate site", ex);
                    }
                }
            }
        } else {
            throw new MojoExecutionException(
                    "This goal only makes sense on reactor projects or project with 'nbm-application' packaging.");

        }
        MakeUpdateDesc descTask = (MakeUpdateDesc) antProject.createTask("updatedist");
        File xmlFile = new File(nbmBuildDirFile, fileName);
        descTask.setDesc(xmlFile);
        if (oldDistBase != null) {
            descTask.setDistBase(oldDistBase);
        }
        if (distRepository != null) {
            descTask.setDistBase(distRepository.getUrl());
        }
        FileSet fs = new FileSet();
        fs.setDir(nbmBuildDirFile);
        fs.createInclude().setName("**/*.nbm");
        descTask.addFileset(fs);
        try {
            descTask.execute();
        } catch (BuildException ex) {
            throw new MojoExecutionException("Cannot create autoupdate site xml file", ex);
        }
        getLog().info("Generated autoupdate site content at " + nbmBuildDirFile.getAbsolutePath());

        try {
            GZipArchiver gz = new GZipArchiver();
            gz.addFile(xmlFile, fileName);
            File gzipped = new File(nbmBuildDirFile, fileName + ".gz");
            gz.setDestFile(gzipped);
            gz.createArchive();
            if ("nbm-application".equals(project.getPackaging())) {
                mavenProjectHelper.attachArtifact(project, "xml.gz", "updatesite", gzipped);
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("Cannot create gzipped version of the update site xml file.", ex);
        }

    }

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.+)::(.+)");

    static RemoteRepository getDeploymentRepository(String distBase)
            throws MojoExecutionException, MojoFailureException {

        RemoteRepository repo = null;

        if (distBase != null) {

            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(distBase);

            if (!matcher.matches()) {
                if (!distBase.contains("::")) {
                    //backward compatibility gag.
                    return null;
                }
                throw new MojoFailureException(distBase,
                        "Invalid syntax for repository.",
                        "Invalid syntax for alternative repository. Use \"id::layout::url\".");
            } else {
                String id = matcher.group(1).trim();
                String layout = matcher.group(2).trim();
                String url = matcher.group(3).trim();

                repo = new RemoteRepository.Builder(id, "default", url).build();
            }
        }
        return repo;
    }

    private boolean matchesIncludes(Artifact art) {
        if (updateSiteIncludes != null) {
            String s = art.getGroupId() + ":" + art.getArtifactId();
            for (String p : updateSiteIncludes) {
                //TODO optimize and only do once per execution.
                p = p.replace(".", "\\.").replace("*", ".*");
                Pattern patt = Pattern.compile(p);
                if (patt.matcher(s).matches()) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
