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
package org.wildfly.managed.server.builder.tool.parser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PomParser extends NodeParser {
    public static final String MAVEN_PLUGIN_CONFIG_PI = "MAVEN_PLUGIN_CONFIG";
    public static final String DOCKER_PLUGIN_ENV_VAR_PI = "DOCKER_PLUGIN_ENV_VARS";
    private static final String ROOT_ELEMENT_NAME = "project";

    private final Path inputFile;
    private ElementNode root;

    private ProcessingInstructionNode mavenPluginConfigPlaceholder;
    private ProcessingInstructionNode dockerPluginEnvVarsPlaceholder;

    public PomParser(Path inputFile) {
        this.inputFile = inputFile;
    }

    public ElementNode getRootNode() {
        return root;
    }

    public ProcessingInstructionNode getMavenPluginConfigPlaceholder() {
        return mavenPluginConfigPlaceholder;
    }

    public ProcessingInstructionNode getDockerPluginEnvVarsPlaceholder() {
        return dockerPluginEnvVarsPlaceholder;
    }

    public void parse() throws IOException, XMLStreamException {
        InputStream in = new BufferedInputStream(new FileInputStream(inputFile.toFile()));
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
            XMLStreamReader reader = factory.createXMLStreamReader(in);

            reader.require(START_DOCUMENT, null, null);
            ParsingUtils.getNextElement(reader, ROOT_ELEMENT_NAME, null, false);
            root = super.parseNode(reader, ROOT_ELEMENT_NAME);

            validateAllProcessingInstructionsExist();
        } finally {
            try {
                in.close();
            } catch (Exception ignore) {
            }
        }
    }

    private void validateAllProcessingInstructionsExist() {
        Set<String> missing = new HashSet<>();
        if (mavenPluginConfigPlaceholder == null) {
            missing.add(toProcessingInstruction(MAVEN_PLUGIN_CONFIG_PI));
        }
        if (dockerPluginEnvVarsPlaceholder == null) {
            missing.add(toProcessingInstruction(DOCKER_PLUGIN_ENV_VAR_PI));
        }

        if (missing.size() > 0) {
            throw new IllegalStateException("The input pom is missing the following processing instructions: " + missing);
        }
    }

    private String toProcessingInstruction(String name) {
        return "<?" + name + "?>";
    }

    @Override
    protected ProcessingInstructionNode parseProcessingInstruction(XMLStreamReader reader, ElementNode parent) throws XMLStreamException {
        ProcessingInstructionNode node;
        String pi = reader.getPITarget();
        Map<String, String> data = parseProcessingInstructionData(reader.getPIData());
        if (pi.equals(MAVEN_PLUGIN_CONFIG_PI)) {
            node = createProcessingInstruction(data, parent, pi, mavenPluginConfigPlaceholder);
            mavenPluginConfigPlaceholder = node;
        } else if (pi.equals(DOCKER_PLUGIN_ENV_VAR_PI)) {
            node = createProcessingInstruction(data, parent, pi, dockerPluginEnvVarsPlaceholder);
            dockerPluginEnvVarsPlaceholder = node;
        } else {
            throw new IllegalStateException("Unknown processing instruction " + toProcessingInstruction(reader.getPITarget()) + " " + reader.getLocation());
        }
        return node;
    }

    private ProcessingInstructionNode createProcessingInstruction(
            Map<String, String> data, ElementNode parent, String processingInstructionName, ProcessingInstructionNode existing) {
        if (!data.isEmpty()) {
            throw new IllegalStateException(toProcessingInstruction(processingInstructionName) + " should not take any data");
        }
        if (existing != null) {
            throw new IllegalStateException("Can only have one occurance of " + toProcessingInstruction(processingInstructionName));
        }
        return new ProcessingInstructionNode(parent, processingInstructionName, null);
    }
}
