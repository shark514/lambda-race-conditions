# Lambda Race Conditions — Démonstration Massive

## Le problème en une phrase

**`ArrayList` n'est pas thread-safe.** Quand plusieurs threads utilisent des lambdas (`replaceAll`, `forEach`, `sort`…) sur la même `ArrayList`, ça casse. Parfois bruyamment (exception), parfois silencieusement (données corrompues).

## Comment on le prouve

Chaque test est exécuté à **5 niveaux de charge** : 100, 1K, 10K, 100K et 1M hits. On observe comment le taux de collision évolue avec la charge. Les tests SAFE prouvent 0% à tous les niveaux.

---

## 📊 Tableau Récapitulatif Complet

### ❌ Tests UNSAFE

#### 1. `replaceAll` sur ArrayList partagée
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 6          | 94          | 100.00%        |
| 1,000     | 52         | 948         | 100.00%        |
| 10,000    | 8,833      | 1,167       | 100.00%        |
| 100,000   | 95,173     | 4,827       | 100.00%        |
| 1,000,000 | 962,885    | 37,115      | 100.00%        |

> 💀 **100% à tous les niveaux.** `replaceAll` touche tous les éléments — la fenêtre de collision est maximale.

#### 2. `forEach` + `add` concurrent
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 2          | 0           | 2.00%          |
| 1,000     | 2          | 0           | 0.20%          |
| 10,000    | 28         | 0           | 0.28%          |
| 100,000   | 3,702      | 0           | 3.70%          |
| 1,000,000 | 395,188    | 0           | 39.52%         |

> 📈 **Monte avec la charge.** De 2% à 100 hits jusqu'à ~40% à 1M. Plus de contention = plus de collisions.

#### 3. `removeIf` + `addAll` concurrent
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 0          | 0           | 0.00%          |
| 1,000     | 5          | 0           | 0.50%          |
| 10,000    | 9,828      | 2           | 98.30%         |
| 100,000   | 99,928     | 1           | 99.93%         |
| 1,000,000 | 999,652    | 4           | 99.97%         |

> 🔥 **Explose au-delà de 1K.** Quasi-invisible à faible charge, catastrophique en prod.

#### 4. `sort` + `set` concurrent
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 2          | 9           | 11.00%         |
| 1,000     | 33         | 53          | 8.60%          |
| 10,000    | 1,762      | 264         | 20.26%         |
| 100,000   | 39,301     | 363         | 39.66%         |
| 1,000,000 | 536,295    | 335         | 53.66%         |

> 📈 **Croissance progressive.** Le tri est O(n log n) — la fenêtre de collision grandit avec la contention.

#### 5. `final` ArrayList (TOUJOURS unsafe)
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 3          | 97          | 100.00%        |
| 1,000     | 3          | 997         | 100.00%        |
| 10,000    | 6,946      | 3,054       | 100.00%        |
| 100,000   | 87,936     | 12,064      | 100.00%        |
| 1,000,000 | 933,167    | 66,833      | 100.00%        |

> 🎭 **`final` ≠ thread-safe.** Identique au test sans `final`. Piège classique en entrevue.

#### 6. `stream()` sur source modifiée
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 1          | 1           | 2.00%          |
| 1,000     | 8          | 0           | 0.80%          |
| 10,000    | 114        | 2           | 1.16%          |
| 100,000   | 2,462      | 3           | 2.47%          |
| 1,000,000 | 28,462     | 52          | 2.85%          |

> 📈 **Taux bas mais constant.** Les streams lisent directement la source — pas une copie.

---

### ✅ Tests SAFE

#### 7. `CopyOnWriteArrayList`
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 0          | 0           | 0.00%          |
| 1,000     | 0          | 0           | 0.00%          |
| 10,000    | 0          | 0           | 0.00%          |
| 100,000   | 0          | 0           | 0.00%          |
| 1,000,000 | 0          | 0           | 0.00%          |

#### 8. `Collections.synchronizedList`
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 0          | 0           | 0.00%          |
| 1,000     | 0          | 0           | 0.00%          |
| 10,000    | 0          | 0           | 0.00%          |
| 100,000   | 0          | 0           | 0.00%          |
| 1,000,000 | 0          | 0           | 0.00%          |

#### 9. `synchronized` block manuel
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 0          | 0           | 0.00%          |
| 1,000     | 0          | 0           | 0.00%          |
| 10,000    | 0          | 0           | 0.00%          |
| 100,000   | 0          | 0           | 0.00%          |
| 1,000,000 | 0          | 0           | 0.00%          |

#### 10. Copie défensive avant stream
| Hits      | Exceptions | Corruptions | Collision Rate |
|-----------|------------|-------------|----------------|
| 100       | 0          | 0           | 0.00%          |
| 1,000     | 0          | 0           | 0.00%          |
| 10,000    | 0          | 0           | 0.00%          |
| 100,000   | 0          | 0           | 0.00%          |
| 1,000,000 | 0          | 0           | 0.00%          |

> ✅ **0% à tous les niveaux, toutes les solutions.** La synchronisation fonctionne.

---

## 🔑 Observations Clés

1. **La charge révèle les bugs.** `removeIf` montre 0% à 100 hits mais 99.97% à 1M. Un test unitaire classique ne voit rien.
2. **`replaceAll` est le pire cas** — 100% de collision même à 100 hits.
3. **`final` est un piège** — même résultat que sans `final`. `final` ≠ immutable ≠ thread-safe.
4. **Les solutions SAFE sont absolues** — 0.00% à tous les niveaux, pas de dégradation.

---

## Explications détaillées

### ❌ Tests UNSAFE

| # | Test | Pourquoi c'est unsafe |
|---|------|----------------------|
| 1 | `replaceAll` sur ArrayList | Itération + modification concurrente sur tous les éléments |
| 2 | `forEach` + `add` | Modification pendant itération — `ConcurrentModificationException` |
| 3 | `removeIf` + `addAll` | Restructuration concurrente de l'array interne |
| 4 | `sort` + `set` | Tri concurrent = corruption silencieuse des données |
| 5 | `final` ArrayList | `final` empêche la réassignation, pas la mutation |
| 6 | `stream` sur source mutée | `stream()` lit directement la source, pas une copie |

### ✅ Tests SAFE

| # | Solution | Comment ça marche | Quand l'utiliser |
|---|----------|-------------------|------------------|
| 7 | `CopyOnWriteArrayList` | Copie l'array à chaque écriture | Lectures fréquentes, écritures rares |
| 8 | `synchronizedList` | Verrou mutex autour de chaque opération | Drop-in replacement rapide |
| 9 | `synchronized` block | Exclusion mutuelle explicite | Contrôle total |
| 10 | Copie défensive | Snapshot isolé avant stream | Stream/itération longue |

---

## Quelle solution choisir ?

```
Lectures fréquentes, écritures rares ?  → CopyOnWriteArrayList
Drop-in replacement rapide ?            → Collections.synchronizedList + synchronized
Contrôle total ?                        → synchronized block manuel
Stream/itération longue ?               → Copie défensive
Haute performance ?                     → ConcurrentHashMap (pas couvert ici)
```

## Lancer les tests

```bash
mvn test
```

Un test spécifique :
```bash
mvn test -Dtest="LambdaRaceConditionTest#testReplaceAll_Unsafe"
```

## Stack

- Java 21
- JUnit 5
- Maven
- 10 tests × 5 niveaux de charge = 50 scénarios exécutés
