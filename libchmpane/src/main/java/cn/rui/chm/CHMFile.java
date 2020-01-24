/**
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com) All Right Reserved
 * File     : CHMFile.java
 * Created	: 2007-3-1
 *
 * ****************************************************************************
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com)
 * http://chmpane.sourceforge.net, All Right Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "CHMPane" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * *****************************************************************************
 */
package cn.rui.chm;

import lombok.Getter;
import lombok.extern.java.Log;

import java.io.*;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Rui Shen
 *
 * CHMFile
 * @see <a href="http://www.russotto.net/chm/chmformat.html">Microsoft's HTML Help (.chm) format</a>
 * @see <a href="http://www.nongnu.org/chmspec/latest/Internal.html">Unofficial (Preliminary) HTML Help Specification</a>
 * @see <a href="http://www.cabextract.org.uk/libmspack/doc/structmschmd__header.html">mschmd_header Struct Reference</a>
 */
@Log
public class CHMFile implements Closeable {

	public static final int CHM_HEADER_LENGTH = 0x60;

	public static final int CHM_DIRECTORY_HEADER_LENGTH = 0x54;

	// header info
	private int version; // 3, 2
	/**
	 * It is the lower 32 bits of a 64-bit value representing the number of centiseconds since 1601-01-01 00:00:00 UTC, plus 42.
	 * It is not useful as a timestamp, but it is useful as a semi-unique ID.
	 */
	private int timestamp;
	private int lang; // Windows Language ID
	private int lang2;
	private long contentOffset;
	private long fileLength;
	private int chunkSize;
	private int quickRef;
	private int depthOfIndexTree;
	private int rootIndexChunkNo;
	private int firstPMGLChunkNo;
	private int lastPMGLChunkNo;
	private final int totalChunks;

	private long chunkOffset;

	RandomAccessFile fileAccess;

	private AtomicInteger completedChunks;
	private DirectoryChunk[] directoryChunks;
	private DirectoryChunk rootIndexChunk;

	private final Map<String, ListingEntry> entryCache;

	private final AtomicReference<Object> resourcesCache = new AtomicReference<Object>();
	public List<String> getResources() throws IOException {
		return Utils.lazyGet(this.resourcesCache, new Utils.SupplierWithException<List<String>, IOException>() {
			public List<String> get() throws IOException {
				if (rootIndexChunkNo >= 0) {
					fillChunkRecursively(rootIndexChunk);
				} else {
					log.warning("should never goes here");
				}
				return collectResources();
			}
		});
	}

	private List<String> collectResources() {
		return Utils.lazyGet(this.resourcesCache, new Utils.Supplier<List<String>>() {
			public List<String> get() {
				List<String> list = new ArrayList<String>(entryCache.size());
				for (String name : entryCache.keySet()) {
					if (name.startsWith("/")) {
						list.add(name);
					}
				}
				//directoryChunks = null;
				//rootIndexChunk = null;
				return Collections.unmodifiableList(list);
			}
		});
	}

	public boolean isResourcesCompleted() {
		return resourcesCache.get() != null;
	}

	private DirectoryChunk getOrCreateDirectoryChunk(int chunkNo, String firstName) {
		DirectoryChunk chunk = directoryChunks[chunkNo];
		if (chunk == null) {
			synchronized (directoryChunks) {
				// double-checked locking
				chunk = directoryChunks[chunkNo];
				if (chunk == null) {
					chunk = new DirectoryChunk(chunkNo, firstName);
					directoryChunks[chunkNo] = chunk;
				}
			}
		}
		return chunk;
	}

	public ListingEntry searchChunk(DirectoryChunk chunk, String name) throws IOException {
		fillChunk(chunk);
		if (chunk.content instanceof ListingChunk) {
			List<ListingEntry> children = ((ListingChunk) chunk.content).children;
			int idx = Collections.<ListingEntry>binarySearch(children, new ListingEntry(name),
					new Comparator<ListingEntry>() {
						public int compare(ListingEntry o1, ListingEntry o2) {
							return String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name);
						}
					}
			);
			if (idx >= 0) {
				return children.get(idx);
			}
		} else if (chunk.content instanceof IndexChunk) {
			List<DirectoryChunk> children = ((IndexChunk) chunk.content).children;
			int idx = Collections.<DirectoryChunk>binarySearch(children, new DirectoryChunk(Integer.MIN_VALUE, name),
					new Comparator<DirectoryChunk>() {
						public int compare(DirectoryChunk o1, DirectoryChunk o2) {
							return String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name);
						}
					}
			);
			if (idx >= 0) {
				return searchChunk(children.get(idx), name);
			} else if (idx < -1) {
				return searchChunk(children.get(-idx - 2), name);
			}
		}
		return null;
	}

	private void chunkCompleted(DirectoryChunk chunk) {
		if (completedChunks.incrementAndGet() == totalChunks) {
			collectResources();
		}
	}

	private DirectoryChunk fillFakeRootIndexChunk(DirectoryChunk chunk) throws IOException {
		// fake root chunk , which rootChunkNo = -1,
		List<DirectoryChunk> children = new ArrayList<DirectoryChunk>(lastPMGLChunkNo - firstPMGLChunkNo + 1);
		for (int chunkNo = firstPMGLChunkNo; chunkNo <= lastPMGLChunkNo; chunkNo++) {
			DirectoryChunk listingChunk = getOrCreateDirectoryChunk(chunkNo, null);
			fillChunk(listingChunk);
			children.add(listingChunk);
		}
		chunk.name = children.get(0).name;
		chunk.content = new IndexChunk(children);
		return chunk;
	}

	private DirectoryChunk fillChunk(DirectoryChunk chunk) throws IOException {
		if (chunk.content != null) {
			return chunk;
		}
		synchronized (chunk) {
			// double-checked locking
			if (chunk.content != null) {
				return chunk;
			}
			LEInputStream in = new LEInputStream(createInputStream(chunkOffset + chunk.chunkNo * (long)chunkSize, chunkSize));
			String chunkMagic = in.readUTF8(4);
			if (DirectoryChunkType.IndexChunk.magic.equals(chunkMagic)) {
				ArrayList<DirectoryChunk> children = new ArrayList<DirectoryChunk>();
				int freeSpace = in.read32(); // Length of free space and/or quickref area at end of directory chunk
				// directory index entries, sorted by filename (case insensitive)
				while (in.available() > freeSpace) {
					int subNameLen = in.readENC();
					String subName = in.readUTF8(subNameLen);
					int subChunkNo = in.readENC();
					DirectoryChunk subChunk = getOrCreateDirectoryChunk(subChunkNo, subName);
					children.add(subChunk);
				}
				if (chunk.name == null) {
					chunk.name = children.get(0).name;
				}
				chunk.content = new IndexChunk(children);
				chunkCompleted(chunk);
				return chunk;
			} else if (DirectoryChunkType.ListingChunk.magic.equals(chunkMagic)) {
				ArrayList<ListingEntry> entries = new ArrayList<ListingEntry>();
				int freeSpace = in.read32(); // Length of free space and/or quickref area at end of directory chunk
				in.read32(); // = 0;
				in.read32(); // previousChunk #
				in.read32(); // nextChunk #
				synchronized (entryCache) {
					while (in.available() > freeSpace) {
						ListingEntry entry = new ListingEntry(in);
						entries.add(entry);
						entryCache.put(entry.name, entry);
					}
				}
				if (chunk.name == null) {
					chunk.name = entries.get(0).name;
				}
				chunk.content = new ListingChunk(entries);
				chunkCompleted(chunk);
				return chunk;
			} else {
				throw new DataFormatException("Index Chunk magic mismatch, '" + chunkMagic +  "' is not 'PMGI' nor 'PMGL'");
			}
		}
	}

	private void fillChunkRecursively(DirectoryChunk chunk) throws IOException {
		fillChunk(chunk);
		if (chunk.content instanceof IndexChunk) {
			for (DirectoryChunk sub : ((IndexChunk) chunk.content).children) {
				fillChunkRecursively(sub);
			}
		}
	}

	/**
	 * Resovle entry by name, using cache and index
	 */
	private ListingEntry resolveEntry(String name) throws IOException {
		ListingEntry entry = entryCache.get(name);
		if (entry == null && !isResourcesCompleted()) {
			entry = searchChunk(rootIndexChunk, name);
		}
		if (entry == null) {
			throw new FileNotFoundException(file + ": " + name);
		}
		return entry;
	}

	enum DirectoryChunkType {
		ListingChunk("PMGL"),
		IndexChunk("PMGI");

		public final String magic;

		DirectoryChunkType(String magic) {
			this.magic = magic;
		}
	}

	static class DirectoryChunk {
		final int chunkNo;
		String name;

		static abstract class Content {
			abstract DirectoryChunkType getType();
		}
		Content content;

		DirectoryChunk(int chunkNo, String name) {
			this.chunkNo = chunkNo;
			this.name = name;
		}
	}

	static class ListingChunk extends DirectoryChunk.Content {
		@Override
		public DirectoryChunkType getType() {
			return DirectoryChunkType.ListingChunk;
		}
		final List<ListingEntry> children;
		ListingChunk(List<ListingEntry> children) {
			this.children = children;
		}
	}

	static class IndexChunk extends DirectoryChunk.Content {
		@Override
		public DirectoryChunkType getType() {
			return DirectoryChunkType.IndexChunk;
		}
		final List<DirectoryChunk> children;
		IndexChunk(List<DirectoryChunk> children) {
			this.children = children;
		}
	}

	private Section[] sections = new Section[]{ new Section() }; // for section 0

	private File file;

	public CHMFile(String filepath) throws IOException, DataFormatException {
		this(new File(filepath));
	}
	/**
	 * We need random access to the source file
	 */
	public CHMFile(File file) throws IOException, DataFormatException {
		fileAccess = new RandomAccessFile(this.file = file, "r");

		/** Step 1. CHM header  */
		// The header length is 0x60 (96)
		LEInputStream inHeader = new LEInputStream(createInputStream(0, CHM_HEADER_LENGTH));
		if (!inHeader.readUTF8(4).equals("ITSF")) {
			throw new DataFormatException("CHM file should start with \"ITSF\"");
		}

		if ((version = inHeader.read32()) > 3) {
			log.warning("CHM header version unexpected value " + version);
		}

		int length = inHeader.read32();
		inHeader.read32(); // -1

		timestamp = inHeader.read32();
		log.info("CHM timestamp " + String.format("%04X", timestamp));
		lang = inHeader.read32();
		log.info("CHM ITSF language " + WindowsLanguageID.getLocale(lang));

		String guid1 = inHeader.readGUID(); // "7C01FD10-7BAA-11D0-9E0C-00A0-C922-E6EC"
		//log.info("guid1 = " + guid1);
		String guid2 = inHeader.readGUID(); // "7C01FD11-7BAA-11D0-9E0C-00A0-C922-E6EC"
		//log.info("guid2 = " + guid2);

		long off0 = inHeader.read64();
		long len0 = inHeader.read64();
		long off1 = inHeader.read64();
		long len1 = inHeader.read64();

		// if the header length is really 0x60, read the final QWORD
		// or the content should be immediate after header section 1
		contentOffset = (length >= CHM_HEADER_LENGTH) ? inHeader.read64() : (off1 + len1);
		log.fine("CHM content offset " + contentOffset);

		/* Step 1.1 (Optional)  CHM header section 0 */
		LEInputStream inHeader0 = new LEInputStream(createInputStream(off0, (int) len0)); // len0 can't exceed 32-bit
		inHeader0.read32(); // 0x01FE;
		inHeader0.read32(); // 0;
		if ((fileLength = inHeader0.read64()) != fileAccess.length()) {
			log.warning("CHM file may be corrupted, expect file length " + fileLength);
		}
		inHeader0.read32(); // 0;
		inHeader0.read32(); // 0;
		
		/* Step 1.2 CHM header section 1: directory index header */
		LEInputStream inDirectory = new LEInputStream(createInputStream(off1, CHM_DIRECTORY_HEADER_LENGTH));

		if (!inDirectory.readUTF8(4).equals("ITSP")) {
			throw new DataFormatException("CHM directory header should start with 'ITSP'");
		}

		inDirectory.read32(); // version
		chunkOffset = off1 + inDirectory.read32(); // = 0x54
		inDirectory.read32(); // = 0x0a
		chunkSize = inDirectory.read32(); // 0x1000
		quickRef = 1 + (1 << inDirectory.read32()); // = 1 + (1 << quickRefDensity )
		depthOfIndexTree = inDirectory.read32(); // depth of index tree, 1: no index, 2: one level of PMGI chunks

		rootIndexChunkNo = inDirectory.read32();	// chunk number of root, -1: none
		firstPMGLChunkNo = inDirectory.read32();
		lastPMGLChunkNo = inDirectory.read32();
		inDirectory.read32(); // = -1
		totalChunks = inDirectory.read32();
		lang2 = inDirectory.read32(); // language code
		log.info("CHM ITSP language " + WindowsLanguageID.getLocale(lang2));

		inDirectory.readGUID(); //.equals("5D02926A-212E-11D0-9DF9-00A0-C922-E6EC"))
		inDirectory.read32(); // = x54
		inDirectory.read32(); // = -1
		inDirectory.read32(); // = -1
		inDirectory.read32(); // = -1

		if (chunkSize * totalChunks + CHM_DIRECTORY_HEADER_LENGTH != len1) {
			throw new DataFormatException("CHM directory list chunks size mismatch");
		}

		// init chunk cache
		entryCache = new ConcurrentSkipListMap<String, ListingEntry>(String.CASE_INSENSITIVE_ORDER);
		completedChunks = new AtomicInteger(0);
		directoryChunks = new DirectoryChunk[totalChunks];
		rootIndexChunk = new DirectoryChunk(rootIndexChunkNo, null);
		if (rootIndexChunkNo >= 0) {
			directoryChunks[rootIndexChunkNo] = rootIndexChunk;
		} else {
			fillFakeRootIndexChunk(rootIndexChunk);
		}

		/* Step 2. CHM name list: content sections */
		LEInputStream inNameList = new LEInputStream(getResourceAsStream("::DataSpace/NameList"));
		if (inNameList == null) {
			throw new DataFormatException("Missing ::DataSpace/NameList entry");
		}
		inNameList.read16(); // length in 16-bit-word, = in.length() / 2
		sections = new Section[inNameList.read16()];
		for (int i = 0; i < sections.length; i ++) {
			String name = inNameList.readUTF16(inNameList.read16() << 1);
			if ("Uncompressed".equals(name)) {
				sections[i] = new Section();
			} else if ("MSCompressed".equals(name)) {
				sections[i] = new LZXCSection();
			} else {
				throw new DataFormatException("Unknown content section " + name);
			}
			inNameList.read16(); // = null
		}

		InputStream inSharpSystem = getResourceAsStream(SharpSystem.FILENAME);
		if (inSharpSystem == null) {
			throw new DataFormatException("Missing " + SharpSystem.FILENAME + " entry");
		}
		sharpSystem = new SharpSystem(inSharpSystem);
	}

	/**
	 * Read len bytes from file beginning from offset.
	 * Since it's really a ByteArrayInputStream, close() operation is optional
	 */
	private InputStream createInputStream(long offset, int len) throws IOException {
		synchronized(fileAccess) {
			fileAccess.seek(offset);
			byte[] b = new byte[len]; // TODO performance?
			fileAccess.readFully(b);
			return new ByteArrayInputStream(b);
		}
	}

	/**
	 * Get an InputStream object for the named resource in the CHM.
	 */
	public InputStream getResourceAsStream(String name) throws IOException {
		if (name == null) {
			throw new NullPointerException("name");
		}
		ListingEntry entry = resolveEntry(name);
		Section section = sections[entry.section];
		return section.resolveInputStream(entry.offset, entry.length);
	}

	@Getter
	private final SharpSystem sharpSystem;

	public Charset getCharset() {
		return sharpSystem.getCharset();
	}

	public static String normalizeFilename(String filename) {
		filename = filename.replace('\\', '/');
		if (!filename.startsWith("/")) {
			filename = "/" + filename;
		}
		return filename;
	}

	private SiteMap createSiteMap(String filename) throws IOException {
		if (filename == null) {
			return null;
		}
		return SiteMap.create(CHMFile.this, normalizeFilename(filename));
	}

	// *.hhc
	private final AtomicReference<Object> contentsFileNameCache = new AtomicReference<Object>();
	public String getContentsFileName() throws IOException {
		return Utils.lazyGet(contentsFileNameCache, new Utils.SupplierWithException<String, IOException>() {
			@Override
			public String get() throws IOException {
				String name = sharpSystem.getProperty(SharpSystem.HhpOption.ContentsFile);
				if (name == null) {
					for(String resource : getResources()) {
						if (resource.toLowerCase().endsWith(".hhc")) {
							return resource;
						}
					}
				}
				return name;
			}
		});
	}

	private final AtomicReference<Object> contentsSiteMapCache = new AtomicReference<Object>();
	public SiteMap getContentsSiteMap() throws IOException {
		return Utils.lazyGet(contentsSiteMapCache, new Utils.SupplierWithException<SiteMap, IOException>() {
			@Override
			public SiteMap get() throws IOException {
				return createSiteMap(getContentsFileName());
			}
		});
	}

	// *.hhk
	private final AtomicReference<Object> indexFileNameCache = new AtomicReference<Object>();
	public String getIndexFileName() throws IOException {
		return Utils.lazyGet(indexFileNameCache, new Utils.SupplierWithException<String, IOException>() {
			@Override
			public String get() throws IOException {
				String name = sharpSystem.getProperty(SharpSystem.HhpOption.IndexFile);
				if (name == null) {
					for(String resource : getResources()) {
						if (resource.toLowerCase().endsWith(".hhk")) {
							return resource;
						}
					}
				}
				return name;
			}
		});
	}

	private final AtomicReference<Object> indexSiteMapCache = new AtomicReference<Object>();
	public SiteMap getIndexSiteMap() throws IOException {
		return Utils.lazyGet(indexSiteMapCache, new Utils.SupplierWithException<SiteMap, IOException>() {
			@Override
			public SiteMap get() throws IOException {
				return createSiteMap(getIndexFileName());
			}
		});
	}

	/**
	 * After close, the object can not be used any more.
	 */
	public synchronized void close() throws IOException {
		sections = null;

		RandomAccessFile fileAccess = this.fileAccess;
		if (fileAccess != null) {
			this.fileAccess = null;
			fileAccess.close();
		}
	}

	protected void finalize() throws IOException {
		close();
	}

	class Section {
		public InputStream resolveInputStream(long off, int len) throws IOException {
			return createInputStream(contentOffset + off, len);
		}
	}

	class LZXCSection extends Section {

		long compressedLength;
		long uncompressedLength;
		int blockSize;
		int resetInterval;
		long[]addressTable;
		int windowSize;
		long sectionOffset;

		LRUCache<Integer, byte[][]> cachedBlocks;

		public LZXCSection() throws IOException, DataFormatException {
			// control data
			LEInputStream in = new LEInputStream(
					getResourceAsStream("::DataSpace/Storage/MSCompressed/ControlData"));
			in.read32(); // words following LZXC
			if ( ! in.readUTF8(4).equals("LZXC"))
				throw new DataFormatException("Must be in LZX Compression");

			in.read32(); // <=2, version
			resetInterval = in.read32(); // huffman reset interval for blocks
			windowSize = in.read32() * 0x8000;	// usu. 0x10, windows size in 0x8000-byte blocks
			int cacheSize = in.read32();	// unknown, 0, 1, 2
			log.info("LZX cache size " + cacheSize);
			cachedBlocks = new LRUCache<Integer, byte[][]>((1 + cacheSize) << 2);
			in.read32(); // = 0

			// reset table
			in = new LEInputStream(
					getResourceAsStream("::DataSpace/Storage/MSCompressed/Transform/" +
						"{7FC28940-9D31-11D0-9B27-00A0C91E9C7C}/InstanceData/ResetTable"));
			if (in == null) throw new DataFormatException("LZXC missing reset table");
			int version = in.read32();
			if ( version != 2) log.warning("LZXC version unknown " + version);
			addressTable = new long[in.read32()];
			in.read32(); // = 8; size of table entry
			in.read32(); // = 0x28, header length
			uncompressedLength = in.read64();
			compressedLength = in.read64();
			blockSize = (int) in.read64(); // 0x8000, do not support blockSize larger than 32-bit integer
			for (int i = 0; i < addressTable.length; i ++ ) {
				addressTable[i] = in.read64();
			}
			// init cache
//			cachedBlocks = new byte[resetInterval][blockSize];
//			cachedResetBlockNo = -1;

			ListingEntry entry = resolveEntry("::DataSpace/Storage/MSCompressed/Content");
			if ( entry == null )
				throw new DataFormatException("LZXC missing content");
			if (compressedLength != entry.length)
				throw new DataFormatException("LZXC content corrupted");
			sectionOffset = contentOffset + entry.offset;
		}

		@Override
		public InputStream resolveInputStream(final long off, final int len) throws IOException {
			// the input stream !
			return new InputStream() {

				int startBlockNo = (int) (off / blockSize);
				int startOffset = (int) (off % blockSize);
				int endBlockNo = (int) ( (off + len) / blockSize );
				int endOffset = (int) ( (off + len) % blockSize );
				// actually start at reset intervals
				int blockNo = startBlockNo - startBlockNo % resetInterval;

				Inflater inflater = new Inflater(windowSize);

				byte[]buf;
				int pos;
				int bytesLeft;

				@Override
				public int available() throws IOException {
					return bytesLeft; // not non-blocking available
				}

				@Override
				public void close() throws IOException {
					inflater = null;
				}

				/**
				 * Read the blockNo block, called when bytesLeft == 0
				 */
				private void readBlock() throws IOException {
					if (blockNo > endBlockNo)
						throw new EOFException();

					int cachedNo = blockNo / resetInterval;
					synchronized(cachedBlocks) {
						byte[][]cache = cachedBlocks.get(cachedNo);
						if (cache == null) {
							if ( (cache = cachedBlocks.prune()) == null) // try reuse old caches
									cache = new byte[resetInterval][blockSize];
							int resetBlockNo =  blockNo - blockNo % resetInterval;
							for (int i = 0; i < cache.length && resetBlockNo + i < addressTable.length; i ++) {
								int blockNo = resetBlockNo + i;
								int len = (int) ( (blockNo + 1 < addressTable.length) ?
										( addressTable[blockNo + 1] - addressTable[blockNo] ):
											( compressedLength - addressTable[blockNo]) );
								log.fine("readBlock " + blockNo + ": " + (sectionOffset + addressTable[blockNo]) + "+ " +  len);
								inflater.inflate(i == 0, // reset flag
										createInputStream(sectionOffset + addressTable[blockNo], len),
										cache[i]); // here is the heart
							}
							cachedBlocks.put(cachedNo, cache);
						}
						if (buf == null) // allocate the buffer
							buf = new byte[blockSize];
						System.arraycopy(cache[blockNo % cache.length], 0, buf, 0, buf.length);
					}

					// the start block has special pos value
					pos = (blockNo == startBlockNo) ? startOffset : 0;
					// the end block has special length
					bytesLeft = (blockNo < startBlockNo) ? 0
							: ( (blockNo < endBlockNo) ? blockSize : endOffset );
					bytesLeft -= pos;

					blockNo ++;
				}

				@Override
				public int read(byte[] b, int off, int len) throws IOException {

					if ( (bytesLeft <= 0) && (blockNo > endBlockNo) ) {
						return -1;	// no more data
					}

					while (bytesLeft <= 0)
						readBlock(); // re-charge

					int togo = Math.min(bytesLeft, len);
					System.arraycopy(buf, pos, b, off, togo);
					pos += togo;
					bytesLeft -= togo;

					return togo;
				}

				@Override
				public int read() throws IOException {
					byte[]b = new byte[1];
					return (read(b) == 1) ? b[0] & 0xff : -1;
				}

				@Override
				public long skip(long n) throws IOException {
					log.warning("LZX skip happens: " + pos + "+ " + n);
					pos += n;	// TODO n chould be negative, so do boundary checks!
					return n;
				}
			};
		}
	}

	static class ListingEntry {
		String name;
		int section;
		long offset;
		int length;

		public ListingEntry(LEInputStream in) throws IOException {
			name = in.readUTF8(in.readENC());
			section = in.readENC();
			offset = in.readENC();
			length = in.readENC();
		}

		ListingEntry(String name) {
			this.name = name;
		}

		public String toString() {
			return name + " @" + section + ": " + offset + " + " + length;
		}
	}

	public Map<String, Object> getLangs() {
		Map<String, Object> values = new HashMap<String, Object>();
		try {
			values.put("lang", lang);
			values.put("lang_locale", WindowsLanguageID.getLocale(lang));
			log.info(String.format("lang1 0x%04X %1s", lang, WindowsLanguageID.getLocale(lang)));

			values.put("lang2", lang2);
			values.put("lang2_locale", WindowsLanguageID.getLocale(lang2));
			log.info(String.format("lang2 0x%04X %1s", lang2, WindowsLanguageID.getLocale(lang2)));

			int lang3 = sharpSystem.getLcid();
			values.put("lang3", lang3);
			values.put("lang3_locale", WindowsLanguageID.getLocale(lang3));
			log.info(String.format("lang3 0x%04X %1s", lang3, WindowsLanguageID.getLocale(lang3)));

			byte[] buf = new byte[256];
			LEInputStream in = new LEInputStream(getResourceAsStream("/$FIftiMain"));
			if (in.read(buf, 0, 0x7a) < 0x7a) {
				throw new IOException("Unexpected end of file " + "/$FIftiMain");
			}
			int codepage4 = in.read32();
			values.put("codepage4", codepage4);
			log.info(String.format("codepage4 %05d", codepage4));
			int lang4 = in.read32();
			values.put("lang4", lang4);
			values.put("lang4_locale", WindowsLanguageID.getLocale(lang4));
			log.info(String.format("lang4 0x%04X %1s", lang4, WindowsLanguageID.getLocale(lang4)));

		} catch(Exception ex) {
			log.throwing("CHMFile", "getLangs", ex);
		}
		return values;
	}

	public static void main(String[]argv) throws Exception {
		if (argv.length == 0) {
			System.err.println("usage: java " + CHMFile.class.getName() + " <chm file name> (file)*");
			System.exit(1);
		}

		CHMFile chm = new CHMFile(argv[0]);
		if (argv.length == 1) {
			for (String file: chm.getResources() ){
				System.out.println(file);
			}
		} else {
			byte[]buf = new byte[1024];
			for (int i = 1; i < argv.length; i ++ ) {
				InputStream in = chm.getResourceAsStream(argv[i]);
				int c = 0;
				while ( (c = in.read(buf)) >= 0) {
					System.out.print(new String(buf, 0, c));
				}
			}
		}
		chm.close();
	}
}
