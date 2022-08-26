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

import org.wildfly.managed.server.builder.tool.parser.FormattingXMLStreamWriter;
import org.wildfly.managed.server.builder.tool.parser.Node;
import org.wildfly.managed.server.builder.tool.parser.generic.ElementNode;
import org.wildfly.managed.server.builder.tool.parser.generic.PomParser;
import org.wildfly.managed.server.builder.tool.parser.generic.ProcessingInstructionNode;
import org.wildfly.managed.server.builder.tool.parser.generic.TextNode;
import org.wildfly.managed.server.builder.tool.parser.serverconfig.ServerConfig;
import org.wildfly.managed.server.builder.tool.parser.serverconfig.ServerConfigParser;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Tool {
    private final Environment environment;

    public Tool(Environment environment) {
        this.environment = environment;
    }

    void prepareDeployment() throws Exception {
        copyDeploymentToServerImageBuilder();
        getServerConfigXmlContentsFromDeployment();

        mergeInputPomAndServerConfigXml();
    }

    private void copyDeploymentToServerImageBuilder() throws IOException {

        try (OutputStream out =
                     new BufferedOutputStream(
                             new FileOutputStream(
                                     environment.getServerImageDeploymentLocation().toFile()))) {
            InputStream in = environment.getWarInputStream();
            byte[] bytes = new byte[8192];
            int len = in.read(bytes);
            while (len != -1) {
                out.write(bytes, 0, len);
                len = in.read(bytes);
            }
        }
    }

    private void getServerConfigXmlContentsFromDeployment() throws IOException {
        boolean foundServerConfigXml = false;
        boolean foundServerInitCli = false;
        boolean foundServerInitYml = false;
        try (ZipInputStream zin =
                     new ZipInputStream(
                             new BufferedInputStream(
                                     new FileInputStream(environment.getServerImageDeploymentLocation().toFile())))) {

            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                try {
                    if (isMetaInfFile(entry.getName(), Environment.SERVER_CONFIG_FILE_NAME)) {
                        if (foundServerConfigXml) {
                            throw new IllegalStateException("The archive contains duplicate " + Environment.SERVER_CONFIG_FILE_NAME + " files");
                        }
                        foundServerConfigXml = true;
                        extractCurrentFile(zin, environment.getServerConfigXmlPath());
                    } else if (isMetaInfFile(entry.getName(), Environment.SERVER_INIT_CLI_FILE_NAME)) {
                        if (foundServerInitCli) {
                            throw new IllegalStateException("The archive contains duplicate " + Environment.SERVER_INIT_CLI_FILE_NAME + " files");
                        }
                        foundServerInitCli = true;
                        extractCurrentFile(zin, environment.getServerInitCliPath());
                    } else if (isMetaInfFile(entry.getName(), Environment.SERVER_INIT_YML_FILE_NAME)) {
                        if (foundServerInitYml) {
                            throw new IllegalStateException("The archive contains duplicate " + Environment.SERVER_INIT_YML_FILE_NAME + " files");
                        }
                        foundServerInitYml = true;
                        extractCurrentFile(zin, environment.getServerInitYmlPath());
                    }
                } finally {
                    zin.closeEntry();
                    entry = zin.getNextEntry();
                }
            }
        }
        if (!foundServerConfigXml) {
            throw new IllegalStateException("The deployment does not contain a " + Environment.SERVER_CONFIG_FILE_NAME + " file");
        }
    }

    private boolean isMetaInfFile(String jarEntryName, String filename) {
        if (jarEntryName.endsWith(filename)) {
            return jarEntryName.equals("WEB-INF/classes/META-INF/" + filename) || jarEntryName.equals("META-INF/" + filename);
        }
        return false;
    }

    private void extractCurrentFile(ZipInputStream zin, Path path) throws IOException {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(path.toFile()))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = zin.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    private void mergeInputPomAndServerConfigXml() throws XMLStreamException, IOException {
        Map<String, String> environmentVariables = new HashMap<>();
        List<String> commandLineArguments = new LinkedList<>();

        // Parse this, so we
        // - can easily get rid of the root element
        // - have some kind of structure in case we need to validate/enhance entries
        ServerConfigParser serverConfigXmlParser = new ServerConfigParser(environment.getServerConfigXmlPath());
        ServerConfig serverConfig = serverConfigXmlParser.parse();

        // Parse the pom
        PomParser pomParser = new PomParser(environment.getInputPomLocation());
        pomParser.parse();

        // Add the server-config.xml layers to the pom
        ProcessingInstructionNode mavenPluginLayersPlaceholder = pomParser.getMavenPluginLayersPlaceholder();
        mavenPluginLayersPlaceholder.addDelegate(serverConfig.getLayers(), true);

        // Add the data sources feature pack if any of the data sources layers were used
        for (String layer : serverConfig.getLayers().getLayers()) {
            if (environment.getDatasourceGalleonPackLayers().contains(layer)) {
                ProcessingInstructionNode datasourcesFeaturePackPlaceholder = pomParser.getDatasourcesFeaturePackPlaceholder();
                datasourcesFeaturePackPlaceholder.addDelegate(new Node() {
                    @Override
                    public void marshall(XMLStreamWriter writer) throws XMLStreamException {
                        writer.writeStartElement("feature-pack");
                        writer.writeStartElement("location");
                        // Defined along with the version in the pom
                        writer.writeCharacters(Environment.DATA_SOURCES_FEATURE_PACK_LOCATION_PROPERTY);
                        writer.writeEndElement();
                        writer.writeEndElement();
                    }
                }, true);
                break;
            }
        }

        // Add the property overrides if we had any
        if (environment.getMavenPropertyOverrides().size() > 0) {
            ProcessingInstructionNode mavenPropertyOverrides = pomParser.getPropertyOverridesPlaceholder();
            mavenPropertyOverrides.addDelegate(new Node(){
                @Override
                public void marshall(XMLStreamWriter writer) throws XMLStreamException {
                    writer.writeStartElement("properties");
                    for (Map.Entry<String, String> entry : environment.getMavenPropertyOverrides().entrySet()) {
                        writer.writeStartElement(entry.getKey());
                        writer.writeCharacters(entry.getValue());
                        writer.writeEndElement();
                    }
                    writer.writeEndElement();
                }
            }, true);
        }

        // If there was a server-init.cli, add instructions to enable it
        if (Files.exists(environment.getServerInitCliPath())) {
            // Set the environment variable to trigger the cli script. The server-image-builder pom will put it
            // under standalone/configuration/init/server-init.cli. The CLI_LAUNCH_SCRIPT env var
            // is relative to the root of the server
            environmentVariables.put("CLI_LAUNCH_SCRIPT", "standalone/configuration/init/server-init.cli");
        }

        // If there was a server-init.yml, add instructions to enable it
        if (Files.exists(environment.getServerInitYmlPath())) {
            // The server-image-builder pom will put the yaml file in the root of the server
            // We need to add some extra information to one of the modules via a service loader to enable the mechanism.
            // We create the file here, and let the service-image-builder pom copy it across for us to
            // standalone/configuration/init/server-init.yml
            Files.writeString(environment.getServerInitYmlServiceLoaderPath(), Environment.SERVER_INIT_YML_SERVICE_LOADER_CONTENTS);

            // Add `--yaml init/server-init.yml` as a command-line argument when starting the server. The file is
            // relative to the standalone/configuration folder
            commandLineArguments.add("--yaml init/server-init.yml");
        }

        if (commandLineArguments.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (String arg : commandLineArguments) {
                sb.append(" ");
                sb.append(arg);
            }
            environmentVariables.put("SERVER_ARGS", sb.toString());
        }

        // Set the collected environment variables in the pom
        ProcessingInstructionNode dockerPluginEnvVarsPlaceholder = pomParser.getDockerPluginEnvVarsPlaceholder();
        for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
            ElementNode node = new ElementNode(dockerPluginEnvVarsPlaceholder.getParent(), entry.getKey());
            node.addChild(new TextNode(entry.getValue()));
            dockerPluginEnvVarsPlaceholder.addDelegate(node, true);
        }

        // Write the updated pom
        FormattingXMLStreamWriter writer =
                new FormattingXMLStreamWriter(
                        XMLOutputFactory.newInstance().createXMLStreamWriter(
                                new BufferedWriter(
                                        new FileWriter(environment.getCreatedPomLocation().toFile()))));
        try {
            writer.writeStartDocument();
            pomParser.getRootNode().marshall(writer);
            writer.writeEndDocument();
        } finally {
            try {
                writer.close();
            } catch (Exception ignore) {
            }
        }
    }
}
