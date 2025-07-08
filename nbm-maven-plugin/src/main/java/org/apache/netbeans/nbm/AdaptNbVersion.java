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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;

/**
 * will try to convert the maven version number to a NetBeans friendly version
 * number.
 *
 * @author Milos Kleint
 *
 */
public class AdaptNbVersion {

    public static final String TYPE_SPECIFICATION = "spec"; //NOI18N
    public static final String TYPE_IMPLEMENTATION = "impl"; //NOI18N
    private static final String SNAPSHOT = "SNAPSHOT"; //NOI18N

    public static String adaptVersion(String version, Object type, Date date) {
        StringTokenizer tok = new StringTokenizer(version, ".");
        if (SNAPSHOT.equals(version) && TYPE_IMPLEMENTATION.equals(type)) {
            return "0.0.0." + generateSnapshotValue(date);
        }
        StringBuilder toReturn = new StringBuilder();
        while (tok.hasMoreTokens()) {
            String token = tok.nextToken();
            if (TYPE_IMPLEMENTATION.equals(type)) {
                int snapshotIndex = token.indexOf(SNAPSHOT);
                if (snapshotIndex > 0) {
                    String repl = token.substring(0, snapshotIndex) + generateSnapshotValue(date);
                    if (token.length() > snapshotIndex + SNAPSHOT.length()) {
                        repl = token.substring(
                                snapshotIndex + SNAPSHOT.length());
                    }
                    token = repl;
                }
            }
            if (TYPE_SPECIFICATION.equals(type)) {
                // strip the trailing -RC1, -BETA5, -SNAPSHOT
                if (token.indexOf('-') > 0) {
                    token = token.substring(0, token.indexOf('-'));
                } else if (token.indexOf('_') > 0) {
                    token = token.substring(0, token.indexOf('_'));
                }
                try {
                    int intValue = Integer.parseInt(token);
                    token = Integer.toString(intValue);
                } catch (NumberFormatException exc) {
                    // ignore, will just not be added to the
                    token = "";
                }
            }
            if (!token.isEmpty()) {
                if (toReturn.length() != 0) {
                    toReturn.append(".");
                }
                toReturn.append(token);
            }

        }
        if (toReturn.length() == 0) {
            toReturn.append("0.0.0");
        }
        String result = toReturn.toString();
        if (TYPE_SPECIFICATION.equals(type)) {
            result = computeSpecificationVersion(result);
        }
        return result;
    }

    private static String generateSnapshotValue(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    private static String computeSpecificationVersion(String v) {
        int pos = -1;
        for (int i = 0; i < 3; i++) {
            pos = v.indexOf('.', pos + 1);
            if (pos == -1) {
                return v;
            }
        }
        return v.substring(0, pos);
    }

}
