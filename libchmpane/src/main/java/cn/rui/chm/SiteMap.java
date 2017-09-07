package cn.rui.chm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.htmlparser.Attribute;
import org.htmlparser.Parser;
import org.htmlparser.Tag;
import org.htmlparser.lexer.InputStreamSource;
import org.htmlparser.lexer.Lexer;
import org.htmlparser.lexer.Page;
import org.htmlparser.util.ParserException;
import org.htmlparser.visitors.NodeVisitor;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;
import java.util.logging.Logger;

/**
 * @see <a href="http://www.nongnu.org/chmspec/latest/Sitemap.html#HHC">HHC format</a>
 */
@EqualsAndHashCode
@ToString
public class SiteMap {
    private static Logger log = Logger.getLogger(SiteMap.class.getName());

    @Getter
    private final Item root;

    SiteMap() {
        root = new Item();
    }

    private SiteMap(Page page) throws IOException {
        this();
        try {
            Lexer lexer = new Lexer(page);
            Parser parser = new Parser(lexer);
            NodeVisitor visitor = new SiteMapNodeVistor();
            parser.visitAllNodesWith(visitor);
        } catch (ParserException ex) {
            log.throwing("SiteMap", "SiteMap(Page page)", ex);
            throw new IOException("HTML Parser error");
        }
    }

    public SiteMap(String content) throws IOException {
        this(new Page(content));
    }

    public SiteMap(Reader reader) throws IOException {
        this(new Scanner(reader).useDelimiter("\\Z").next());
    }

    public SiteMap(InputStream is, String charset) throws IOException {
        this(new Page(new InputStreamSource(is, charset)));
    }

    public static SiteMap create(CHMFile chm, String filename) throws IOException {
        InputStream is = chm.getResourceAsStream(filename);
        if (is == null) {
            return null;
        }
        String charsetName = chm.getCharsetName();
        if (charsetName == null) {
            charsetName = detectCharset(is);
            log.info("sitemap " + filename + " encoding detected: " + charsetName);
        }
        is = chm.getResourceAsStream(filename);
        SiteMap sitemap = new SiteMap(is, charsetName);
        return sitemap;
    }

    private static String detectCharset(InputStream is) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);

        byte[] buf = new byte[512];
        int nread;
        while ((nread = is.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, nread);
        }

        detector.dataEnd();
        try {
            is.close();
        } catch (Exception ex) {
        }

        return detector.getDetectedCharset();
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

        private Map<String, String> params;
        public Map<String, String> getParams() {
            if (params == null) {
                return null;
            }
            return Collections.unmodifiableMap(params);
        }

        private List<Item> items;
        public List<Item> getItems() {
            if (items == null) {
                return null;
            }
            return Collections.unmodifiableList(items);
        }
    }

    class SiteMapNodeVistor extends NodeVisitor {
        private Stack<Item> stack;
        private boolean closeUlToBeDetermined = false;

        SiteMapNodeVistor() {
            super();
            stack = new Stack<Item>();
            stack.push(root);
        }

        @Override
        public void visitTag(Tag tag) {
            String localName = tag.getTagName().toLowerCase();

            if (closeUlToBeDetermined) {
                closeUlToBeDetermined = false;
                if ("ul".equals(localName)) {
                    // do nothing and return
                    return;
                } else {
                    closeUl();
                    // continue
                }
            }

            if ("li".equals(localName)) {
                Item item = new Item();
                if (stack.peek().items == null) {
                    stack.pop();
                }
                stack.peek().items.add(item);
                stack.push(item);
            } else if ("object".equals(localName)) {
                Map<String, String> params = new HashMap<String, String>();
                stack.peek().params = params;
            } else if ("param".equals(localName)) {
                String name = null;
                String value = null;
                Vector<Attribute> attributes = tag.getAttributesEx();
                for (Attribute attr : attributes) {
                    String attrName = attr.getName();
                    String attrValue = attr.getValue();
                    if ("name".equalsIgnoreCase(attrName)) {
                        name = attrValue;
                    } else if ("value".equalsIgnoreCase(attrName)) {
                        value = attrValue;
                    }
                }
                if (name != null && value != null) {
                    stack.peek().params.put(name, value);
                }
            } else if ("ul".equalsIgnoreCase(localName)) {
                List<Item> items = new LinkedList<Item>();
                stack.peek().items = items;
            }
        }

        @Override
        public void visitEndTag(Tag tag) {
            String localName = tag.getTagName().toLowerCase();

            if (closeUlToBeDetermined) {
                closeUlToBeDetermined = false;
                closeUl();
                // continue
            }

            if ("li".equalsIgnoreCase(localName)) {
                // should not occur
            } else if ("object".equalsIgnoreCase(localName)) {
                // parse item fields from params
                Item item = stack.peek();
                for (Map.Entry<String, String> entry : item.params.entrySet()) {
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

