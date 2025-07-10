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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.GenerateKey;
import org.apache.tools.ant.taskdefs.SignJar;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Parameter;
import org.apache.tools.ant.types.selectors.AndSelector;
import org.apache.tools.ant.types.selectors.FilenameSelector;
import org.apache.tools.ant.types.selectors.OrSelector;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.InterpolationFilterReader;
import org.eclipse.aether.RepositorySystem;
import org.netbeans.nbbuild.MakeJNLP;
import org.netbeans.nbbuild.ModuleSelector;
import org.netbeans.nbbuild.VerifyJNLP;

import javax.inject.Inject;

/**
 * Create webstartable binaries for a 'nbm-application'.
 * @deprecated webstart is not available on jdk 11+. Will be removed in nbm-plugin 15.0
 * @author Johan Andrén
 * @author Milos Kleint
 * @since 3.0
 */
@Deprecated(forRemoval = true,since = "14.3")
@Mojo(name = "webstart-app", defaultPhase = LifecyclePhase.PACKAGE)
public final class CreateWebstartAppMojo extends AbstractNbmMojo {

    /**
     * The branding token for the application based on NetBeans platform.
     */
    @org.apache.maven.plugins.annotations.Parameter(required = true, property = "netbeans.branding.token")
    private String brandingToken;

    /**
     * output directory where the the NetBeans application will be created.
     */
    @org.apache.maven.plugins.annotations.Parameter(required = true, defaultValue = "${project.build.directory}")
    private File outputDirectory;

    /**
     * Ready-to-deploy WAR containing application in JNLP packaging.
     *
     */
    @org.apache.maven.plugins.annotations.Parameter(required = true, defaultValue
            = "${project.build.directory}/${project.artifactId}-${project.version}-jnlp.war")
    private File destinationFile;

    /**
     * Artifact Classifier to use for the webstart distributable zip file.
     *
     * @since 3.1
     */
    @org.apache.maven.plugins.annotations.Parameter(defaultValue = "webstart", property = "nbm.webstart.classifier")
    private String webstartClassifier;

    /**
     * Codebase value within *.jnlp files.
     * <strong>Defining this parameter is generally a bad idea.</strong>
     */
    @org.apache.maven.plugins.annotations.Parameter(property = "nbm.webstart.codebase")
    private String codebase;

    /**
     * A custom master JNLP file. If not defined, the
     * <a href="http://mojo.codehaus.org/nbm-maven-plugin/masterjnlp.txt">default
     * one</a> is used. The following expressions can be used within the file
     * and will be replaced when generating content.
     * <ul>
     * <li>${jnlp.resources}</li>
     * <li>${jnlp.codebase} - the 'codebase' parameter value is passed in.</li>
     * <li>${app.name}</li>
     * <li>${app.title}</li>
     * <li>${app.vendor}</li>
     * <li>${app.description}</li>
     * <li>${branding.token} - the 'brandingToken' parameter value is passed
     * in.</li>
     * <li>${netbeans.jnlp.fixPolicy}</li>
     * </ul>
     */
    @org.apache.maven.plugins.annotations.Parameter
    private File masterJnlpFile;

    /**
     * The basename (minus .jnlp extension) of the master JNLP file in the
     * output. This file will be the entry point for javaws. Defaults to the
     * branding token.
     *
     * @since 3.5
     */
    @org.apache.maven.plugins.annotations.Parameter(property = "master.jnlp.file.name")
    private String masterJnlpFileName;

    /**
     * keystore location for signing the nbm file
     */
    @org.apache.maven.plugins.annotations.Parameter(property = "keystore")
    private String keystore;

    /**
     * keystore password
     */
    @org.apache.maven.plugins.annotations.Parameter(property = "keystorepass")
    private String keystorepassword;

    /**
     * keystore alias
     */
    @org.apache.maven.plugins.annotations.Parameter(property = "keystorealias")
    private String keystorealias;

    /**
     * keystore type
     *
     * @since 3.5
     */
    @org.apache.maven.plugins.annotations.Parameter(property = "keystoretype")
    private String keystoretype;

    /**
     * If set true, build-jnlp target creates versioning info in jnlp
     * descriptors and version.xml files. This allows for incremental updates of
     * Webstart applications, but requires download via JnlpDownloadServlet
     * Defaults to false, which means versioning info is not generated (see
     * http://java.sun.com/j2se/1.5.0/docs/guide/javaws/developersguide/downloadservletguide.html#resources).
     *
     */
    @org.apache.maven.plugins.annotations.Parameter(defaultValue = "false", property = "nbm.webstart.versions")
    private boolean processJarVersions;
    /**
     * additional command line arguments. Eg. -J-Xdebug -J-Xnoagent
     * -J-Xrunjdwp:transport=dt_socket,suspend=n,server=n,address=8888 can be
     * used to debug the IDE.
     */
    @org.apache.maven.plugins.annotations.Parameter(property = "netbeans.run.params")
    private String additionalArguments;

    @Inject
    public CreateWebstartAppMojo(RepositorySystem repositorySystem, MavenProjectHelper mavenProjectHelper, ProjectDependenciesResolver projectDependenciesResolver, Artifacts artifacts) {
        super(repositorySystem, mavenProjectHelper, projectDependenciesResolver, artifacts);
    }

    /**
     *
     * @throws MojoExecutionException if an unexpected problem occurs
     * @throws MojoFailureException if an expected problem occurs
     */
    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        if (!"nbm-application".equals(project.getPackaging())) {
            throw new MojoExecutionException(
                    "This goal only makes sense on project with nbm-application packaging.");
        }
        Project antProject = antProject();

        getLog().warn(
                "WARNING: Unsigned and self-signed WebStart applications are deprecated from JDK7u21 onwards. "
                + "To ensure future correct functionality please use trusted certificate.");

        if (keystore != null && keystorealias != null && keystorepassword != null) {
            File ks = new File(keystore);
            if (!ks.exists()) {
                throw new MojoFailureException("Cannot find keystore file at " + ks.getAbsolutePath());
            } else {
                //proceed..
            }
        } else if (keystore != null || keystorepassword != null || keystorealias != null) {
            throw new MojoFailureException("If you want to sign the jnlp application, "
                    + "you need to define all three keystore related parameters.");
        } else {
            File generatedKeystore = new File(outputDirectory, "generated.keystore");
            if (!generatedKeystore.exists()) {
                getLog().warn("Keystore related parameters not set, generating a default keystore.");
                GenerateKey genTask = (GenerateKey) antProject.createTask("genkey");
                genTask.setAlias("jnlp");
                genTask.setStorepass("netbeans");
                genTask.setDname("CN=" + System.getProperty("user.name"));
                genTask.setKeystore(generatedKeystore.getAbsolutePath());
                genTask.execute();
            }
            keystore = generatedKeystore.getAbsolutePath();
            keystorepassword = "netbeans";
            keystorealias = "jnlp";
        }

        Taskdef taskdef = (Taskdef) antProject.createTask("taskdef");
        taskdef.setClassname("org.netbeans.nbbuild.MakeJNLP");
        taskdef.setName("makejnlp");
        taskdef.execute();

        taskdef = (Taskdef) antProject.createTask("taskdef");
        taskdef.setClassname("org.netbeans.nbbuild.VerifyJNLP");
        taskdef.setName("verifyjnlp");
        taskdef.execute();

        try {
            File webstartBuildDir = new File(
                    outputDirectory + File.separator + "webstart" + File.separator + brandingToken);
            if (webstartBuildDir.exists()) {
                FileUtils.deleteDirectory(webstartBuildDir);
            }
            webstartBuildDir.mkdirs();
            final String localCodebase = codebase != null ? codebase : webstartBuildDir.toURI().toString();
            getLog().info("Generating webstartable binaries at " + webstartBuildDir.getAbsolutePath());

            File nbmBuildDirFile = new File(outputDirectory, brandingToken);

//            FileUtils.copyDirectoryStructureIfModified( nbmBuildDirFile, webstartBuildDir );
            MakeJNLP jnlpTask = (MakeJNLP) antProject.createTask("makejnlp");
            jnlpTask.setDir(webstartBuildDir);
            jnlpTask.setCodebase(localCodebase);
            //TODO, how to figure verify excludes..
            jnlpTask.setVerify(false);
            jnlpTask.setPermissions("<security><all-permissions/></security>");
            jnlpTask.setSignJars(true);

            jnlpTask.setAlias(keystorealias);
            jnlpTask.setKeystore(keystore);
            jnlpTask.setStorePass(keystorepassword);
            if (keystoretype != null) {
                jnlpTask.setStoreType(keystoretype);
            }
            jnlpTask.setProcessJarVersions(processJarVersions);

            FileSet fs = jnlpTask.createModules();
            fs.setDir(nbmBuildDirFile);
            OrSelector or = new OrSelector();
            AndSelector and = new AndSelector();
            FilenameSelector inc = new FilenameSelector();
            inc.setName("*/modules/**/*.jar");
            or.addFilename(inc);
            inc = new FilenameSelector();
            inc.setName("*/lib/**/*.jar");
            or.addFilename(inc);
            inc = new FilenameSelector();
            inc.setName("*/core/**/*.jar");
            or.addFilename(inc);

            ModuleSelector ms = new ModuleSelector();
            Parameter included = new Parameter();
            included.setName("includeClusters");
            included.setValue("");
            Parameter excluded = new Parameter();
            excluded.setName("excludeClusters");
            excluded.setValue("");
            Parameter exModules = new Parameter();
            exModules.setName("excludeModules");
            exModules.setValue("");
            ms.setParameters(new Parameter[]{
                included,
                excluded,
                exModules
            });
            and.add(or);
            and.add(ms);
            fs.addAnd(and);
            jnlpTask.execute();

            //TODO is it really netbeans/
            String extSnippet = generateExtensions(fs, antProject, ""); // "netbeans/"

            if (masterJnlpFileName == null) {
                masterJnlpFileName = brandingToken;
            }

            Properties props = new Properties();
            props.setProperty("jnlp.codebase", localCodebase);
            props.setProperty("app.name", brandingToken);
            props.setProperty("app.title", project.getName());
            if (project.getOrganization() != null) {
                props.setProperty("app.vendor", project.getOrganization().getName());
            } else {
                props.setProperty("app.vendor", "Nobody");
            }
            String description = project.getDescription() != null ? session.getCurrentProject().getDescription() : "No Project Description";
            props.setProperty("app.description", description);
            props.setProperty("branding.token", brandingToken);
            props.setProperty("master.jnlp.file.name", masterJnlpFileName);
            props.setProperty("netbeans.jnlp.fixPolicy", "false");

            StringBuilder stBuilder = new StringBuilder();
            if (additionalArguments != null) {
                StringTokenizer st = new StringTokenizer(additionalArguments);
                while (st.hasMoreTokens()) {
                    String arg = st.nextToken();
                    if (arg.startsWith("-J")) {
                        if (stBuilder.length() > 0) {
                            stBuilder.append(' ');
                        }
                        stBuilder.append(arg.substring(2));
                    }
                }
            }
            props.setProperty("netbeans.run.params", stBuilder.toString());

            File masterJnlp = new File(
                    webstartBuildDir.getAbsolutePath() + File.separator + masterJnlpFileName + ".jnlp");
            filterCopy(masterJnlpFile, "master.jnlp", masterJnlp, props);

            File startup = copyLauncher(outputDirectory, nbmBuildDirFile);
            File jnlpDestination = new File(
                    webstartBuildDir.getAbsolutePath() + File.separator + "startup.jar");

            SignJar signTask = (SignJar) antProject.createTask("signjar");
            signTask.setKeystore(keystore);
            signTask.setStorepass(keystorepassword);
            signTask.setAlias(keystorealias);
            if (keystoretype != null) {
                signTask.setStoretype(keystoretype);
            }
            signTask.setSignedjar(jnlpDestination);
            signTask.setJar(startup);
            signTask.execute();

            //branding
            DirectoryScanner ds = new DirectoryScanner();
            ds.setBasedir(nbmBuildDirFile);
            ds.setIncludes(new String[]{
                "**/locale/*.jar"
            });
            ds.scan();
            String[] includes = ds.getIncludedFiles();
            StringBuilder brandRefs = new StringBuilder();
            if (includes != null && includes.length > 0) {
                File brandingDir = new File(webstartBuildDir, "branding");
                brandingDir.mkdirs();
                for (String incBran : includes) {
                    File source = new File(nbmBuildDirFile, incBran);
                    File dest = new File(brandingDir, source.getName());
                    FileUtils.copyFile(source, dest);
                    brandRefs.append("    <jar href=\'branding/").append(dest.getName()).append("\'/>\n");
                }

                signTask = (SignJar) antProject.createTask("signjar");
                signTask.setKeystore(keystore);
                signTask.setStorepass(keystorepassword);
                signTask.setAlias(keystorealias);
                if (keystoretype != null) {
                    signTask.setStoretype(keystoretype);
                }

                FileSet set = new FileSet();
                set.setDir(brandingDir);
                set.setIncludes("*.jar");
                signTask.addFileset(set);
                signTask.execute();
            }

            File modulesJnlp = new File(
                    webstartBuildDir.getAbsolutePath() + File.separator + "modules.jnlp");
            props.setProperty("jnlp.branding.jars", brandRefs.toString());
            props.setProperty("jnlp.resources", extSnippet);
            filterCopy(null, /* filename is historical */ "branding.jnlp", modulesJnlp, props);

            getLog().info("Verifying generated webstartable content.");
            VerifyJNLP verifyTask = (VerifyJNLP) antProject.createTask("verifyjnlp");
            FileSet verify = new FileSet();
            verify.setFile(masterJnlp);
            verifyTask.addConfiguredFileset(verify);
            verifyTask.execute();

            // create zip archive
            if (destinationFile.exists()) {
                destinationFile.delete();
            }
            ZipArchiver archiver = new ZipArchiver();
            if (codebase != null) {
                getLog().warn("Defining <codebase>/${nbm.webstart.codebase} is generally unnecessary");
                archiver.addDirectory(webstartBuildDir);
            } else {
                archiver.addDirectory(webstartBuildDir, null, new String[]{
                    "**/*.jnlp"
                });
                for (final File jnlp : webstartBuildDir.listFiles()) {
                    if (!jnlp.getName().endsWith(".jnlp")) {
                        continue;
                    }
                    archiver.addResource(new PlexusIoResource() {
                        public @Override
                        InputStream getContents() throws IOException {
                            return new ByteArrayInputStream(FileUtils.fileRead(jnlp, "UTF-8").replace(localCodebase,
                                    "$$codebase").getBytes("UTF-8"));
                        }

                        public @Override
                        long getLastModified() {
                            return jnlp.lastModified();
                        }

                        public @Override
                        boolean isExisting() {
                            return true;
                        }

                        public @Override
                        long getSize() {
                            return UNKNOWN_RESOURCE_SIZE;
                        }

                        public @Override
                        URL getURL() throws IOException {
                            return null;
                        }

                        public @Override
                        String getName() {
                            return jnlp.getAbsolutePath();
                        }

                        public @Override
                        boolean isFile() {
                            return true;
                        }

                        public @Override
                        boolean isDirectory() {
                            return false;
                        }

                        @Override
                        public boolean isSymbolicLink() {
                            return false;
                        }
                    }, jnlp.getName(), archiver.getDefaultFileMode());
                }
            }
            File jdkhome = new File(System.getProperty("java.home"));
            File servlet = new File(jdkhome, "sample/jnlp/servlet/jnlp-servlet.jar");
            if (!servlet.isFile()) {
                servlet = new File(jdkhome.getParentFile(), "sample/jnlp/servlet/jnlp-servlet.jar");
            }
            if (servlet.isFile()) {
                archiver.addFile(servlet, "WEB-INF/lib/jnlp-servlet.jar");
                archiver.addResource(new PlexusIoResource() {
                    public @Override
                    InputStream getContents() throws IOException {
                        return new ByteArrayInputStream((""
                                + "<web-app>\n"
                                + "    <servlet>\n"
                                + "        <servlet-name>JnlpDownloadServlet</servlet-name>\n"
                                + "        <servlet-class>jnlp.sample.servlet.JnlpDownloadServlet</servlet-class>\n"
                                + "    </servlet>\n"
                                + "    <servlet-mapping>\n"
                                + "        <servlet-name>JnlpDownloadServlet</servlet-name>\n"
                                + "        <url-pattern>*.jnlp</url-pattern>\n"
                                + "    </servlet-mapping>\n"
                                + "</web-app>\n").getBytes());
                    }

                    public @Override
                    long getLastModified() {
                        return UNKNOWN_MODIFICATION_DATE;
                    }

                    public @Override
                    boolean isExisting() {
                        return true;
                    }

                    public @Override
                    long getSize() {
                        return UNKNOWN_RESOURCE_SIZE;
                    }

                    public @Override
                    URL getURL() throws IOException {
                        return null;
                    }

                    public @Override
                    String getName() {
                        return "web.xml";
                    }

                    public @Override
                    boolean isFile() {
                        return true;
                    }

                    public @Override
                    boolean isDirectory() {
                        return false;
                    }

                    @Override
                    public boolean isSymbolicLink() {
                        return false;
                    }
                }, "WEB-INF/web.xml", archiver.getDefaultFileMode());
            }
            archiver.setDestFile(destinationFile);
            archiver.createArchive();

            // attach standalone so that it gets installed/deployed
            mavenProjectHelper.attachArtifact(project, "war", webstartClassifier, destinationFile);

        } catch (Exception ex) {
            throw new MojoExecutionException("Error creating webstartable binary.", ex);
        }
    }

    /**
     * @param standaloneBuildDir
     * @return The name of the jnlp-launcher jarfile in the build directory
     */
    private File copyLauncher(File standaloneBuildDir, File builtInstallation)
            throws IOException {
        File jnlpStarter
                = new File(builtInstallation.getAbsolutePath() + File.separator + "harness" + File.separator + "jnlp"
                        + File.separator + "jnlp-launcher.jar");
        // buffer so it isn't reading a byte at a time!
        File jnlpDestination = new File(
                standaloneBuildDir.getAbsolutePath() + File.separator + "jnlp-launcher.jar");
        try (InputStream source = (!jnlpStarter.exists()
                ? getClass().getClassLoader().getResourceAsStream("harness/jnlp/jnlp-launcher.jar")
                : new FileInputStream(jnlpStarter)); FileOutputStream outstream = new FileOutputStream(jnlpDestination);) {
            IOUtil.copy(source, outstream);
            return jnlpDestination;
        }
    }

    private void filterCopy(File sourceFile, String resourcePath, File destinationFile, Properties filterProperties)
            throws IOException {
        // buffer so it isn't reading a byte at a time!

        InputStream instream;
        if (sourceFile != null) {
            instream = new FileInputStream(sourceFile);
        } else {
            instream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        }

        FileOutputStream outstream = new FileOutputStream(destinationFile);

        try (Reader source = new BufferedReader(new InputStreamReader(instream, "UTF-8")); Writer destination = new OutputStreamWriter(outstream, "UTF-8");) {
            // support ${token}
            Reader reader = new InterpolationFilterReader(source, filterProperties, "${", "}");

            IOUtil.copy(reader, destination);
        }

    }

    /**
     * copied from MakeMasterJNLP ant task.
     *
     * @param files
     * @param antProject
     * @param masterPrefix
     * @return
     * @throws java.io.IOException
     */
    private String generateExtensions(FileSet files, Project antProject, String masterPrefix)
            throws IOException {
        StringBuilder buff = new StringBuilder();
        for (String nm : files.getDirectoryScanner(antProject).getIncludedFiles()) {
            File jar = new File(files.getDir(antProject), nm);

            if (!jar.canRead()) {
                throw new IOException("Cannot read file: " + jar);
            }

            JarFile theJar = new JarFile(jar);
            Attributes attr = theJar.getManifest().getMainAttributes();
            String codenamebase = attr.getValue("OpenIDE-Module");
            if (codenamebase == null) {
                codenamebase = attr.getValue("Bundle-SymbolicName");
            }
            if (codenamebase == null) {
                throw new IOException("Not a NetBeans Module: " + jar);
            }

            // see http://hg.netbeans.org/main-silver/rev/87823abb86d9
            if (codenamebase.equals("org.objectweb.asm.all")
                    && jar.getParentFile().getName().equals("core")
                    && jar.getParentFile().getParentFile().getName().startsWith("platform")) {
                continue;
            }

            int slash = codenamebase.indexOf('/');
            if (slash >= 0) {
                codenamebase = codenamebase.substring(0, slash);
            }

            String dashcnb = codenamebase.replace('.', '-');

            buff.append("    <extension name='").append(codenamebase).append("' href='").append(masterPrefix).
                    append(dashcnb).append(".jnlp' />\n");
            theJar.close();
        }
        return buff.toString();

    }
}
