/* ==========================================================================
 * Copyright Milos Kleint
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */
package org.codehaus.mojo.nbm;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Chmod;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

/**
 * Create the Netbeans module clusters/application
 * @author <a href="mailto:mkleint@codehaus.org">Milos Kleint</a>
 * @goal cluster-app
 * @requiresDependencyResolution runtime
 * @requiresProject
 */
    public class CreateClusterAppMojo
        extends AbstractNbmMojo {

    /**
     * output directory where the the netbeans application will be created.
     * @parameter default-value="${project.build.directory}"
     * @required
     */
    private File buildDirectory;
    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    /**
     * The branding token for the application based on NetBeans platform.
     * @parameter expression="${netbeans.branding.token}"
     * @required
     */
    protected String brandingToken;

    /**
     * 
     * @parameter expression="${netbeans.conf.file}"
     */
    private File etcConfFile;

    /**
     * 
     * @parameter expression="${netbeans.clusters.file}"
     */
    private File etcClustersFile;

    /**
     * @parameter expression="${netbeans.bin.directory}"
     */
    private File binDirectory;

    public void execute() throws MojoExecutionException, MojoFailureException {
        
        File nbmBuildDirFile = new File(buildDirectory, brandingToken);
        if (!nbmBuildDirFile.exists()) {
            nbmBuildDirFile.mkdirs();
        }

        if ("nbm-application".equals(project.getPackaging())) {
            Set knownClusters = new HashSet();
            Set artifacts = project.getArtifacts();
            Iterator it = artifacts.iterator();
            while (it.hasNext()) {
                Artifact art = (Artifact) it.next();
                if (art.getType().equals("nbm-file")) {
                    JarFile jf = null;
                    try {
                        jf = new JarFile(art.getFile());
                        String cluster = findCluster(jf);
                        if (!knownClusters.contains(cluster)) {
                            getLog().info("Processing cluster '" + cluster + "'");
                            knownClusters.add(cluster);
                        }
                        File clusterFile = new File(nbmBuildDirFile, cluster);
                        boolean newer = false;
                        if (!clusterFile.exists()) {
                            clusterFile.mkdir();
                            newer = true;
                        } else {
                            File stamp = new File(clusterFile, ".lastModified");
                            if (stamp.lastModified() < art.getFile().lastModified()) {
                                newer = true;
                            }
                        }
                        if (newer) {
                            getLog().debug("Copying " + art.getId() + " to cluster " + cluster);
                            Enumeration enu = jf.entries();
                            while (enu.hasMoreElements()) {
                                ZipEntry ent = (ZipEntry) enu.nextElement();
                                String name = ent.getName();
                                if (name.startsWith("netbeans/")) { //ignore everything else.
                                    String path = name.replace("netbeans/", cluster + "/");
                                    File fl = new File(nbmBuildDirFile, path.replace("/", File.separator));
                                    if (ent.isDirectory()) {
                                        fl.mkdirs();
                                    } else {
                                        fl.getParentFile().mkdirs();
                                        fl.createNewFile();
                                        BufferedOutputStream outstream = null;
                                        try {
                                            outstream = new BufferedOutputStream(new FileOutputStream(fl));
                                            InputStream instream = jf.getInputStream(ent);
                                            IOUtil.copy(instream, outstream);
                                        } finally {
                                            IOUtil.close(outstream);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException ex) {
                        getLog().error(ex);
                    } finally {
                        try {
                            jf.close();
                        } catch (IOException ex) {
                            getLog().error(ex);
                        }
                    }
                }
            }
            getLog().info("Created NetBeans module cluster(s) at " + nbmBuildDirFile.getAbsoluteFile());

        } else {
            throw new MojoExecutionException("This goal only makes sense on project with nbm-application packaging");
        }
        //in 6.1 the rebuilt modules will be cached if the timestamp is not touched.
        File[] files = nbmBuildDirFile.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                File stamp = new File(files[i], ".lastModified");
                if (!stamp.exists()) {
                    try {
                        stamp.createNewFile();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                stamp.setLastModified(new Date().getTime());
            }
        }
        try {
            createBinEtcDir(nbmBuildDirFile, brandingToken);
        } catch (IOException ex) {
            throw new MojoExecutionException("Cannot process etc folder content creation.", ex);
        }
    }
    private final static Pattern patt = Pattern.compile(".*targetcluster=\"([a-zA-Z0-9_\\.\\-]+)\".*", Pattern.DOTALL);

    private String findCluster(JarFile jf) throws MojoFailureException, IOException {
        ZipEntry entry = jf.getEntry("Info/info.xml");
        InputStream ins = jf.getInputStream(entry);
        String str = IOUtil.toString(ins, "UTF8");
        Matcher m = patt.matcher(str);
        if (!m.matches()) {
            getLog().error("Cannot find cluster for " + jf.getName());
        } else {
            return m.group(1);
        }
        return "extra";
    }
    
    /**
     * 
     * @param buildDir Directory where the platform bundle is built
     * @param harnessDir "harness" directory of the netbeans installation
     * @param enabledClusters The names of all enabled clusters
     * @param defaultOptions Options for the netbeans platform to be placed in config file
     * @param brandingToken 
     * 
     * @throws java.io.IOException
     */
    private void createBinEtcDir(File buildDir, String brandingToken) throws IOException, MojoExecutionException {
        File etcDir = new File(buildDir + File.separator + "etc");
        etcDir.mkdir();

        // create app.clusters which contains a list of clusters to include in the application

        File clusterConf = new File(etcDir + File.separator + brandingToken + ".clusters");
        String clustersString;
        if (etcClustersFile != null) {
            clustersString = FileUtils.fileRead(etcClustersFile, "UTF-8");
        } else {
            clusterConf.createNewFile();
            StringBuffer buffer = new StringBuffer();
            File[] clusters = buildDir.listFiles(new FileFilter() {
                public boolean accept(File pathname) {
                    return new File(pathname, ".lastModified").exists();
                }
            });
            for (File cluster : clusters) {
                buffer.append(cluster.getName());
                buffer.append("\n");
            }
            clustersString = buffer.toString();
        }

        FileUtils.fileWrite(clusterConf.getAbsolutePath(), clustersString);

        File confFile = etcConfFile;
        if (confFile == null) {
            File harnessDir = new File(buildDir, "harness");
            if (!harnessDir.exists()) {
                throw new MojoExecutionException("Missing the harness cluster module(s). Either define parameters etcConfFile and binDirectory or includ ethe harness modules/cluster in the application.");
            }
            // app.conf contains default options and other settings
            confFile = new File(harnessDir.getAbsolutePath() + File.separator + "etc" + File.separator + "app.conf");
        }
        File confDestFile = new File(etcDir.getAbsolutePath() + File.separator + brandingToken + ".conf");
        
        String str = FileUtils.fileRead(confFile, "UTF-8");
        str = str.replace("${APPNAME}", brandingToken);
        FileUtils.fileWrite(confDestFile.getAbsolutePath(), "UTF-8", str);
        
        File destBinDir = new File(buildDir + File.separator + "bin");
        destBinDir.mkdir();
        
        File binDir;
        if (binDirectory != null) {
            binDir = binDirectory;
            FileUtils.copyDirectoryStructureIfModified(binDir, destBinDir);
        } else {
            File harnessDir = new File(buildDir, "harness");
            if (!harnessDir.exists()) {
                throw new MojoExecutionException("Missing the harness cluster module(s). Either define parameters etcConfFile and binDirectory or includ ethe harness modules/cluster in the application.");
            }
            binDir = new File(harnessDir.getAbsolutePath() + File.separator + "launchers");
            File exe = new File(binDir, "app.exe");
            FileUtils.copyFile(exe, new File(destBinDir, brandingToken + ".exe"));
            File exew = new File(binDir, "app_w.exe");
            FileUtils.copyFile(exew, new File(destBinDir, brandingToken + "_w.exe"));
            File sh = new File(binDir, "app.sh");
            FileUtils.copyFile(sh, new File(destBinDir, brandingToken));
        }

        Project antProject = new Project();
        antProject.init();
        
        Chmod chmod = (Chmod) antProject.createTask("chmod");
        FileSet fs = new FileSet();
        fs.setDir(destBinDir);
        fs.setIncludes("*");
        chmod.addFileset(fs);
        chmod.setPerm("755");
        chmod.execute();
    }
    
}