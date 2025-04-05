
# EqualityDomain

Ce projet implémente un **domaine relationnel d'égalités entre identifiants** (`x == y`). Ce domaine permet de propager les égalités entre variables, avec ou sans valeur concrète.

## Objectif

L’objectif est de détecter des groupes d’égalités au sein d’un programme, et de les faire évoluer selon les affectations et conditions.

Exemple :

```
def a = 1;
def b = a;
def c = b;
```

Le domaine identifiera que `a`, `b` et `c` appartiennent au même groupe d’égalités.

----------

## Représentation

L’implémentation repose sur :

-   `FunctionalLattice<EqualityDomain, Identifier, EqualityGroup>` :

    -   Les **clés** sont des identifiants (`Identifier`)

    -   Les **valeurs** sont des groupes d’égalité (`EqualityGroup`)


Chaque `EqualityGroup` hérite de `InverseSetLattice`, et contient :

-   Un ensemble d’identifiants égaux (ex. `{a, b, c}`)

-   Une éventuelle **valeur concrète** (ex. `x = 5`)

-   Un booléen `isTop` (hérité)


----------

## Fonctionnalités

### Affectation (`assign`)

Trois cas sont pris en charge :

-   `x = y` : fusionne les groupes de `x` et `y`

-   `x = 5` : crée un groupe `{x}` avec la valeur concrète `5`

-   `x = y + 1` : casse l’égalité, on ne peut rien inférer


Chaque affectation est suivie d’un appel à `mergeGroups()` pour fusionner les groupes ayant la même valeur concrète.

----------

### Condition (`assume`)

Pour les expressions de type :

-   `x == y` : fusion des groupes de `x` et `y`, si valeurs compatibles

-   `x == 5` : assigne `5` à `x` si pas de conflit


Si la condition est fausse (valeurs concrètes incompatibles), on retourne `bottom()`.

----------

### Satisfaction (`satisfies`)

Permet de répondre à : « Est-ce que `x == y` est vrai ? »

-   `SATISFIED` si `x` et `y` sont dans le même groupe, ou ont la même valeur concrète

-   `UNKNOWN` sinon


----------

### LUB (join)

Le `lub()` fusionne les égalités compatibles entre deux états :

-   Les groupes d’égalité sont fusionnés si les identifiants apparaissent dans les deux.

-   La valeur concrète est conservée si identique dans les deux états.

-   Sinon, l’information est perdue.


----------

### Widening

Une méthode `widening()` est définie mais **commentée**, car :

-   Non nécessaire sur les tests actuels

-   L’analyse converge rapidement sans perte de précision

-   Le widening pourrait supprimer des égalités valides


----------

### Forget

La méthode `forgetIdentifier(id)` casse toutes les égalités d’un identifiant, en recréant des groupes `{id}` isolés. Cela est essentiel avant une nouvelle affectation.

----------



## Jeux de tests couverts et interprétation

### Test 1

```
def y = x;
def z = y;
def w = 3;
y = w;
```

Résultat :

```
x: [x]
y: [y, w]
z: [z]
w: [y, w]
```

**Interprétation** : L’égalité `y = x` est rompue par `y = w`. `y` et `w` sont liés par la nouvelle affectation, mais `z` reste égal à l’ancienne valeur de `y`, donc indépendant.

----------

### Test 2

```
def a = 1;
def b = 1;
def c = a;
if (a == b) {
 def d = a;
}
```

Résultat :

```
a: [a, b, c]
b: [a, b, c]
c: [a, b, c]
d: [d]
```

**Interprétation** : `a`, `b` et `c` sont tous égaux, grâce à la propagation d’égalité. `d` est isolé à la fin du programme, car défini dans une seule branche du `if`, mais dans le if on a bien : `a == b == c == d`.

----------

### Test 3

```
def y = x + 1;
def z = y;
x = 0;
```

Résultat :

```
x: [x]
y: [y, z]
z: [y, z]
```

**Interprétation** : `y = x + 1` casse toute égalité, mais `z = y` lie `y` et `z`. Puisque `x` change ensuite, il reste isolé.

----------

### Test 5

```
def x = 0;
def y = 0;
while (x < 10) {
 x = x + 1;
 y = y + 1;
}
```

Résultat :

```
x: [x, y]
y: [x, y]
```

**Interprétation** : Même si `x` et `y` sont modifiés, ils le sont de manière identique. Le domaine conserve cette égalité car elle reste valide dans toutes les itérations.

----------
## Limites connues

### Précision

-   L’analyse est **prudente** : une affectation casse potentiellement une égalité, même si elle pourrait être préservée dans certains cas

-   Les **valeurs concrètes** ne sont manipulées que lorsqu’elles sont littérales et directement accessibles


### Portée

-   Pas de distinction précise entre les **scopes locaux et globaux** : les variables définies dans une branche conditionnelle sont visibles, mais isolées

### Pas de widening actif
- Le domaine converge naturellement dans les tests actuels, car les égalités évoluent peu dans le temps.
- Activer un widening ici pourrait **casser inutilement des égalités encore valides**, et donc **réduire la précision** de l’analyse.
- Une implémentation prudente du widening serait nécessaire dans des cas de boucles plus complexes.

----------