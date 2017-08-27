/**
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com) All Right Reserved
 * File     : Handler.java
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
package cn.rui.chm.protocol.chm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import cn.rui.chm.CHMFile;

/**
 * Handling CHM URL stream
 */
public class Handler extends URLStreamHandler {
	
	static {
		//String pkgs = System.getProperty("java.protocol.handler.pkgs", "cn.rui.chm.protocol");
		//if (pkgs.indexOf("cn.rui") < 0) pkgs += "|cn.rui";
		//System.setProperty("java.protocol.handler.pkgs", pkgs);

		URL.setURLStreamHandlerFactory(new HandlerFactory());
	}

	static class HandlerFactory implements URLStreamHandlerFactory {
		public URLStreamHandler createURLStreamHandler(String protocol) {
			if ("chm".equals(protocol)) {
				return new Handler();
			} else {

			}
			return null;
		}
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
