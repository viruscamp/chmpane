package cn.rui.chm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @see <a href='http://www.nongnu.org/chmspec/latest/Internal.html#SYSTEM'>Internal file formats /#SYSTEM</a>
 */
@EqualsAndHashCode
@ToString
@Getter
public class SharpSystem {
    public static final String FILENAME = "/#SYSTEM";

    public SharpSystem(InputStream is, String charsetName) throws IOException {
        LEInputStream in = new LEInputStream(is);
        version = in.read32();
        List<Entry> entries = new LinkedList<Entry>();
        Map<HhpOption, String> properties = new HashMap<HhpOption, String>();
        while (in.available() > 0) {
            Entry entry = Entry.build(in, charsetName);
            entries.add(entry);
            if (entry instanceof StringEntry) {
                properties.put(((StringEntry) entry).hhpOption, ((StringEntry) entry).value);
            }
        }
        this.entries = Collections.unmodifiableList(entries);
        this.properties = Collections.unmodifiableMap(properties);
    }

    public SharpSystem(CHMFile chm) throws IOException {
        this(chm.getResourceAsStream(FILENAME), chm.getCharsetName());
    }

    public static Charset getCharset(CHMFile chm) throws IOException {
        LEInputStream in = null;
        try {
            in = new LEInputStream(chm.getResourceAsStream(FILENAME));
            int version = in.read32();
            while (in.available() > 0) {
                int code = in.read16();
                int dataLen = in.read16();
                if (code == 4) {
                    int lcid = in.read32();
                    return WindowsLanguageID.getDefaultCharset(lcid);
                }
                in.skip(dataLen);
                continue;
            }
            return null;
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private final int version;
    private final List<Entry> entries;
    private final Map<HhpOption, String> properties;

    @EqualsAndHashCode
    @ToString
    public static class Entry {
        public static Entry build(LEInputStream in, String charsetName) throws IOException {
            int code = in.read16();
            int dataLen = in.read16();
            byte[] data = new byte[dataLen];
            if (in.read(data) < data.length) {
                throw new IOException("Unexpected end of entry[code=" + code + "] in file " + FILENAME);
            }
            StringEntry stringEntry = StringEntry.build(code, data, charsetName);
            if (stringEntry != null) {
                return stringEntry;
            }
            return new Entry(code, data);
        }

        private Entry(int code, byte[] data) {
            this.code = code;
            this.data = data;
        }

        @Getter
        private final int code;

        private final byte[] data;
        public byte[] getData() {
            byte[] data = this.data;
            byte[] cloneData = new byte[data.length];
            System.arraycopy(data, 0, cloneData, 0, data.length);
            return cloneData;
        }
    }

    public enum HhpOption {
        ContentsFile(0, "Contents file"),
        IndexFile(1, "Index file"),
        DefaultTopic(2, "Default topic"),
        Title(3, "Title"),

        DefaultWindow(5, "Default Window"),
        CompiledFile(6, "Compiled file"),

        DefaultFont(16, "Default Font");

        @Getter
        public final int code;

        @Getter
        public final String optionName;

        HhpOption(int code, String optionName) {
            this.code = code;
            this.optionName = optionName;
        }

        public static HhpOption codeOf(int code) {
            for (HhpOption hhpOption : HhpOption.values()) {
                if (hhpOption.code == code) {
                    return hhpOption;
                }
            }
            return null;
        }

        public static HhpOption optionNameOf(String optionName) {
            for (HhpOption hhpOption : HhpOption.values()) {
                if (hhpOption.optionName.equals(optionName)) {
                    return hhpOption;
                }
            }
            return null;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @ToString
    @Getter
    public static class StringEntry extends Entry {
        public static StringEntry build(int code, byte[] data, String charsetName) throws IOException {
            HhpOption hhpOption = HhpOption.codeOf(code);
            if (hhpOption != null) {
                return new StringEntry(hhpOption, data, charsetName);
            }
            return null;
        }

        private StringEntry(HhpOption hhpOption, byte[] data, String charsetName) {
            super(hhpOption.code, data);
            this.hhpOption = hhpOption;

            // find first null
            int endIndex = data.length;
            for (int idx = 0; idx < data.length; idx++) {
                if (data[idx] == (byte)0) {
                    endIndex = idx;
                    break;
                }
            }
            if (charsetName == null) {
                this.value = new String(data, 0, endIndex);
            } else {
                try {
                    this.value = new String(data, 0, endIndex, charsetName);
                } catch (UnsupportedEncodingException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        private final HhpOption hhpOption;
        private final String value;
    }
}
