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
package org.apache.netbeans.nbm.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.StringUtils;

/**
 * Tag examines the manifest of a jar file and retrieves NetBeans specific information.
 * @author Milos Kleint
 *
 */
public class ExamineManifest {

    private final Log logger;
    private File jarFile;
    private File manifestFile;
    private boolean netBeansModule;
    private boolean osgiBundle;

    private boolean localized;
    private String specVersion;
    private String implVersion;
    private String module;
    private String locBundle;
    private String classpath;
    private boolean publicPackages;
    private boolean populateDependencies = false;
    private List<String> dependencyTokens = Collections.<String>emptyList();
    private Set<String> osgiImports = Collections.<String>emptySet();
    private Set<String> osgiExports = Collections.<String>emptySet();

    private boolean friendPackages = false;
    private List<String> friends = Collections.<String>emptyList();
    private List<String> packages = Collections.<String>emptyList();

    private List<String> requires = Collections.<String>emptyList();

    private List<String> provides = Collections.<String>emptyList();
    // that's the default behaviour without the special manifest entry
    private boolean bundleAutoload = true;

    public ExamineManifest(Log logger) {
        this.logger = logger;
    }

    public void checkFile() throws MojoExecutionException {

        resetExamination();

        Manifest mf = null;
        if (jarFile != null) {
            JarFile jar = null;
            try {
                jar = new JarFile(jarFile);
                mf = jar.getManifest();
            } catch (Exception exc) {
                throw new MojoExecutionException("Could not open " + jarFile + ": " + exc.getMessage(), exc);
            } finally {
                if (jar != null) {
                    try {
                        jar.close();
                    } catch (IOException io) {
                        throw new MojoExecutionException(io.getMessage(), io);
                    }
                }
            }
        } else if (manifestFile != null) {
            InputStream stream = null;
            try {
                stream = new FileInputStream(manifestFile);
                mf = new Manifest(stream);
            } catch (Exception exc) {
                throw new MojoExecutionException(exc.getMessage(), exc);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException io) {
                        throw new MojoExecutionException(io.getMessage(), io);
                    }
                }
            }
        }
        if (mf != null) {
            processManifest(mf);
        } else {
            // MNBMODULE-22
            File source = manifestFile;
            if (source == null) {
                source = jarFile;
            }
            if (source == null) {
                logger.debug("No manifest to examine");
            } else {
                logger.debug("Cannot find manifest entries in " + source.getAbsolutePath());
            }
        }
    }

    private void resetExamination() {
        setNetBeansModule(false);
        this.localized = false;
        this.specVersion = null;
        this.implVersion = null;
        this.module = null;
        this.locBundle = null;
        this.publicPackages = false;
        classpath = "";
    }

    private void processManifest(Manifest mf) {
        Attributes attrs = mf.getMainAttributes();
        this.module = attrs.getValue("OpenIDE-Module");
        setNetBeansModule(getModule() != null);
        if (isNetBeansModule()) {
            this.locBundle = attrs.getValue("OpenIDE-Module-Localizing-Bundle");
            this.localized = locBundle != null;
            this.specVersion = attrs.getValue("OpenIDE-Module-Specification-Version");
            this.implVersion = attrs.getValue("OpenIDE-Module-Implementation-Version");
            String cp = attrs.getValue(Attributes.Name.CLASS_PATH);
            classpath = cp == null ? "" : cp;
            String value = attrs.getValue("OpenIDE-Module-Public-Packages");
            String frList = attrs.getValue("OpenIDE-Module-Friends");
            if (value == null || value.trim().equals("-")) {
                this.publicPackages = false;
            } else {
                if (frList != null) {
                    this.publicPackages = false;
                    String[] friendList = StringUtils.stripAll(StringUtils.split(frList, ","));
                    friendPackages = true;
                    friends = Arrays.asList(friendList);
                } else {
                    this.publicPackages = true;
                }
                String[] packageList = StringUtils.stripAll(StringUtils.split(value, ","));
                packages = Arrays.asList(packageList);
            }
            if (populateDependencies) {
                String deps = attrs.getValue("OpenIDE-Module-Module-Dependencies");
                if (deps != null) {
                    StringTokenizer tokens = new StringTokenizer(deps, ",");
                    List<String> depList = new ArrayList<String>();
                    while (tokens.hasMoreTokens()) {
                        String tok = tokens.nextToken();
                        // we are just interested in specification and loose dependencies.
                        int spec = tok.indexOf('>');
                        int impl = tok.indexOf('=');
                        if (spec > 0) {
                            tok = tok.substring(0, spec);
                        } else if (impl > 0) {
                            tok = tok.substring(0, impl);
                        }
                        int slash = tok.indexOf('/');
                        if (slash > 0) {
                            tok = tok.substring(0, slash);
                        }
                        depList.add(tok.trim().intern());
                    }
                    this.dependencyTokens = depList;
                }
                String req = attrs.getValue("OpenIDE-Module-Requires");
                String prov = attrs.getValue("OpenIDE-Module-Provides");
                String needs = attrs.getValue("OpenIDE-Module-Needs");
                if (prov != null) {
                    provides = Arrays.asList(StringUtils.stripAll(StringUtils.split(prov, ",")));
                }
                if (req != null || needs != null) {
                    requires = new ArrayList<String>();
                    if (req != null) {
                        requires.addAll(Arrays.asList(StringUtils.stripAll(StringUtils.split(req, ","))));
                    }
                    if (needs != null) {
                        requires.addAll(Arrays.asList(StringUtils.stripAll(StringUtils.split(needs, ","))));
                    }
                }
            }

        } else {

            // check osgi headers first, let nb stuff override it, making nb default
            String bndName = attrs.getValue("Bundle-SymbolicName");
            if (bndName != null) {
                this.osgiBundle = true;
                this.module =
                        bndName./* MNBMODULE-125 */ replaceFirst(" *;.+", "")./* MNBMODULE-96 */ replace('-', '_');
                this.specVersion = attrs.getValue("Bundle-Version");
                String exp = attrs.getValue("Export-Package");
                String autoload = attrs.getValue("Nbm-Maven-Plugin-Autoload");
                if (autoload != null) {
                    bundleAutoload = Boolean.parseBoolean(autoload);
                }
                this.publicPackages = exp != null;
                if (populateDependencies) {
                    // well, this doesn't appear to cover
                    // the major way of declation dependencies in osgi - Import-Package
                    String deps = attrs.getValue("Require-Bundle");
                    if (deps != null) {
                        List<String> depList = new ArrayList<String>();
                        // https://stackoverflow.com/questions/1757065
                        // java-splitting-a-comma-separated-string-but-ignoring-commas-in-quotes
                        for (String piece : deps.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)")) {
                            depList.add(piece.replaceFirst(";.+", "").trim().intern());
                        }
                        this.dependencyTokens = depList;
                    }
                    String imps = attrs.getValue("Import-Package");
                    if (imps != null) {
                        Set<String> depList = new HashSet<String>();
                        // https://stackoverflow.com/questions/1757065
                        // java-splitting-a-comma-separated-string-but-ignoring-commas-in-quotes
                        for (String piece : imps.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)")) {
                            depList.add(piece.replaceFirst(";.+", "").trim().intern());
                        }
                        this.osgiImports = depList;
                    }
                    String exps = attrs.getValue("Export-Package");
                    if (exps != null) {
                        Set<String> depList = new HashSet<String>();
                        // https://stackoverflow.com/questions/1757065
                        // java-splitting-a-comma-separated-string-but-ignoring-commas-in-quotes
                        for (String piece : exps.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)")) {
                            depList.add(piece.replaceFirst(";.+", "").trim().intern());
                        }
                        this.osgiExports = depList;
                    }
                }
            } else {

                // for non-netbeans, non-osgi jars.
                this.specVersion = attrs.getValue("Specification-Version");
                this.implVersion = attrs.getValue("Implementation-Version");
                this.module = attrs.getValue("Package");
                this.publicPackages = false;
                classpath = "";
                /*    if ( module != null )
                {
                // now we have the package to make it a module definition, add the version there..
                module = module + "/1";
                }
                 */
                if (getModule() == null) {
                    // do we want to do that?
                    this.module = attrs.getValue("Extension-Name");
                }
            }
        }
    }

    /**
     * The jar file to examine. It is exclusive with manifestFile.
     * @param jarFileLoc jar file
     */
    public void setJarFile(File jarFileLoc) {
        jarFile = jarFileLoc;
    }

    /**
     * Manifest file to be examined. It is exclusive with jarFile.
     * @param manifestFileLoc manifedt file
     */
    public void setManifestFile(File manifestFileLoc) {
        manifestFile = manifestFileLoc;
    }

    /**
     * Either call {@link #setJarFile} or {@link #setManifestFile} as appropriate.
     * @param artifactFileLoc a JAR or folder
     */
    public void setArtifactFile(File artifactFileLoc) {
        if (artifactFileLoc.isFile()) {
            setJarFile(artifactFileLoc);
        } else if (artifactFileLoc.isDirectory()) {
            File mani = new File(artifactFileLoc, "META-INF/MANIFEST.MF");
            if (mani.isFile()) {
                setManifestFile(mani);
            } // else e.g. jarprj/target/classes has no manifest, so nothing to examine
        } else {
            throw new IllegalArgumentException(artifactFileLoc.getAbsolutePath());
        }
    }

    public String getClasspath() {
        return classpath;
    }

    public boolean isNetBeansModule() {
        return netBeansModule;
    }

    public void setNetBeansModule(boolean netBeansModule) {
        this.netBeansModule = netBeansModule;
    }

    public boolean isLocalized() {
        return localized;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public String getImplVersion() {
        return implVersion;
    }

    /**
     * Code name base of the module only.
     * Does not include any release version.
     * @return module code name base
     */
    public String getModule() {
        return module != null ? module.replaceFirst("/\\d+$", "") : module;
    }

    /**
     * Full name of module: code name base, then optionally slash and major release version.
     * @return module full name
     */
    public String getModuleWithRelease() {
        return module;
    }

    /**
     * returns true if there are defined public packages and there is no friend
     * declaration.
     * @return true if has public package
     */
    public boolean hasPublicPackages() {
        return publicPackages;
    }

    public void setPopulateDependencies(boolean populateDependencies) {
        this.populateDependencies = populateDependencies;
    }

    public List<String> getDependencyTokens() {
        return dependencyTokens;
    }

    /**
     * returns true if both public packages and friend list are declared.
     * @return true if has friend package
     */
    public boolean hasFriendPackages() {
        return friendPackages;
    }

    public List<String> getFriends() {
        return friends;
    }

    /**
     * list of package statements from OpenIDE-Module-Public-Packages.
     * All items end with .*
     * @return list of package
     */
    public List<String> getPackages() {
        return packages;
    }

    public boolean isOsgiBundle() {
        return osgiBundle;
    }

    public Set<String> getOsgiImports() {
        return osgiImports;
    }

    public Set<String> getOsgiExports() {
        return osgiExports;
    }

    public List<String> getNetBeansRequiresTokens() {
        return requires;
    }

    public List<String> getNetBeansProvidesTokens() {
        return provides;
    }

    public boolean isBundleAutoload() {
        return bundleAutoload;
    }
}
