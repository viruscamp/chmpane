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

import lombok.NonNull;
import lombok.extern.java.Log;

import java.io.*;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * @author Rui Shen
 *
 * CHMFile
 * @see <a href="http://www.russotto.net/chm/chmformat.html">Microsoft's HTML Help (.chm) format</a>
 * @see <a href="http://www.nongnu.org/chmspec/latest/Internal.html">Unofficial (Preliminary) HTML Help Specification</a>
 * @see <a href="http://www.cabextract.org.uk/libmspack/doc/structmschmd__header.html">mschmd_header Struct Reference</a>
 */
@Log
public final class CHMFile implements Closeable {

	public static final int CHM_HEADER_LENGTH = 0x60;

	public static final int CHM_DIRECTORY_HEADER_LENGTH = 0x54;

	// header info
	private final int version; // 3, 2
	/**
	 * It is the lower 32 bits of a 64-bit value representing the number of centiseconds since 1601-01-01 00:00:00 UTC, plus 42.
	 * It is not useful as a timestamp, but it is useful as a semi-unique ID.
	 */
	private final int timestamp;
	/**
	 * ITSF Header 0x14 DWORD : LCID of OS building the file, useless
	 */
	private final int lcidITSF;
	/**
	 * ITSP Header 0x30 DWORD : LCID of ITSS.DLL, useless
	 */
	private final int lcidITSP;
	private final long contentOffset;
	private final long fileLength;
	private final int chunkSize;
	private final int quickRef;
	private final int depthOfIndexTree;
	private final int rootIndexChunkNo;
	private final int firstPMGLChunkNo;
	private final int lastPMGLChunkNo;
	private final int totalChunks;

	private final long chunkOffset;

	private final RandomAccessFile fileAccess;

	// should be cleared when entryCache is fully filled
	private final AtomicInteger completedChunks;
	private DirectoryChunk[] directoryChunks;
	private DirectoryChunk rootIndexChunk;

	// will be filled stepwise
	private final Map<String, ResourceEntry> entryCache;

	// should have value when entryCache is fully filled
	private final AtomicReference<Object> resourcesCache = new AtomicReference<Object>();
	public List<String> getResources() throws IOException {
		return Utils.lazyGet(this.resourcesCache, new Utils.SupplierWithException<List<String>, IOException>() {
			public List<String> get() throws IOException {
				if (rootIndexChunkNo >= 0) {
					fillChunkRecursively(rootIndexChunk);
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
				directoryChunks = null;
				rootIndexChunk = null;
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

	public ResourceEntry searchChunk(DirectoryChunk chunk, String name) throws IOException {
		fillChunk(chunk);
		if (chunk.content instanceof ListingChunk) {
			List<ResourceEntry> children = ((ListingChunk) chunk.content).children;
			int idx = Collections.binarySearch(children, new ResourceEntryKey(name),
					new Comparator<ResourceEntryKey>() {
						public int compare(ResourceEntryKey o1, ResourceEntryKey o2) {
							return String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name);
						}
					}
			);
			if (idx >= 0) {
				return children.get(idx);
			}
		} else if (chunk.content instanceof IndexChunk) {
			List<DirectoryChunk> children = ((IndexChunk) chunk.content).children;
			int idx = Collections.binarySearch(children, new DirectoryChunkKey(name),
					new Comparator<DirectoryChunkKey>() {
						public int compare(DirectoryChunkKey o1, DirectoryChunkKey o2) {
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
			LEInputStream in = new LEInputStream(rawInputStream(chunkOffset + chunk.chunkNo * (long)chunkSize, chunkSize));
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
				ArrayList<ResourceEntry> entries = new ArrayList<ResourceEntry>();
				int freeSpace = in.read32(); // Length of free space and/or quickref area at end of directory chunk
				in.read32(); // = 0;
				in.read32(); // previousChunk #
				in.read32(); // nextChunk #
				synchronized (entryCache) {
					while (in.available() > freeSpace) {
						ResourceEntry entry = new ResourceEntry(in);
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
	 * @param name not null
	 * @return null if there is no entry called 'name'
	 * @throws IOException
	 */
	public ResourceEntry resolveEntry(@NonNull String name) throws IOException {
		ResourceEntry entry = entryCache.get(name);
		if (entry == null && !isResourcesCompleted()) {
			entry = searchChunk(rootIndexChunk, name);
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

	static class DirectoryChunkKey {
		String name;
		public DirectoryChunkKey(String name) {
			this.name = name;
		}
	}
	static class DirectoryChunk extends DirectoryChunkKey {
		final int chunkNo;

		interface Content {
			DirectoryChunkType getType();
		}
		Content content;

		DirectoryChunk(int chunkNo, String name) {
			super(name);
			this.chunkNo = chunkNo;
		}
	}

	static class ListingChunk implements DirectoryChunk.Content {
		@Override
		public DirectoryChunkType getType() {
			return DirectoryChunkType.ListingChunk;
		}
		final List<ResourceEntry> children;
		ListingChunk(List<ResourceEntry> children) {
			this.children = children;
		}
	}

	static class IndexChunk implements DirectoryChunk.Content {
		@Override
		public DirectoryChunkType getType() {
			return DirectoryChunkType.IndexChunk;
		}
		final List<DirectoryChunk> children;
		IndexChunk(List<DirectoryChunk> children) {
			this.children = children;
		}
	}

	private final Section[] sections;

	private File file;

	public CHMFile(String filepath) throws IOException, DataFormatException {
		this(new File(filepath));
	}
	/**
	 * We need random access to the source file
	 */
	public CHMFile(@NonNull File file) throws IOException, DataFormatException {
		this.file = file;
		fileAccess = new RandomAccessFile(file, "r");

		/** Step 1. CHM header  */
		// The header length is 0x60 (96)
		LEInputStream inHeader = new LEInputStream(rawInputStream(0, CHM_HEADER_LENGTH));
		if (!inHeader.readUTF8(4).equals("ITSF")) {
			throw new DataFormatException("CHM file should start with 'ITSF'");
		}

		if ((version = inHeader.read32()) > 3) {
			log.warning("CHM header version unexpected value " + version);
		}

		int length = inHeader.read32();
		inHeader.read32(); // -1

		timestamp = inHeader.read32();
		//log.info("CHM timestamp " + String.format("%04X", timestamp));
		lcidITSF = inHeader.read32();
		//log.info(String.format("CHM ITSF lcid: 0x%04X , locale: %1s", lcidITSF, WindowsLanguageID.getLocale(lcidITSF)));

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
		//log.fine("CHM content offset " + contentOffset);

		/* Step 1.1 (Optional)  CHM header section 0 */
		LEInputStream inHeader0 = new LEInputStream(rawInputStream(off0, (int) len0)); // len0 can't exceed 32-bit
		inHeader0.read32(); // 0x01FE;
		inHeader0.read32(); // 0;
		if ((fileLength = inHeader0.read64()) != fileAccess.length()) {
			log.warning("CHM file may be corrupted, expect file length " + fileLength);
		}
		inHeader0.read32(); // 0;
		inHeader0.read32(); // 0;
		
		/* Step 1.2 CHM header section 1: directory index header */
		LEInputStream inDirectory = new LEInputStream(rawInputStream(off1, CHM_DIRECTORY_HEADER_LENGTH));

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
		lcidITSP = inDirectory.read32();
		//log.info(String.format("CHM ITSP lcid: 0x%04X , locale: %1s", lcidITSP, WindowsLanguageID.getLocale(lcidITSP)));

		inDirectory.readGUID(); //.equals("5D02926A-212E-11D0-9DF9-00A0-C922-E6EC"))
		inDirectory.read32(); // = x54
		inDirectory.read32(); // = -1
		inDirectory.read32(); // = -1
		inDirectory.read32(); // = -1

		if (chunkSize * totalChunks + CHM_DIRECTORY_HEADER_LENGTH != len1) {
			throw new DataFormatException("CHM directory list chunks size mismatch");
		}

		// init chunk cache
		entryCache = new ConcurrentSkipListMap<String, ResourceEntry>(String.CASE_INSENSITIVE_ORDER);
		completedChunks = new AtomicInteger(0);
		directoryChunks = new DirectoryChunk[totalChunks];
		rootIndexChunk = new DirectoryChunk(rootIndexChunkNo, null);
		if (rootIndexChunkNo >= 0) {
			directoryChunks[rootIndexChunkNo] = rootIndexChunk;
		} else {
			fillFakeRootIndexChunk(rootIndexChunk);
		}

		/* Step 2. CHM name list: content sections */
		LEInputStream isNameList = new LEInputStream(getUncompressedResourceAsStream(ResourceNames.NameList, null));
		isNameList.read16(); // length in 16-bit-word, = in.length() / 2
		sections = new Section[isNameList.read16()];
		LZXCConfig lzxcConfig = null;
		for (int i = 0; i < sections.length; i ++) {
			String name = isNameList.readUTF16(isNameList.read16() << 1);
			if ("Uncompressed".equals(name)) {
				sections[i] = new UncompressedSection();
			} else if ("MSCompressed".equals(name)) {
				if (lzxcConfig == null) {
					lzxcConfig = new LZXCConfig(); // use Uncompressed sections, should be sections[0]
				}
				sections[i] = lzxcConfig.createLZXCSection();
			} else {
				throw new DataFormatException("Unknown content section " + name);
			}
			isNameList.read16(); // = null
		}
	}

	/**
	 * Read len bytes from file beginning from offset.
	 * Since it's really a ByteArrayInputStream, close() operation is optional
	 */
	private InputStream rawInputStream(long offset, int len) throws IOException {
		synchronized(fileAccess) {
			fileAccess.seek(offset);
			byte[] b = new byte[len]; // TODO performance?
			fileAccess.readFully(b);
			return new ByteArrayInputStream(b);
		}
	}

	private InputStream getStreamFromEntry(@NonNull ResourceEntry entry) throws IOException {
		Section section = sections[entry.section];
		return section.resolveInputStream(entry.offset, entry.length);
	}

	/**
	 * Get an InputStream object for the named resource in the CHM.
	 * @param name not null
	 * @return cannot be null
	 * @throws IOException, FileNotFoundException if cannot find the entry
	 */
	public @NonNull InputStream getResourceAsStream(@NonNull String name) throws IOException {
		ResourceEntry entry = resolveEntry(name);
		if (entry == null) {
			throw new FileNotFoundException(file.getName() + name);
		}
		return getStreamFromEntry(entry);
	}

	// never use sections , cause it has not been initialized.
	private InputStream getUncompressedResourceAsStream(@NonNull String name, String simpleName) throws IOException {
		ResourceEntry entry = resolveEntry(name);
		if (entry == null) {
			throw new DataFormatException("Missing " + (simpleName != null ? simpleName : name) + " entry");
		}
		return rawInputStream(contentOffset + entry.offset, entry.length);
	}

	private final AtomicReference<Object> sharpSystem = new AtomicReference<Object>();
	public SharpSystem getSharpSystem() throws IOException {
		return Utils.lazyGet(sharpSystem, new Utils.SupplierWithException<SharpSystem, IOException>() {
			@Override
			public SharpSystem get() throws IOException {
				ResourceEntry entrySharpSystem = resolveEntry(ResourceNames.SharpSystem);
				if (entrySharpSystem == null) {
					throw new DataFormatException("Missing " + ResourceNames.SharpSystem + " entry");
				}
				return new SharpSystem(getStreamFromEntry(entrySharpSystem));
			}
		});
	}

	public Charset getCharset() throws IOException {
		return getSharpSystem().getCharset();
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
				String name = getSharpSystem().getProperty(SharpSystem.HhpOption.ContentsFile);
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
				String name = getSharpSystem().getProperty(SharpSystem.HhpOption.IndexFile);
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

	public String getDefaultTopic() throws IOException {
		return getSharpSystem().getProperty(SharpSystem.HhpOption.DefaultTopic);
	}

	/**
	 * After close, the object can not be used any more.
	 */
	public synchronized void close() throws IOException {
		RandomAccessFile fileAccess = this.fileAccess;
		if (fileAccess != null) {
			fileAccess.close();
		}
	}

	protected void finalize() throws IOException {
		close();
	}

	interface Section {
		InputStream resolveInputStream(long off, int len) throws IOException;
	}

	class UncompressedSection implements Section {
		public InputStream resolveInputStream(long off, int len) throws IOException {
			return rawInputStream(contentOffset + off, len);
		}
	}

	class LZXCConfig {
		final long compressedLength;
		final long uncompressedLength;
		final int blockSize;
		final int resetInterval;
		final long[]addressTable;
		final int windowSize;
		final long sectionOffset;
		final int cacheSize;
		public LZXCConfig() throws IOException, DataFormatException {
			// control data
			LEInputStream in = new LEInputStream(getUncompressedResourceAsStream(ResourceNames.LzxcControlData, "LZXC control data"));
			in.read32(); // words following LZXC
			if ( ! in.readUTF8(4).equals("LZXC")) {
				throw new DataFormatException("Must be in LZX Compression");
			}

			in.read32(); // <=2, version
			resetInterval = in.read32(); // huffman reset interval for blocks
			windowSize = in.read32() * 0x8000;	// usu. 0x10, windows size in 0x8000-byte blocks
			cacheSize = in.read32();	// unknown, 0, 1, 2
			//log.info("LZX cache size " + cacheSize);
			in.read32(); // = 0

			// reset table
			in = new LEInputStream(getUncompressedResourceAsStream(ResourceNames.LzxcResetTable, "LZXC reset table"));
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

			ResourceEntry entry = resolveEntry(ResourceNames.LzxcContent);
			if (entry == null) {
				throw new DataFormatException("LZXC content missing");
			}
			if (compressedLength != entry.length) {
				if (compressedLength > entry.length) {
					throw new DataFormatException("LZXC content corrupted");
				}
				log.warning(MessageFormat.format("LZXC content compressedLength={0} , but entry.length={1}", compressedLength, entry.length));
			}
			sectionOffset = contentOffset + entry.offset;
		}

		public Section createLZXCSection() {
			return new LZXCSection();
		}

		class LZXCSection implements Section {
			final LRUCache<Integer, byte[][]> cachedBlocks;
			LZXCSection() {
				cachedBlocks = new LRUCache<Integer, byte[][]>((1 + cacheSize) << 2);
			}

			@Override
			public InputStream resolveInputStream(final long off, final int len) throws IOException {
				// the input stream !
				return new InputStream() {
					int startBlockNo = (int) (off / blockSize);
					int startOffset = (int) (off % blockSize);
					int endBlockNo = (int) ((off + len) / blockSize);
					int endOffset = (int) ((off + len) % blockSize);
					// actually start at reset intervals
					int blockNo = startBlockNo - startBlockNo % resetInterval;

					Inflater inflater = new Inflater(windowSize);

					byte[] buf;
					int posInBlock = 0;
					int bytesLeftInBlock = 0;

					@Override
					public int available() throws IOException {
						return bytesLeftInBlock; // not non-blocking available
					}

					/**
					 * Read the blockNo block, called when bytesLeft == 0
					 */
					private void readBlock() throws IOException {
						if (blockNo > endBlockNo)
							throw new EOFException();

						int cachedNo = blockNo / resetInterval;
						synchronized (cachedBlocks) {
							byte[][] cache = cachedBlocks.get(cachedNo);
							if (cache == null) {
								if ((cache = cachedBlocks.prune()) == null) // try reuse old caches
									cache = new byte[resetInterval][blockSize];
								int resetBlockNo = blockNo - blockNo % resetInterval;
								for (int i = 0; i < cache.length && resetBlockNo + i < addressTable.length; i++) {
									int blockNo = resetBlockNo + i;
									int len = (int) ((blockNo + 1 < addressTable.length) ?
											(addressTable[blockNo + 1] - addressTable[blockNo]) :
											(compressedLength - addressTable[blockNo]));
									//log.fine("readBlock " + blockNo + ": " + (sectionOffset + addressTable[blockNo]) + "+ " + len);
									inflater.inflate(i == 0, // reset flag
											rawInputStream(sectionOffset + addressTable[blockNo], len),
											cache[i]); // here is the heart
								}
								cachedBlocks.put(cachedNo, cache);
							}
							if (buf == null) // allocate the buffer
								buf = new byte[blockSize];
							System.arraycopy(cache[blockNo % cache.length], 0, buf, 0, buf.length);
							//buf = cache[blockNo % cache.length]; // 直接使用缓存, 被缓存丢弃没有问题, 问题出在被复用
						}

						if (blockNo < startBlockNo) {
							// invalid blocks before startBlockNo
							posInBlock = 0;
							bytesLeftInBlock = 0;
						} else {
							if (blockNo == startBlockNo) {
								// the start block has special pos value
								posInBlock = startOffset;
							} else {
								posInBlock = 0;
							}
							if (blockNo == endBlockNo) {
								// the end block has special length
								bytesLeftInBlock = endOffset;
							} else {
								bytesLeftInBlock = blockSize;
							}
							bytesLeftInBlock -= posInBlock;
						}

						blockNo++;
					}

					@Override
					public int read(byte[] b, int off, int len) throws IOException {

						if (blockNo > endBlockNo) {
							// last block
							if (bytesLeftInBlock <= 0) {
								// read out
								return -1; // no more data
							}
						} else if (bytesLeftInBlock <= 0) {
							// not last block, invalid or read out
							do {
								readBlock(); // re-charge to next valid block
							} while (blockNo <= startBlockNo);
						}

						int togo = Math.min(bytesLeftInBlock, len);
						System.arraycopy(buf, posInBlock, b, off, togo);
						posInBlock += togo;
						bytesLeftInBlock -= togo;

						return togo;
					}

					@Override
					public int read() throws IOException {
						byte[] b = new byte[1];
						return (read(b) == 1) ? b[0] & 0xff : -1;
					}

					@Override
					public long skip(long n) throws IOException {
						log.warning("LZX skip happens: " + posInBlock + "+ " + n);
						posInBlock += n;    // TODO n chould be negative, so do boundary checks!
						return n;
					}
				};
			}
		}
	}

	static class ResourceEntryKey {
		final String name;
		ResourceEntryKey(String name) {
			this.name = name;
		}
	}

	public static class ResourceEntry extends ResourceEntryKey {
		final int section;
		final long offset;
		final int length;

		public ResourceEntry(LEInputStream in) throws IOException {
			super(in.readUTF8(in.readENC()));
			section = in.readENC();
			offset = in.readENC();
			length = in.readENC();
		}

		public String toString() {
			return name + " @" + section + ": " + offset + " + " + length;
		}
	}

	private static void putLcid(Map<String, Object> values, int lcid, String name) {
		values.put(name, lcid);
		Locale locale = WindowsLanguageID.getLocale(lcid);
		values.put("Locale from " + name, locale);
		//log.info(String.format(name + ": 0x%04X , locale: %1s", lcid, locale));
	}

	private static void putCodePage(Map<String, Object> values, int codePage, String name) {
		values.put(name, codePage);
		Charset charset = WindowsLanguageID.getCharsetByCodePage(codePage);
		values.put("Charset from " + name, charset);
		//log.info(String.format(name + ": %0$d , charset: %1s", codePage, charset));
	}

	public Map<String, Object> getLangs() {
		Map<String, Object> values = new HashMap<String, Object>();
		try {
			putLcid(values, lcidITSF, "LCID of building OS, in ITSF Header");
			putLcid(values, lcidITSP, "LCID of Itss.Dll, in ITSP Header");
			putLcid(values, getSharpSystem().getLcid(), "LCID in " + ResourceNames.SharpSystem);

			ResourceEntry entryFIftiMain = resolveEntry(ResourceNames.DollarFIftiMain);
			if (entryFIftiMain != null) {
				LEInputStream in = new LEInputStream(getStreamFromEntry(entryFIftiMain));
				if (in.read(new byte[256], 0, 0x7a) < 0x7a) {
					throw new IOException("Unexpected end of file " + ResourceNames.DollarFIftiMain);
				}
				int codePageSharpFIftiMain = in.read32();
				putCodePage(values, codePageSharpFIftiMain, "CodePage in " + ResourceNames.DollarFIftiMain);
				int lcidSharpFIftiMain = in.read32();
				putLcid(values, lcidSharpFIftiMain, "LCID in " + ResourceNames.DollarFIftiMain);
			}
		} catch(Exception ex) {
			log.throwing("CHMFile", "getLangs", ex);
		}
		return values;
	}

	public String testReadAll() {
		log.info("begin file: " + file.getAbsolutePath());
		byte[] b = new byte[1024];
		try {
			int resourceSuccess = 0;
			int resourceFailed = 0;
			for(String name : getResources()) {
				try {
					ResourceEntry entry = resolveEntry(name);
					InputStream ris = getStreamFromEntry(entry);
					int actualLength = 0;
					int read = 0;
					while ((read = ris.read(b)) > 0) {
						actualLength += read;
					}
					if (entry.length != actualLength) {
						log.log(Level.SEVERE, "resource: {0} length:{1} actual length:{2}",
								new Object[]{name, entry.length, actualLength});
					}
					resourceSuccess++;
				} catch (Exception ex) {
					log.log(Level.SEVERE, "fail resource: {0}", new Object[]{name});
					log.log(Level.SEVERE, "", ex);
					resourceFailed++;
				}
			}
			log.log(Level.INFO, "success file: {0} resourceSuccess:{1} resourceFailed:{2}",
					new Object[]{file.getAbsoluteFile(), resourceSuccess, resourceFailed});
			return MessageFormat.format("success file: {0} resourceSuccess:{1} resourceFailed:{2}",
					file.getAbsoluteFile(), resourceSuccess, resourceFailed);
		} catch (Exception ex) {
			log.info("failed file: " + file.getAbsolutePath());
			return "failed file: " + file.getAbsolutePath();
		}
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
