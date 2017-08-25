/**
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com) All Right Reserved
 * File     : Handler.java
 * Created	: 2007-3-1
 * ****************************************************************************
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA  02111-1307, USA.
 * *****************************************************************************
 */
package cn.rui.chm.protocol.chm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import cn.rui.chm.CHMFile;

/**
 * Handling CHM URL stream
 */
public class Handler extends URLStreamHandler {
	
	static {
		String pkgs = System.getProperty("java.protocol.handler.pkgs", "cn.rui.chm.protocol");
		if (pkgs.indexOf("cn.rui") < 0) pkgs += "|cn.rui";
		System.setProperty("java.protocol.handler.pkgs", pkgs);
	}

	private static Logger log = Logger.getLogger(Handler.class.getName());
	
	private static Map<String, CHMFile> files = new TreeMap<String, CHMFile>();
	
	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		return new URLConnection(u) {
			
			@Override
			public InputStream getInputStream() throws IOException {
				CHMFile chm = files.get(url.getHost());
				if (chm == null)
					throw new IOException("CHMFile not opened");
				return chm.getResourceAsStream(url.getFile());
			}

			@Override
			public String getContentType() {
				String file = url.getFile();
				int i = file.lastIndexOf('.');
				if (i >= 0) {
					String ext = file.substring(i).toLowerCase();
					if (".gif".equals(ext)) 
						return "image/gif";
					else if (".jpeg".equals(ext) || ".jpg".equals(ext))
						return "image/jpeg";
					else if (".png".equals(ext))
						return "image/png";
				}
				// TODO this should be determined by the file extension
				
				return "text/html";
			}

			@Override
			public String getContentEncoding() {
				return null;
			}

			@Override
			public void connect() throws IOException {
				log.info("Open " + url.toExternalForm());
			}
			
		};
	}
	public static URL open(String filepath) throws IOException {
		byte[]bytes = filepath.getBytes();
		StringBuffer buf = new StringBuffer("chm://");
		for (int i = 0; i < bytes.length; i ++) {
			String s = "0" + Integer.toHexString(bytes[i] & 0xff);
			buf.append(s.substring(s.length() - 2));
		}
		URL url = new URL(buf.toString());
		synchronized (files) {
			if (! files.containsKey(url.getHost())) {
				files.put(url.getHost(), new CHMFile(filepath));
			}
		}
		return url;
	}
	
	public static void close(URL url) throws IOException {
		synchronized (files) {
			CHMFile chm = files.get(url.getHost());
			if (chm != null) {
				files.remove(url.getHost());
				chm.close();
			}
		}
	}

}
