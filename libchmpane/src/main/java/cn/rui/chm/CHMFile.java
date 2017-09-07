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

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * @author Rui Shen
 *
 * CHMFile
 * @see <a href="http://www.russotto.net/chm/chmformat.html">Microsoft's HTML Help (.chm) format</a>
 * @see <a href="http://www.nongnu.org/chmspec/latest/Internal.html">Unofficial (Preliminary) HTML Help Specification</a>
 * @see <a href="http://www.cabextract.org.uk/libmspack/doc/structmschmd__header.html">mschmd_header Struct Reference</a>
 */
public class CHMFile implements Closeable {

	public static final int CHM_HEADER_LENGTH = 0x60;

	public static final int CHM_DIRECTORY_HEADER_LENGTH = 0x54;

	private static Logger log = Logger.getLogger(CHMFile.class.getName());

	// header info
	private int version; // 3, 2
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

	Charset charset;

	private Map<String, ListingEntry> entryCache;
	private List<String> resources;


	private LazyLoadChunks lazyLoadChunks;
	class LazyLoadChunks {
		private Map<String, ListingEntry> entryCache;

		private AtomicInteger completedChunks;
		private DirectoryChunk[] directoryChunks;
		private DirectoryChunk rootIndexChunk;

		LazyLoadChunks(int rootIndexChunkNo, int totalChunks) {
			//entryCache = new ConcurrentSkipListMap<String, ListingEntry>(String.CASE_INSENSITIVE_ORDER); // since 1.6
			//entryCache = Collections.synchronizedMap(new TreeMap<String, ListingEntry>(String.CASE_INSENSITIVE_ORDER));
			entryCache = new TreeMap<String, ListingEntry>(String.CASE_INSENSITIVE_ORDER);

			completedChunks = new AtomicInteger(0);
			directoryChunks = new DirectoryChunk[totalChunks];
			rootIndexChunk = new DirectoryChunk(rootIndexChunkNo, null);
			if (rootIndexChunkNo >= 0) {
				directoryChunks[rootIndexChunkNo] = rootIndexChunk;
			}
		}

		private DirectoryChunk getOrCreateChunk(int chunkNo, String firstName) {
			synchronized (directoryChunks) {
				DirectoryChunk chunk = directoryChunks[chunkNo];
				if (chunk == null) {
					chunk = new DirectoryChunk(chunkNo, firstName);
					directoryChunks[chunkNo] = chunk;
				}
				return chunk;
			}
		}

		public ListingEntry searchChunkRecursively(DirectoryChunk chunk, String name) throws IOException {
			readChunk(chunk);
			if (chunk.type == DirectoryChunkType.ListingChunk) {
				int idx = Collections.<ListingEntry>binarySearch(chunk.children, new ListingEntry(name),
						new Comparator<ListingEntry>() {
							public int compare(ListingEntry o1, ListingEntry o2) {
								return String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name);
							}
						}
				);
				if (idx >= 0) {
					return (ListingEntry) chunk.children.get(idx);
				}
			} else if (chunk.type == DirectoryChunkType.IndexChunk) {
				int idx = Collections.<DirectoryChunk>binarySearch(chunk.children, new DirectoryChunk(Integer.MIN_VALUE, name),
						new Comparator<DirectoryChunk>() {
							public int compare(DirectoryChunk o1, DirectoryChunk o2) {
								return String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name);
							}
						}
				);
				if (idx >= 0) {
					return searchChunkRecursively((DirectoryChunk) chunk.children.get(idx), name);
				} else if (idx < -1) {
					return searchChunkRecursively((DirectoryChunk) chunk.children.get(-idx - 2), name);
				}
			}
			return null;
		}

		private void chunkCompleted(DirectoryChunk chunk) {
			if (completedChunks.incrementAndGet() == totalChunks) {
				lazyLoadChunksCompleted();
			}
		}

		private DirectoryChunk readChunk(DirectoryChunk chunk) throws IOException {
			if (chunk.type != DirectoryChunkType.Unknown) {
				return chunk;
			}
			synchronized (chunk) {
				if (chunk.chunkNo == -1) {
					List<DirectoryChunk> children = new ArrayList<DirectoryChunk>(lastPMGLChunkNo - firstPMGLChunkNo + 1);
					for (int chunkNo = firstPMGLChunkNo; chunkNo <= lastPMGLChunkNo; chunkNo++) {
						DirectoryChunk listingChunk = getOrCreateChunk(chunkNo, null);
						readChunk(listingChunk);
						children.add(listingChunk);
					}
					chunk.name = children.get(0).name;
					chunk.children = children;
					chunk.type = DirectoryChunkType.IndexChunk;
					return chunk;
				}
				LEInputStream in = new LEInputStream(createInputStream(chunkOffset + chunk.chunkNo * chunkSize, chunkSize));
				String chunkMagic = in.readUTF8(4);
				if (DirectoryChunkType.IndexChunk.magic.equals(chunkMagic)) {
					ArrayList<DirectoryChunk> children = new ArrayList<DirectoryChunk>();
					int freeSpace = in.read32(); // Length of free space and/or quickref area at end of directory chunk
					// directory index entries, sorted by filename (case insensitive)
					while (in.available() > freeSpace) {
						int subNameLen = in.readENC();
						String subName = in.readUTF8(subNameLen);
						int subChunkNo = in.readENC();
						DirectoryChunk subChunk = getOrCreateChunk(subChunkNo, subName);
						children.add(subChunk);
					}
					if (chunk.name == null) {
						chunk.name = children.get(0).name;
					}
					chunk.children = children;
					chunk.type = DirectoryChunkType.IndexChunk;
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
							if (entry.name.endsWith(".hhc") && entry.name.charAt(0) == '/') {
								// .hhc entry is the navigation file
								contentsSiteMapName = entry.name;
								log.info("CHM contents sitemap " + contentsSiteMapName);
							} else if (entry.name.endsWith(".hhk") && entry.name.charAt(0) == '/') {
								// .hhk entry is the navigation file
								indexSiteMapName = entry.name;
								log.info("CHM index sitemap " + indexSiteMapName);
							}
						}
					}
					if (chunk.name == null) {
						chunk.name = entries.get(0).name;
					}
					chunk.children = entries;
					chunk.type = DirectoryChunkType.ListingChunk;
					chunkCompleted(chunk);
					return chunk;
				} else {
					throw new DataFormatException("Index Chunk magic mismatch, not 'PMGI' nor 'PMGL'");
				}
			}
		}

		private void readChunkRecursively(DirectoryChunk chunk) throws IOException {
			readChunk(chunk);
			if (chunk.type == DirectoryChunkType.IndexChunk) {
				for (Object sub : chunk.children) {
					readChunkRecursively((DirectoryChunk)sub);
				}
			}
		}

		public void listAllChunksRecursively() throws IOException {
			readChunkRecursively(rootIndexChunk);
		}

		public synchronized void listAllChunks() throws IOException {
			if (completedChunks.get() < totalChunks) {
				for (int i = firstPMGLChunkNo; i <= lastPMGLChunkNo; i++) {
					DirectoryChunk chunk = getOrCreateChunk(i, null);
					readChunk(chunk);
				}
				if (completedChunks.get() < totalChunks) {
					lazyLoadChunksCompleted();
				}
			}
		}
	}

	enum DirectoryChunkType {
		Unknown(""),
		ListingChunk("PMGL"),
		IndexChunk("PMGI");

		public final String magic;

		DirectoryChunkType(String magic) {
			this.magic = magic;
		}
	}

	static class DirectoryChunk {
		int chunkNo;
		String name;

		DirectoryChunkType type;
		List children;

		DirectoryChunk(int chunkNo, String name) {
			this.chunkNo = chunkNo;
			this.name = name;
			this.type = DirectoryChunkType.Unknown;
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
		LEInputStream in = new LEInputStream(createInputStream(0, CHM_HEADER_LENGTH));
		if ( ! in.readUTF8(4).equals("ITSF") )
			throw new DataFormatException("CHM file should start with \"ITSF\"");

		if ( (version = in.read32()) > 3)
			log.warning("CHM header version unexpected value " + version);

		int length = in.read32();
		in.read32(); // -1

		timestamp = in.read32(); // big-endian DWORD?
		log.info("CHM timestamp " + new Date(timestamp));
		lang = in.read32();
		log.info("CHM ITSF language " + WindowsLanguageID.getLocale(lang));

		String guid1 = in.readGUID();	//.equals("7C01FD10-7BAA-11D0-9E0C-00A0-C922-E6EC");
		//log.info("guid1 = " + guid1);
		String guid2 = in.readGUID();	//.equals("7C01FD11-7BAA-11D0-9E0C-00A0-C922-E6EC");
		//log.info("guid2 = " + guid2);

		long off0 = in.read64();
		long len0 = in.read64();
		long off1 = in.read64();
		long len1 = in.read64();

		// if the header length is really 0x60, read the final QWORD
		// or the content should be immediate after header section 1
		contentOffset = (length >= CHM_HEADER_LENGTH) ? in.read64() : (off1 + len1);
		log.fine("CHM content offset " + contentOffset);

		/* Step 1.1 (Optional)  CHM header section 0 */
		in = new LEInputStream(createInputStream(off0, (int) len0)); // len0 can't exceed 32-bit
		in.read32(); // 0x01FE;
		in.read32(); // 0;
		if ( (fileLength = in.read64()) != fileAccess.length())
			log.warning("CHM file may be corrupted, expect file length " + fileLength);
		in.read32(); // 0;
		in.read32(); // 0;
		
		/* Step 1.2 CHM header section 1: directory index header */
		in = new LEInputStream(createInputStream(off1, CHM_DIRECTORY_HEADER_LENGTH));

		if (! in.readUTF8(4).equals("ITSP") )
			throw new DataFormatException("CHM directory header should start with \"ITSP\"");

		in.read32(); // version
		chunkOffset = off1 + in.read32(); // = 0x54
		in.read32(); // = 0x0a
		chunkSize = in.read32(); // 0x1000
		quickRef = 1 + (1 << in.read32()); // = 1 + (1 << quickRefDensity )
		depthOfIndexTree = in.read32(); // depth of index tree, 1: no index, 2: one level of PMGI chunks

		rootIndexChunkNo = in.read32();	// chunk number of root, -1: none
		firstPMGLChunkNo = in.read32();
		lastPMGLChunkNo = in.read32();
		in.read32(); // = -1
		totalChunks = in.read32();
		lang2 = in.read32(); // language code
		log.info("CHM ITSP language " + WindowsLanguageID.getLocale(lang2));

		lazyLoadChunks = new LazyLoadChunks(rootIndexChunkNo, totalChunks);

		in.readGUID(); //.equals("5D02926A-212E-11D0-9DF9-00A0-C922-E6EC"))
		in.read32(); // = x54
		in.read32(); // = -1
		in.read32(); // = -1
		in.read32(); // = -1

		if (chunkSize * totalChunks + CHM_DIRECTORY_HEADER_LENGTH != len1)
			throw new DataFormatException("CHM directory list chunks size mismatch");

		/* Step 2. CHM name list: content sections */
		in = new LEInputStream(
				getResourceAsStream("::DataSpace/NameList"));
		if (in == null)
			throw new DataFormatException("Missing ::DataSpace/NameList entry");
		in.read16(); // length in 16-bit-word, = in.length() / 2
		sections = new Section[in.read16()];
		for (int i = 0; i < sections.length; i ++) {
			String name = in.readUTF16(in.read16() << 1);
			if ("Uncompressed".equals(name)) {
				sections[i] = new Section();
			} else if ("MSCompressed".equals(name)) {
				sections[i] = new LZXCSection();
			} else throw new DataFormatException("Unknown content section " + name);
			in.read16(); // = null
		}

		// read LCID from #SYSYTEM then translate it to Charset
		charset = SharpSystem.getCharset(this);
	}

	public Charset getCharset() {
		return charset;
	}

	public String getCharsetName() {
		return (charset != null) ? charset.name() : null;
	}

	/**
	 * make sure to run it only once
	 */
	private synchronized void lazyLoadChunksCompleted() {
		LazyLoadChunks lazyLoadChunks = this.lazyLoadChunks;
		if (lazyLoadChunks != null) {
			entryCache = lazyLoadChunks.entryCache;
			List<String> resources = new ArrayList<String>(entryCache.size());
			for (String name : entryCache.keySet()) {
				if (name.startsWith("/")) {
					resources.add(name);
				}
			}
			this.resources = Collections.unmodifiableList(resources);
			this.lazyLoadChunks = null;
		}
	}

	/**
	 * Read len bytes from file beginning from offset.
	 * Since it's really a ByteArrayInputStream, close() operation is optional
	 */
	private synchronized InputStream createInputStream(long offset, int len) throws IOException {
		fileAccess.seek(offset);
		byte[] b = new byte[len]; // TODO performance?
		fileAccess.readFully(b);
		return new ByteArrayInputStream(b);
	}

	/**
	 * Resovle entry by name, using cache and index
	 */
	private ListingEntry resolveEntry(String name) throws IOException {
		ListingEntry entry;

		LazyLoadChunks lazyLoadChunks = this.lazyLoadChunks;
		if (lazyLoadChunks != null) {
			entry = lazyLoadChunks.searchChunkRecursively(lazyLoadChunks.rootIndexChunk, name);
		} else {
			entry = entryCache.get(name);
		}

		if (entry == null) {
			throw new FileNotFoundException(file + "#" + name);
		}

		return entry;
	}

	/**
	 * Get an InputStream object for the named resource in the CHM.
	 */
	public InputStream getResourceAsStream(String name) throws IOException {
		if (name == null) {
			throw new NullPointerException("name");
		}
		ListingEntry entry = resolveEntry(name);
		if (entry == null)
			throw new FileNotFoundException(file + "#" + name);
		Section section = sections[entry.section];
		return section.resolveInputStream(entry.offset, entry.length);
	}

	public List<String> list() throws IOException {
		LazyLoadChunks lazyLoadChunks = this.lazyLoadChunks;
		if (lazyLoadChunks != null) {
			lazyLoadChunks.listAllChunks();
		}
		return resources;
	}

	private volatile String contentsSiteMapName; // *.hhc
	public String getContentsSiteMapName() throws IOException {
		if (contentsSiteMapName != null) {
			return contentsSiteMapName;
		}
		list();
		return contentsSiteMapName;
	}

	private final Object contentsSiteMapLock = new Object();
	private FinalWrapper<SiteMap> contentsSiteMap;
	public SiteMap getContentsSiteMap() throws IOException {
		FinalWrapper<SiteMap> tempWrapper = contentsSiteMap;
		if (tempWrapper == null) {
			synchronized(contentsSiteMapLock) {
				if (contentsSiteMap == null) {
					SiteMap result = null;
					String filename = getContentsSiteMapName();
					if (filename != null) {
						result = SiteMap.create(this, filename);
					}
					contentsSiteMap = new FinalWrapper<SiteMap>(result);
				}
				tempWrapper = contentsSiteMap;
			}
		}
		return tempWrapper.value;
	}

	private volatile String indexSiteMapName; // *.hhk
	public String getIndexSiteMapName() throws IOException {
		if (indexSiteMapName != null) {
			return indexSiteMapName;
		}
		list();
		return indexSiteMapName;
	}

	private final Object indexSiteMapLock = new Object();
	private FinalWrapper<SiteMap> indexSiteMap;
	public SiteMap getIndexSiteMap() throws IOException {
		FinalWrapper<SiteMap> tempWrapper = indexSiteMap;
		if (tempWrapper == null) {
			synchronized(indexSiteMapLock) {
				if (indexSiteMap == null) {
					SiteMap result = null;
					String filename = getIndexSiteMapName();
					if (filename != null) {
						result = SiteMap.create(this, filename);
					}
					indexSiteMap = new FinalWrapper<SiteMap>(result);
				}
				tempWrapper = indexSiteMap;
			}
		}
		return tempWrapper.value;
	}

	private final Object sharpSystemLock = new Object();
	private FinalWrapper<SharpSystem> sharpSystem;
	public SharpSystem getSharpSystem() throws IOException {
		FinalWrapper<SharpSystem> tempWrapper = sharpSystem;
		if (tempWrapper == null) {
			synchronized(sharpSystemLock) {
				if (sharpSystem == null) {
					sharpSystem = new FinalWrapper<SharpSystem>(new SharpSystem(this));
				}
				tempWrapper = sharpSystem;
			}
		}
		return tempWrapper.value;
	}

	/**
	 * After close, the object can not be used any more.
	 */
	public synchronized void close() throws IOException {
		lazyLoadChunks = null;

		entryCache = null;
		resources = null;
		contentsSiteMapName = null;
		indexSiteMapName = null;
		contentsSiteMap = null;
		indexSiteMap = null;

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
				public int read(byte[] b, int off, int len) throws IOException, DataFormatException {

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

	class ListingEntry {

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

			SharpSystem sharpSystem = getSharpSystem();
			for (SharpSystem.Entry entry : sharpSystem.getEntries()) {
				if (entry.getCode() == 4) {
					int lang3 = intFromBytes(entry.getData());
					values.put("lang3", lang3);
					values.put("lang3_locale", WindowsLanguageID.getLocale(lang3));
					log.info(String.format("lang3 0x%04X %1s", lang3, WindowsLanguageID.getLocale(lang3)));
					break;
				}
			}

			byte[] buf = new byte[256];
			LEInputStream in = new LEInputStream(getResourceAsStream("/$FIftiMain"));
			in.read(buf, 0, 0x7a);
			int codepage4 = in.read32();
			log.info(String.format("codepage4 %05d", codepage4));
			int lang4 = in.read32();
			log.info(String.format("lang4 0x%04X %1s", lang4, WindowsLanguageID.getLocale(lang4)));

		} catch(Exception ex) {
			log.throwing("CHMFile", "getLangs", ex);
		}
		return values;
	}

	private int intFromBytes(byte[] bytes) {
		int value= 0;
		for (int i = 0; i < 4; i++) {
			int shift = i * 8;
			value += (bytes[i] & 0x000000FF) << shift;
		}
		return value;
	}

	public static void main(String[]argv) throws Exception {
		if (argv.length == 0) {
			System.err.println("usage: java " + CHMFile.class.getName() + " <chm file name> (file)*");
			System.exit(1);
		}

		CHMFile chm = new CHMFile(argv[0]);
		if (argv.length == 1) {
			for (String file: chm.list() ){
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
