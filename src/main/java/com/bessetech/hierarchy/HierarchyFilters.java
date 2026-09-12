package com.bessetech.hierarchy;

import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * Utility methods for Hierarchy.
 */
public final class HierarchyFilters {
    private HierarchyFilters() {
    }

    /**
     * A node is included only when it passes the predicate and every ancestor
     * also passes the predicate.
     */
    public static Hierarchy filter(Hierarchy hierarchy, IntPredicate nodeIdPredicate) {
        int[] nodeIds = new int[hierarchy.size()];
        int[] depths = new int[hierarchy.size()];
        boolean[] includedAtDepth = new boolean[hierarchy.size()];

        int filteredSize = 0;

        for (int i = 0; i < hierarchy.size(); i++) {
            int depth = hierarchy.depth(i);

            boolean parentIncluded = depth == 0 || includedAtDepth[depth - 1];
            boolean included = parentIncluded && nodeIdPredicate.test(hierarchy.nodeId(i));

            includedAtDepth[depth] = included;

            if (included) {
                nodeIds[filteredSize] = hierarchy.nodeId(i);
                depths[filteredSize] = depth;
                filteredSize++;
            }
        }

        return new ArrayBasedHierarchy(
                Arrays.copyOf(nodeIds, filteredSize),
                Arrays.copyOf(depths, filteredSize)
        );
    }
}

