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

import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class EapXmlParser extends NodeParser {

    private final Path inputFile;
    private final String rootElementName;
    private ElementNode root;

    public EapXmlParser(Path inputFile, String rootElementName) {
        this.inputFile = inputFile;
        this.rootElementName = rootElementName;

    }

    public ElementNode getRootNode() {
        return root;
    }

    public void parse() throws IOException, XMLStreamException {
        InputStream in = new BufferedInputStream(new FileInputStream(inputFile.toFile()));
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
            XMLStreamReader reader = factory.createXMLStreamReader(in);

            reader.require(START_DOCUMENT, null, null);
            ParsingUtils.getNextElement(reader, rootElementName, null, false);
            root = super.parseNode(reader, rootElementName);

        } finally {
            try {
                in.close();
            } catch (Exception ignore) {
            }
        }
    }
}
