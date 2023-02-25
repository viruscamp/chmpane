package cn.rui.chm;

import lombok.experimental.UtilityClass;
import java.util.concurrent.atomic.AtomicReference;

@UtilityClass
public  class Utils {
    public interface Supplier<T> {
        T get();
    }

    // @see https://www.projectlombok.org/features/GetterLazy
    public <T> T lazyGet(AtomicReference<Object> cached, Supplier<T> supplier) {
        Object value = cached.get();
        if (value == null) {
            synchronized(cached) {
                value = cached.get();
                if (value == null) {
                    final T actualValue = supplier.get();
                    value = (actualValue == null) ? cached : actualValue;
                    cached.set(value);
                }
            }
        }
        return (T)(value == cached ? null : value);
    }

    public interface SupplierWithException<T, EX extends Throwable> {
        T get() throws EX;
    }

    // @see https://www.projectlombok.org/features/GetterLazy
    public <T, EX extends Throwable> T lazyGet(AtomicReference<Object> cached, SupplierWithException<T, EX> supplier)
            throws EX {
        Object value = cached.get();
        if (value == null) {
            synchronized(cached) {
                value = cached.get();
                if (value == null) {
                    final T actualValue = supplier.get();
                    value = actualValue == null ? cached : actualValue;
                    cached.set(value);
                }
            }
        }
        return (T)(value == cached ? null : value);
    }

    public static final long WINDOWS_TICK = 10000L;  //10的7次方
    public static final long SEC_TO_UNIX_EPOCH = 11644473600000L; //1601与1970的时间间隔 毫秒

    java.util.Date windowsTickToDate(long windowsTicks)
    {
        long mills = (windowsTicks / WINDOWS_TICK - SEC_TO_UNIX_EPOCH);
        return new java.util.Date(mills);
    }
}
