package cn.rui.chm;

/**
 * java.io.ByteArrayInputStream.skip does not support negative
 * so we extends it just to support skip negative
 */
public class ByteArrayInputStream extends java.io.ByteArrayInputStream {
    public ByteArrayInputStream(byte[] buf) {
        super(buf);
    }

    public ByteArrayInputStream(byte[] buf, int offset, int length) {
        super(buf, offset, length);
    }

    /**
     * Skips <code>n</code> (n could be < 0) bytes of input from this input stream. Fewer
     * bytes might be skipped if the end of the input stream is reached.
     * The actual number <code>k</code>
     * of bytes to be skipped is equal to the smaller
     * of <code>n</code> and  <code>count-pos</code>.
     * The value <code>k</code> is added into <code>pos</code>
     * and <code>k</code> is returned.
     *
     * @param   n   the number of bytes to be skipped, could be negative
     * @return  the actual number of bytes skipped.
     */
    @Override
    public synchronized long skip(long n) {
        long k = count - pos;
        if (n <= -pos) {
            k = -pos;
        } else if (n < k) {
            k = n;
        }
        pos += k;
        return k;
    }
}
