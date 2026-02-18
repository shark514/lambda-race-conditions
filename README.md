# Lambda Race Conditions — Démonstration Massive

## Objectif

Prouver que les opérations lambda sur `ArrayList` ne sont **pas thread-safe**, avec **100,000 hits par test** pour mesurer le taux de collision réel.

## Cas testés

### ❌ UNSAFE — Doivent montrer des collisions

| Test | Opération | Problème |
|------|-----------|----------|
| `testReplaceAll_Unsafe` | `replaceAll(x -> x+1)` sur ArrayList partagée | ConcurrentModificationException + corruption |
| `testForEachAdd_Unsafe` | `forEach` + `add` sur même liste | ConcurrentModificationException |
| `testRemoveIf_Unsafe` | `removeIf` avec lambda + `addAll` concurrent | ConcurrentModificationException |
| `testSort_Unsafe` | `sort(Comparator)` + modification concurrente | Ordre incohérent + exceptions |
| `testFinal_StillUnsafe` | `final ArrayList` — prouve que `final` ne change RIEN | Même corruption qu'ArrayList normal |
| `testStreamCollect_SharedSource_Unsafe` | `stream().map().collect()` avec source mutée | ArrayIndexOutOfBounds + données corrompues |

### ✅ SAFE — Doivent montrer 0% collision

| Test | Solution | Pourquoi ça marche |
|------|----------|-------------------|
| `testReplaceAll_CopyOnWriteArrayList` | `CopyOnWriteArrayList` | Copie interne à chaque écriture |
| `testReplaceAll_SynchronizedList` | `Collections.synchronizedList` + `synchronized` block | Mutex explicite |
| `testReplaceAll_Synchronized_Block` | `synchronized` block manuel | Exclusion mutuelle |
| `testStream_DefensiveCopy` | Copie défensive avant `stream()` | Snapshot isolé des modifications |

## Résultats attendus

```
=== testReplaceAll_Unsafe ===
Threads: 50 | Iterations: 2000 | Total hits: 100,000
Exceptions caught: ~800-2000
Data corruptions: ~1000-5000
Collision rate: ~2-7%
Status: UNSAFE ❌

=== testForEachAdd_Unsafe ===
Threads: 50 | Iterations: 2000 | Total hits: 100,000
Exceptions caught: ~500-1500
Data corruptions: ~100-500
Collision rate: ~1-2%
Status: UNSAFE ❌

=== testReplaceAll_CopyOnWriteArrayList ===
Threads: 50 | Iterations: 2000 | Total hits: 100,000
Exceptions caught: 0
Data corruptions: 0
Collision rate: 0.00%
Status: SAFE ✅
```

## Lancer les tests

```bash
mvn test
```

Ou un test spécifique :
```bash
mvn test -Dtest="LambdaRaceConditionTest#testReplaceAll_Unsafe"
```

## Concepts clés

- **`final` ≠ thread-safe** : `final` empêche la réassignation de la référence, pas la mutation du contenu
- **`ArrayList` n'est jamais thread-safe** : même les opérations "read-only" comme `stream()` cassent si un autre thread modifie
- **`CopyOnWriteArrayList`** : parfait pour lecture fréquente, écriture rare
- **`synchronized`** : solution universelle mais il faut synchroniser TOUTES les opérations
- **Copie défensive** : `new ArrayList<>(shared)` avant de streamer = isolation garantie

## Stack

- Java 17
- JUnit 5
- Maven
