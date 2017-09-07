package cn.rui.chm;

/**
 * Correct double-checked locking , using final field wrapper instead of volatile
 * @see https://en.wikipedia.org/wiki/Double-checked_locking#Usage_in_Java
 * @param <T>
 */
public final class FinalWrapper<T> {
    public final T value;
    public FinalWrapper(T value) {
        this.value = value;
    }

    /*
    private FinalWrapper<Helper> helperWrapper;
    public Helper getHelper() {
        FinalWrapper<Helper> tempWrapper = helperWrapper;
        if (tempWrapper == null) {
            synchronized(this) {
                if (helperWrapper == null) {
                    helperWrapper = new FinalWrapper<Helper>(new Helper());
                }
                tempWrapper = helperWrapper;
            }
        }
        return tempWrapper.value;
    }
    */
}
