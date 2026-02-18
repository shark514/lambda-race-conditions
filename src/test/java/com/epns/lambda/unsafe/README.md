# ❌ Tests UNSAFE — Ce qui casse

Ces tests prouvent que `ArrayList` n'est **PAS thread-safe**, même avec des lambdas "modernes" de Java 8+.

## Scénarios

| Test | Opération | Pourquoi ça casse |
|------|-----------|-------------------|
| `ReplaceAllUnsafeTest` | `replaceAll(x -> x + 1)` | Itération + modification concurrente sur tous les éléments |
| `ForEachAddUnsafeTest` | `forEach()` + `add()` | Modification pendant itération → ConcurrentModificationException |
| `RemoveIfUnsafeTest` | `removeIf()` + `addAll()` | Restructuration concurrente de l'array interne |
| `SortUnsafeTest` | `sort()` + `set()` | Tri concurrent = corruption silencieuse |
| `FinalStillUnsafeTest` | `final ArrayList` + `replaceAll()` | `final` ≠ immutable ≠ thread-safe |
| `StreamSharedSourceUnsafeTest` | `stream().map().collect()` + mutation | Stream lit la source en direct, pas une copie |

## Comment lancer

Tous les tests unsafe :
```bash
mvn test -pl . -Dtest="com.epns.lambda.unsafe.*"
```

Un test spécifique :
```bash
mvn test -Dtest=ReplaceAllUnsafeTest
```

Avec un niveau de charge spécifique :
```bash
mvn test -Dtest=ReplaceAllUnsafeTest -Dhits=1000000
```

## Ce qu'on observe

- **À 100 hits** : certains tests montrent déjà des collisions (replaceAll : 100%), d'autres semblent safe (removeIf : 0%)
- **À 1M hits** : TOUS les tests unsafe montrent des collisions significatives
- **Leçon** : un test unitaire classique (quelques appels) ne voit pas les race conditions. Il faut de la charge.

## Types d'erreurs détectées

1. **Exceptions** : `ConcurrentModificationException`, `ArrayIndexOutOfBoundsException`, `NullPointerException`
2. **Corruptions** : données incohérentes sans exception (le pire cas — silencieux en prod)
