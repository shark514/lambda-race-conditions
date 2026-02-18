# ❌ UNSAFE Tests — What Breaks

These tests prove that `ArrayList` is **NOT thread-safe**, even with "modern" Java 8+ lambdas.

## Scenarios

| Test | Operation | Why it breaks |
|------|-----------|---------------|
| `ReplaceAllUnsafeTest` | `replaceAll(x -> x + 1)` | Concurrent iteration + modification on all elements |
| `ForEachAddUnsafeTest` | `forEach()` + `add()` | Modification during iteration → ConcurrentModificationException |
| `RemoveIfUnsafeTest` | `removeIf()` + `addAll()` | Concurrent restructuring of the internal array |
| `SortUnsafeTest` | `sort()` + `set()` | Concurrent sort = silent corruption |
| `FinalStillUnsafeTest` | `final ArrayList` + `replaceAll()` | `final` ≠ immutable ≠ thread-safe |
| `StreamSharedSourceUnsafeTest` | `stream().map().collect()` + mutation | Stream reads the source directly, not a copy |

## How to run

All unsafe tests:
```bash
mvn test -pl . -Dtest="com.epns.lambda.unsafe.*"
```

A specific test:
```bash
mvn test -Dtest=ReplaceAllUnsafeTest
```

With a specific load level:
```bash
mvn test -Dtest=ReplaceAllUnsafeTest -Dhits=1000000
```

## What we observe

- **At 100 hits**: some tests already show collisions (replaceAll: 100%), others appear safe (removeIf: 0%)
- **At 1M hits**: ALL unsafe tests show significant collisions
- **Lesson**: a standard unit test (a few calls) won't detect race conditions. You need load.

## Types of errors detected

1. **Exceptions**: `ConcurrentModificationException`, `ArrayIndexOutOfBoundsException`, `NullPointerException`
2. **Corruptions**: inconsistent data without exceptions (the worst case — silent in production)
