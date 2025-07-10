package org.apache.netbeans.nbm.lifecycle;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.inject.Provider;
import org.apache.maven.lifecycle.mapping.Lifecycle;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;

public abstract class LifecycleMappingProviderSupport implements Provider<LifecycleMapping> {

    private static final String DEFAULT_LIFECYCLE_KEY = "default";

    private final Lifecycle defaultLifecycle;
    private final LifecycleMapping lifecycleMapping;

    public LifecycleMappingProviderSupport() {
        this.defaultLifecycle = new Lifecycle();
        this.defaultLifecycle.setId(DEFAULT_LIFECYCLE_KEY);
        this.defaultLifecycle.setLifecyclePhases(loadMapping());

        this.lifecycleMapping = new LifecycleMapping() {
            @Override
            public Map<String, Lifecycle> getLifecycles() {
                return Collections.singletonMap(DEFAULT_LIFECYCLE_KEY, defaultLifecycle);
            }

            @Override
            public List<String> getOptionalMojos(String lifecycle) {
                return null;
            }

            @Override
            public Map<String, String> getPhases(String lifecycle) {
                if (DEFAULT_LIFECYCLE_KEY.equals(lifecycle)) {
                    return defaultLifecycle.getPhases();
                } else {
                    return null;
                }
            }
        };
    }

    private Map<String, LifecyclePhase> loadMapping() {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getResourceAsStream(getClass().getSimpleName() + ".properties")) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        HashMap<String, LifecyclePhase> result = new HashMap<>();
        for (String phase : properties.stringPropertyNames()) {
            result.put(phase, new LifecyclePhase(properties.getProperty(phase)));
        }
        return result;
    }

    @Override
    public LifecycleMapping get() {
        return lifecycleMapping;
    }
}