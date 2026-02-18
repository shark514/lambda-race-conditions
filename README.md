# Lambda Race Conditions — Démonstration

Ce projet démontre les **race conditions** qui surviennent lorsque plusieurs threads manipulent une `ArrayList` en parallèle. 6 approches sont comparées : boucle for classique, `replaceAll` avec lambda, variable `final`, copie défensive, `synchronizedList`, et `CopyOnWriteArrayList`.

Chaque scénario fait exactement la même chose : **50 threads incrémentent simultanément chaque élément d'une liste**. La seule différence = la méthode utilisée. À la fin, on compare la valeur obtenue vs la valeur attendue. La différence = les incréments perdus par collision.

## Lancer les tests

```bash
# Tous les scénarios
mvn test

# Un scénario individuel
mvn test -Dtest=com.epns.lambda.scenarios.BaselineForLoopTest
mvn test -Dtest=com.epns.lambda.scenarios.ReplaceAllUnsafeTest
mvn test -Dtest=com.epns.lambda.scenarios.FinalReplaceAllTest
mvn test -Dtest=com.epns.lambda.scenarios.DefensiveCopyTest
mvn test -Dtest=com.epns.lambda.scenarios.SynchronizedListTest
mvn test -Dtest=com.epns.lambda.scenarios.CopyOnWriteArrayListTest
```

## Résultats consolidés

**Métrique** : Taux de perte = `(attendu - obtenu) / attendu × 100`

50 threads concurrents, liste de 10 éléments initialisés à 0.

| Scénario               | 100 hits | 1 000 hits | 10 000 hits | 100 000 hits |
|------------------------|----------|------------|-------------|--------------|
| BaselineForLoop        | 1.00%    | 1.80%      | 20.60%      | **53.83%**   |
| ReplaceAllUnsafe       | 11.00%   | 1.00%      | 2.69%       | **34.37%**   |
| FinalReplaceAll        | 0.00%    | 0.40%      | 1.96%       | **14.83%**   |
| DefensiveCopy          | 44.00%   | 30.20%     | 56.21%      | **64.35%**   |
| SynchronizedList       | 0.00%    | 0.00%      | 0.00%       | **0.00%**    |
| CopyOnWriteArrayList   | 0.00%    | 0.00%      | 0.00%       | **0.00%**    |

### Ce que montrent les résultats

- **BaselineForLoop** : La boucle `for` classique perd >50% des incréments à 100K hits. Le read-modify-write (`get` → `+1` → `set`) n'est pas atomique.
- **ReplaceAllUnsafe** : Le lambda `replaceAll` perd ~34% + génère des `ConcurrentModificationException`. Le pire des deux mondes.
- **FinalReplaceAll** : `final` réduit *apparemment* les pertes (~15% vs ~34%) mais c'est un faux sentiment de sécurité. La référence est figée, **pas le contenu**.
- **DefensiveCopy** : La PIRE approche (~64% de pertes !). Chaque thread copie la liste, la transforme, puis écrase l'original — mais pendant ce temps, les autres threads ont aussi écrasé. Lost updates en cascade.
- **SynchronizedList** : **0% de pertes**. Le bloc `synchronized(list)` garantit l'atomicité de chaque opération.
- **CopyOnWriteArrayList** : **0% de pertes**. Le verrouillage interne protège chaque mutation.

## Les 6 scénarios

### 1. BaselineForLoop — `for` loop classique (pas de lambda)
```java
for (int i = 0; i < list.size(); i++) {
    list.set(i, list.get(i) + 1);
}
```
Pas de lambda. Démontre que le problème est fondamentalement lié à la **concurrence**, pas aux lambdas eux-mêmes. Le `get` + `set` séparés = read-modify-write non atomique.

### 2. ReplaceAllUnsafe — `replaceAll` avec lambda
```java
list.replaceAll(x -> x + 1);
```
Lambda directement sur `ArrayList` partagée. Génère des `ConcurrentModificationException` parce que `replaceAll` vérifie le `modCount` interne.

### 3. FinalReplaceAll — `final` + `replaceAll`
```java
final ArrayList<Integer> list = new ArrayList<>();
list.replaceAll(x -> x + 1);
```
Le mot-clé `final` empêche de réassigner la variable `list`. Mais il ne protège **absolument pas** le contenu de la liste. Les race conditions sont les mêmes.

### 4. DefensiveCopy — copie défensive
```java
ArrayList<Integer> copy = new ArrayList<>(list);
copy.replaceAll(x -> x + 1);
Collections.copy(list, copy);
```
Chaque thread travaille sur sa propre copie — pas d'exception. Mais quand il écrase la liste originale avec sa copie, il écrase aussi les modifications des autres threads. Résultat : **le pire taux de perte de tous les scénarios**.

### 5. SynchronizedList — `synchronized` + `Collections.synchronizedList()`
```java
List<Integer> list = Collections.synchronizedList(new ArrayList<>());
synchronized (list) {
    list.replaceAll(x -> x + 1);
}
```
Le verrou `synchronized` rend chaque `replaceAll` atomique. **Zéro perte**, mais les threads attendent leur tour (contention).

### 6. CopyOnWriteArrayList
```java
CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
list.replaceAll(x -> x + 1);
```
Chaque écriture crée une copie interne protégée par un `ReentrantLock`. **Zéro perte**. Plus lent en écriture, mais parfait pour les cas read-heavy.

## Conclusion

| Approche | Safe ? | Taux perte @ 100K | Pourquoi |
|----------|--------|-------------------|----------|
| for loop | ❌ | 53.83% | read-modify-write non atomique |
| replaceAll | ❌ | 34.37% | ConcurrentModificationException + pertes |
| final + replaceAll | ❌ | 14.83% | `final` = référence immuable, pas contenu |
| Copie défensive | ❌ | 64.35% | Lost updates — la pire approche |
| synchronizedList | ✅ | 0.00% | Verrou explicite = atomicité |
| CopyOnWriteArrayList | ✅ | 0.00% | Verrouillage interne |

**Règle d'or** : Si plusieurs threads modifient une collection, il faut soit un verrou explicite (`synchronized`), soit une structure concurrent (`CopyOnWriteArrayList`, `ConcurrentHashMap`). Ni `final`, ni les copies défensives ne suffisent — et les copies défensives sont paradoxalement **la pire approche**.

## Structure du projet

```
src/test/java/com/epns/lambda/
├── scenarios/          ← Les 6 scénarios comparatifs
│   ├── ScenarioRunner.java
│   ├── BaselineForLoopTest.java
│   ├── ReplaceAllUnsafeTest.java
│   ├── FinalReplaceAllTest.java
│   ├── DefensiveCopyTest.java
│   ├── SynchronizedListTest.java
│   └── CopyOnWriteArrayListTest.java
├── unsafe/             ← Tests unitaires détaillés (anomalies par hit)
└── safe/               ← Tests unitaires thread-safe
```

## Pré-requis

- Java 21+
- Maven 3.8+

## Article complet

Voir [`docs/article.md`](docs/article.md) pour l'analyse approfondie avec les implications en production et le lien avec les attaques DDoS.
