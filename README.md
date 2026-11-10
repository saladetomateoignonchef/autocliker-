# AutoClicker

Application Android qui clique à ta place : plusieurs points à l'écran, déclenchés en même
temps ou en chaîne, avec des délais, et un démarrage possible à une heure précise.

## Fonctionnalités

- **Plusieurs points de clic**, placés en faisant glisser des pastilles numérotées sur l'écran.
- **Clics simultanés** : les points qui tombent au même instant partent dans un seul geste
  multi-doigts.
- **Deux modes de déclenchement par point** :
  - *compté depuis le démarrage* : le point clique X temps après le lancement de la séquence ;
  - *quand le point précédent se déclenche* : le point clique X temps après le point d'avant
    (X = 0 → exactement en même temps que lui).
- **Durée d'appui réglable** par point (appui court ou appui long).
- **Répétition** : un nombre de passages, ou en boucle jusqu'à l'arrêt, avec une pause entre
  deux passages.
- **Démarrage programmé** à une heure précise (22:00, 02:00…), une fois ou tous les jours.

## Installation

1. Récupère l'APK : onglet **Actions** du dépôt → dernière exécution de *Build APK* →
   artefact `AutoClicker-debug-apk` (ou compile-le, voir plus bas).
2. Installe-le sur le téléphone (il faut autoriser l'installation depuis cette source).
3. Ouvre AutoClicker → **Activer le service** → Accessibilité → AutoClicker → activer.

> **Android 13 et plus** : pour une application installée hors Play Store, Android grise le
> réglage (« Paramètre restreint »). Ouvre les infos de l'application (appui long sur l'icône →
> Infos), menu ⋮ en haut à droite → **Autoriser les paramètres restreints**, puis recommence
> l'étape 3.

Android 8.0 minimum.

## Utilisation

1. Dans l'application, **Afficher le panneau flottant**, puis ouvre l'application à cliquer.
2. Sur le panneau : **+** ajoute un point. Fais glisser chaque pastille à l'endroit voulu.
3. Appuie sur une pastille pour régler son déclenchement, son délai et sa durée d'appui.
4. **▶** lance la séquence, **■** l'arrête. L'icône de réglages rouvre l'application
   (répétition, démarrage programmé).

### Exemple

| Point | Déclenchement | Délai | Résultat |
|-------|---------------|-------|----------|
| 1 | depuis le démarrage | 0 | clique tout de suite |
| 2 | depuis le démarrage | 0 | clique en même temps que le point 1 |
| 3 | quand le point 2 se déclenche | 2 s | clique 2 s après le point 2 |
| 4 | depuis le démarrage | 10 s | clique 10 s après le lancement |

### Démarrage programmé

Active *Démarrer à une heure précise* et choisis l'heure. À l'heure dite, la séquence démarre
toute seule, même si l'application est fermée.

Limite à connaître : l'application ne peut pas passer un écran verrouillé par code, schéma ou
empreinte. Laisse donc le téléphone déverrouillé sur l'application visée (l'option *Garder
l'écran allumé en attendant* s'en occupe) et de préférence branché.

## Compiler

Il faut le JDK 17 ou plus et le SDK Android (plateforme 35).

```bash
./gradlew assembleDebug
```

L'APK est écrit dans `app/build/outputs/apk/debug/app-debug.apk`.

```bash
./gradlew testDebugUnitTest
```

lance les tests (calcul de la séquence, heure de démarrage, écran principal, service).

`app/debug.keystore` est une clé de test, publique, qui sert uniquement à ce que toutes les
compilations produisent un APK qui se met à jour par-dessus le précédent. Elle ne doit pas
servir à publier l'application.

## Organisation du code

| Dossier | Rôle |
|---------|------|
| `model/` | Points de clic, configuration, démarrage programmé |
| `data/` | Sauvegarde de la configuration (JSON dans les préférences) |
| `engine/` | Calcul de la séquence (`SequencePlanner`) et envoi des gestes (`ClickEngine`) |
| `service/` | Service d'accessibilité, le seul composant autorisé à cliquer |
| `overlay/` | Panneau flottant et pastilles |
| `schedule/` | Alarme du démarrage programmé |
| `ui/` | Écran principal et réglages d'un point |
