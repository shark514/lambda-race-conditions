# Lambda Race Conditions — Demonstration

This project demonstrates **race conditions** that occur when multiple threads manipulate an `ArrayList` in parallel. 7 approaches are compared: classic for loop, `replaceAll` with lambda, `final` variable, defensive copy, **defensive copy with return**, `synchronizedList`, and `CopyOnWriteArrayList`.

Each scenario does exactly the same thing: **50 threads simultaneously increment each element of a list**. The only difference = the method used. At the end, we compare the obtained value vs the expected value. The difference = increments lost due to collisions.

## Running the tests

```bash
# All scenarios (default: 100, 1K, 10K, 100K hits)
mvn test

# Individual scenario
mvn test -Dtest=com.epns.lambda.scenarios.BaselineForLoopTest
mvn test -Dtest=com.epns.lambda.scenarios.ReplaceAllUnsafeTest
mvn test -Dtest=com.epns.lambda.scenarios.FinalReplaceAllTest
mvn test -Dtest=com.epns.lambda.scenarios.DefensiveCopyTest
mvn test -Dtest=com.epns.lambda.scenarios.DefensiveCopyReturnTest
mvn test -Dtest=com.epns.lambda.scenarios.SynchronizedListTest
mvn test -Dtest=com.epns.lambda.scenarios.CopyOnWriteArrayListTest
```

### Custom hit count

Use `-Dhits=N` to run a single level with a specific number of hits:

```bash
# 1 million hits on a single scenario
mvn test -Dtest=com.epns.lambda.scenarios.BaselineForLoopTest -Dhits=1000000

# 5 million hits
mvn test -Dtest=com.epns.lambda.scenarios.ReplaceAllUnsafeTest -Dhits=5000000
```

**IntelliJ IDEA**: Run → Edit Configurations → select your test → add `-Dhits=1000000` in **VM Options**.

Without `-Dhits`, all 4 default levels run sequentially (100 → 1,000 → 10,000 → 100,000).

## Consolidated Results

**Metric**: Loss rate = `(expected - obtained) / expected × 100`

50 concurrent threads, list of 10 elements initialized to 0.

| Scenario               | 100 hits | 1,000 hits | 10,000 hits | 100,000 hits |
|------------------------|----------|------------|-------------|--------------|
| BaselineForLoop        | 1.00%    | 1.80%      | 20.60%      | **53.83%**   |
| ReplaceAllUnsafe       | 11.00%   | 1.00%      | 2.69%       | **34.37%**   |
| FinalReplaceAll        | 0.00%    | 0.40%      | 1.96%       | **14.83%**   |
| DefensiveCopy          | 44.00%   | 30.20%     | 56.21%      | **64.35%**   |
| SynchronizedList       | 0.00%    | 0.00%      | 0.00%       | **0.00%**    |
| CopyOnWriteArrayList   | 0.00%    | 0.00%      | 0.00%       | **0.00%**    |

> 🧊 **DefensiveCopyReturn** is intentionally excluded from this table. It doesn't compete — it proves a different point: true defensive copy **never modifies shared state**. Zero collisions, zero corruption, zero shared mutation. See [scenario 5](#5-defensivecopyreturn---true-defensive-copy-return-dont-write-back) for details.

### Detailed results per scenario

#### BaselineForLoop
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 100         | 100      | 0               |
| 1,000   | 1,000   | 0    | 0          | 931         | 1,000    | 69              |
| 10,000  | 9,999   | 1    | 0          | 9,262       | 10,000   | 738             |
| 100,000 | 99,823  | 177  | 0          | 92,807      | 100,000  | **7,193**       |

#### ReplaceAllUnsafe
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 97      | 0    | 3          | 99          | 100      | 1               |
| 1,000   | 987     | 0    | 13         | 993         | 1,000    | 7               |
| 10,000  | 9,987   | 0    | 13         | 9,994       | 10,000   | 6               |
| 100,000 | 90,956  | 137  | 8,907      | 88,132      | 100,000  | **11,868**      |

#### FinalReplaceAll
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 98      | 0    | 2          | 98          | 100      | 2               |
| 1,000   | 991     | 0    | 9          | 978         | 1,000    | 22              |
| 10,000  | 9,983   | 0    | 17         | 9,987       | 10,000   | 13              |
| 100,000 | 87,987  | 87   | 11,926     | 84,781      | 100,000  | **15,219**      |

#### DefensiveCopy
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 80          | 100      | 20              |
| 1,000   | 999     | 1    | 0          | 965         | 1,000    | 35              |
| 10,000  | 9,982   | 18   | 0          | 9,121       | 10,000   | 879             |
| 100,000 | 99,432  | 568  | 0          | 67,807      | 100,000  | **32,193**      |

#### DefensiveCopyReturn 🧊 *(not a loss — by design)*

This scenario is **not comparable** to the others. It doesn't measure collision loss — it demonstrates that true defensive copy **never touches shared state**.

Each thread: copies → increments → **returns the copy**. The shared list stays at 0. That's not a bug, that's the point.

- ✅ Zero collisions
- ✅ Zero corruption
- ✅ Zero `ConcurrentModificationException`
- ❌ Zero shared mutation (by design)

**Conclusion**: If you need shared mutation, defensive copy is the wrong pattern. If you don't need shared mutation, defensive copy is perfect — but then you don't have a concurrency problem in the first place.

#### SynchronizedList ✅
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 100         | 100      | 0               |
| 1,000   | 1,000   | 0    | 0          | 1,000       | 1,000    | 0               |
| 10,000  | 10,000  | 0    | 0          | 10,000      | 10,000   | 0               |
| 100,000 | 100,000 | 0    | 0          | 100,000     | 100,000  | **0**           |

#### CopyOnWriteArrayList ✅
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 100         | 100      | 0               |
| 1,000   | 1,000   | 0    | 0          | 1,000       | 1,000    | 0               |
| 10,000  | 10,000  | 0    | 0          | 10,000      | 10,000   | 0               |
| 100,000 | 100,000 | 0    | 0          | 100,000     | 100,000  | **0**           |

> **Note**: Results vary depending on CPU speed. Faster machines (e.g., gaming laptops with RTX 4060) show *higher* loss rates because threads collide more frequently. Run the tests yourself to see your own numbers.

### What the results show

- **BaselineForLoop**: The classic `for` loop loses >50% of increments at 100K hits. The read-modify-write (`get` → `+1` → `set`) is not atomic.
- **ReplaceAllUnsafe**: The `replaceAll` lambda loses ~34% + generates `ConcurrentModificationException`. The worst of both worlds.
- **FinalReplaceAll**: `final` *apparently* reduces losses (~15% vs ~34%) but this is a false sense of security. The reference is frozen, **not the content**.
- **DefensiveCopy**: The WORST *unsafe* approach (~64% losses!). Each thread copies the list, transforms it, then overwrites the original — but meanwhile, other threads have also overwritten it. Cascading lost updates. The copy-back (`Collections.copy`) is the bug.
- **DefensiveCopyReturn** 🧊: The "correct" defensive copy — copy, transform, **return without writing back**. Result: 100% loss on shared state because nobody writes. Proves that true defensive copy = **total isolation**. If you need shared mutation, defensive copy is simply the wrong pattern.
- **SynchronizedList**: **0% losses**. The `synchronized(list)` block guarantees atomicity of each operation.
- **CopyOnWriteArrayList**: **0% losses**. Internal locking protects each mutation.

## The 6 Scenarios

### 1. BaselineForLoop — Classic `for` loop (no lambda)
```java
for (int i = 0; i < list.size(); i++) {
    list.set(i, list.get(i) + 1);
}
```
No lambda. Demonstrates that the problem is fundamentally about **concurrency**, not lambdas themselves. Separate `get` + `set` = non-atomic read-modify-write.

### 2. ReplaceAllUnsafe — `replaceAll` with lambda
```java
list.replaceAll(x -> x + 1);
```
Lambda directly on a shared `ArrayList`. Generates `ConcurrentModificationException` because `replaceAll` checks the internal `modCount`.

### 3. FinalReplaceAll — `final` + `replaceAll`
```java
final ArrayList<Integer> list = new ArrayList<>();
list.replaceAll(x -> x + 1);
```
The `final` keyword prevents reassigning the `list` variable. But it provides **absolutely no protection** for the list's content. The race conditions are the same.

### 4. DefensiveCopy — Defensive copy
```java
ArrayList<Integer> copy = new ArrayList<>(list);
copy.replaceAll(x -> x + 1);
Collections.copy(list, copy);
```
Each thread works on its own copy — no exception. But when it overwrites the original list with its copy, it also overwrites the modifications from other threads. Result: **the worst loss rate of all scenarios**.

### 5. DefensiveCopyReturn 🧊 — True defensive copy (return, don't write back)
```java
ArrayList<Integer> copy = new ArrayList<>(list);
copy.replaceAll(x -> x + 1);
return copy; // never touch the original
```
This is what defensive copy **actually means**: work on your own copy, return it, never modify the shared state. The shared list stays at 0 forever — **100% "loss"** from the shared perspective, but **zero corruption**. This exposes the fundamental contradiction: if you need to mutate shared state, defensive copy is the wrong tool.

### 6. SynchronizedList — `synchronized` + `Collections.synchronizedList()`
```java
List<Integer> list = Collections.synchronizedList(new ArrayList<>());
synchronized (list) {
    list.replaceAll(x -> x + 1);
}
```
The `synchronized` lock makes each `replaceAll` atomic. **Zero loss**, but threads wait their turn (contention).

### 7. CopyOnWriteArrayList
```java
CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
list.replaceAll(x -> x + 1);
```
Each write creates an internal copy protected by a `ReentrantLock`. **Zero loss**. Slower for writes, but perfect for read-heavy cases.

## Conclusion

| Approach | Safe? | Loss rate @ 100K | Why |
|----------|-------|-------------------|-----|
| for loop | ❌ | 53.83% | Non-atomic read-modify-write |
| replaceAll | ❌ | 34.37% | ConcurrentModificationException + losses |
| final + replaceAll | ❌ | 14.83% | `final` = immutable reference, not content |
| Defensive copy | ❌ | 64.35% | Copy-back overwrites other threads' work |
| Defensive copy return | 🧊 | N/A | Correct isolation — no shared mutation by design |
| synchronizedList | ✅ | 0.00% | Explicit lock = atomicity |
| CopyOnWriteArrayList | ✅ | 0.00% | Internal locking |

**Golden rule**: If multiple threads modify a collection, you need either an explicit lock (`synchronized`) or a concurrent data structure (`CopyOnWriteArrayList`, `ConcurrentHashMap`). Neither `final` nor defensive copies are sufficient. Defensive copy with write-back is the **worst approach** (64% loss). Defensive copy with return is **correct but useless** for shared mutation (100% loss). Pick your poison — or just synchronize.

## Project Structure

```
src/test/java/com/epns/lambda/
├── scenarios/          ← The 7 comparative scenarios
│   ├── ScenarioRunner.java
│   ├── BaselineForLoopTest.java
│   ├── ReplaceAllUnsafeTest.java
│   ├── FinalReplaceAllTest.java
│   ├── DefensiveCopyTest.java
│   ├── DefensiveCopyReturnTest.java
│   ├── SynchronizedListTest.java
│   └── CopyOnWriteArrayListTest.java
├── unsafe/             ← Detailed unit tests (anomalies per hit)
└── safe/               ← Thread-safe unit tests
```

## Prerequisites

- Java 21+
- Maven 3.8+

## Full Article

See [`docs/article.md`](docs/article.md) for the in-depth analysis with production implications and the link to DDoS attacks.
