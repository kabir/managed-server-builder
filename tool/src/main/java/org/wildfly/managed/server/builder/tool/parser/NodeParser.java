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
import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.COMMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class NodeParser {

    private final String namespaceURI;

    NodeParser() {
        this(null);
    }

    NodeParser(String namespaceURI){
        this.namespaceURI = namespaceURI;
    }

    ElementNode parseNode(XMLStreamReader reader, String nodeName) throws XMLStreamException {
        if (reader.getEventType() != START_ELEMENT) {
            throw new XMLStreamException("Expected START_ELEMENT", reader.getLocation());
        }
        if (!reader.getLocalName().equals(nodeName)) {
            throw new XMLStreamException("Expected <" + nodeName + ">", reader.getLocation());
        }

        ElementNode rootNode = createNodeWithAttributesAndNs(reader, null);
        ElementNode currentNode = rootNode;
        while (reader.hasNext()) {
            int type = reader.next();
            switch (type) {
            case END_ELEMENT:
                currentNode = currentNode.getParent();
                String name = reader.getLocalName();
                //TODO this looks wrong
                if (name.equals(nodeName)) {
                    return rootNode;
                }
                break;
            case START_ELEMENT:
                ElementNode childNode = createNodeWithAttributesAndNs(reader, currentNode);
                currentNode.addChild(childNode);
                currentNode = childNode;
                break;
            case COMMENT:
                String comment = reader.getText();
                currentNode.addChild(new CommentNode(comment));
                break;
            case CDATA:
                currentNode.addChild(new CDataNode(reader.getText()));
                break;
            case CHARACTERS:
                if (!reader.isWhiteSpace()) {
                    currentNode.addChild(new TextNode(reader.getText()));
                }
                break;
            case PROCESSING_INSTRUCTION:
                throw new IllegalStateException("Processing instructions not supported");
            default:
                break;
            }
        }

        throw new XMLStreamException("Element was not terminated", reader.getLocation());
    }

    private ElementNode createNodeWithAttributesAndNs(XMLStreamReader reader, ElementNode parent) {
        String namespace = reader.getNamespaceURI() != null && reader.getNamespaceURI().length() > 0 ? reader.getNamespaceURI() : namespaceURI;

        ElementNode childNode = new ElementNode(parent, reader.getLocalName(), namespace);
        int count = reader.getAttributeCount();
        for (int i = 0 ; i < count ; i++) {
            String name = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            childNode.addAttribute(name, createAttributeValue(value));
        }
        return childNode;
    }

    protected AttributeValue createAttributeValue(String attributeValue) {
        return new AttributeValue(attributeValue);
    }
}
