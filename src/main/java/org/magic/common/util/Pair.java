package org.magic.common.util;

import java.io.Serializable;
import java.util.Objects;

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
