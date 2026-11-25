# LostFound Campus

Application Android (Java, Material 3) d'objets perdus et trouvés sur le campus.
Backend : Supabase gratuit (Auth, PostgreSQL avec RLS, Storage). Carte : OpenStreetMap (osmdroid).
Analyse des photos sur l'appareil : ML Kit (étiquettes, texte) et Palette (couleur dominante).

## 1. Préparer Supabase (une seule fois)

1. Créer un projet sur https://supabase.com (offre gratuite, sans carte bancaire).
2. **SQL Editor > New query** : coller tout le contenu de `supabase/lostfound_schema.sql`, puis **Run**.
   Le script crée les tables, les règles RLS, les fonctions appelées par l'app et les deux buckets
   privés (`announcement-photos`, `avatars`). Il peut être relancé sans risque.
3. **Authentication > Sign In / Providers > Email** :
   - longueur minimale du mot de passe : 8 ;
   - en développement, désactiver « Confirm email » (sinon chaque compte de test doit valider un e-mail).
4. **Authentication > Emails > Reset password** : ajouter `{{ .Token }}` dans le modèle.
   L'app demande ce code à 6 chiffres, puis le nouveau mot de passe.
5. **Project Settings > API Keys** : noter l'URL du projet et la clé *publishable* (`sb_publishable_…`).

### Données de démonstration (facultatif)

1. **SQL Editor** : exécuter `supabase/seed_demo.sql` (6 comptes `@universite.tn`, 18 annonces dont
   5 paires perdu/trouvé qui se correspondent, analyses de photos, 2 conversations). Choisir le mot de passe
   des comptes de test (REMPLACER_MOT_DE_PASSE) et le mettre dans `local.properties` (`DEMO_PASSWORD=…`).
2. Depuis la racine du projet (après avoir rempli `local.properties`) : `python supabase/seed_photos.py`
   envoie une photo d'illustration pour chaque annonce (nécessite `pip install pillow`).

> Un projet gratuit est mis en pause après une semaine sans activité : ouvrir le tableau de bord
> avant une démonstration pour le réveiller.

## 2. Configurer l'app

Copier `local.properties.example` dans `local.properties` (fichier non versionné) et remplir :

```
SUPABASE_URL=https://votre-projet.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxxxxxxxxxxxxxxx
```

Aucune clé n'est écrite dans le code : Gradle les copie dans `BuildConfig` au moment du build.

## 3. Compiler et lancer

Android Studio : **File > Sync Project with Gradle Files**, puis **Run**. En ligne de commande :

```
./gradlew assembleDebug
```

Seules les adresses `@universite.tn` peuvent créer un compte (contrôle dans l'app et dans la base).

## Architecture

| Dossier | Rôle |
|---|---|
| `data/remote` | Client Retrofit/OkHttp : en-têtes `apikey` et `Authorization`, rafraîchissement du jeton, session, erreurs traduites en français |
| `data` | Dépôts : comptes, profils, annonces, messagerie, Storage |
| `ui/*` | Écrans (Navigation Component, ViewBinding) |
| `ui/publish` | Publication : Photo Picker, appareil photo, compression, analyse ML Kit |
| `supabase/` | Script SQL de la base |

Messagerie : interrogation du serveur toutes les 4 s quand une conversation est ouverte, toutes
les 15 s pour la liste, uniquement écran visible ; l'intervalle s'allonge en cas d'erreur réseau.
Correspondances : score calculé par la base (fonction `match_candidates`), affiché à partir de 60 %.
