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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProcessingInstructionNode implements Node {
    private final ElementNode parent;
    private final String name;
    private final Map<String, String> data;
    private List<Node> delegates = new ArrayList<Node>();

    public ProcessingInstructionNode(final ElementNode parent, final String name, final Map<String, String> data) {
        this.parent = parent;
        this.name = name;
        this.data = data;
    }

    public void addDelegate(Node delegate, boolean useParentNs) {
        if (delegate != null) {
            if (useParentNs && delegate instanceof ElementNode) {
                delegate = ((ElementNode) delegate).updateForNewNsAndParent(parent, parent.getNamespace());
            }
            delegates.add(delegate);
        }
    }

    public ElementNode getParent() {
        return parent;
    }

    String getDataValue(String name, String defaultValue) {
        if (data != null) {
            String s = data.get(name);
            if (s != null) {
                return s;
            }
        }
        return defaultValue;
    }

    @Override
    public void marshall(XMLStreamWriter writer) throws XMLStreamException {
        for (Node delegate : delegates) {
            delegate.marshall(writer);
        }
    }

    public boolean hasContent() {
        for (Node delegate : delegates) {
            if (delegate.hasContent()) {
                return true;
            }
        }
        return false;
    }

}
