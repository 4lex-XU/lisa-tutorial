# IntervalWithRounding

Ce projet implémente un **domaine abstrait non-relationnel d’intervalles numériques** prenant en compte les **arrondis flottants**.  
Chaque variable est associée à un intervalle `[min, max]` de type `FloatOrInf`, permettant de raisonner prudemment sur les valeurs possibles en présence d’arithmétique flottante.


## Fichiers

- IntervalWithRounding.java
- intervalWithRoundingTests.imp
---

## Objectif

- Conserver des bornes réalistes pour chaque variable
- Tenir compte des arrondis flottants et des infinis
- Gérer les opérations arithmétiques classiques
- Réagir aux conditions (ex. `x > 0`, `x == y`...)

---

## Représentation

Chaque intervalle `[low, high]` est une instance de `IntervalWithRounding`, avec :

- `FloatOrInf low` : borne inférieure
- `FloatOrInf high` : borne supérieure

Deux cas spéciaux :

- `⊤` : `[−∞, +∞]`
- `⊥` : `[+∞, −∞]` (intervalle vide ou invalide)

Le type `FloatOrInf` permet de représenter :

- Des valeurs numériques précises (`BigDecimal`),
- 1.11 != 1.1100000143051147, on représente en prenant en compte l'erreur d'arrondi
- Les cas spéciaux `+∞`, `−∞`

---

##  Fonctionnalités

### Opérations arithmétiques

-  Addition (`+`)
-  Soustraction (`-`)
-  Multiplication (`*`)
-  Division (`/`) – prudente si le dénominateur contient 0
-  Négation (`-x`)

Chaque opération prend en compte toutes les combinaisons possibles entre bornes pour garantir une **surestimation correcte**.

---

### Conditions (`assumeBinaryExpression`)

Permet d’affiner les intervalles selon une condition.  
Par exemple :

| Condition        | Mise à jour                              |
|------------------|------------------------------------------|
| `x < y`          | borne sup. de `x` ≤ borne inf. de `y`    |
| `x ≥ 3`          | borne inf. de `x` ≥ 3                     |
| `x == y`         | si chevauchement d’intervalles → égalise |
| `x != y`         | conserve les bornes actuelles            |

---

### Évaluation de constantes

Les constantes numériques (`0.0`, `1.0`, etc.) sont intégrées précisément :  
`def x = 1.11` donne l’intervalle `[1.11, 1.11]`

---

##  Lattice abstrait

Le domaine hérite de `BaseNonRelationalValueDomain` et redéfinit :

- `lub` (union prudente)
- `glb` (intersection des bornes)
- `widening` (pour convergence dans les boucles)
- `lessOrEqual`
- `top`, `bottom`, `isTop`, `isBottom`

---

##  Jeux de tests couverts et interprétation

### Basic Operations

```
def i = 2.0;
def j = -10.0;
def a = i + j;
def s = i - j;
def m = i * j;
j = j * 2.22222; // => [-22.2222, -22.2222] 
j = j * 10.97579; // =>  [-243.906200538, -243.906200538]
j = j / 10.111; // => [24.1228563483, 24.1228563483]
def count = 0.0;
count = count + 1.11; // => [1.11, 1.11]
count = count + 1.11; // => [2.22, 2.22]
count = count + 1.11; // => [3.33, 3.33]
count = count + 1.11; // => [4.44, 4.44]
count = count + 1.11; // => [5.55, 5.55]
count = count / 1.11;
```

Résultat :

```
i: [2, 2]
j: [-24.1228563483, -24.1228563483]
a: [-8, -8]
s: [12, 12]
m: [-20, -20]
count: [5, 5]
```

**Interprétation** : Toutes les opérations fonctionnent, il n'y a pas d'erreur d'arrondi.
<br>```double i = 1.11 => 1.1100000143051147 ```
<br> On corrige l'erreur d'arrondi avec BigDecimal
<br>```double i = 1.11 => 1.11 ```

----------

### While avec un paramètre

```
def x = 0.0;
while (x < a)
    x = x + 1.0;
```

Résultat :

```
a: ⊤
x: [0, +∞]
```

**Interprétation** : Boucle correctement, puis widening.

----------
### While simple de 10 itérations

```
def i = 0.0;
while (i < 10.0)
    i = i + 1.0;
```

Résultat :

```
i: [0, +∞]
OU
i: [0, 10] si wideningThreshold >= 10
```

**Interprétation** : Boucle correctement

----------

### While simple de 3 itérations avec incrément de 1.1

```
def i = 0.0;
while (i < 3)
    i = i + 1.1;
```

Résultat attendu:
```
i: [0, 3.3]
```

Résultat :

```
i: [0, 3.1]
```

**Interprétation** : Boucle correctement, mais il y a une opération de raffinement dans **assumeBinaryExpression**, qui fait que i prendra la valeur [1.1, 2] à l'avant dernière itération. <br>Donc à la sortie de boucle i = [0, 3.1]. <br>Nous n'avons pas trouvé de solution pour que i = [0, 3.3], car toutes solutions testées nous a menés vers une boucle infinie.

-------
### If simple

```
def i = 0.0;
if (i > 0.0)
    i = 1.0;
def j = i * 0.0;
```

Résultat :

```
i: [0, 1]
j: [0, 0]
```

**Interprétation** : If fonctionne corretement

----------

### Teste plusieurs valeurs avec des conditions
```
def i = 0.0;
def j = 1.0;
if (i > 1.0) {
    i = 1.0;
    j = 2.0;
}
if (i > 1.0) {
    i = i + 1.0;
    j = j + 2.0;
}
if (i > 1.0) {
    i = i + 1.0;
    j = j + 2.0;
}
def a = i + j;
```

Résultat :

```
a: [1, 9]
i: [0, 3]
j: [1, 6]
```

# Conclusion 

Toutes les opérations binaires fonctionnent en prenant en compte les erreurs d'arrondi: + - / *.
<br> Le if fonctionne tout aussi bien. 
<br> Seulement le while pose problème pour une incrémentation précise (cf. test - while incrément +1.1)

----------
# EqualityDomain

Ce projet implémente un **domaine relationnel d'égalités entre identifiants** (`x == y`). Ce domaine permet de propager les égalités entre variables, avec ou sans valeur concrète.


## Fichiers

- EqualityDomain.java
- equality.imp
---

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
# EqualityIntervalWithRoundingProductDomain

Ce projet combine deux domaines abstraits complémentaires dans un **produit cartésien** :

- `EqualityDomain` : capture des groupes d’égalités entre identifiants (`x == y`)
- `IntervalWithRounding` : capture des intervalles numériques prenant en compte les erreurs d’arrondi flottant

Ce produit permet à chaque domaine de **renforcer l’autre** grâce à une phase de **réduction**, en croisant les informations disponibles.

---

##  Objectif

L’objectif est d’améliorer la précision d’analyse en :

- **Répercutant les valeurs concrètes** de `EqualityDomain` dans `IntervalWithRounding`
- **Détectant les singletons numériques** pour les injecter comme valeurs concrètes dans `EqualityDomain`

---

##  Structure du produit

Ce domaine est un produit cartésien :

```java
ValueCartesianProduct<ValueEnvironment<IntervalWithRounding>, EqualityDomain>
```

Il combine :

- Un environnement d’intervalles (`left`)
- Un ensemble de groupes d’égalité (`right`)

---

##  Réduction croisée (`reduce()`)

Chaque fois qu’un nouvel état est créé, une réduction est effectuée :

###  De `EqualityDomain` vers `IntervalWithRounding`

- Si un identifiant possède une **valeur concrète** (`x = 5`), alors un **singleton** `[5, 5]` est inséré dans l’environnement d’intervalles.

###  De `IntervalWithRounding` vers `EqualityDomain`

- Si un identifiant a un intervalle **singleton** (`[3.0, 3.0]`), alors cette valeur devient la **valeur concrète** de son groupe d’égalité dans `EqualityDomain`.
- Si la valeur est incompatible avec une valeur existante dans le groupe : l’état devient `⊥`.

---

##  Exemples de tests

###  basicOperations

```scala
def x = 0;
def y = 0;
def z = x;
def w = z + 1.1;
def vv = 1.1;
if (vv == w) {
  def i = 2.2 / 2;
  w = y + i;
}
```

**Résultat** :

```
EqualityDomain :
  x, y, z ∈ [0, 0]
  vv, w ∈ [1.1, 1.1]

IntervalWithRounding :
  x = [0.0]
  y = [0.0]
  z = [0.0]
  w = [1.1]
  vv = [1.1]
  i = [1.1]
```

**Interprétation** :
- `z = x` → groupe d’égalité {x, z}
- `w = z + 1.1` → casse l’égalité, mais si `vv == w`, alors `w = 1.1`
- Ensuite `i = 2.2 / 2` → `i = 1.1`, donc `w = y + i = 0 + 1.1`

---

###  ifStatement

```scala
def x = 0;
def y = 0;
def z = x;
def w = x + 1;
if (x == 0){
  z = z * 1;
  w = x;
}
```

**Résultat** :

```
EqualityDomain :
  x, y, z, w ∈ [0, 0]

IntervalWithRounding :
  x = [0.0]
  y = [0.0]
  z = [0.0]
  w = [0.0]
```

**Interprétation** :
- `z = x` → groupe d’égalité
- `w = x + 1` → casse l’égalité
- Mais `x == 0`, donc la condition est vraie → on redéfinit `w = x`, donc égalité restaurée

---

###  whileStatement

```scala
def x = 0;
def y = 2.2;
def z = 0;
z = z + 1.1;
z = z + 1.1;
while (x < y) {
  x = x + 1.1;
}
```
**Résultat attendu** :

```
EqualityDomain :
x: [x]
y: [y, z]
z: [y, z]

IntervalWithRounding :
x: [0, 2.2]
y: [2.2, 2.2]
z: [2.2, 2.2]
```

**Résultat** :

```
EqualityDomain :
x: [x]
y: [y, z]
z: [y, z]

IntervalWithRounding :
x: [0, 2.3]
y: [2.2, 2.2]
z: [2.2, 2.2]
```

**Interprétation** :
- `z` accumule `1.1` deux fois → `[2.2, 2.2]`
- `x` s’incrémente de `1.1` jusqu’à atteindre `2.2`, mais le problème du while dans intervalWithRounding donnera 2.3
- À la fin, `y == z == 2.2 et x == 2.3`

---

##  Opérations définies

- `top()` et `bottom()` : envoient `top/bottom` à chaque composant
- `mk()` : instancie un produit et lance `reduce()`
- `toString()` : affiche les deux domaines

---

##  Avantages

- Améliore la **précision** de l’analyse dans les branches et boucles
- Permet de détecter les égalités **numériques concrètes**
- Compatible avec les tests `EqualityDomain` et `IntervalWithRounding`

---

## Limites

- Réduction uniquement sur des valeurs concrètes / singletons
- Si une contradiction apparaît entre domaines → retour à `⊥`
- Pas encore de propagation transitive entre plusieurs intervalles numériques
- Boucle while de intervalWithRounding

---
