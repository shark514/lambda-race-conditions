# Lambda Race Conditions - Démonstration

## Objectif

Démontrer que les opérations lambda sur `ArrayList` ne sont **pas thread-safe**.
Les méthodes `replaceAll`, `forEach`, `removeIf` et `sort` lancent des `ConcurrentModificationException`
(ou pire, corrompent silencieusement les données) quand plusieurs threads accèdent à la même liste.

## Cas testés

| Méthode | Risque | Solution |
|---------|--------|----------|
| `replaceAll` | ConcurrentModificationException | `synchronized` ou `CopyOnWriteArrayList` |
| `forEach` + `add` | ConcurrentModificationException | `synchronized` |
| `removeIf` | ConcurrentModificationException / données corrompues | `synchronized` |
| `sort` | ConcurrentModificationException / ordre incohérent | `synchronized` |

## Lancer les tests

```bash
mvn test
```

## Ce qu'on observe

- Les tests `_UNSAFE` **échouent volontairement** : ils attrapent des exceptions ou détectent des incohérences.
- Les tests `_SAFE` passent : ils montrent les solutions (`synchronized`, `CopyOnWriteArrayList`).
- `Thread.sleep()` est utilisé pour maximiser les chances de collision entre threads.

## Prérequis

- Java 17+
- Maven 3.8+
