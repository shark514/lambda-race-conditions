# Lambda Race Conditions — Démonstration

Ce projet démontre les **race conditions** qui surviennent lorsque plusieurs threads manipulent une `ArrayList` en parallèle, en comparant 6 approches différentes : boucle for classique, `replaceAll` avec lambda, variable `final`, copie défensive, `synchronizedList`, et `CopyOnWriteArrayList`. Chaque scénario exécute exactement la même opération (incrémenter tous les éléments) avec 50 threads concurrents, seule la méthode de synchronisation change.

## Lancer les tests

```bash
# Tous les scénarios
mvn test -Dtest="com.epns.lambda.scenarios.*"

# Un scénario individuel
mvn test -Dtest=com.epns.lambda.scenarios.BaselineForLoopTest
```

## Résultats consolidés

| Scénario               | 100 hits | 1 000 hits | 10 000 hits | 100 000 hits |
|------------------------|----------|------------|-------------|--------------|
| BaselineForLoop        | X.XX%    | X.XX%      | X.XX%       | X.XX%        |
| ReplaceAllUnsafe       | X.XX%    | X.XX%      | X.XX%       | X.XX%        |
| FinalReplaceAll        | X.XX%    | X.XX%      | X.XX%       | X.XX%        |
| DefensiveCopy          | X.XX%    | X.XX%      | X.XX%       | X.XX%        |
| SynchronizedList       | X.XX%    | X.XX%      | X.XX%       | X.XX%        |
| CopyOnWriteArrayList   | X.XX%    | X.XX%      | X.XX%       | X.XX%        |

## Les 6 scénarios

### 1. BaselineForLoop
Boucle `for` classique avec `list.get(i)` et `list.set(i, ...)`. Pas de lambda. Démontre que le problème n'est pas spécifique aux lambdas — c'est un problème de concurrence pure.

### 2. ReplaceAllUnsafe
Utilise `list.replaceAll(x -> x + 1)` directement sur une `ArrayList` partagée. ConcurrentModificationException garantie à haute charge car `replaceAll` vérifie le `modCount`.

### 3. FinalReplaceAll
Même chose que ReplaceAllUnsafe, mais avec `final ArrayList<Integer>`. Démontre que `final` protège la **référence**, pas le **contenu** — les race conditions sont identiques.

### 4. DefensiveCopy
Crée une copie `new ArrayList<>(list)`, transforme la copie, puis écrase l'original avec `Collections.copy()`. Réduit les exceptions mais introduit des pertes de mises à jour (lost updates).

### 5. SynchronizedList
`Collections.synchronizedList()` + bloc `synchronized(list)` autour de `replaceAll`. **Thread-safe** : chaque opération est atomique grâce au verrou explicite.

### 6. CopyOnWriteArrayList
`CopyOnWriteArrayList` avec `replaceAll`. **Thread-safe** grâce au verrouillage interne sur chaque mutation. Plus lent à haute charge mais correcte.

## Conclusion

| Approche | Safe ? | Pourquoi |
|----------|--------|----------|
| for loop | ❌ | Aucune synchronisation, read-modify-write non atomique |
| replaceAll | ❌ | ConcurrentModificationException (modCount check) |
| final + replaceAll | ❌ | `final` = référence immuable, pas contenu |
| Copie défensive | ⚠️ | Pas d'exception, mais lost updates possibles |
| synchronizedList + sync block | ✅ | Verrou explicite rend l'opération atomique |
| CopyOnWriteArrayList | ✅ | Verrouillage interne, copie à chaque écriture |

**Règle d'or :** Si plusieurs threads modifient une collection, il faut soit un verrou explicite (`synchronized`), soit une structure concurrent (`CopyOnWriteArrayList`, `ConcurrentHashMap`). Ni `final`, ni les copies défensives ne suffisent.
