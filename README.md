# Lambda Race Conditions — Demonstration

This project demonstrates **race conditions** that occur when multiple threads manipulate an `ArrayList` in parallel. 7 approaches are compared: classic for loop, `replaceAll` with lambda, `final` variable, defensive copy (write-back), **defensive copy (return)**, `synchronizedList`, and `CopyOnWriteArrayList`.

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
| BaselineForLoop        | 0.00% | 0.00%  | 0.01%    | **0.14%** |
| ReplaceAllUnsafe       | 5.00% | 0.40%  | 0.16%    | **5.37%** |
| FinalReplaceAll        | 5.00% | 0.30%  | 0.19%    | **6.51%** |
| DefensiveCopy          | 0.00% | 0.20%  | 0.17%    | **1.06%** |
| DefensiveCopyReturn    | 0.00% | 0.00%  | 0.00%    | **0.00%** |
| SynchronizedList       | 0.00% | 0.00%  | 0.00%    | **0.00%** |
| CopyOnWriteArrayList   | 0.00% | 0.00%  | 0.00%    | **0.00%** |

### Detailed results per scenario

#### BaselineForLoop
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 99          | 100      | 1               |
| 1,000   | 1,000   | 0    | 0          | 977         | 1,000    | 23              |
| 10,000  | 9,999   | 1    | 0          | 8,372       | 10,000   | 1,628           |
| 100,000 | 99,856  | 144  | 0          | 87,228      | 100,000  | **12,772**      |

#### ReplaceAllUnsafe
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 95      | 0    | 5          | 99          | 100      | 1               |
| 1,000   | 996     | 0    | 4          | 999         | 1,000    | 1               |
| 10,000  | 9,984   | 0    | 16         | 9,984       | 10,000   | 16              |
| 100,000 | 94,630  | 135  | 5,235      | 92,585      | 100,000  | **7,415**       |

#### FinalReplaceAll
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 95      | 0    | 5          | 96          | 100      | 4               |
| 1,000   | 997     | 0    | 3          | 987         | 1,000    | 13              |
| 10,000  | 9,981   | 0    | 19         | 9,967       | 10,000   | 33              |
| 100,000 | 93,495  | 202  | 6,303      | 95,690      | 100,000  | **4,310**       |

#### DefensiveCopy
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 92          | 100      | 8               |
| 1,000   | 998     | 2    | 0          | 817         | 1,000    | 183             |
| 10,000  | 9,983   | 17   | 0          | 9,299       | 10,000   | 701             |
| 100,000 | 98,939  | 1,061| 0          | 63,416      | 100,000  | **36,584**      |

#### DefensiveCopyReturn
**Metric**: is each RETURNED COPY correct? (each element = original + 1)

| Hits    | OK      | Lost | Exceptions | Error Rate | Shared list[0] |
|---------|---------|------|------------|------------|----------------|
| 100     | 100     | 0    | 0          | 0.00%      | 0              |
| 1,000   | 1,000   | 0    | 0          | 0.00%      | 0              |
| 10,000  | 10,000  | 0    | 0          | 0.00%      | 0              |
| 100,000 | 100,000 | 0    | 0          | 0.00%      | **0**          |

Every returned copy is correct — each element is properly incremented. Zero corruption, zero exceptions. The shared list stays at 0 because no thread writes back. That's not a failure — that's the point: true defensive copy means total isolation from shared state.

#### SynchronizedList
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 100         | 100      | 0               |
| 1,000   | 1,000   | 0    | 0          | 1,000       | 1,000    | 0               |
| 10,000  | 10,000  | 0    | 0          | 10,000      | 10,000   | 0               |
| 100,000 | 100,000 | 0    | 0          | 100,000     | 100,000  | **0**           |

#### CopyOnWriteArrayList
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 100         | 100      | 0               |
| 1,000   | 1,000   | 0    | 0          | 1,000       | 1,000    | 0               |
| 10,000  | 10,000  | 0    | 0          | 10,000      | 10,000   | 0               |
| 100,000 | 100,000 | 0    | 0          | 100,000     | 100,000  | **0**           |

> **Note**: Results vary depending on CPU speed. Faster machines (e.g., gaming laptops with RTX 4060) show *higher* loss rates because threads collide more frequently. Run the tests yourself to see your own numbers.

### What the results show

- **BaselineForLoop**: The classic `for` loop loses >12% of increments at 100K hits. The read-modify-write (`get` → `+1` → `set`) is not atomic.
- **ReplaceAllUnsafe**: The `replaceAll` lambda loses ~5% + generates `ConcurrentModificationException`. The worst of both worlds.
- **FinalReplaceAll**: `final` doesn't help — ~6.5% error rate at 100K. The reference is frozen, **not the content**.
- **DefensiveCopy**: Low error rate (1%) but **massive lost increments** (36,584 at 100K). Each thread copies, transforms, then overwrites the original — but meanwhile, other threads have also overwritten it. The copy-back (`Collections.copy`) is the bug.
- **DefensiveCopyReturn**: **0% error rate** — every returned copy is correct (each element = original + 1). The shared list stays at 0 because no thread writes back. This is what defensive copy *actually means*: total isolation. The copies work perfectly; the shared state is untouched by design.
- **SynchronizedList**: **0% losses**. The `synchronized(list)` block guarantees atomicity of each operation.
- **CopyOnWriteArrayList**: **0% losses**. Internal locking protects each mutation.

## The 7 Scenarios

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

### 4. DefensiveCopy — Defensive copy (write-back)
```java
ArrayList<Integer> copy = new ArrayList<>(list);
copy.replaceAll(x -> x + 1);
Collections.copy(list, copy);
```
Each thread works on its own copy — no exception. But when it overwrites the original list with its copy, it also overwrites the modifications from other threads. Result: **massive lost increments** (36,584 at 100K).

### 5. DefensiveCopyReturn — Defensive copy (return, don't write back)
```java
ArrayList<Integer> copy = new ArrayList<>(list);
copy.replaceAll(x -> x + 1);
return copy; // never touch the original
```
This is what defensive copy **actually means**: work on your own copy, return it, never modify the shared state. The shared list stays at 0 forever — **100% loss**. Each thread's copy is perfect (1 increment), but none of it reaches the shared list. This exposes the fundamental contradiction: if you need to mutate shared state, defensive copy is the wrong tool.

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
| for loop | | 0.14% | Non-atomic read-modify-write |
| replaceAll | | 5.37% | ConcurrentModificationException + losses |
| final + replaceAll | | 6.51% | `final` = immutable reference, not content |
| Defensive copy (write-back) | | 1.06% | Copy-back overwrites other threads' work |
| Defensive copy (return) | -- | 0.00% copies bad | Copies perfect, shared state untouched by design |
| synchronizedList | | 0.00% | Explicit lock = atomicity |
| CopyOnWriteArrayList | | 0.00% | Internal locking |

**Golden rule**: If multiple threads modify a collection, you need either an explicit lock (`synchronized`) or a concurrent data structure (`CopyOnWriteArrayList`, `ConcurrentHashMap`). Neither `final` nor defensive copies are sufficient. Defensive copy with write-back causes **massive silent data loss** (36K lost increments at 100K). Defensive copy with return produces **perfect copies** but never mutates shared state — correct isolation, useless for shared mutation. Pick your poison — or just synchronize.

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
