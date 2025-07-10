/*
 * Copyright 2024 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.netbeans.nbm.stubs;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.netbeans.nbm.handlers.NbmApplicationArtifactHandler;
import org.apache.netbeans.nbm.handlers.NbmArtifactHandler;
import org.apache.netbeans.nbm.handlers.NbmFileArtifactHandler;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author skygo
 */
public class ArtifactHandlerManagerStub implements ArtifactHandlerManager {
    private final Map<String, ArtifactHandler> handlers;

    public ArtifactHandlerManagerStub() {
        this.handlers = new HashMap<>();
        this.handlers.put(NbmArtifactHandler.NAME, new NbmArtifactHandler());
        this.handlers.put(NbmFileArtifactHandler.NAME, new NbmFileArtifactHandler());
        this.handlers.put(NbmApplicationArtifactHandler.NAME, new NbmApplicationArtifactHandler());
        this.handlers.put("jar", new DefaultArtifactHandler("jar"));
    }

    @Override
    public ArtifactHandler getArtifactHandler(String type) {
        ArtifactHandler handler = handlers.get(type);
        if (handler == null) {
            handler = new DefaultArtifactHandler(type);
        }
        return handler;
    }

    @Override
    public void addHandlers(Map<String, ArtifactHandler> handlers) {
        throw new UnsupportedOperationException();
    }
}
