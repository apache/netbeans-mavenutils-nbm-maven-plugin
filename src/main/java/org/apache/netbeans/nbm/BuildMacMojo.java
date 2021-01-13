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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.util.DefaultFileSet;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a macOS application bundle for Mavenized NetBeans application. <br>
 * Creates the brandingToken.app macOS Application bundle.
 *
 * @author <a href="mailto:oyarzun@apache.org">Christian Oyarzun</a>
 */
@Mojo( name = "build-mac",
      requiresProject = true,
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      threadSafe = true,
      defaultPhase = LifecyclePhase.PACKAGE )
public class BuildMacMojo
        extends AbstractNbmMojo
{

    /**
     * output directory.
     */
    @Parameter( defaultValue = "${project.build.directory}", required = true )
    protected File outputDirectory;
    /**
     * The branding token for the application based on NetBeans platform.
     */
    @Parameter( property = "netbeans.branding.token", required = true )
    protected String brandingToken;
    /**
     * Optional macOS icon file (in ICNS format) to use for the application bundle to replace the default icon 
     * from the harness.
     */
    @Parameter( property = "netbeans.mac.icon", required = false )
    private File macIconFile;
    /**
     * Optional macOS Info.plist file to use for the application bundle to replace the one from the harness.
     * <p>
     * ${app.title} is replaced by macAppTitle
     * ${app.name} is replaced by brandingToken
     * ${app.version} is replaced by project.version
     */
    @Parameter( property = "netbeans.mac.info.plist", required = false )
    private File macInfoplistFile;
    /**
     * Optional macOS native launcher to use for the application bundle to replace the one from the harness.
     */
    @Parameter( property = "netbeans.mac.launcher", required = false )
    private File macLauncherFile;
    /**
     * Zip the macOS app bundle to brandingToken-macOS.zip
     */
    @Parameter( property = "netbeans.mac.zipbundle", defaultValue = "false" )
    private boolean macZipBundle;
    /**
     * Optional application title to use to replace ${app.title} for value of CFBundleName in the Info.plist file,
     * otherwise brandingTokin is used.
     */
    @Parameter( property = "netbeans.mac.title", required = false )
    private String macAppTitle;
    /**
     * The Maven Project.
     */
    @Parameter( required = true, readonly = true, property = "project" )
    private MavenProject project;

    // </editor-fold>
    @Override
    public void execute(  )
            throws MojoExecutionException, MojoFailureException
    {
        if ( !"nbm-application".equals( project.getPackaging(  ) ) )
        {
            throw new MojoExecutionException(
                    "This goal only makes sense on project with 'nbm-application' packaging." );
        }

        try
        {
            File bundleDir = new File( outputDirectory, brandingToken + ".app" );
            bundleDir.mkdirs(  );

            File contentsDir = new File( bundleDir, "Contents" );
            contentsDir.mkdirs(  );

            File resourcesDir = new File( contentsDir, "Resources" );
            resourcesDir.mkdirs(  );

            File appDir = new File( resourcesDir, brandingToken );
            appDir.mkdirs(  );

            File macOSDir = new File( contentsDir, "MacOS" );
            macOSDir.mkdirs(  );

            File app = new File( outputDirectory, brandingToken );
            FileUtils.copyDirectoryStructure( app, appDir );

            // delete windows executables
            Files.delete( appDir.toPath(  ).resolve( "bin/" + brandingToken + ".exe" ) );
            Files.delete( appDir.toPath(  ).resolve( "bin/" + brandingToken + "64.exe" ) );

            copyIcon( resourcesDir );

            copyInfoPlist( contentsDir );

            copyLauncher( macOSDir );

            if ( macZipBundle )
            {
                DefaultFileSet fileset = new DefaultFileSet( outputDirectory );
                fileset.setIncludes( new String[] {bundleDir.getName(  ), bundleDir.getName(  ) + "/**"} );

                ZipArchiver archiver = new ZipArchiver(  );
                archiver.addFileSet( fileset );

                File zipFile = new File( outputDirectory, brandingToken + "-macOS.zip" );
                archiver.setDestFile( zipFile );

                archiver.createArchive(  );
            }

        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException( "Build macOS application bundle failed: " + ex, ex );
        }
    }

    void copyInfoPlist( File contentsDir ) throws IOException, MojoExecutionException
    {
        Path infoplist = contentsDir.toPath(  ).resolve( "Info.plist" );
        if ( macAppTitle == null )
        {
            macAppTitle = brandingToken;
        }

        if ( macInfoplistFile != null )
        {
            try ( Stream<String> lines = Files.lines( macInfoplistFile.toPath(  ) ) ) 
            {
                String infoPListString = lines.map( s -> s.replace( "${app.title}", macAppTitle ) )
                                              .map( s -> s.replace( "${app.name}", brandingToken ) )
                                              .map( s -> s.replace( "${app.version}", project.getVersion(  ) ) )
                                              .collect( Collectors.joining( "\n" ) );

                Files.write( infoplist, infoPListString.getBytes(  ) );
            }
        }
        else
        {
            URL harnessResourse = getClass(  ).getClassLoader(  ).getResource( "harness" );
            JarURLConnection jarConnection = ( JarURLConnection ) harnessResourse.openConnection(  );
            JarFile jarFile = jarConnection.getJarFile(  );

            JarEntry entry = jarFile.getJarEntry( "harness/etc/Info.plist" );

            if ( entry == null )
            {
                throw new MojoExecutionException( "macOS Info.plist not found in harness"
                                                  + " or via macInfoplistFile parameter" );
            }

            try (BufferedReader reader =
                    new BufferedReader( new InputStreamReader( jarFile.getInputStream( entry ) ) ) )
            {
                String infoPListString = reader.lines(  )
                                               .map( s -> s.replace( "${app.title}", macAppTitle ) )
                                               .map( s -> s.replace( "${app.name}", brandingToken ) )
                                               .map( s -> s.replace( "${app.version}", project.getVersion(  ) ) )
                                               .collect( Collectors.joining( "\n" ) );

                Files.write( infoplist, infoPListString.getBytes(  ) );
            }

        }

    }

    void copyIcon( File resourcesDir ) throws IOException, MojoExecutionException
    {
        Path icnsPath = resourcesDir.toPath(  ).resolve( brandingToken + ".icns" );

        if ( macIconFile != null )
        {
            FileUtils.copyFile( macIconFile, icnsPath.toFile(  ) );
        }
        else
        {
            URL harnessResourse = getClass(  ).getClassLoader(  ).getResource( "harness" );
            JarURLConnection jarConnection = ( JarURLConnection ) harnessResourse.openConnection(  );
            JarFile jarFile = jarConnection.getJarFile(  );

            JarEntry entry = jarFile.getJarEntry( "harness/etc/applicationIcon.icns" );

            if ( entry == null )
            {
                throw new MojoExecutionException( "macOS icon not found in harness or via macIconFile parameter" );
            }

            try ( InputStream entryInputStream = jarFile.getInputStream( entry ) )
            {
                Files.copy( entryInputStream, icnsPath, StandardCopyOption.REPLACE_EXISTING );
            }

            getLog(  ).info( "macOS icon not provided with macIconFile, using default icon." );
        }
    }

    void copyLauncher( File macOSDir ) throws IOException, MojoExecutionException
    {
        Path launcherPath = macOSDir.toPath(  ).resolve( brandingToken );

        if ( macLauncherFile != null )
        {
            FileUtils.copyFile( macLauncherFile, launcherPath.toFile(  ) );
        }
        else
        {
            URL harnessResourse = getClass(  ).getClassLoader(  ).getResource( "harness" );
            JarURLConnection jarConnection = ( JarURLConnection ) harnessResourse.openConnection(  );
            JarFile jarFile = jarConnection.getJarFile(  );

            JarEntry entry = jarFile.getJarEntry( "harness/launchers/app-macOS" );

            if ( entry == null )
            {
                throw new MojoExecutionException( "macOS launcher not found in harness"
                                                 + " or via macLauncherFile parameter" );

            }

            try ( InputStream entryInputStream = jarFile.getInputStream( entry ) )
            {
                Files.copy( entryInputStream, launcherPath, StandardCopyOption.REPLACE_EXISTING );
            }

        }

        launcherPath.toFile(  ).setExecutable( true );
    }

}
