# Lambda Race Conditions — Démonstration Massive

## Le problème

**Les lambdas Java ne protègent pas contre les race conditions.** `replaceAll`, `forEach`, `removeIf`, `sort`, `stream`… toutes ces API "modernes" de Java 8+ opèrent directement sur la collection sous-jacente. Si cette collection est un `ArrayList` partagé entre threads, ça casse.

Ce projet le prouve avec **10 scénarios** testés à **5 niveaux de charge** (100 → 1M hits), mesurant les exceptions et corruptions de données à chaque palier.

## Structure

```
src/test/java/com/epns/lambda/
├── unsafe/                              ❌ Ce qui casse
│   ├── README.md
│   ├── ReplaceAllUnsafeTest.java        replaceAll sur ArrayList partagée
│   ├── ForEachAddUnsafeTest.java        forEach + add concurrent
│   ├── RemoveIfUnsafeTest.java          removeIf + addAll concurrent
│   ├── SortUnsafeTest.java              sort + set concurrent
│   ├── FinalStillUnsafeTest.java        final ≠ thread-safe
│   └── StreamSharedSourceUnsafeTest.java stream sur source mutée
├── safe/                                ✅ Ce qui marche
│   ├── README.md
│   ├── CopyOnWriteArrayListTest.java    Copie à chaque écriture
│   ├── SynchronizedListTest.java        synchronizedList + synchronized
│   ├── SynchronizedBlockTest.java       Lock manuel
│   └── DefensiveCopyTest.java           Snapshot avant stream
└── util/
    └── CollisionTestRunner.java         Helper partagé (multi-scale, -Dhits)
```

## 📊 Tableau Récapitulatif — Tous les résultats

### ❌ UNSAFE

| Test | 100 hits | 1,000 hits | 10,000 hits | 100,000 hits | 1,000,000 hits |
|------|----------|------------|-------------|--------------|----------------|
| **replaceAll** | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% |
| **forEach + add** | 2.00% | 0.20% | 0.28% | 3.70% | 39.52% |
| **removeIf + addAll** | 0.00% | 0.50% | 98.30% | 99.93% | 99.97% |
| **sort + set** | 11.00% | 8.60% | 20.26% | 39.66% | 53.66% |
| **final ArrayList** | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% |
| **stream sur source mutée** | 2.00% | 0.80% | 1.16% | 2.47% | 2.85% |

### ✅ SAFE

| Test | 100 hits | 1,000 hits | 10,000 hits | 100,000 hits | 1,000,000 hits |
|------|----------|------------|-------------|--------------|----------------|
| **CopyOnWriteArrayList** | 0.00% | 0.00% | 0.00% | 0.00% | 0.00% |
| **synchronizedList** | 0.00% | 0.00% | 0.00% | 0.00% | 0.00% |
| **synchronized block** | 0.00% | 0.00% | 0.00% | 0.00% | 0.00% |
| **Copie défensive** | 0.00% | 0.00% | 0.00% | 0.00% | 0.00% |

## 🔑 Observations

1. **`replaceAll` et `final`** : 100% de collision à TOUS les niveaux, même 100 hits
2. **`removeIf`** : 0% à 100 hits → 99.97% à 1M — **invisible en dev, catastrophique en prod**
3. **`forEach + add`** : croissance progressive de 2% à 40% — le bug qui empire sous la charge
4. **Toutes les solutions SAFE** : 0.00% absolu à tous les niveaux — la synchronisation est non-négociable

## Lancer les tests

```bash
# Tous les tests (5 niveaux de charge chacun)
mvn test

# Un scénario spécifique
mvn test -Dtest=ReplaceAllUnsafeTest

# Avec un nombre de hits personnalisé
mvn test -Dtest=SortUnsafeTest -Dhits=500000

# Tous les unsafe
mvn test -Dtest="com.epns.lambda.unsafe.*"

# Tous les safe
mvn test -Dtest="com.epns.lambda.safe.*"
```

Le paramètre `-Dhits=N` permet de choisir un seul niveau de charge au lieu des 5 par défaut.

## Stack

- Java 21
- JUnit 5
- Maven
- 10 tests × 5 niveaux = 50 scénarios
