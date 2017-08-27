package cn.rui.chm;

import java.io.InputStream;

import junit.framework.TestCase;

public class CHMFileTest extends TestCase {

	public void testList() throws Exception {
		CHMFile chm = new CHMFile("E:/LIB/BOOKS/2002,0596004087,2,Cole.CHM");
		for (String file: chm.list()) {
			System.out.println(file);
		}
		chm.close();
	}
	
	public void testSitemap() throws Exception {
		CHMFile chm = new CHMFile("E:/LIB/BOOKS/1999,0130832928,Topley.CHM");
		byte[]buf = new byte[1024];
		InputStream in = chm.getResourceAsStream("");
		int c = 0;
		while ( (c = in.read(buf)) >= 0) {
			System.out.print(new String(buf, 0, c));
		}
		chm.close();
	}
	
	public void testAll() throws Exception {
		CHMFile chm = new CHMFile("E:/LIB/BOOKS/2005,0321334612,McAffer.CHM");
		byte[]buf = new byte[1024];
		long t0 = System.currentTimeMillis();
		for( String filename: chm.list()) {
			InputStream in = chm.getResourceAsStream(filename);
			while ( (in.read(buf)) >= 0) {
				// 
			}
		}
		System.out.print("Time elapsed: " + (System.currentTimeMillis() - t0));
		chm.close();
	}
}
