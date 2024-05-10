package org.magic.draft.util;

import java.io.Serializable;
import java.util.Objects;

public class Pair<K, V> implements Serializable {

    private final K item1;
    private final V item2;
    
    public Pair(final K item1, final V item2) {
        this.item1 = Objects.requireNonNull(item1, "First Item Required for Pair");
        this.item2 = Objects.requireNonNull(item2, "Second Item Required for Pair");
    }

    public K getItem1() {
        return item1;
    }

    public V getItem2() {
        return item2;
    }

    @Override
    public String toString() {
        return "<" + item1.toString() + "," + item2.toString() + ">";
    }
}