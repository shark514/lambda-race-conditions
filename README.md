# Lambda Race Conditions — Démonstration Massive

## Le problème en une phrase

**`ArrayList` n'est pas thread-safe.** Quand plusieurs threads utilisent des lambdas (`replaceAll`, `forEach`, `sort`…) sur la même `ArrayList`, ça casse. Parfois bruyamment (exception), parfois silencieusement (données corrompues).

## Comment on le prouve

Chaque test lance **50 threads** qui exécutent **20,000 itérations** chacun = **1,000,000 opérations par test**. On compte les exceptions et les corruptions de données, puis on affiche le taux de collision. Si c'est > 0%, c'est unsafe.

```
=== testReplaceAll_Unsafe ===
Threads: 50 | Iterations: 2000 | Total hits: 1,000,000
Exceptions caught: 847
Data corruptions: 1,203
Collision rate: 2.05%
Status: UNSAFE ❌
```

---

## ❌ Tests UNSAFE — Ce qui casse

### 1. `testReplaceAll_Unsafe` — replaceAll sur ArrayList partagée

**Ce qu'on fait :** 50 threads appellent simultanément `shared.replaceAll(x -> x + 1)` sur la même `ArrayList<Integer>`.

**Pourquoi c'est unsafe :** `replaceAll` itère sur chaque élément de la liste et le remplace. Pendant cette itération, un autre thread fait la même chose. Java détecte la modification concurrente via un compteur interne (`modCount`) et lance une `ConcurrentModificationException`. Mais quand deux threads modifient `modCount` en même temps, le compteur peut "sauter" la détection — et là, les données sont corrompues silencieusement : certains éléments reçoivent plus d'incréments que d'autres.

**Résultat attendu :** ~100% de collision. Quasiment chaque itération produit soit une exception, soit une corruption. C'est le cas le plus violent parce que `replaceAll` touche **tous** les éléments — la fenêtre de collision est énorme.

**Leçon :** Ne jamais appeler `replaceAll` sur une `ArrayList` partagée entre threads sans synchronisation.

---

### 2. `testForEachAdd_Unsafe` — forEach + add sur la même liste

**Ce qu'on fait :** Pour chaque itération, on crée une petite liste `[1, 2, 3, 4, 5]`. Un thread la parcourt avec `forEach`, pendant qu'un autre thread appelle `add(99)` dessus.

**Pourquoi c'est unsafe :** `forEach` utilise un itérateur interne qui vérifie `modCount` à chaque étape. Quand `add` modifie la liste pendant l'itération, le `modCount` change et l'itérateur lance une `ConcurrentModificationException`. Parfois, `add` se glisse entre deux vérifications et le résultat est une liste avec un nombre d'éléments inattendu.

**Résultat attendu :** ~3% de collision. Le taux est plus bas que `replaceAll` parce que l'opération `add` est très rapide (un seul appel) — la fenêtre de collision est plus étroite. Mais sur 1,000,000 essais, on capture quand même des centaines de cas.

**Leçon :** "Je ne fais que lire avec `forEach`" ne suffit pas. Si un autre thread écrit en même temps, ça casse.

---

### 3. `testRemoveIf_Unsafe` — removeIf avec lambda

**Ce qu'on fait :** 50 threads appellent simultanément `removeIf(x -> x % 2 == 0)` puis `addAll(Arrays.asList(2, 4, 6, 8, 10))` sur la même liste.

**Pourquoi c'est unsafe :** `removeIf` parcourt la liste, évalue le prédicat sur chaque élément, et supprime ceux qui matchent. Pendant ce temps, un autre thread ajoute des éléments. L'array interne de `ArrayList` peut être redimensionné par `addAll` pendant que `removeIf` est en train de le parcourir — ça cause des `ArrayIndexOutOfBoundsException`, des `ConcurrentModificationException`, ou des `null` dans la liste.

**Résultat attendu :** ~99-100% de collision. `removeIf` + `addAll` modifient tous les deux la structure — la probabilité de conflit est élevée.

**Leçon :** Les méthodes "fonctionnelles" de Java (`removeIf`, `replaceAll`) ne sont pas plus sûres que les boucles classiques. Elles utilisent le même `ArrayList` non-synchronisé en dessous.

---

### 4. `testSort_Unsafe` — sort avec comparator lambda

**Ce qu'on fait :** 50 threads appellent `sort(Comparator.reverseOrder())` puis `set(0, random)` sur la même liste.

**Pourquoi c'est unsafe :** `sort` réorganise physiquement les éléments dans l'array interne. Pendant ce tri, un autre thread modifie un élément avec `set` ou lance son propre `sort`. Le résultat : des éléments dupliqués, des éléments perdus, un ordre qui n'est ni ascendant ni descendant. Le test vérifie que la liste est bien triée en ordre décroissant après `sort` — et elle ne l'est presque jamais.

**Résultat attendu :** ~100% de collision. Chaque `sort` touche potentiellement tous les éléments (O(n log n) comparaisons et swaps) — la fenêtre de collision est massive.

**Leçon :** Le tri concurrent sans verrou est une recette pour la corruption de données. Et contrairement aux exceptions, la corruption est **silencieuse** — votre liste a l'air normale mais les données sont fausses.

---

### 5. `testFinal_StillUnsafe` — `final` ne protège RIEN

**Ce qu'on fait :** Exactement le même test que `testReplaceAll_Unsafe`, mais la liste est déclarée `final`.

```java
final ArrayList<Integer> shared = new ArrayList<>();
```

**Pourquoi c'est unsafe :** `final` empêche de **réassigner la variable** (`shared = autreList` ne compile pas). Mais `final` ne protège absolument pas le **contenu** de l'objet. `shared.replaceAll(...)`, `shared.add(...)`, `shared.clear()` fonctionnent exactement pareil. C'est comme mettre un cadenas sur le panneau d'affichage mais laisser les feuilles épinglées dessus accessibles à tout le monde.

**Résultat attendu :** ~100% de collision — identique au test sans `final`.

**Leçon :** C'est un piège classique en entrevue. `final` ≠ immutable ≠ thread-safe. Si quelqu'un vous dit "c'est `final` donc c'est safe", il se trompe.

---

### 6. `testStreamCollect_SharedSource_Unsafe` — stream sur source modifiée

**Ce qu'on fait :** La moitié des threads exécute `shared.stream().map(x -> x * 2).collect(toList())`. L'autre moitié modifie la liste source avec `add` et `subList.clear()`.

**Pourquoi c'est unsafe :** Les streams Java sont **lazy** — ils ne copient pas la source. `stream()` crée un pipeline qui lit directement depuis l'`ArrayList` sous-jacente. Si un autre thread modifie cette liste pendant que le stream la parcourt, on obtient des `ConcurrentModificationException`, des `ArrayIndexOutOfBoundsException`, des `null` dans le résultat, ou un résultat de taille incohérente.

**Résultat attendu :** ~99-100% de collision. Le stream itère sur la liste entière, donc la fenêtre de collision est proportionnelle à la taille de la liste.

**Leçon :** `stream()` n'est pas une copie défensive. Il lit la source en direct. Si la source bouge, le stream casse.

---

## ✅ Tests SAFE — Ce qui marche

### 7. `testReplaceAll_CopyOnWriteArrayList` — CopyOnWriteArrayList

**Ce qu'on fait :** Même test que `testReplaceAll_Unsafe`, mais avec `CopyOnWriteArrayList` au lieu d'`ArrayList`.

**Pourquoi c'est safe :** `CopyOnWriteArrayList` crée une **copie de l'array interne à chaque modification**. Chaque `replaceAll` travaille sur sa propre copie. Les lectures ne sont jamais bloquées et ne voient jamais un état intermédiaire.

**Résultat attendu :** 0% de collision. Toujours.

**Avantages :**
- Aucune synchronisation manuelle requise
- Les lectures sont très rapides (pas de verrou)
- Thread-safe par design

**Inconvénients :**
- Chaque écriture copie l'array entier → O(n) par modification
- Catastrophique si les écritures sont fréquentes (notre test avec 100K écritures est lent)
- Mémoire : chaque copie alloue un nouvel array

**Quand l'utiliser :** Quand les lectures sont fréquentes et les écritures rares (ex: liste de listeners, configuration chargée au démarrage).

---

### 8. `testReplaceAll_SynchronizedList` — Collections.synchronizedList

**Ce qu'on fait :** On wrappe l'`ArrayList` avec `Collections.synchronizedList()`, et on utilise un bloc `synchronized(shared)` autour de `replaceAll`.

**Pourquoi c'est safe :** `synchronizedList` wrappe chaque méthode individuelle dans un `synchronized`. Mais pour les opérations composées (itération, replaceAll), il faut **synchroniser manuellement** sur la liste. Le bloc `synchronized(shared)` garantit qu'un seul thread exécute `replaceAll` à la fois.

**Résultat attendu :** 0% de collision.

**Avantages :**
- Simple à comprendre
- Pas de copie → O(1) overhead par opération
- Fonctionne avec n'importe quelle `List`

**Inconvénients :**
- Il faut penser à synchroniser manuellement pour les itérations — `synchronizedList` seul ne suffit PAS pour `replaceAll`, `forEach`, `sort`, etc.
- Un seul thread à la fois → les lectures bloquent aussi
- Risque de deadlock si on synchronise sur plusieurs objets

**Quand l'utiliser :** Quand on a besoin d'un drop-in replacement rapide pour `ArrayList` et qu'on contrôle tous les points d'accès.

---

### 9. `testReplaceAll_Synchronized_Block` — synchronized block manuel

**Ce qu'on fait :** On utilise un objet `lock` dédié et on wrappe `replaceAll` dans `synchronized(lock)`.

**Pourquoi c'est safe :** Le verrou garantit l'exclusion mutuelle. Un seul thread à la fois peut exécuter le bloc synchronisé. Les autres attendent. C'est la solution la plus explicite et la plus contrôlable.

**Résultat attendu :** 0% de collision.

**Avantages :**
- Contrôle total sur la granularité du verrou
- On peut protéger plusieurs opérations dans le même bloc (lecture + écriture atomique)
- Pas de copie, pas de wrapper

**Inconvénients :**
- Verbeux — il faut synchroniser à chaque point d'accès
- Si on oublie un `synchronized` quelque part, on retombe dans l'unsafe
- Performance : un seul thread à la fois

**Quand l'utiliser :** Quand on veut un contrôle fin et qu'on est discipliné sur la synchronisation. C'est la solution "old school" mais fiable.

---

### 10. `testStream_DefensiveCopy` — copie défensive avant stream

**Ce qu'on fait :** Avant de streamer, on prend un `synchronized` sur la liste et on fait `new ArrayList<>(shared)`. Puis on stream la copie, librement et sans verrou.

**Pourquoi c'est safe :** La copie crée un snapshot isolé. Peu importe ce que les autres threads font à la liste originale après — notre copie ne bouge plus. Le stream travaille sur des données figées.

**Résultat attendu :** 0% de collision.

**Avantages :**
- Le stream peut être parallèle sans risque
- Pas besoin de garder le verrou pendant toute l'opération de stream
- Pattern simple et facile à comprendre

**Inconvénients :**
- Coût mémoire : une copie par lecture
- La copie elle-même doit être synchronisée (sinon on copie un état incohérent)
- Les données sont potentiellement "stale" (snapshot du passé)

**Quand l'utiliser :** Quand on a besoin de streamer/itérer longuement sur une collection partagée sans bloquer les écritures.

---

## Tableau récapitulatif

| # | Test | Type | Collision | Pourquoi |
|---|------|------|-----------|----------|
| 1 | `replaceAll` sur ArrayList | ❌ UNSAFE | ~100% | Itération + modification concurrente |
| 2 | `forEach` + `add` | ❌ UNSAFE | ~3% | Modification pendant itération |
| 3 | `removeIf` + `addAll` | ❌ UNSAFE | ~99-100% | Restructuration concurrente |
| 4 | `sort` + `set` | ❌ UNSAFE | ~100% | Tri concurrent = corruption |
| 5 | `final` ArrayList | ❌ UNSAFE | ~100% | `final` ≠ thread-safe |
| 6 | `stream` sur source mutée | ❌ UNSAFE | ~99-100% | Stream = lecture directe, pas copie |
| 7 | `CopyOnWriteArrayList` | ✅ SAFE | 0% | Copie à chaque écriture |
| 8 | `synchronizedList` | ✅ SAFE | 0% | Verrou mutex |
| 9 | `synchronized` block | ✅ SAFE | 0% | Exclusion mutuelle explicite |
| 10 | Copie défensive | ✅ SAFE | 0% | Snapshot isolé |

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

- Java 17
- JUnit 5
- Maven
