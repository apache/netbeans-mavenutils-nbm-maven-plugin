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
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Organization;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.netbeans.nbm.handlers.NbmFileArtifactHandler;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.util.FileUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.netbeans.nbbuild.MakeNBM;
import org.netbeans.nbbuild.MakeNBM.Blurb;
import org.netbeans.nbbuild.MakeNBM.Signature;

import javax.inject.Inject;

/**
 * Create the NetBeans module artifact (nbm file), part of "nbm"
 * lifecycle/packaging.
 *
 *
 * @author Milos Kleint
 */
@Mojo(name = "nbm",
        requiresProject = true,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        defaultPhase = LifecyclePhase.PACKAGE)
public final class CreateNbmMojo extends CreateNetBeansFileStructure {

    /**
     * keystore location for signing the nbm file
     */
    @Parameter(property = "keystore")
    private String keystore;
    /**
     * keystore password
     */
    @Parameter(property = "keystorepass")
    private String keystorepassword;
    /**
     * keystore alias
     */
    @Parameter(property = "keystorealias")
    private String keystorealias;
    /**
     * Timestamp Authority (TSA) URL
     */
    @Parameter(property = "tsaurl")
    private String tsaurl;
    /**
     * Timestamp Authority (TSA) Certificate
     */
    @Parameter(property = "tsacert")
    private String tsacert;

    /**
     * Boolean parameter denoting if creation of NBM file shall be skipped or
     * not. If skipped, just the expanded directory for cluster is created
     *
     * @since 3.0
     */
    @Parameter(defaultValue = "false", property = "maven.nbm.skip")
    private boolean skipNbm;

    /**
     * if true, upon installing the NBM the platform app/IDE restart is
     * requested. Not necessary in most cases.
     *
     * @since 3.8
     */
    @Parameter(defaultValue = "false")
    private boolean requiresRestart;

    /**
     * Get homepage URL of the module. Is accessible from NetBeans UI upon
     * installation, should point to place with additional information about the
     * functionality.
     *
     * @since 3.8
     */
    @Parameter(defaultValue = "${project.url}")
    private String homePageUrl;

    /**
     * Author of the module. Shown in the Module manager UI.
     *
     * @since 3.8
     */
    @Parameter(defaultValue = "${project.organization.name}")
    private String author;

    /**
     * Distribution base URL for the NBM at runtime deployment time. Note:
     * Usefulness of the parameter is questionable, it doesn't allow for mirrors
     * and usually when downloading the nbm, one already knows the location
     * anyway. Please note that the netbeans.org Ant scripts put a dummy url
     * here. The actual correct value used when constructing update site is
     * explicitly set there. The general assumption there is that all modules
     * from one update center come from one base URL. Also see
     * <code>distBase</code> parameter in auto-update mojo.
     * <p/>
     * The value is either a direct http protocol based URL that points to the
     * location under which nbm file will be located, or
     * <p/>
     * it allows to create an update site based on maven repository content. The
     * later created autoupdate site document can use this information and
     * compose the application from one or multiple maven repositories.
     * <br/>
     * Format: id::layout::url same as in maven-deploy-plugin
     * <br/>
     * with the 'default' and 'legacy' layouts. (maven2 vs maven1 layout)
     * <br/>
     * If the value doesn't contain :: characters, it's assumed to be the flat
     * structure and the value is just the URL.
     *
     */
    @Parameter(property = "maven.nbm.distributionURL")
    private String distributionUrl;

    /**
     * name of the license applicable to the NBM. The value should be equal
     * across modules with the same license. If the user already agreed to the
     * same license before, he/she won't be asked again to agree and for
     * multiple one installed at the same time, just one license agreement is
     * shown. When defined, <code>licenseFile</code> needs to be defined as
     * well.
     *
     * @since 3.8
     */
    @Parameter
    private String licenseName;

    /**
     * path to the license agreement file that will be shown when installing the
     * module. When defined, <code>licenseName</code> needs to be defined as
     * well.
     *
     * @since 3.8
     */
    @Parameter
    private File licenseFile;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");

    @Inject
    public CreateNbmMojo(RepositorySystem repositorySystem, MavenProjectHelper mavenProjectHelper, ProjectDependenciesResolver projectDependenciesResolver, Artifacts artifacts, MavenResourcesFiltering mavenResourcesFiltering) {
        super(repositorySystem, mavenProjectHelper, projectDependenciesResolver, artifacts, mavenResourcesFiltering);
    }

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        if (skipNbm) {
            getLog().info("Skipping generation of NBM file.");
            return;
        }

        if ("pom".equals(project.getPackaging())) {
            getLog().info(
                    "Skipping " + project.getId() + ", no nbm:nbm execution for 'pom' packaging");
            return;
        }
        super.execute();

        // 3. generate nbm
        File nbmFile = new File(nbmBuildDir, finalName + ".nbm");
        MakeNBM nbmTask = (MakeNBM) antProject.createTask("makenbm");
        nbmTask.setFile(nbmFile);
        nbmTask.setProductDir(clusterDir);

        nbmTask.setModule("modules" + File.separator + moduleJarName + ".jar");
        boolean reqRestart = requiresRestart;
        if (!reqRestart && module.isRequiresRestart()) {
            reqRestart = module.isRequiresRestart();
            getLog().warn("Module descriptor's requiresRestart field is deprecated, use plugin's configuration in pom.xml");
        }
        nbmTask.setNeedsrestart(Boolean.toString(reqRestart));
        String moduleAuthor = author;
        if (module.getAuthor() != null) {
            moduleAuthor = module.getAuthor();
            getLog().warn("Module descriptor's author field is deprecated, use plugin's configuration in pom.xml");
        }
        nbmTask.setModuleauthor(moduleAuthor);
        if (keystore != null && keystorealias != null && keystorepassword != null) {
            File ks = new File(keystore);
            if (!ks.exists()) {
                getLog().warn("Cannot find keystore file at " + ks.getAbsolutePath());
            } else {
                Signature sig = nbmTask.createSignature();
                sig.setKeystore(ks);
                sig.setAlias(keystorealias);
                sig.setStorepass(keystorepassword);
                if (tsaurl != null) {
                    sig.setTsaurl(tsaurl);
                }
                if (tsacert != null) {
                    sig.setTsacert(tsacert);
                }
                getLog().debug("Setup the Ant task to sign the NBM file.");
            }
        } else if (keystore != null || keystorepassword != null || keystorealias != null) {
            getLog().warn(
                    "If you want to sign the nbm file, you need to define all three keystore related parameters.");
        }
        String licName = licenseName;
        File licFile = licenseFile;
        if (module.getLicenseName() != null) {
            licName = module.getLicenseName();
            getLog().warn("Module descriptor's licenseName field is deprecated, use plugin's configuration in pom.xml");
        }
        if (module.getLicenseFile() != null) {
            File lf = new File(project.getBasedir(), module.getLicenseFile());
            licFile = lf;
            getLog().warn("Module descriptor's licenseFile field is deprecated, use plugin's configuration in pom.xml");

        }
        if (licName != null && licFile != null) {
            if (!licFile.exists() || !licFile.isFile()) {
                getLog().warn("Cannot find license file at " + licFile.getAbsolutePath());
            } else {
                Blurb lb = nbmTask.createLicense();
                lb.setFile(licFile);
                lb.addText(licName);
            }
        } else if (licName != null || licFile != null) {
            getLog().warn(
                    "To set license for the nbm, you need to specify both licenseName and licenseFile parameters.");
        } else {
            Blurb lb = nbmTask.createLicense();
            lb.addText(createDefaultLicenseHeader());
            lb.addText(createDefaultLicenseText());
        }
        String hpUrl = homePageUrl;
        if (module.getHomepageUrl() != null) {
            getLog().warn("Module descriptor's homePageUrl field is deprecated, use plugin's configuration in pom.xml");
            hpUrl = module.getHomepageUrl();
        }
        if (hpUrl != null) {
            nbmTask.setHomepage(hpUrl);
        }
        String distribUrl = distributionUrl;
        if (module.getDistributionUrl() != null) {
            distribUrl = module.getDistributionUrl();
            getLog().warn("Module descriptor's distributionUrl field is deprecated, use plugin's configuration in pom.xml");
        }
        if (distribUrl != null) {
            RemoteRepository distRepository = CreateUpdateSiteMojo.getDeploymentRepository(distribUrl);
            String dist = null;
            if (distRepository == null) {
                if (!distribUrl.contains("::")) {
                    dist = distribUrl + (distribUrl.endsWith("/") ? "" : "/") + nbmFile.getName();
                }
            } else {
                ArtifactType nbmFileType = artifacts.getArtifactType(NbmFileArtifactHandler.NAME);
                Artifact art = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), null, nbmFileType.getExtension(), project.getVersion(), nbmFileType);
                dist = distRepository.getUrl() + (distRepository.getUrl().endsWith("/") ? "" : "/") + artifacts.pathOf(art);

            }
            nbmTask.setDistribution(dist);
        } else {
            nbmTask.setDistribution(nbmFile.getName());
        }
        if (!"extra".equals(cluster)) {
            nbmTask.setTargetcluster(cluster);
        }
        //MNBMODULE-217 avoid using the static DATE_FORMAT variable in MavenNBM.java (in ant harness)
        nbmTask.setReleasedate(DATE_FORMAT.format(getOutputTimestampOrNow(project)));
        Path updaterPath = nbmTask.createUpdaterJar();
        try {
            // locate a public class from updater to get the path
            URL url = org.netbeans.updater.XMLUtil.class.getProtectionDomain().getCodeSource().getLocation();
            updaterPath.setPath(url.getFile());
        } catch (BuildException e) {
            // we could continue without having updater
            getLog().warn("Could not find updater.jar");
        }
        try {
            nbmTask.execute();
        } catch (BuildException e) {
            throw new MojoExecutionException("Cannot Generate nbm file:" + e.getMessage(), e);
        }
        try {
            File nbmfile = new File(buildDir, nbmFile.getName());
            FileUtils.getFileUtils().copyFile(nbmFile, nbmfile);
            mavenProjectHelper.attachArtifact(project, "nbm-file", null, nbmfile);
        } catch (IOException ex) {
            throw new MojoExecutionException("Cannot copy nbm to build directory", ex);
        }
    }

    private String createDefaultLicenseHeader() {
        String organization = "";
        Organization org = project.getOrganization();
        if (org != null) {
            organization = org.getName();
        }
        if (organization == null) {
            List<Developer> devs = project.getDevelopers();
            if (!devs.isEmpty()) {
                Iterator<Developer> dvs = devs.iterator();
                String devsString = "";
                while (dvs.hasNext()) {
                    Developer d = dvs.next();
                    devsString = devsString + "," + d.getName() != null ? d.getName() : d.getId();
                }
                organization = devsString.substring(1);
            }
        }
        if (organization == null) {
            organization = ""; //what's a good default value?
        }
        String date = "";
        if (project.getInceptionYear() != null) {
            date = project.getInceptionYear();
        }
        String year = Integer.toString(Calendar.getInstance().get(Calendar.YEAR));
        if (!year.equals(date)) {
            date = date.isEmpty() ? year : date + "-" + year;
        }
        return "Copyright " + organization + " " + date;
    }

    private String createDefaultLicenseText() {
        String toRet = "License terms:\n";

        List<License> licenses = project.getLicenses();
        if (licenses != null && !licenses.isEmpty()) {
            Iterator<License> lic = licenses.iterator();
            while (lic.hasNext()) {
                License ll = lic.next();

                if (ll.getName() != null) {
                    toRet = toRet + ll.getName() + " - ";
                }
                if (ll.getUrl() != null) {
                    toRet = toRet + ll.getUrl();
                }
                if (lic.hasNext()) {
                    toRet = toRet + ",\n";
                }
            }
        } else {
            toRet = toRet + "Unknown";
        }
        return toRet;
    }
}
