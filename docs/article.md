# 🔥 Lambdas : le refactoring "cool" qui a silencieusement cassé des milliers d'apps

*On vous a dit que c'était plus propre. Plus moderne. Plus lisible. On ne vous a pas dit que c'était une bombe à retardement.*

*Depuis 2014, les développeurs Java refactorent leur code en lambdas. Modernisation, lisibilité, bonne vibe. Personne ne s'est demandé ce qu'on perdait en route. Cet article est pour ceux qui veulent comprendre pourquoi leur app crash "au hasard" en production — et pourquoi ce n'est pas du hasard.*

---

## Un peu d'histoire

Les expressions lambda ont été introduites en **Java 8**, sorti officiellement le **18 mars 2014**. Une révolution dans l'écosystème Java. Fini le code verbeux, place à l'élégance fonctionnelle.

Et partout dans le monde, des équipes de développeurs ont lancé le même chantier : *"On refactore en lambdas."* Sprint après sprint, pull request après pull request, le vieux code a été réécrit. Les code reviews applaudissaient. La couverture de tests restait au vert.

**Personne n'a vu venir le problème.**

---

## Qu'est-ce qu'une expression lambda ?

Pour ceux qui débarquent : une fonction lambda en Java est une expression qui représente un **comportement** (du code) qu'on peut passer comme donnée. Elle sert généralement à implémenter une **interface fonctionnelle** — une interface avec une seule méthode abstraite.

```java
// Avant Java 8 — verbeux mais explicite
Runnable r = new Runnable() {
    @Override
    public void run() {
        System.out.println("Hello");
    }
};

// Avec lambda — élégant et concis
Runnable r = () -> System.out.println("Hello");
```

Plus court. Plus lisible. Plus *cool*.

On comprend l'engouement. Qui voudrait revenir aux classes anonymes de 6 lignes quand une seule suffit ?

Mais derrière cette simplicité se cache une ambiguïté fondamentale.

---

## Le problème : ni synchrone, ni asynchrone

> ➡️ Une lambda Java n'est ni synchrone ni asynchrone par nature. Le mode d'exécution dépend **entièrement du contexte qui l'exécute**.

Et c'est **là** que tout se joue.

On ne gère pas les choses de la même façon quand on a un ou plusieurs threads qui accèdent aux mêmes données. Or, **rien dans la syntaxe d'une lambda ne vous dit dans quel contexte elle sera exécutée.**

Quand est-ce que c'est synchrone ? Quand est-ce que c'est asynchrone ? **Le framework ou l'appelant décide** quand et dans quel thread le code tourne.

Une réponse qui a au moins l'avantage d'être honnête : *nobody knows*.

### Lambda synchrone

```java
Runnable r = () -> System.out.println("Hello");
r.run(); // Exécuté dans le thread courant. Synchrone.
```

### Lambda asynchrone

```java
new Thread(() -> {
    System.out.println("Async");
}).start(); // Exécuté dans un nouveau thread. Asynchrone.
```

**Le même style de code. La même syntaxe. Deux comportements radicalement différents.**

Et le développeur qui écrit le lambda ? Il ne voit aucune différence. La syntaxe ne trahit rien.

---

## En entreprise : le vrai danger

Les exemples ci-dessus sont théoriques. En entreprise, la réalité est plus insidieuse.

Partout dans le code, les développeurs Java utilisent les lambdas **sans trop se demander si c'est sync ou async**. Et soyons honnêtes : la plupart d'entre nous pensent que c'est du synchrone. Parce que le code *a l'air* synchrone. Parce qu'il est écrit de cette façon. Parce qu'il *marche en test*.

Prenons un exemple concret — le genre de code qu'on trouve dans n'importe quel projet d'entreprise :

```java
ArrayList<String> truc(ArrayList<String> in) {
    in.replaceAll(s -> s.toUpperCase());
    return in;
}
```

Ça paraît anodin. Et **ça marche**. La liste `in` retournée est bien en majuscules. Les tests passent. La code review approuve. Le sprint est livré.

**SAUF QUE.**

Dans un contexte multithread, **personne ne peut garantir que `in` ne sera pas altérée avant, pendant et après** le traitement du `.replaceAll()`.

C'est ça le piège des lambdas. Le traitement peut être exécuté dans un contexte où **plusieurs services appellent la fonction `truc()` en parallèle**. Au moment où le `.replaceAll()` itère sur la liste, un autre thread peut être en train de :

- Ajouter un élément à la même liste
- Supprimer un élément de la même liste
- Appeler la même fonction avec la même référence

Le résultat ? Au mieux, une `ConcurrentModificationException`. Au pire — et c'est le cas le plus courant — **une corruption silencieuse des données**. Pas d'exception. Pas d'erreur dans les logs. Juste un résultat faux, aléatoirement, de manière non reproductible.

Le genre de bug qui rend fou.

---

## Comment ça se produit concrètement ?

Pas besoin d'un scénario exotique. Un cas banal suffit :

> Un utilisateur fait **"refresh, refresh, refresh"** sur sa page web parce que c'est lent. Il bombarde le service, qui charge 2 ou 3 fois le même objet et finit en **collision avec d'autres requêtes**.

Trois threads. La même `ArrayList`. Trois lambdas qui la manipulent en parallèle. **Race condition.**

Le résultat dépend de l'ordre d'exécution des threads — un ordre que personne ne contrôle, que personne ne peut prédire, et qui change à chaque exécution.

Lundi, ça marche. Mardi, ça crash. Mercredi, ça retourne des données corrompues sans erreur. Jeudi, impossible de reproduire. Vendredi, on ferme le ticket en "non reproductible".

Le bug est toujours là. Il attend.

---

## Le sommet de l'iceberg : le DDoS

Maintenant, étendons le scénario.

Dans un contexte de **DDoS** (attaque par déni de service distribué), le système est plus lent et surchargé. Les requêtes s'accumulent. Les threads se multiplient. Le risque de collision est **multiplié**.

Mais voilà ce que la plupart des gens ne comprennent pas :

**Un DDoS n'est pas juste une attaque pour "faire tomber" un service.**

C'est aussi — et surtout — une façon de **provoquer des conditions particulières** afin de forcer l'exécution d'actions ou de code **non prévu**. En surchargeant un système, un attaquant peut :

- **Provoquer des race conditions ciblées** — forcer deux threads à accéder à la même ressource au même moment
- **Exploiter des états incohérents** — une liste de permissions à moitié modifiée, un token partiellement invalidé, une session dans un état impossible
- **Bypasser des contrôles de sécurité** — si un contrôle d'accès repose sur l'intégrité d'une `ArrayList` manipulée par des lambdas, une race condition peut l'annuler
- **Corrompre des données métier** — des montants financiers altérés, des réservations en double, des inventaires faux

Le DDoS n'est que **le sommet de l'iceberg**.

En dessous, il y a des milliers d'applications "modernisées" avec des lambdas, qui portent des race conditions silencieuses comme autant de **portes ouvertes**. Des failles qui **n'existaient pas avant le refactoring**. Le vieux `synchronized for` loop était moche, mais il était *safe*.

En remplaçant le code moche par du code élégant, on a remplacé la sécurité par l'esthétique.

---

## La morale : les lambdas ne vous protègent pas

### Les opérations dangereuses

Chaque lambda qui **modifie la collection d'origine** est une bombe potentielle en contexte multithread :

```java
// ❌ DANGER — modification in-place de la collection
list.forEach(s -> list.add(s + "_copy"));     // ConcurrentModificationException
list.forEach(s -> list.remove(s));             // ConcurrentModificationException
list.replaceAll(s -> s.toUpperCase());         // Race condition silencieuse
list.removeIf(s -> s.isEmpty());              // Race condition silencieuse
list.sort((a, b) -> a.compareTo(b));          // Race condition silencieuse
```

Ces opérations **ne créent pas de nouvel objet** à l'intérieur du lambda. Elles modifient directement la liste d'origine. C'est là que le danger est maximal.

### Les opérations "généralement safe"

`.map()` et `.filter()` via les Streams sont **généralement safe** car ils produisent de nouvelles collections :

```java
// ✅ Généralement safe — crée une nouvelle liste
List<String> result = list.stream()
    .filter(s -> !s.isEmpty())
    .map(String::toUpperCase)
    .collect(Collectors.toList());
```

### Mais même là, rien n'est garanti

Car le vrai problème est en amont : la `ArrayList` passée en paramètre **n'est pas thread-safe**. Point.

Rien — absolument rien — ne garantit que la liste d'origine n'aura pas été altérée :

- **Avant** l'exécution du lambda — un autre thread modifie la liste entre l'appel de la fonction et le début de l'itération
- **Pendant** l'exécution du lambda — un autre thread ajoute ou supprime des éléments alors que le lambda itère
- **Après** l'exécution du lambda — le résultat est déjà obsolète au moment où il est retourné

Entre le moment où vous appelez `.stream()` et le moment où le lambda s'exécute, un autre thread a pu `.add()`, `.remove()`, `.clear()` la liste. Votre lambda travaille sur un **état fantôme** — une photo qui n'existe peut-être déjà plus.

### Et non, `final` ne change rien

```java
final ArrayList<String> list = getList();
list.forEach(s -> System.out.println(s));
// "C'est final, c'est safe !" — NON.
```

Le mot-clé `final` est un **verrou immuable sur la référence** de la variable. Il garantit que `list` pointera toujours vers le même objet en mémoire.

**Mais il ne garantit absolument pas que le contenu de la liste reste inchangé.**

`final` empêche ça :
```java
list = new ArrayList<>(); // ❌ Erreur de compilation — la référence est final
```

`final` n'empêche PAS ça :
```java
list.add("nouveau");      // ✅ Compile — le contenu change
list.clear();             // ✅ Compile — la liste est vidée
list.remove(0);           // ✅ Compile — un élément disparaît
```

C'est la différence entre **verrouiller la boîte aux lettres** et **verrouiller le courrier à l'intérieur**. `final` verrouille la boîte. N'importe qui peut encore changer ce qu'il y a dedans.

---

## En résumé

| Ce que vous croyez | La réalité |
|---|---|
| "C'est un lambda, c'est simple" | Le contexte d'exécution est imprévisible |
| "C'est `final`, c'est protégé" | Seule la référence est protégée, pas le contenu |
| "`.map()` et `.filter()` sont safe" | La source peut être altérée pendant l'itération |
| "Mon code marche en test" | Les tests tournent en single-thread |
| "On a jamais eu de bug" | Les race conditions sont silencieuses et aléatoires |
| "Le refactoring en lambda modernise le code" | Il peut aussi introduire des failles qui n'existaient pas |

---

## Conclusion

Les lambdas sont un outil magnifique. Elles rendent le code plus lisible, plus expressif, plus élégant. Personne ne dit qu'il faut les abandonner.

Mais un outil magnifique entre des mains qui ne comprennent pas ce qu'il fait, **c'est une arme**.

La prochaine fois que vous refactorez un vieux `for` loop en lambda, posez-vous une seule question :

> **"Qui d'autre touche à cette donnée en ce moment ?"**

Si la réponse est *"je ne sais pas"* — vous avez un problème.

Et si vous êtes en train de vous dire *"ça ne m'arrivera pas"* — relisez cet article. Parce que le développeur qui a introduit le bug en production pensait exactement la même chose.

---

*Par Pierre — développeur Java/Kotlin, architecte logiciel, et le gars qui a découvert tout ça grâce à une question d'entretien d'embauche qu'il n'a pas su répondre.*

*Et c'est peut-être la meilleure chose qui lui soit arrivée.*
