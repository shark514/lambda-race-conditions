# ✅ SAFE Tests — Solutions That Work

These tests prove that synchronization solutions work: **0% collision at all load levels**.

## Solutions

| Test | Solution | How it works | When to use |
|------|----------|-------------|-------------|
| `CopyOnWriteArrayListTest` | `CopyOnWriteArrayList` | Copies the array on every write | Frequent reads, rare writes |
| `SynchronizedListTest` | `Collections.synchronizedList` | Mutex lock + `synchronized(shared)` | Quick drop-in replacement |
| `SynchronizedBlockTest` | Manual `synchronized` block | Mutual exclusion with dedicated lock | Total control |
| `DefensiveCopyTest` | Defensive copy | Isolated snapshot before stream | Long stream/iteration |

## How to run

All safe tests:
```bash
mvn test -pl . -Dtest="com.epns.lambda.safe.*"
```

A specific test with a load level:
```bash
mvn test -Dtest=CopyOnWriteArrayListTest -Dhits=100000
```

## Which solution to choose?

```
Frequent reads, rare writes?     → CopyOnWriteArrayList
Quick drop-in replacement?       → Collections.synchronizedList + synchronized
Total control?                   → Manual synchronized block
Long stream/iteration?           → Defensive copy
High performance?                → ConcurrentHashMap (not covered here)
```

## ⚠️ Common pitfalls

- `synchronizedList` **alone** is NOT enough for `replaceAll`/`forEach`/`sort` — you need a `synchronized(list)` block around it
- `CopyOnWriteArrayList` is catastrophic if writes are frequent (O(n) copy on each mutation)
- The defensive copy itself must be synchronized (otherwise you copy an inconsistent state)
