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

import org.wildfly.managed.server.builder.tool.parser.Node;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.List;

public class Layers implements Node {

    static final String LAYERS = "layers";
    static final String LAYER = "layer";

    private final List<String> layers;

    public Layers(List<String> layers) {
        this.layers = layers;
    }

    public List<String> getLayers() {
        return layers;
    }

    @Override
    public void marshall(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(LAYERS);
        for (String layer : layers) {
            writer.writeStartElement(LAYER);
            writer.writeCharacters(layer);
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    @Override
    public boolean hasContent() {
        return layers.size() > 0;
    }
}
