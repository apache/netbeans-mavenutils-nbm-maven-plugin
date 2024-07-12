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

import java.time.Instant;
import java.util.Date;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * AdaptNbVersionTest.
 */
public class AdaptNbVersionTest {

    /**
     * Test of destinationFileName method, of class BrandingMojo.
     */
    @Test
    public void testAdaptVersion() {
        Date date = Date.from(Instant.ofEpochSecond(1721051156)); // 2024-07-15T13:45:56Z

        // Empty version
        assertEquals("0.0.0", AdaptNbVersion.adaptVersion("", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("0.0.0", AdaptNbVersion.adaptVersion("", AdaptNbVersion.TYPE_SPECIFICATION, date));

        // SNAPSHOT version
        assertEquals("0.0.0.20240715", AdaptNbVersion.adaptVersion("SNAPSHOT", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("0.0.0", AdaptNbVersion.adaptVersion("SNAPSHOT", AdaptNbVersion.TYPE_SPECIFICATION, date));

        // Implementation versions
        assertEquals("1", AdaptNbVersion.adaptVersion("1", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2", AdaptNbVersion.adaptVersion("1.2", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2.3.4", AdaptNbVersion.adaptVersion("1.2.3.4", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        // Implementation versions with -SNAPSHOT qualifier
        assertEquals("1-20240715", AdaptNbVersion.adaptVersion("1-SNAPSHOT", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2-20240715", AdaptNbVersion.adaptVersion("1.2-SNAPSHOT", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2.3-20240715", AdaptNbVersion.adaptVersion("1.2.3-SNAPSHOT", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2.3.4-20240715", AdaptNbVersion.adaptVersion("1.2.3.4-SNAPSHOT", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        // Implementation versions with a different qualifier
        assertEquals("1-BETA1", AdaptNbVersion.adaptVersion("1-BETA1", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2-BETA1", AdaptNbVersion.adaptVersion("1.2-BETA1", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2.3-BETA1", AdaptNbVersion.adaptVersion("1.2.3-BETA1", AdaptNbVersion.TYPE_IMPLEMENTATION, date));
        assertEquals("1.2.3.4-BETA1", AdaptNbVersion.adaptVersion("1.2.3.4-BETA1", AdaptNbVersion.TYPE_IMPLEMENTATION, date));

        // Specification versions
        assertEquals("1", AdaptNbVersion.adaptVersion("1", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2", AdaptNbVersion.adaptVersion("1.2", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3.4", AdaptNbVersion.TYPE_SPECIFICATION, date));
        // Specification versions with -SNAPSHOT qualifier
        assertEquals("1", AdaptNbVersion.adaptVersion("1-SNAPSHOT", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2", AdaptNbVersion.adaptVersion("1.2-SNAPSHOT", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3-SNAPSHOT", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3.4-SNAPSHOT", AdaptNbVersion.TYPE_SPECIFICATION, date));
        // Specification versions with a different qualifier
        assertEquals("1", AdaptNbVersion.adaptVersion("1-BETA1", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2", AdaptNbVersion.adaptVersion("1.2-BETA1", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3-BETA1", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3.4-BETA1", AdaptNbVersion.TYPE_SPECIFICATION, date));
        // Specification versions with a different qualifier
        assertEquals("1", AdaptNbVersion.adaptVersion("1_BETA1", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2", AdaptNbVersion.adaptVersion("1.2_BETA1", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3_BETA1", AdaptNbVersion.TYPE_SPECIFICATION, date));
        assertEquals("1.2.3", AdaptNbVersion.adaptVersion("1.2.3.4_BETA1", AdaptNbVersion.TYPE_SPECIFICATION, date));
    }

}
