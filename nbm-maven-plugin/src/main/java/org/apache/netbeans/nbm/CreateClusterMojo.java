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
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.netbeans.nbm.utils.ExamineManifest;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.filters.StringInputStream;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.io.InputStreamFacade;

/**
 * Create the NetBeans module clusters from reactor. Semi-deprecated; used only for standalone modules and "suites".
 *
 * @author Milos Kleint
 */
@Mojo( name = "cluster", aggregator = true, requiresDependencyResolution = ResolutionScope.RUNTIME )
public class CreateClusterMojo
        extends AbstractNbmMojo
{

    /**
     * NetBeans module assembly build directory. directory where the the NetBeans jar and nbm file get constructed.
     */
    @Parameter( defaultValue = "${project.build.directory}/nbm", property = "maven.nbm.buildDir" )
    protected File nbmBuildDir;

    /**
     * NetBeans module's cluster. Replaces the cluster element in module descriptor.
     *
     */
    @Parameter( required = true, defaultValue = "extra" )
    protected String cluster;

    /**
     * directory where the the NetBeans cluster will be created.
     */
    @Parameter( defaultValue = "${project.build.directory}/netbeans_clusters", required = true )
    protected File clusterBuildDir;

    /**
     * If the executed project is a reactor project, this will contains the full list of projects in the reactor.
     */
    @Parameter( required = true, readonly = true, property = "reactorProjects" )
    private List<MavenProject> reactorProjects;

    public void execute()
            throws MojoExecutionException, MojoFailureException
    {
        Project antProject = registerNbmAntTasks();

        if ( !clusterBuildDir.exists() )
        {
            clusterBuildDir.mkdirs();
        }

        if ( reactorProjects != null && reactorProjects.size() > 0 )
        {
            for ( MavenProject proj : reactorProjects )
            {

                File nbmDir = new File( nbmBuildDir, "clusters" );
                if ( nbmDir.exists() )
                {
                    Copy copyTask = (Copy) antProject.createTask( "copy" );
                    copyTask.setTodir( clusterBuildDir );
                    copyTask.setOverwrite( true );
                    FileSet set = new FileSet();
                    set.setDir( nbmDir );
                    set.createInclude().setName( "**" );
                    copyTask.addFileset( set );

                    try
                    {
                        copyTask.execute();
                    }
                    catch ( BuildException ex )
                    {
                        getLog().error( "Cannot merge modules into cluster" );
                        throw new MojoExecutionException(
                                "Cannot merge modules into cluster", ex );
                    }
                }
                else
                {
                    if ( "nbm".equals( proj.getPackaging() ) )
                    {
                        String error
                                = "The NetBeans binary directory structure for "
                                + proj.getId()
                                + " is not created yet."
                                + "\n Please execute 'mvn install nbm:cluster' "
                                + "to build all relevant projects in the reactor.";
                        throw new MojoFailureException( error );
                    }
                    if ( "bundle".equals( proj.getPackaging() ) )
                    {
                        Artifact art = proj.getArtifact();
                        final ExamineManifest mnf = new ExamineManifest( getLog() );

                        File jar = new File( proj.getBuild().getDirectory(), proj.getBuild().getFinalName() + ".jar" );
                        if ( !jar.exists() )
                        {
                            getLog().error( "Skipping " + proj.getId()
                                    + ". Cannot find the main artifact in output directory." );
                            continue;
                        }
                        mnf.setJarFile( jar );
                        mnf.checkFile();

                        File clusterDir = new File( clusterBuildDir, cluster );
                        getLog().debug( "Copying " + art.getId() + " to cluster " + cluster );
                        File modules = new File( clusterDir, "modules" );
                        modules.mkdirs();
                        File config = new File( clusterDir, "config" );
                        File confModules = new File( config, "Modules" );
                        confModules.mkdirs();
                        File updateTracting = new File( clusterDir, "update_tracking" );
                        updateTracting.mkdirs();

                        final String cnb = mnf.getModule();
                        final String cnbDashed = cnb.replace( ".", "-" );
                        //do we need the file in some canotical name pattern for moduleArt?
                        final File moduleArt = new File( modules, cnbDashed + ".jar" );
                        final String specVer = mnf.getSpecVersion();
                        try
                        {
                            FileUtils.copyFile( jar, moduleArt );
                            final File moduleConf = new File( confModules, cnbDashed + ".xml" );
                            FileUtils.copyStreamToFile( new InputStreamFacade()
                            {
                                public InputStream getInputStream() throws IOException
                                {
                                    return new StringInputStream( CreateClusterAppMojo.createBundleConfigFile( cnb, mnf.
                                            isBundleAutoload() ), "UTF-8" );
                                }
                            }, moduleConf );
                            FileUtils.copyStreamToFile( new InputStreamFacade()
                            {
                                public InputStream getInputStream() throws IOException
                                {
                                    return new StringInputStream( CreateClusterAppMojo.createBundleUpdateTracking( cnb,
                                            moduleArt, moduleConf, specVer ), "UTF-8" );
                                }
                            }, new File( updateTracting, cnbDashed + ".xml" ) );
                        }
                        catch ( IOException exc )
                        {
                            getLog().error( exc );
                        }

                    }
                }
            }
            //in 6.1 the rebuilt modules will be cached if the timestamp is not touched.
            File[] files = clusterBuildDir.listFiles();
            for ( int i = 0; i < files.length; i++ )
            {
                if ( files[i].isDirectory() )
                {
                    File stamp = new File( files[i], ".lastModified" );
                    if ( !stamp.exists() )
                    {
                        try
                        {
                            stamp.createNewFile();
                        }
                        catch ( IOException ex )
                        {
                            ex.printStackTrace();
                        }
                    }
                    stamp.setLastModified( new Date().getTime() );
                }
            }
            getLog().info( "Created NetBeans module cluster(s) at " + clusterBuildDir );
        }
        else
        {
            throw new MojoExecutionException( "This goal only makes sense on reactor projects." );
        }
    }
}
