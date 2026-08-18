# Budget — gestion de budget personnel (Android)

Application Android native (Kotlin + Jetpack Compose) de suivi de budget personnel.
Toutes les données restent sur le téléphone : aucun compte, aucun serveur, aucune connexion requise.

## Fonctionnalités

- **Dépenses par catégorie** — saisie rapide pensée pour le pouce, catégories personnalisables avec budget mensuel.
- **Historique** — navigation mois par mois, total du mois, détail de chaque dépense.
- **Statistiques** — répartition par catégorie, consommation du budget, évolution sur 6 mois, repères (moyenne, plus grosse dépense).
- **Prévisions** — projection sur 6 mois, calculée à partir de la moyenne par catégorie des 3 derniers mois complets, augmentée des échéances datées de l'agenda.
- **Agenda et rappels** — anniversaires, maintenances d'équipement, renouvellements de licence : date, récurrence, délai de rappel, montant prévu. Une vérification quotidienne notifie les échéances qui entrent dans leur fenêtre de rappel.
- **Export / import texte** — sauvegarde intégrale dans un fichier texte lisible, modifiable à la main et réimportable sans perte.

## Récupérer l'APK depuis un téléphone

Le projet est compilé par GitHub Actions ; aucun ordinateur n'est nécessaire.

1. Ouvrir l'onglet **Actions** du dépôt.
2. Sélectionner la dernière exécution réussie du workflow **Build APK**.
3. Télécharger l'artefact **budget-app-debug** (archive ZIP contenant l'APK).
4. Décompresser l'archive, puis ouvrir le fichier `.apk`. Android demandera d'autoriser l'installation depuis cette source.

En cas d'échec de compilation, une **issue** est ouverte automatiquement avec la fin du journal : elle se lit directement depuis le téléphone.

## Format d'échange texte

C'est la pierre angulaire du projet : la sauvegarde, la migration d'un téléphone à l'autre et la correction en masse passent toutes par ce fichier.

### Principes

- Encodage UTF-8, une donnée par ligne.
- Trois sections délimitées par des crochets : `[CATEGORIES]`, `[DEPENSES]`, `[EVENEMENTS]`.
- Colonnes séparées par ` | `.
- Les lignes vides et celles commençant par `#` sont ignorées à l'import.
- Montants en euros avec un point décimal (`42.50`). À la saisie, la virgule et le symbole `€` sont également acceptés.
- Dates au format `AAAA-MM-JJ`. À l'import, `JJ/MM/AAAA` et `JJ.MM.AAAA` sont aussi acceptés.
- Échappement dans une cellule : `\|` pour une barre verticale, `\n` pour un retour à la ligne, `\\` pour un antislash.

### Colonnes

`[CATEGORIES]`

| Colonne | Obligatoire | Description |
|---|---|---|
| `code` | oui | Identifiant court, référencé par les dépenses |
| `nom` | oui | Libellé affiché |
| `couleur` | non | Hexadécimal `#RRGGBB` |
| `budget_mensuel` | non | Vide si aucun budget défini |

`[DEPENSES]`

| Colonne | Obligatoire | Description |
|---|---|---|
| `date` | oui | Date de la dépense |
| `montant` | oui | Positif pour une dépense, négatif pour un remboursement |
| `categorie` | oui | `code` d'une catégorie |
| `libelle` | non | Description libre |
| `moyen` | non | CB, espèces, virement… |
| `notes` | non | Texte libre |

`[EVENEMENTS]`

| Colonne | Obligatoire | Description |
|---|---|---|
| `date` | oui | Date de la première occurrence |
| `titre` | oui | Libellé |
| `type` | non | `ANNIVERSAIRE`, `MAINTENANCE`, `LICENCE`, `ASSURANCE`, `AUTRE` |
| `recurrence` | non | `AUCUNE`, `MENSUELLE`, `TRIMESTRIELLE`, `SEMESTRIELLE`, `ANNUELLE` |
| `rappel_jours` | non | Nombre de jours d'avance pour la notification (7 par défaut) |
| `montant` | non | Renseigné, l'événement entre dans les prévisions |
| `notes` | non | Texte libre |
| `derniere_occurrence` | non | Date de la dernière occurrence traitée |

### Exemple

```
# BudgetApp export v1
# Genere le 2026-08-18

[CATEGORIES]
# code | nom | couleur | budget_mensuel
alimentation | Alimentation | #E4A11B | 400.00
loisirs | Loisirs | #4C956C |

[DEPENSES]
# date | montant | categorie | libelle | moyen | notes
2026-08-17 | 42.50 | alimentation | Courses | CB |
2026-08-18 | 12.00 | loisirs | Cinéma | | séance de 20h

[EVENEMENTS]
# date | titre | type | recurrence | rappel_jours | montant | notes | derniere_occurrence
2026-09-12 | Anniversaire Lina | ANNIVERSAIRE | ANNUELLE | 14 | 50.00 | prévoir le gâteau |
2026-11-03 | Renouvellement licence | LICENCE | ANNUELLE | 30 | 89.90 | | 2025-11-03
```

Le fichier [`exemple-export.txt`](exemple-export.txt) reprend cet exemple : il peut être importé tel quel pour prendre l'application en main.

### Tolérance à l'erreur

L'import ne s'arrête jamais sur une ligne fautive. Chaque anomalie est signalée avec son numéro de ligne dans le rapport affiché après l'import, et les lignes valides sont conservées. Une dépense référençant une catégorie absente du fichier n'est pas perdue : la catégorie est créée automatiquement.

Deux modes sont proposés :

- **Fusionner** — les données importées s'ajoutent aux données existantes ;
- **Remplacer tout** — la base est vidée avant l'import.

## Architecture

```
app/src/main/java/com/medsamet/budgetapp/
├── domain/          logique métier pure, sans dépendance Android
│   ├── Model.kt         modèles et manipulation des montants (en centimes)
│   ├── TextFormat.kt    export et import du format texte
│   └── Stats.kt         statistiques, récurrences, prévisions
├── data/            persistance SQLite écrite à la main
├── notif/           rappels quotidiens (WorkManager) et notifications
└── ui/              écrans Jetpack Compose
```

Choix techniques notables :

- **Montants en centimes** (`Long`) : aucun arrondi flottant sur des sommes d'argent.
- **SQLite direct, sans Room** : pas d'annotation processing, donc une chaîne de compilation minimale et plus robuste.
- **Aucun sélecteur de date graphique** : saisie au clavier avec raccourcis « Aujourd'hui » / « Hier », plus rapide et sans API expérimentale.
- **Logique métier isolée d'Android** : `domain/` est couvert par des tests unitaires JVM exécutés à chaque compilation.

## Tests

Les tests unitaires couvrent le cœur sensible du projet : arithmétique monétaire, aller-retour export/import, échappement des séparateurs, tolérance aux lignes fautives, calcul des récurrences (y compris l'absence de dérive en fin de mois) et prévisions.

```
gradle testDebugUnitTest
```

Ils s'exécutent automatiquement avant chaque compilation d'APK : un test rouge fait échouer la production de l'APK.

## Compilation locale (facultatif)

```
git clone https://github.com/medsamet/budget-app.git
cd budget-app
gradle assembleDebug        # ou ouvrir le projet dans Android Studio
```

Prérequis : JDK 17, SDK Android 35.

## Feuille de route

- Modification d'une dépense existante (aujourd'hui : suppression puis nouvelle saisie).
- Dépenses récurrentes automatiques.
- Graphique d'évolution par catégorie.
- Version signée en release et publication d'APK dans les *Releases* GitHub.
