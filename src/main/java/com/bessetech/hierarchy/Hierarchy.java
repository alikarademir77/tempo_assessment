package com.bessetech.hierarchy;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A Hierarchy stores an ordered forest using parallel node-ID and depth arrays.
 */
public interface Hierarchy {
    int size();

    int nodeId(int index);

    int depth(int index);

    default String formatString() {
        return IntStream.range(0, size())
                .mapToObj(i -> nodeId(i) + ":" + depth(i))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}

