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

import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ExampleParser {

    private final File inputFile;
    private String[] subsystems;

    public ExampleParser(final File inputFile) {
        this.inputFile = inputFile;
    }

    void parse() throws IOException, XMLStreamException {
        InputStream in = new BufferedInputStream(new FileInputStream(inputFile));
        try {
            XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(in);
            reader.require(START_DOCUMENT, null, null);
            int type = reader.next();
            while (type != END_DOCUMENT) {
                System.out.println(formatType(type));
                if (type == START_ELEMENT) {
                    System.out.println(reader.getLocalName());
                } else if (type == CHARACTERS) {
                    if (!reader.isWhiteSpace()) {
                        System.out.println("->" + reader.getText());
                    }
                }
                type = reader.next();
            }
        } finally {
            try {
                in.close();
            } catch (Exception ignore) {
            }
        }
    }

    private void parseUsingEventReader() {

    }

    private String formatType(int type) {
        switch (type) {
        case 1: return type + "-START_ELEMENT";
        case 2: return type + "-END_ELEMENT";
        case 3: return type + "-PROCESSING_INSTRUCTION";
        case 4: return type + "-CHARACTERS";
        case 5: return type + "-COMMENT";
        case 6: return type + "-SPACE";
        case 7: return type + "-START_DOCUMENT";
        case 8: return type + "-END_DOCUMENT";
        case 9: return type + "-ENTITY_REFERENCE";
        case 10: return type + "-ATTRIBUTE";
        case 11: return type + "-DTD";
        case 12: return type + "-CDATA";
        case 13: return type + "-NAMESPACE";
        case 14: return type + "-NOTATION_DECLARATION";
        case 15: return type + "-ENTITY_DECLARATION";
        default: throw new IllegalStateException("Grr");
        }

    }
}
