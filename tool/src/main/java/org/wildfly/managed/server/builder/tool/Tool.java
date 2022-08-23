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

import org.wildfly.managed.server.builder.tool.parser.EapXmlParser;
import org.wildfly.managed.server.builder.tool.parser.ElementNode;
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
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Tool {
    private final Environment environment;

    public Tool(Environment environment) {
        this.environment = environment;
    }

    void prepareDeployment() throws Exception {
        copyDeploymentToServerImageBuilder();
        Path path = getEapXmlContentsFromDeployment();

        mergeInputPomAndEapXml(path);



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

    private Path getEapXmlContentsFromDeployment() throws IOException {
        boolean found = false;
        Path eapXmlPath = environment.getServerImageBuilderLocation().resolve(Environment.EAP_XML_FILE_NAME);
        try (ZipInputStream zin =
                     new ZipInputStream(
                             new BufferedInputStream(
                                     new FileInputStream(environment.getServerImageDeploymentLocation().toFile())))) {

            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                try {
                    if (entry.getName().endsWith(Environment.EAP_XML_PATH)) {
                        found = true;
                        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(eapXmlPath.toFile()))) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = zin.read(buffer)) > 0) {
                                out.write(buffer, 0, len);
                            }
                        }
                    }
                } finally {
                    zin.closeEntry();
                    entry = zin.getNextEntry();
                }
            }
        }
        if (!found) {
            throw new IllegalStateException("The deployment does not contain a " + Environment.EAP_XML_PATH + " file");
        }
        return eapXmlPath;
    }

    private void mergeInputPomAndEapXml(Path eapXmlPath) throws XMLStreamException, IOException {
        // Parse this, so we
        // - can easily get rid of the root element
        // - have some kind of structure in case we need to validate/enhance entries
        EapXmlParser eapXmlParser = new EapXmlParser(eapXmlPath);
        eapXmlParser.parse();

        // Parse the pon
        PomParser pomParser = new PomParser(environment.getInputPomLocation());
        pomParser.parse();

        // Add the eap.xml contents to the pom
        ProcessingInstructionNode mavenPluginConfigPlaceholder = pomParser.getMavenPluginConfigPlaceholder();
        if (mavenPluginConfigPlaceholder == null) {
            throw new IllegalStateException(environment.getInputPomLocation() + " is missing the <?" + PomParser.MAVEN_PLUGIN_CONFIG_PI + "?> procesing instruction");
        }
        for (Node node : eapXmlParser.getRootNode().getChildren()) {
             mavenPluginConfigPlaceholder.addDelegate(node, true);
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
