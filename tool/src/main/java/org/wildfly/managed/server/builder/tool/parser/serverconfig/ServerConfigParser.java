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
package org.wildfly.managed.server.builder.tool.parser.serverconfig;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerConfigParser  {

    private static final String ROOT_ELEMENT_NAME = "server-config";
    private final Path inputFile;

    private static final String SERVER_CONFIG = "server-config";

    public ServerConfigParser(Path inputFile) {
        this.inputFile = inputFile;
    }

    public ServerConfig parse() throws IOException, XMLStreamException {
        ServerConfig serverConfig = null;
        InputStream in = new BufferedInputStream(new FileInputStream(inputFile.toFile()));
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
            XMLStreamReader reader = factory.createXMLStreamReader(in);

            reader.require(START_DOCUMENT, null, null);
            while (reader.hasNext()) {
                if (reader.next() == START_ELEMENT) {
                    final String element = reader.getLocalName();
                    System.out.println(element);
                    switch (element) {
                        case SERVER_CONFIG:
                            if (serverConfig != null) {
                                throw new XMLStreamException("Duplicate " + SERVER_CONFIG + " elements", reader.getLocation());
                            }
                            serverConfig = parseServerConfig(reader);
                            break;
                        default:
                            throw new XMLStreamException("Unknown element: " + element, reader.getLocation());
                    }
                }
            }

        } finally {
            try {
                in.close();
            } catch (Exception ignore) {
            }
        }
        return serverConfig;
    }

    private ServerConfig parseServerConfig(XMLStreamReader reader) throws XMLStreamException {
        ensureNoAttributes(reader);
        Layers layers = null;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final String element = reader.getLocalName();
            System.out.println(element);
            switch (element) {
                case Layers.LAYERS:
                    if (layers != null) {
                        throw new XMLStreamException("Duplicate " + Layers.LAYERS + " elements");
                    }
                    layers = parseLayers(reader);
                    break;
                default:
                    throw new XMLStreamException("Unknown element: " + element, reader.getLocation());
            }
        }
        return new ServerConfig(layers);
    }

    private Layers parseLayers(XMLStreamReader reader) throws XMLStreamException {
        ensureNoAttributes(reader);
        List<String> layers = new LinkedList<>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final String element = reader.getLocalName();
            System.out.println(element);
            switch (element) {
                case Layers.LAYER:
                    ensureNoAttributes(reader);
                    layers.add(reader.getElementText());
                    break;
                default:
                    throw new XMLStreamException("Unknown element: " + element, reader.getLocation());
            }
        }
        if (layers == null) {
            throw new XMLStreamException("No top level 'layers'", reader.getLocation());
        }
        return new Layers(layers);
    }

    private void ensureNoAttributes(XMLStreamReader reader) throws XMLStreamException {
        readAttributes(reader);
    }

    private Map<String, String> readAttributes(XMLStreamReader reader, String...attributes) throws XMLStreamException {
        Map<String, String> attributeValues = new HashMap<>();
        Set<String> attributeNames = new HashSet<>(Arrays.asList(attributes));
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attr = reader.getAttributeLocalName(i);
            if (!attributeNames.contains(attr)) {
                throw new XMLStreamException("Unknown attribute: " + attr, reader.getLocation());
            }
            String value = reader.getAttributeValue(i);
            attributeValues.put(attr, value);
        }
        return attributeValues;
    }
}
