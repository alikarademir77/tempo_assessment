# Tempo Assessment

This repository contains a compact Java/Maven assessment focused on two core exercises:

- hierarchy filtering logic for an ordered forest structure
- a cache implementation with a safer, fixed version that addresses common design issues

## Project layout

```text
.
├── pom.xml
├── README.md
├── src/
│   ├── main/java/com/bessetech/
│   │   ├── cache/
│   │   │   ├── SimpleCache.java
│   │   │   └── SimpleCacheFixed.java
│   │   └── hierarchy/
│   │       ├── ArrayBasedHierarchy.java
│   │       ├── Hierarchy.java
│   │       └── HierarchyFilters.java
│   └── test/java/com/bessetech/hierarchy/
│       └── FilterTest.java
├── target/
└── .gitignore
```

## Key packages

### hierarchy

The `com.bessetech.hierarchy` package models a forest using parallel node ID and depth arrays.

- `Hierarchy` defines the hierarchy contract and formatting helpers
- `ArrayBasedHierarchy` is the concrete array-backed implementation
- `HierarchyFilters.filter(...)` keeps nodes only when they pass the predicate and all their ancestors also pass

The behavior is validated in `src/test/java/com/bessetech/hierarchy/FilterTest.java` with edge-case coverage for:

- root removal
- sibling preservation
- descendant pruning
- empty and single-node hierarchies

### cache

The `com.bessetech.cache` package contains two cache implementations:

- `SimpleCache` is the original version with a fixed TTL and limited safety checks
- `SimpleCacheFixed` improves the design with:
  - validation for TTL and capacity
  - monotonic time-based expiration
  - expired-entry cleanup
  - bounded eviction
  - de-duped in-flight loads via `getOrLoad(...)`

## Build and test

This project uses Maven and JUnit 5.

```bash
mvn test
```

## Notes

- Java sources are under `src/main/java`
- Tests are under `src/test/java`
- The project is intentionally small and focused on demonstrating correctness and defensive Java design
