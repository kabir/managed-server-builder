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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ConfigurationAssembler {

    private final File baseDir;
    private final File templateFile;
    private final String templateRootElementName;
    private final File subsystemsFile;
    private final File outputFile;

    public ConfigurationAssembler(File baseDir, File templateFile, String templateRootElementName, File subsystemsFile, File outputFile) {
        this.baseDir = baseDir.getAbsoluteFile();
        this.templateFile = templateFile.getAbsoluteFile();
        this.templateRootElementName = templateRootElementName;
        this.subsystemsFile = subsystemsFile.getAbsoluteFile();
        this.outputFile = outputFile.getAbsoluteFile();
    }

    public void assemble() throws IOException, XMLStreamException {
        EapXmlParser templateParser = new EapXmlParser(null, templateRootElementName);
        templateParser.parse();

        if (outputFile.exists()) {
            outputFile.delete();
        }
        if (!outputFile.getParentFile().exists()) {
            if (!outputFile.getParentFile().mkdirs()) {
                throw new IllegalStateException("Could not create " + outputFile.getParentFile());
            }
        }
        FormattingXMLStreamWriter writer = new FormattingXMLStreamWriter(XMLOutputFactory.newInstance().createXMLStreamWriter(new BufferedWriter(new FileWriter(outputFile))));
        try {
            writer.writeStartDocument();
            templateParser.getRootNode().marshall(writer);
            writer.writeEndDocument();
        } finally {
            try {
                writer.close();
            } catch (Exception ignore) {
            }
        }
    }
}
