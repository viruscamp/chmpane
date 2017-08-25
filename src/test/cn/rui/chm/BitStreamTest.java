package cn.rui.chm;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import junit.framework.TestCase;

public class BitStreamTest extends TestCase {

	public void testEnsure() throws DataFormatException, IOException {
		byte[]b = new byte[]{
			(byte) 0xf1, (byte) 0x1f, (byte) 0x56, (byte) 0x78, (byte) 0x9a
		};
		BitsInputStream bs = new BitsInputStream(new ByteArrayInputStream(b));
		assertEquals("00000000 00000000 00000000 00000000", bs.toString());
		
		bs.ensure(1);
		assertEquals("00011111 11110001 00000000 00000000", bs.toString());
		bs.ensure(16);
		assertEquals("00011111 11110001 00000000 00000000", bs.toString());
		
		bs.ensure(17);
		assertEquals("00011111 11110001 01111000 01010110", bs.toString());
		bs.ensure(32);
		assertEquals("00011111 11110001 01111000 01010110", bs.toString());
	}
	
	public void testReadLE() throws DataFormatException, IOException {
		byte[]b = new byte[]{
				(byte) 0xf1, (byte) 0x1f, (byte) 0x56, (byte) 0x78, 
				(byte) 0x9a, (byte) 0xbc, (byte) 0xde, (byte) 0x3f,
				(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe,
				(byte) 0x1f, (byte) 0x2e, (byte) 0x3d, (byte) 0x4c,
				(byte) 0x5b, (byte) 0x6a,
			};
			BitsInputStream bs = new BitsInputStream(new ByteArrayInputStream(b));
			
			assertEquals(0x00, bs.readLE(1));
			assertEquals(0x00, bs.readLE(2));
			assertEquals(0x07, bs.readLE(3));
			assertEquals(0x0f, bs.readLE(4));
			assertEquals(0x18, bs.readLE(5));
			assertEquals(0x2f, bs.readLE(6));
			assertEquals(0x05, bs.readLE(7));			
			assertEquals(0x6b, bs.readLE(8));
			assertEquals(0x193, bs.readLE(9));
			assertEquals(0x11f, bs.readLE(10));
			assertEquals(0x77b, bs.readLE(11));
			assertEquals(0xfb2, bs.readLE(12));
			assertEquals(0x15f5, bs.readLE(13));
			assertEquals(0x345c, bs.readLE(14));
			assertEquals(0x1f4c, bs.readLE(15));
			assertEquals(0x3d6a, bs.readLE(16));
	}
}
