package cn.family.learning.common;

import java.util.*;

/** Immutable JSON values built using APIs available on Java 8. */
public final class Values {
    private Values() {}

    @SuppressWarnings("unchecked")
    public static <K,V> Map<K,V> map(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("Expected key/value pairs");
        Map<K,V> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            K key = (K) Objects.requireNonNull(entries[i]);
            V value = (V) Objects.requireNonNull(entries[i + 1]);
            if (result.containsKey(key)) throw new IllegalArgumentException("Duplicate key");
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    @SafeVarargs
    public static <T> List<T> list(T... values) {
        List<T> result = new ArrayList<>(values.length);
        for (T value : values) result.add(Objects.requireNonNull(value));
        return Collections.unmodifiableList(result);
    }

    @SafeVarargs
    public static <T> Set<T> set(T... values) {
        Set<T> result = new LinkedHashSet<>();
        for (T value : values) {
            if (!result.add(Objects.requireNonNull(value))) throw new IllegalArgumentException("Duplicate value");
        }
        return Collections.unmodifiableSet(result);
    }
}
