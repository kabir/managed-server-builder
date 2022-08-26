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
package org.wildfly.managed.server.builder.tool.parser.generic;

import org.wildfly.managed.server.builder.tool.parser.Node;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ElementNode implements Node {

    private final ElementNode parent;
    private final String name;
    private final String namespace;
    private final Map<String, AttributeValue> attributes = new LinkedHashMap<String, AttributeValue>();
    private List<Node> children = new ArrayList<Node>();

    public ElementNode(final ElementNode parent, final String name) {
        this(parent, name, parent.getNamespace());
    }

    ElementNode(final ElementNode parent, final String name, final String namespace) {
        this.parent = parent;
        this.name = name;
        this.namespace = namespace == null ? namespace : namespace.isEmpty() ? null : namespace;
    }

    public ElementNode updateForNewNsAndParent(ElementNode parent, String namespace) {
        ElementNode copy = new ElementNode(parent, this.name, namespace);
        copy.attributes.putAll(this.attributes);

        for (Node child : children) {
            if (child instanceof ElementNode) {
                copy.children.add(((ElementNode) child).updateForNewNsAndParent(copy, namespace));
            } else {
                copy.children.add(child);
            }
        }
        return copy;
    }

    public ElementNode getNamedChildElement(String name) {
        for (Node node : children) {
            if (node instanceof ElementNode) {
                ElementNode en = (ElementNode) node;
                if (name.equals(en.name)) {
                    return en;
                }
            }
        }
        return null;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public void addAttribute(String name, AttributeValue value) {
        attributes.put(name, value);
    }

    public void addChild(Node child) {
        children.add(child);
    }

    public Collection<Node> getChildren() {
        return children;
    }

    public ElementNode getParent() {
        return parent;
    }

    public Iterator<Node> iterateChildren(){
        return children.iterator();
    }

    public String getAttributeValue(String name) {
        AttributeValue av = attributes.get(name);
        if (av == null) {
            return null;
        }
        return av.getValue();
    }

    public String getAttributeValue(String name, String defaultValue) {
        String s = getAttributeValue(name);
        if (s == null) {
            return defaultValue;
        }
        return s;
    }

    @Override
    public void marshall(XMLStreamWriter writer) throws XMLStreamException {
//        boolean empty = false;//children.isEmpty()
        boolean empty = isEmpty();
        NamespaceContext context = writer.getNamespaceContext();
        String prefix = writer.getNamespaceContext().getPrefix(namespace);
        if (prefix == null) {
            // Unknown namespace; it becomes default
            writer.setDefaultNamespace(namespace);
            if (empty) {
                writer.writeEmptyElement(name);
            }
            else {
                writer.writeStartElement(name);
            }
            writer.writeNamespace(null, namespace);
        }
        else {
            if (empty) {
                writer.writeEmptyElement(namespace, name);
            }
            else {
                writer.writeStartElement(namespace, name);
            }
        }

        for (Map.Entry<String, AttributeValue> attr : attributes.entrySet()) {
            writer.writeAttribute(attr.getKey(), attr.getValue().getValue());
        }

        for (Node child : children) {
            child.marshall(writer);
        }

        if (!empty) {
            try {
                writer.writeEndElement();
            } catch(XMLStreamException e) {
                //TODO REMOVE THIS
                throw e;
            }
        }
    }

    private boolean isEmpty() {
        if (children.isEmpty()) {
            return true;
        }
        for (Node child : children) {
            if (child.hasContent()) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return "Element(name=" + name + ",ns=" + namespace + ")";
    }
}