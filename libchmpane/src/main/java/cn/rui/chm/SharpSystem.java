package cn.rui.chm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.IOException;
import java.io.InputStream;
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

    public SharpSystem(InputStream inSharpSystem) throws IOException {
        LEInputStream in = new LEInputStream(inSharpSystem);
        version = in.read32();
        int lcid = 0;
        boolean lcidRead = false;
        List<Entry> entries = new ArrayList<Entry>(20);
        while (in.available() > 0) {
            Entry entry = Entry.build(in);
            entries.add(entry);
            if (entry.code == NonStringEntryCode.Entry4.code) {
                // entry.data.size() must be >= 4
                lcid = LEInputStream.bytesToInt(entry.data);
                lcidRead = true;
            }
        }
        if (!lcidRead) {
            // TODO: should I set a default value to lcid ?
            throw new DataFormatException("Cannot read lcid in file " + FILENAME);
        }
        this.lcid = lcid;
        this.charset = WindowsLanguageID.getDefaultCharset(lcid);

        Map<HhpOption, String> properties = new HashMap<HhpOption, String>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            StringEntry se = StringEntry.tryBuild(entry, this.charset);
            if (se != null) {
                entries.set(i, se);
                properties.put(se.hhpOption, se.value);
            }
        }
        this.entries = Collections.unmodifiableList(entries);
        this.properties = Collections.unmodifiableMap(properties);
    }

    private final int version;
    private final int lcid;
    private final Charset charset;
    private final List<Entry> entries;
    private final Map<HhpOption, String> properties;

    public String getProperty(HhpOption hhpOption) {
        return properties.get(hhpOption);
    }

    @EqualsAndHashCode
    @ToString
    public static class Entry {
        public static Entry build(LEInputStream in) throws IOException {
            int code = in.read16();
            int dataLen = in.read16();
            byte[] data = new byte[dataLen];
            if (in.read(data) < data.length) {
                throw new IOException("Unexpected end of entry[code=" + code + "] in file " + FILENAME);
            }
            return new Entry(code, data);
        }

        private Entry(int code, byte[] data) {
            this.code = code;
            this.data = data;
        }

        @Getter
        final int code;

        final byte[] data;
        public byte[] getData() {
            byte[] data = this.data;
            byte[] cloneData = new byte[data.length];
            System.arraycopy(data, 0, cloneData, 0, data.length);
            return cloneData;
        }
    }

    @Getter
    public enum NonStringEntryCode {
        Entry4(4),
        Entry7(7),
        Entry8(8),
        Entry9(9),
        Entry10(10),
        Entry11(11),
        Entry12(12),
        Entry13(13),
        Entry14(14),
        Entry15(15);

        public final int code;
        NonStringEntryCode(int code) {
            this.code = code;
        }
    }

    @Getter
    public enum HhpOption {
        ContentsFile(0, "Contents file"),
        IndexFile(1, "Index file"),
        DefaultTopic(2, "Default topic"),
        Title(3, "Title"),

        DefaultWindow(5, "Default Window"),
        CompiledFile(6, "Compiled file"),

        DefaultFont(16, "Default Font");

        public final int code;
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
        public static StringEntry tryBuild(Entry entry, Charset charset) {
            HhpOption hhpOption = HhpOption.codeOf(entry.code);
            if (hhpOption != null) {
                return new StringEntry(hhpOption, entry.data, charset);
            }
            return null;
        }

        private StringEntry(HhpOption hhpOption, byte[] data, Charset charset) {
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
            if (charset == null) {
                this.value = new String(data, 0, endIndex);
            } else {
                this.value = new String(data, 0, endIndex, charset);
            }
        }

        final HhpOption hhpOption;
        final String value;
    }
}
