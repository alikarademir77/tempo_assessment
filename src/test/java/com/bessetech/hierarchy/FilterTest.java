package com.bessetech.hierarchy;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class FilterTest {

  @Test
  void removeMultipleParentsWithDescendants() {
    // Filters a forest containing multiple roots and nested descendants.
    // Nodes divisible by 3 are removed, along with any descendants below them.
    Hierarchy unfiltered = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
            new int[]{0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2}
    );

    Hierarchy filteredActual =
            HierarchyFilters.filter(unfiltered, nodeId -> nodeId % 3 != 0);

    Hierarchy filteredExpected = new ArrayBasedHierarchy(
            new int[]{1, 2, 5, 8, 10, 11},
            new int[]{0, 1, 1, 0, 1, 2}
    );

    assertEquals(filteredExpected.formatString(), filteredActual.formatString());
  }

  @Test
  void removesDescendantsWhenParentFails() {
    // Removes node 2 and its descendant node 3.
    // Node 4 stays because it is node 2's sibling, not its descendant.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5},
            new int[]{0, 1, 2, 1, 0}
    );

    Hierarchy filteredActual = HierarchyFilters.filter(hierarchy, id -> id != 2);

    Hierarchy filteredExpected = new ArrayBasedHierarchy(
            new int[]{1, 4, 5},
            new int[]{0, 1, 0}
    );
    assertEquals(filteredExpected.formatString(), filteredActual.formatString());
  }

  @Test
  void returnsEmptyHierarchyWhenRootFails() {
    // Removes the only root and therefore removes its complete subtree.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3},
            new int[]{0, 1, 2}
    );

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> false);

    assertEquals("[]", filtered.formatString());
    assertEquals(0, filtered.size());
  }

  // Added test
  @Test
  void keepsAllNodesWhenEveryNodePasses() {
    // Confirms filtering preserves the complete hierarchy when every node passes.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5},
            new int[]{0, 1, 2, 1, 0}
    );

    Hierarchy filteredActual = HierarchyFilters.filter(hierarchy, id -> true);

    Hierarchy filteredExpected = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5},
            new int[]{0, 1, 2, 1, 0}
    );
    assertEquals("[1:0, 2:1, 3:2, 4:1, 5:0]", filteredActual.formatString());
    assertEquals(filteredExpected.formatString(), filteredActual.formatString());
 }

  // Added test
  @Test
  void returnsEmptyHierarchyForEmptyInput() {
    // Confirms an empty hierarchy can be filtered without errors
    // and remains empty regardless of the predicate.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{},
            new int[]{}
    );

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> true);

    assertEquals("[]", filtered.formatString());
    assertEquals(0, filtered.size());
  }

  // Added test
  @Test
  void keepsSingleRootWhenItPasses() {
    // Confirms a one-node hierarchy is retained when its root passes the predicate.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{42},
            new int[]{0}
    );

    Hierarchy filteredActual = HierarchyFilters.filter(hierarchy, id -> id == 42);

    assertEquals("[42:0]", filteredActual.formatString());
    assertEquals(1, filteredActual.size());
  }

  // Added test
  @Test
  void removesSingleRootWhenItFails() {
    // Confirms a one-node hierarchy becomes empty when its only root fails.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{42},
            new int[]{0}
    );

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> id != 42);

    assertEquals("[]", filtered.formatString());
    assertEquals(0, filtered.size());
  }

  // Added test
  @Test
  void keepsLaterRootWhenEarlierRootFails() {
    // Removing one root and its subtree must not affect a later,
    // independent tree in the same forest.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5},
            new int[]{0, 1, 0, 1, 2}
    );

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> id != 1);

    assertEquals("[3:0, 4:1, 5:2]", filtered.formatString());
  }

  // Added test
  @Test
  void keepsSiblingWhenPreviousSiblingFails() {
    // Removing node 2 must not remove nodes 3 and 4,
    // because they are siblings with the same included parent, node 1.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4},
            new int[]{0, 1, 1, 1}
    );

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> id != 2);

    assertEquals("[1:0, 3:1, 4:1]", filtered.formatString());
  }

  // Added test
  @Test
  void removesEntireDeepSubtreeWhenIntermediateAncestorFails() {
    // Removing node 2 removes every node below it, even when descendants
    // are several levels deeper. The separate root node 7 and sibling 6 remains.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5, 6, 7},
            new int[]{0, 1, 2, 3, 2, 1, 0}
    );

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> id != 2);

    assertEquals("[1:0, 6:1, 7:0]", filtered.formatString());
  }

  // Added test
  @Test
  void doesNotKeepDescendantThatPassesWhenAncestorFails() {
    // Node 3 passes the predicate, but it is still removed because
    // its parent, node 2, fails. A retained node needs every ancestor to pass.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3},
            new int[]{0, 1, 2}
    );

    Hierarchy filtered = HierarchyFilters.filter(
            hierarchy,
            id -> id == 1 || id == 3
    );

    assertEquals("[1:0]", filtered.formatString());
  }

  // Added test
  @Test
  void preservesOriginalDepthsForRetainedNodes() {
    // Confirms retained nodes keep their original depths.
    // Filtering removes nodes; it does not rebuild or flatten the hierarchy.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4, 5, 6},
            new int[]{0, 1, 2, 1, 0, 1}
    );

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> id != 3);

    assertEquals("[1:0, 2:1, 4:1, 5:0, 6:1]", filtered.formatString());
  }

  // Added test
  @Test
  void evaluatesPredicateForEveryInputNode() {
    // Confirms the current implementation does not call the predicate for descendents of failed nodes,
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 5},
            new int[]{0, 1, 2, 0}
    );
    AtomicInteger predicateCalls = new AtomicInteger();

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> {
      predicateCalls.incrementAndGet();
      return id != 1;
    });

    assertEquals(2, predicateCalls.get());
    assertEquals("[5:0]", filtered.formatString());
  }

  // Added test
  @Test
  void doesNotModifyOriginalHierarchy() {
    // Confirms filter creates a new result and leaves the source hierarchy unchanged.
    Hierarchy hierarchy = new ArrayBasedHierarchy(
            new int[]{1, 2, 3, 4},
            new int[]{0, 1, 2, 0}
    );

    Hierarchy filtered = HierarchyFilters.filter(hierarchy, id -> id != 2);

    assertEquals("[1:0, 2:1, 3:2, 4:0]", hierarchy.formatString());
    assertEquals("[1:0, 4:0]", filtered.formatString());
  }
}