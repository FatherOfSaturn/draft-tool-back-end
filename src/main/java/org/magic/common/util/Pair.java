package org.magic.common.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * A generic immutable pair of two values. Both items are required and non-null.
 *
 * @param <K> the type of the first item
 * @param <V> the type of the second item
 */
public record Pair<K, V>(K item1, V item2) implements Serializable {

    public Pair {
        Objects.requireNonNull(item1, "First Item Required for Pair");
        Objects.requireNonNull(item2, "Second Item Required for Pair");
    }

    @Override
    public String toString() {
        return "<" + item1 + "," + item2 + ">";
    }
}
