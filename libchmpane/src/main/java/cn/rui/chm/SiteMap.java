package cn.rui.chm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.ccil.cowan.tagsoup.jaxp.SAXParserImpl;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;
import java.util.logging.Logger;

@EqualsAndHashCode
@ToString
public class SiteMap {
    private static Logger log = Logger.getLogger(SiteMap.class.getName());

    @Getter
    private Item root;

    SiteMap() {
        root = new Item();
    }

    public SiteMap(String content) throws IOException {
        this();
        try {
            saxParser.parse(content, new SiteMapSaxHandler());
        } catch (SAXException ex) {
            log.throwing("SiteMap", "SiteMap(String content)", ex);
        }
    }

    public SiteMap(Reader reader) throws IOException {
        this();
        try {
            saxParser.parse(new InputSource(reader), new SiteMapSaxHandler());
        } catch (SAXException ex) {
            log.throwing("SiteMap", "SiteMap(Reader reader)", ex);
        }
    }

    public SiteMap(InputStream is) throws IOException {
        this();
        try {
            saxParser.parse(new InputSource(is), new SiteMapSaxHandler());
        } catch (SAXException ex) {
            log.throwing("SiteMap", "SiteMap(InputStream is)", ex);
        }
    }

    private static SAXParser saxParser;
    static {
        try {
            saxParser = SAXParserImpl.newInstance(null);
        } catch (Exception ex) {
            log.throwing("SiteMap", "static init", ex);
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class Item {
        @Getter
        private String name;
        @Getter
        private String local;
        @Getter
        private String imageNumber;
        @Getter
        private String newImage;

        private Map<String, String> properties;
        private List<Item> items;

        public Map<String, String> getProperties() {
            if (properties == null) {
                return null;
            }
            return Collections.unmodifiableMap(properties);
        }

        public List<Item> getItems() {
            if (items == null) {
                return null;
            }
            return Collections.unmodifiableList(items);
        }
    }

    class SiteMapSaxHandler extends DefaultHandler {
        private Stack<Item> stack;
        private boolean closeUlToBeDetermined = false;

        SiteMapSaxHandler() {
            super();
            stack = new Stack<Item>();
            stack.push(root);
        }

        @Override
        public void startElement (String uri, String localName,
                                  String qName, Attributes attributes)
                throws SAXException {
            if (closeUlToBeDetermined) {
                closeUlToBeDetermined = false;
                if ("ul".equalsIgnoreCase(localName)) {
                    // do nothing and return
                    return;
                } else {
                    closeUl();
                    // continue
                }
            }

            if ("li".equalsIgnoreCase(localName)) {
                Item item = new Item();
                if (stack.peek().items == null) {
                    stack.pop();
                }
                stack.peek().items.add(item);
                stack.push(item);
            } else if ("object".equalsIgnoreCase(localName)) {
                Map<String, String> properties = new HashMap<String, String>();
                stack.peek().properties = properties;
            } else if ("param".equalsIgnoreCase(localName)) {
                String name = null;
                String value = null;
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attrName = attributes.getLocalName(i);
                    String attrValue = attributes.getValue(i);
                    if ("name".equalsIgnoreCase(attrName)) {
                        name = attrValue;
                    } else if ("value".equalsIgnoreCase(attrName)) {
                        value = attrValue;
                    }
                }
                if (name != null && value != null) {
                    stack.peek().properties.put(name, value);
                }
            } else if ("ul".equalsIgnoreCase(localName)) {
                List<Item> items = new LinkedList<Item>();
                stack.peek().items = items;
            }
        }

        @Override
        public void endElement (String uri, String localName, String qName)
                throws SAXException {
            if (closeUlToBeDetermined) {
                closeUlToBeDetermined = false;
                closeUl();
                // continue
            }

            if ("li".equalsIgnoreCase(localName)) {
                // should not occur
            } else if ("object".equalsIgnoreCase(localName)) {
                // parse item fields from properties
                Item item = stack.peek();
                for (Map.Entry<String, String> entry : item.properties.entrySet()) {
                    if ("Name".equals(entry.getKey())) {
                        item.name = entry.getValue();
                    } else if ("Local".equals(entry.getKey())) {
                        item.local = entry.getValue();
                    } else if ("ImageNumber".equals(entry.getKey())) {
                        item.imageNumber = entry.getValue();
                    } else if ("New".equals(entry.getKey())) {
                        item.newImage = entry.getValue();
                    }
                }
            } else if ("param".equalsIgnoreCase(localName)) {
                // do nothing
            } else if ("ul".equalsIgnoreCase(localName)) {
                closeUlToBeDetermined = true;
            }
        }

        private void closeUl() {
            if (stack.peek().items == null) {
                stack.pop();
            }
            stack.pop();
        }
    }
}

