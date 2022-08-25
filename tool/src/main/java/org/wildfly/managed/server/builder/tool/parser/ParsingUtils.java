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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.Map;

import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ParsingUtils {
    public static String getNextElement(XMLStreamReader reader, String name, Map<String, String> attributes, boolean getElementText) throws XMLStreamException {
        if (!reader.hasNext()) {
            throw new XMLStreamException("Expected more elements", reader.getLocation());
        }
        int type = reader.next();
        while (reader.hasNext() && type != START_ELEMENT) {
            type = reader.next();
        }
        if (reader.getEventType() != START_ELEMENT) {
            throw new XMLStreamException("No <" + name + "> found");
        }
        if (!reader.getLocalName().equals("" + name + "")) {
            throw new XMLStreamException("<" + name + "> expected", reader.getLocation());
        }

        if (attributes != null) {
            for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
                String attr = reader.getAttributeLocalName(i);
                if (!attributes.containsKey(attr)) {
                    throw new XMLStreamException("Unexpected attribute " + attr, reader.getLocation());
                }
                attributes.put(attr, reader.getAttributeValue(i));
            }
        }

        return getElementText ? reader.getElementText() : null;
    }
}
