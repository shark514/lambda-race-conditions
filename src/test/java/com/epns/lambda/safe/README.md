# ✅ Tests SAFE — Les solutions qui marchent

Ces tests prouvent que les solutions de synchronisation fonctionnent : **0% de collision à tous les niveaux de charge**.

## Solutions

| Test | Solution | Comment ça marche | Quand l'utiliser |
|------|----------|-------------------|------------------|
| `CopyOnWriteArrayListTest` | `CopyOnWriteArrayList` | Copie l'array à chaque écriture | Lectures fréquentes, écritures rares |
| `SynchronizedListTest` | `Collections.synchronizedList` | Verrou mutex + `synchronized(shared)` | Drop-in replacement rapide |
| `SynchronizedBlockTest` | `synchronized` block manuel | Exclusion mutuelle avec lock dédié | Contrôle total |
| `DefensiveCopyTest` | Copie défensive | Snapshot isolé avant stream | Stream/itération longue |

## Comment lancer

Tous les tests safe :
```bash
mvn test -pl . -Dtest="com.epns.lambda.safe.*"
```

Un test spécifique avec un niveau de charge :
```bash
mvn test -Dtest=CopyOnWriteArrayListTest -Dhits=100000
```

## Quelle solution choisir ?

```
Lectures fréquentes, écritures rares ?  → CopyOnWriteArrayList
Drop-in replacement rapide ?            → Collections.synchronizedList + synchronized
Contrôle total ?                        → synchronized block manuel
Stream/itération longue ?               → Copie défensive
Haute performance ?                     → ConcurrentHashMap (pas couvert ici)
```

## ⚠️ Pièges courants

- `synchronizedList` **seul** ne suffit PAS pour `replaceAll`/`forEach`/`sort` — il faut un bloc `synchronized(list)` autour
- `CopyOnWriteArrayList` est catastrophique si les écritures sont fréquentes (copie O(n) à chaque mutation)
- La copie défensive doit elle-même être synchronisée (sinon on copie un état incohérent)
