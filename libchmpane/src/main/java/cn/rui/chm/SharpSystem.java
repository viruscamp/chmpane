package cn.rui.chm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.IOException;
import java.util.*;

@EqualsAndHashCode
@ToString
@Getter
public class SharpSystem {
    public static final String FILENAME = "/#SYSTEM";

    public SharpSystem(CHMFile chm) throws IOException {
        LEInputStream in = new LEInputStream(chm.getResourceAsStream(FILENAME));
        version = in.read32();
        List<Entry> entries = new LinkedList<Entry>();
        Map<HhpOption, String> properties = new HashMap<HhpOption, String>();
        while (in.available() > 0) {
            Entry entry = Entry.build(in);
            entries.add(entry);

            if (entry instanceof StringEntry) {
                properties.put(((StringEntry) entry).hhpOption, ((StringEntry) entry).value);
            }
        }
        this.entries = Collections.unmodifiableList(entries);
        this.properties = Collections.unmodifiableMap(properties);
    }

    private int version;
    private List<Entry> entries;
    private Map<HhpOption, String> properties;

    @EqualsAndHashCode
    @ToString
    public static class Entry {
        public static Entry build(LEInputStream in) throws IOException {
            int code = in.read16();
            int dataLen = in.read16();
            byte[] data = new byte[dataLen];
            in.read(data);
            StringEntry stringEntry = StringEntry.build(code, data);
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

        private final int code;
        public int getCode() {
            return code;
        }

        private final String optionName;
        public String getOptionName() {
            return optionName;
        }

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
                if (hhpOption.optionName == optionName) {
                    return hhpOption;
                }
            }
            return null;
        }
    }

    @EqualsAndHashCode
    @ToString
    @Getter
    public static class StringEntry extends Entry {
        public static StringEntry build(int code, byte[] data) throws IOException {
            HhpOption hhpOption = HhpOption.codeOf(code);
            if (hhpOption != null) {
                return new StringEntry(code, data, hhpOption);
            }
            return null;
        }

        private StringEntry(int code, byte[] data, HhpOption hhpOption) {
            super(code, data);
            this.hhpOption = hhpOption;

            // find first null
            int endIndex = data.length;
            for (int idx = 0; idx < data.length; idx++) {
                if (data[idx] == (byte)0) {
                    endIndex = idx;
                    break;
                }
            }
            this.value = new String(data, 0, endIndex);
            // TODO determine charset
            //this.value = new String(data, charset);
        }

        private HhpOption hhpOption;
        private String value;
    }
}
