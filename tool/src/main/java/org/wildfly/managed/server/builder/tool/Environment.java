/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.managed.server.builder.tool;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Environment implements AutoCloseable {
    static final String SERVER_CONFIG_FILE_NAME = "server-config.xml";
    static final String SERVER_CONFIG_PATH = "META-INF/" + SERVER_CONFIG_FILE_NAME;

    private static final Entry WAR_LOCATION =
            new Entry(true,
                    "wildfly.builder.war.location",
                    "WILDFLY_BUILDER_WAR_LOCATION");

    private static final Entry SERVER_IMAGE_BUILDER_LOCATION =
            new Entry(true,
                    "wildfly.builder.server.image.builder.location",
                    "WILDFLY_BUILDER_SERVER_IMAGE_BUILDER_LOCATION");

    private final InputStream warInputStream;
    private final Path serverImageBuilderLocation;
    private final Path serverImageDeploymentLocation;
    private final Path inputPomLocation;
    private final Path createdPomLocation;

    public Environment(InputStream warInputStream, Path serverImageBuilderLocation) {
        this.warInputStream = warInputStream;
        this.serverImageBuilderLocation = serverImageBuilderLocation;
        this.serverImageDeploymentLocation = serverImageBuilderLocation.resolve("ROOT.war");
        inputPomLocation = serverImageBuilderLocation.resolve("input-pom.xml");
        createdPomLocation = serverImageBuilderLocation.resolve("pom.xml");
    }

    public InputStream getWarInputStream() {
        return warInputStream;
    }

    public Path getServerImageBuilderLocation() {
        return serverImageBuilderLocation;
    }

    public Path getServerImageDeploymentLocation() {
        return serverImageDeploymentLocation;
    }

    public Path getInputPomLocation() {
        return inputPomLocation;
    }

    public Path getCreatedPomLocation() {
        return createdPomLocation;
    }

    @Override
    public void close() throws Exception {
        safeClose(warInputStream);
    }

    static Environment initialize() {
        InputStream warInputStream = null;
        try {
            warInputStream = getLocalOrRemoteInputStream(WAR_LOCATION.read());
            Path serverImageBuilderLocation = Paths.get(SERVER_IMAGE_BUILDER_LOCATION.read());
            if (!Files.exists(serverImageBuilderLocation)) {
                throw new IllegalStateException("Cannot find " + serverImageBuilderLocation);
            }
            return new Environment(
                    warInputStream,
                    serverImageBuilderLocation);
        } catch (Throwable t) {
            safeClose(warInputStream);
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new RuntimeException(t);
        }
    }

    private static InputStream getLocalOrRemoteInputStream(String location) throws IOException {
        try {
            URL url = new URL(location);
            return new BufferedInputStream(url.openStream());
        } catch (MalformedURLException e) {
            // It is not a valid URL so try resolving locally
            Path path = Path.of(location);
            if (!path.isAbsolute()) {
                path = Path.of(".").resolve(path).normalize();
            }
            if (!Files.exists(path)) {
                throw new IllegalStateException(location + " resolves to the follwing non-existant location: " + path);
            }
            return new BufferedInputStream(new FileInputStream(path.toFile()));
        }
    }

    private static void safeClose(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (Exception ignore) {
        }
    }

    private static class Entry {
        private final boolean required;
        private final String property;
        private final String envVar;

        public Entry(boolean required, String property, String envVar) {
            this.required = required;
            this.property = property;
            this.envVar = envVar;
        }

        String read() {
            String value = System.getProperty(property);
            if (value != null) {
                return value.trim();
            }
            value = System.getenv(envVar);
            if (value != null) {
                return value.trim();
            }

            if (required) {
                String msg = String.format("Neither system property %s, nor environment variable %s was set", property, envVar);
                throw new IllegalStateException(msg);
            }

            return null;
        }
    }
}
