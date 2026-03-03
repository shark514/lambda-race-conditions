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

**Metric**: Collision rate = `(lost + exceptions) / total hits × 100`

50 concurrent threads, list of 10 elements initialized to 0.

| Scenario               | 100 hits | 1,000 hits | 10,000 hits | 100,000 hits |
|------------------------|----------|------------|-------------|--------------|
| BaselineForLoop        | 0.00%    | 0.10%      | 0.03%       | **0.16%**    |
| ReplaceAllUnsafe       | 1.00%    | 1.10%      | 0.27%       | **25.62%**   |
| FinalReplaceAll        | 1.00%    | 0.20%      | 0.24%       | **14.66%**   |
| DefensiveCopy          | 0.00%    | 0.00%      | 0.24%       | **2.10%**    |
| DefensiveCopyReturn    | 0.00%    | 0.00%      | 0.00%       | **0.00%**    |
| SynchronizedList       | 0.00%    | 0.00%      | 0.00%       | **0.00%**    |
| CopyOnWriteArrayList   | 0.00%    | 0.00%      | 0.00%       | **0.00%**    |

### Detailed results per scenario

#### BaselineForLoop
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 100     | 0    | 0          | 100         | 100      | 0               |
| 1,000   | 999     | 1    | 0          | 950         | 1,000    | 50              |
| 10,000  | 9,997   | 3    | 0          | 8,965       | 10,000   | 1,035           |
| 100,000 | 99,843  | 157  | 0          | 55,945      | 100,000  | **44,055**      |

#### ReplaceAllUnsafe
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 99      | 0    | 1          | 100         | 100      | 0               |
| 1,000   | 989     | 0    | 11         | 993         | 1,000    | 7               |
| 10,000  | 9,973   | 0    | 27         | 9,991       | 10,000   | 9               |
| 100,000 | 74,377  | 180  | 25,443     | 89,259      | 100,000  | **10,741**      |

#### FinalReplaceAll
| Hits    | OK      | Lost | Exceptions | Final value | Expected | Lost increments |
|---------|---------|------|------------|-------------|----------|-----------------|
| 100     | 99      | 0    | 1          | 100         | 100      | 0               |
| 1,000   | 998     | 0    | 2          | 999         | 1,000    | 1               |
| 10,000  | 9,976   | 0    | 24         | 9,985       | 10,000   | 15              |
| 100,000 | 85,337  | 49   | 14,614     | 93,944      | 100,000  | **6,056**       |

#### DefensiveCopy
| Hits    | OK      | Lost  | Exceptions | Final value | Expected | Lost increments |
|---------|---------|-------|------------|-------------|----------|-----------------|
| 100     | 100     | 0     | 0          | 86          | 100      | 14              |
| 1,000   | 1,000   | 0     | 0          | 911         | 1,000    | 89              |
| 10,000  | 9,976   | 24    | 0          | 8,330       | 10,000   | 1,670           |
| 100,000 | 97,905  | 2,095 | 0          | 43,212      | 100,000  | **56,788**      |

#### DefensiveCopyReturn
| Hits    | OK      | Lost | Exceptions | Loss Rate |
|---------|---------|------|------------|-----------|
| 100     | 100     | 0    | 0          | 0.00%     |
| 1,000   | 1,000   | 0    | 0          | 0.00%     |
| 10,000  | 10,000  | 0    | 0          | 0.00%     |
| 100,000 | 100,000 | 0    | 0          | **0.00%** |

0% loss. Every returned copy is correct — each element equals original + 1. Zero collisions, zero exceptions, zero corruption. Each thread works on its own copy and returns it; threads never interfere with each other because they never touch the shared state.

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

- **BaselineForLoop**: Low collision rate (0.16%) but **catastrophic silent data loss** (44,055 lost increments at 100K). The `get` + `set` is not atomic — threads overwrite each other without knowing.
- **ReplaceAllUnsafe**: **25.62%** collision rate at 100K. Generates `ConcurrentModificationException` because `replaceAll` checks `modCount`.
- **FinalReplaceAll**: **14.66%** collision rate at 100K. `final` doesn't help — the reference is frozen, **not the content**.
- **DefensiveCopy**: Low collision rate (2.10%) but **catastrophic silent data loss** (56,788 lost increments at 100K). Each thread copies, transforms, then overwrites the original — other threads' work disappears silently.
- **DefensiveCopyReturn**: **0% loss** — every returned copy is correct. Each thread copies, increments, returns. No thread touches the shared state, so there's nothing to collide on. Total isolation = zero collisions.
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
| for loop | Unsafe | 0.16% collision, 44% silent loss | Non-atomic read-modify-write |
| replaceAll | Unsafe | 25.62% collision | ConcurrentModificationException + losses |
| final + replaceAll | Unsafe | 14.66% collision | `final` = immutable reference, not content |
| Defensive copy (write-back) | Unsafe | 2.10% collision, 57% silent loss | Copy-back overwrites other threads' work |
| Defensive copy (return) | Safe | 0.00% collision | Total isolation — every copy correct |
| synchronizedList | Safe | 0.00% collision | Explicit lock = atomicity |
| CopyOnWriteArrayList | Safe | 0.00% collision | Internal locking |

**Golden rule**: If multiple threads modify a collection, you need either an explicit lock (`synchronized`) or a concurrent data structure (`CopyOnWriteArrayList`, `ConcurrentHashMap`). Neither `final` nor defensive copies are sufficient. Defensive copy with write-back has a low collision rate but the **worst silent data loss** (57% at 100K). Defensive copy with return gives **0% collision** (total isolation), but never mutates shared state. Pick your poison — or just synchronize.

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
