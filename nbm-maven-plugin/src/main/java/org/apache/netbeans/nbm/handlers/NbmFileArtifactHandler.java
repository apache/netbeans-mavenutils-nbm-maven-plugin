package org.apache.netbeans.nbm.handlers;

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

import org.apache.maven.artifact.handler.ArtifactHandler;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named(NbmFileArtifactHandler.NAME)
public class NbmFileArtifactHandler implements ArtifactHandler {
    public static final String NAME = "nbm-file";

    @Override
    public String getExtension() {
        return "nbm";
    }

    @Override
    public String getClassifier() {
        return null;
    }

    @Override
    public String getPackaging() {
        return NAME;
    }

    @Override
    public boolean isIncludesDependencies() {
        return true;
    }

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public boolean isAddedToClasspath() {
        return false;
    }

    // Legacy; unused

    @Override
    public String getDirectory() {
        return getPackaging() + "s";
    }
}
