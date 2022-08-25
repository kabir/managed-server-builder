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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Manifest;

public class Environment implements AutoCloseable {


    static final String SERVER_CONFIG_FILE_NAME = "server-config.xml";

    // Where to look for the server-init.cli in the archive
    static final String SERVER_INIT_CLI_FILE_NAME = "server-init.cli";

    // Where to look for the server-init.yml in the archive
    static final String SERVER_INIT_YML_FILE_NAME = "server-init.yml";

    // Name of the service loader file needed to enable the yaml mechanism
    static final String SERVER_INIT_YML_SERVICE_LOADER_NAME = "org.jboss.as.controller.persistence.ConfigurationExtension";
    static final String SERVER_INIT_YML_SERVICE_LOADER_CONTENTS = "org.jboss.as.controller.persistence.yaml.YamlConfigurationExtension";

    // This value is defined in the server-image-builder pom
    static final String DATA_SOURCES_GALLEON_PACK_LOCATION_PROPERTY = "${wildfly.datasources.galleon.pack.location}";

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
    private final Path serverConfigXmlPath;
    private final Path serverInitCliPath;
    private final Path serverInitYmlPath;
    private final Path serverInitYmlServiceLoaderPath;

    private final Set<String> datasourceGalleonPackLayers;

    private Environment(InputStream warInputStream, Path serverImageBuilderLocation, Set<String> datasourceGalleonPackLayers) throws IOException {
        this.warInputStream = warInputStream;
        this.serverImageBuilderLocation = serverImageBuilderLocation;
        inputPomLocation = serverImageBuilderLocation.resolve("input-pom.xml");
        createdPomLocation = serverImageBuilderLocation.resolve("pom.xml");
        this.datasourceGalleonPackLayers = datasourceGalleonPackLayers;

        // The deployment and files to be extracted from it go into the files/ folder of the
        // server-image-builder
        Path serverBuilderFilesLocation = serverImageBuilderLocation.resolve("files");
        if (!Files.exists(serverBuilderFilesLocation)) {
            Files.createDirectories(serverBuilderFilesLocation);
        }
        serverImageDeploymentLocation = serverBuilderFilesLocation.resolve("ROOT.war");
        serverConfigXmlPath = serverBuilderFilesLocation.resolve(SERVER_CONFIG_FILE_NAME);
        serverInitCliPath = serverBuilderFilesLocation.resolve(SERVER_INIT_CLI_FILE_NAME);
        serverInitYmlPath = serverBuilderFilesLocation.resolve(SERVER_INIT_YML_FILE_NAME);
        serverInitYmlServiceLoaderPath = serverBuilderFilesLocation.resolve(SERVER_INIT_YML_SERVICE_LOADER_NAME);


    }

    public InputStream getWarInputStream() {
        return warInputStream;
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

    public Path getServerConfigXmlPath() {
        return serverConfigXmlPath;
    }

    public Path getServerInitCliPath() {
        return serverInitCliPath;
    }

    public Path getServerInitYmlPath() {
        return serverInitYmlPath;
    }

    public Path getServerInitYmlServiceLoaderPath() {
        return serverInitYmlServiceLoaderPath;
    }

    public Set<String> getDatasourceGalleonPackLayers() {
        return datasourceGalleonPackLayers;
    }

    @Override
    public void close() throws Exception {
        safeClose(warInputStream);
    }

    static String getToolVersion() throws IOException {
        return readManifestValue("tool-version");
    }

    static String readManifestValue(String name) throws IOException {
        // Doing a simple ManifestUtils.class.getClassLoader().getResource("META-INF/MANIFEST.MF") doesn't always work
        // since it sometimes first tries to load from jar:file:/System/Library/Java/Extensions/MRJToolkit.jar!/META-INF/MANIFEST.MF
        for (Enumeration<URL> e = Environment.class.getClassLoader().getResources("META-INF/MANIFEST.MF"); e.hasMoreElements() ; ) {
            URL url = e.nextElement();
            try (InputStream stream = url.openStream()) {
                Manifest manifest = null;
                if (stream != null) {
                    manifest = new Manifest(stream);
                    String value = manifest.getMainAttributes().getValue(name);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }

        throw new IllegalStateException("Could not find manifest entry: " + name);
    }

    static Environment initialize() {
        InputStream warInputStream = null;
        try {
            warInputStream = getLocalOrRemoteInputStream(WAR_LOCATION.read());
            Path serverImageBuilderLocation = Paths.get(SERVER_IMAGE_BUILDER_LOCATION.read());
            if (!Files.exists(serverImageBuilderLocation)) {
                throw new IllegalStateException("Cannot find " + serverImageBuilderLocation);
            }

            Set<String> datasourceGalleonPackLayers = populateDatasourceGalleonPackLayers();
            return new Environment(
                    warInputStream,
                    serverImageBuilderLocation,
                    datasourceGalleonPackLayers);
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

    private static Set<String> populateDatasourceGalleonPackLayers() throws IOException, URISyntaxException {
        URL url = Environment.class.getClassLoader().getResource("datasource-layers.txt");
        if (url == null) {
            throw new IllegalStateException("Missing datasource-layers.txt");
        }

        List<String> lines = Files.readAllLines(Path.of(url.toURI()));
        Set<String> layers = new HashSet<>();
        for (String line : lines) {
            line = line.trim();
            if (line.length() > 0 && !line.startsWith("#")) {
                layers.add(line);
            }
        }
        return layers;
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
