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

import org.wildfly.managed.server.builder.tool.parser.DockerCopyInitCliParser;
import org.wildfly.managed.server.builder.tool.parser.ServerConfigParser;
import org.wildfly.managed.server.builder.tool.parser.FormattingXMLStreamWriter;
import org.wildfly.managed.server.builder.tool.parser.Node;
import org.wildfly.managed.server.builder.tool.parser.PomParser;
import org.wildfly.managed.server.builder.tool.parser.ProcessingInstructionNode;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Tool {
    private final Environment environment;
    Path serverConfigXmlPath;
    Path serverInitCliPath;

    public Tool(Environment environment) {
        this.environment = environment;
        serverConfigXmlPath = environment.getServerImageBuilderLocation().resolve(Environment.SERVER_CONFIG_FILE_NAME);
        serverInitCliPath = environment.getServerImageBuilderLocation().resolve(Environment.SERVER_INIT_FILE_NAME);
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
        try (ZipInputStream zin =
                     new ZipInputStream(
                             new BufferedInputStream(
                                     new FileInputStream(environment.getServerImageDeploymentLocation().toFile())))) {

            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                try {
                    if (entry.getName().equals(Environment.SERVER_CONFIG_JAR_LOCATION_A) || entry.getName().equals(Environment.SERVER_CONFIG_JAR_LOCATION_B)) {
                        if (foundServerConfigXml) {
                            throw new IllegalStateException("The archive contains duplicate " + Environment.SERVER_CONFIG_FILE_NAME + " files");
                        }
                        foundServerConfigXml = true;
                        extractCurrentFile(zin, serverConfigXmlPath);
                    } else if (entry.getName().equals(Environment.SERVER_INIT_JAR_LOCATION_A) || entry.getName().equals(Environment.SERVER_INIT_JAR_LOCATION_B)) {
                        if (foundServerInitCli) {
                            throw new IllegalStateException("The archive contains duplicate " + Environment.SERVER_INIT_FILE_NAME + " files");
                        }
                        foundServerInitCli = true;
                        extractCurrentFile(zin, serverInitCliPath);
                    }
                } finally {
                    zin.closeEntry();
                    entry = zin.getNextEntry();
                }
            }
        }
        if (!foundServerConfigXml) {
            throw new IllegalStateException("The deployment does not contain a " + Environment.SERVER_CONFIG_JAR_LOCATION_A + " file");
        }
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
        // Parse this, so we
        // - can easily get rid of the root element
        // - have some kind of structure in case we need to validate/enhance entries
        ServerConfigParser serverConfigXmlParser = new ServerConfigParser(serverConfigXmlPath);
        serverConfigXmlParser.parse();

        // Parse the pom
        PomParser pomParser = new PomParser(environment.getInputPomLocation());
        pomParser.parse();

        // Add the server-config.xml contents to the pom
        ProcessingInstructionNode mavenPluginConfigPlaceholder = pomParser.getMavenPluginConfigPlaceholder();
        if (mavenPluginConfigPlaceholder == null) {
            throw new IllegalStateException(environment.getInputPomLocation() + " is missing the <?" + PomParser.MAVEN_PLUGIN_CONFIG_PI + "?> procesing instruction");
        }
        for (Node node : serverConfigXmlParser.getRootNode().getChildren()) {
             mavenPluginConfigPlaceholder.addDelegate(node, true);
        }

        // If there was a server-init.cli, add instructions to copy it
        if (Files.exists(serverInitCliPath)) {
            ProcessingInstructionNode dockerCopyCliPlaceholder = pomParser.getDockerCopyCliPlaceholder();
            if (mavenPluginConfigPlaceholder == null) {
                throw new IllegalStateException(environment.getInputPomLocation() + " is missing the <?" + PomParser.DOCKER_COPY_CLI_PI + "?> procesing instruction");
            }
            DockerCopyInitCliParser dockerCopyInitCliParser = new DockerCopyInitCliParser(environment.getInputCopyInitCliLocation());
            dockerCopyInitCliParser.parse();
            dockerCopyCliPlaceholder.addDelegate(dockerCopyInitCliParser.getRootNode(), true);
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
