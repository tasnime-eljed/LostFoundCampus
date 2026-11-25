# LostFound Campus : guide de soutenance

## 1. À retenir en une minute

LostFound Campus est une application Android **native en Java** pour publier et retrouver des objets perdus sur un campus.

- **Client :** Java 11, XML, ViewBinding, Material 3, une seule activité et 15 fragments.
- **Serveur :** Supabase, c'est-à-dire PostgreSQL, une API REST automatique, l'authentification et le stockage de photos.
- **IA :** analyse des photos **sur le téléphone** (MediaPipe et ML Kit). L'utilisateur peut toujours modifier les suggestions.
- **Sécurité :** les droits sont contrôlés **dans la base** (Row Level Security), pas seulement dans l'app.
- **Matching :** un score sur 100, calculé en SQL entre annonces « perdu » et « trouvé ».

Réponses directes :

| Question | Réponse |
|---|---|
| ListView ? | **Non**, aucune |
| Spinner ? | **Non**. Le choix de catégorie est une liste déroulante Material (`MaterialAutoCompleteTextView`) |
| RecyclerView ? | **Oui, 6**, avec 6 adaptateurs. Un 7e adaptateur sert à ViewPager2 |
| Base locale SQLite / Room ? | **Non**. Tout est en ligne ; seule la session est en SharedPreferences |
| Firebase ? | **Non**. ML Kit est embarqué et ne nécessite pas Firebase |

## 2. Fonctionnalités

| Écran | Rôle |
|---|---|
| Connexion, inscription, mot de passe oublié | Adresse `@universite.tn` obligatoire ; réinitialisation par code reçu par e-mail |
| Accueil | Fil des annonces actives, recherche, onglets, filtres par catégorie, pagination de 20 |
| Publier / modifier | 1 à 5 photos, analyse IA, catégorie, couleur, lieu, date |
| Aperçu, cadrage, analyse IA | Voir la photo, cadrer l'objet, lancer l'analyse |
| Sélection du lieu | Carte OpenStreetMap et position GPS |
| Détail | Galerie, informations, carte, « Contacter » ou « Voir les correspondances » |
| Correspondances | Annonces opposées ayant un score d'au moins 60 |
| Messages et discussion | Messagerie par polling |
| Profil, modifier le profil | Mes annonces, objet restitué, nom, photo, mot de passe |

## 3. Architecture

Architecture **en couches** avec le patron **Repository**. Elle s'inspire de MVVM, mais ce **n'est pas un MVVM complet** : seul le formulaire de publication utilise un `ViewModel`. Les autres fragments appellent les dépôts directement, avec des callbacks.

```text
┌─────────────────────────────────────────────┐
│ ui : fragments, adaptateurs, layouts XML    │
│      (PublishViewModel pour le brouillon)   │
└──────────────────────┬──────────────────────┘
                       │ ResultCallback<T>
┌──────────────────────▼──────────────────────┐
│ data : Repository, Mappers (DTO -> modèle)  │
└──────────────────────┬──────────────────────┘
                       │ Retrofit
┌──────────────────────▼──────────────────────┐
│ data/remote : AuthApi, RestApi, StorageApi  │
│               SupabaseClient, SessionStore  │
└──────────────────────┬──────────────────────┘
                       │ HTTPS + JSON + jeton
┌──────────────────────▼──────────────────────┐
│ Supabase : Auth │ PostgREST │ Storage       │
│            PostgreSQL + RLS + fonctions SQL │
└─────────────────────────────────────────────┘
```

**Pourquoi des DTO et des modèles ?** Le DTO copie le JSON (`event_date`, texte). Le modèle sert à l'écran (date en millisecondes, énumérations). Si l'API change, on adapte surtout `Mappers`.

**Pourquoi des Repository ?** Un écran demande « le fil des annonces » sans connaître ni l'URL ni Retrofit.

**Navigation :** `MainActivity` contient un `FragmentContainerView`. Le fichier `nav_graph.xml` déclare les 15 fragments ; le départ est la connexion, ou l'accueil si une session existe.

### Exemple à savoir raconter : afficher l'accueil

1. `HomeFragment.onResume()` appelle `reload()` puis `loadPage(true)`.
2. `AnnouncementRepository.feed(...)` construit les filtres (`status=eq.ACTIVE`, `limit=20`, `offset`).
3. `RestApi` envoie `GET /rest/v1/announcements`.
4. `SupabaseClient.addBearer()` renouvelle le jeton si besoin et ajoute `Authorization: Bearer`.
5. PostgreSQL applique la RLS puis renvoie du JSON.
6. Gson crée des `AnnouncementDto`, `Mappers` les convertit en `Announcement`.
7. `AnnouncementAdapter.submit()` met à jour le RecyclerView ; Glide charge les photos.

### Exemple : publier une annonce

1. `PublishFragment` vérifie les champs obligatoires.
2. `AnnouncementPublisher` compresse chaque photo (1 600 px maximum, environ 1,8 Mo) et l'envoie dans Storage.
3. Il enregistre la ligne dans `announcements`, avec un identifiant choisi par l'app. C'est un *upsert* : réessayer ne crée pas de doublon.
4. Il enregistre les analyses dans `photo_analyses`, puis ouvre le détail.

## 4. Listes et composants d'interface

### RecyclerView

| Écran (layout) | Adaptateur | Élément affiché | Disposition |
|---|---|---|---|
| Accueil (`fragment_home`) | `AnnouncementAdapter` | `item_announcement` | Verticale, paginée |
| Profil (`fragment_profile`) | `MyAnnouncementAdapter` | `item_my_announcement` | Verticale |
| Correspondances (`fragment_matches`) | `MatchAdapter` | `item_match` | Verticale |
| Messages (`fragment_messages`) | `ConversationAdapter` | `item_conversation` | Verticale, avec séparateurs |
| Discussion (`fragment_chat`) | `MessageAdapter` | `item_message_sent` / `item_message_received` | Verticale, 2 types de vues |
| Photos à publier (`fragment_publish`) | `PhotoThumbAdapter` | `item_photo_thumb` / `item_add_photo` | **Horizontale**, 2 types de vues |

`PhotoPagerAdapter` (`item_photo_page`) hérite aussi de `RecyclerView.Adapter`, mais il alimente le **ViewPager2** de la galerie du détail.

**Comment fonctionne un adaptateur :**

| Méthode | Rôle |
|---|---|
| `onCreateViewHolder` | Crée la vue d'un élément |
| `onBindViewHolder` | Remplit la vue avec les données de la position |
| `getItemCount` | Donne le nombre d'éléments |
| `getItemViewType` | Choisit le type : message envoyé ou reçu, photo ou bouton « Ajouter » |

Le `ViewHolder` garde les vues d'un élément ; le `LayoutManager` place les éléments ; les vues sorties de l'écran sont **recyclées**.

### Pourquoi pas ListView ni Spinner ?

- **ListView :** plus ancienne. RecyclerView impose le ViewHolder, sépare la disposition, gère l'horizontal et permet de ne rafraîchir qu'un élément.
- **Spinner :** la liste Material s'intègre au champ du formulaire (libellé, message d'erreur, « Suggestion IA »). Elle est remplie dans `PublishFragment.setUpFields()` avec `setSimpleItems(categories)`.

### Autres composants

| Composant | Où |
|---|---|
| `BottomNavigationView` | Barre basse de `activity_main` |
| `TabLayout` | Tous/Perdus/Trouvés, J'ai perdu/J'ai trouvé, points de la galerie |
| `ChipGroup` | Catégories, filtres du profil, couleurs détectées |
| `ViewPager2` | Galerie du détail |
| `MaterialDatePicker` | Date de l'événement, dans `PublishFragment` |
| `MaterialAlertDialogBuilder` | Confirmations, caméra ou galerie |
| `Snackbar` | Messages courts |
| `MapView` (osmdroid) | Cartes |
| Vues personnalisées | `CropView` (cadre), `AiScanView` (animation d'analyse) |

## 5. Contenu des fichiers Java

Racine : `app/src/main/java/com/example/lostfoundcampus/` (84 fichiers Java).

### Démarrage

| Fichier | Rôle |
|---|---|
| `LostFoundApp` | Démarre l'application et initialise `SupabaseClient` |
| `MainActivity` | Activité unique : navigation, barre basse, session expirée |

### `data` : les dépôts

| Fichier | Rôle |
|---|---|
| `AuthRepository` | Inscription, connexion, déconnexion, mot de passe |
| `AnnouncementRepository` | Fil, détail, ajout, modification, analyses, correspondances |
| `MessageRepository` | Conversations, lecture et envoi de messages |
| `ProfileRepository` | Profils, avec cache mémoire |
| `StorageRepository` | Envoi, suppression et URL authentifiée des photos |
| `Mappers` | DTO vers modèle |
| `Calls` | Transforme un appel Retrofit en `ResultCallback` |
| `ResultCallback` | Interface `onSuccess` / `onError` |

### `data/remote` : le réseau

| Fichier | Rôle |
|---|---|
| `SupabaseClient` | Crée OkHttp et Retrofit, ajoute les en-têtes, renouvelle le jeton |
| `AuthApi`, `RestApi`, `StorageApi` | Déclarent les routes HTTP avec les annotations Retrofit |
| `SessionStore` | Garde les jetons dans des SharedPreferences privées |
| `ApiErrors` | Traduit les erreurs en français |
| `dto/*` | `AnnouncementDto`, `MessageDto`, `MatchDto`, `ProfileDto`, `PhotoAnalysisDto`, `ConversationRowDto`, `AuthSession`, `AuthUser`, `ApiErrorBody` |

### `data/model` : les objets de l'app

`Announcement`, `User`, `Message`, `ConversationSummary`, `Match`, `CampusLocation`, `ImageAnalysis`, `DetectedColor`, et les énumérations `Category` (8 valeurs), `AnnouncementType` (LOST, FOUND) et `AnnouncementStatus` (ACTIVE, RETURNED, ARCHIVED). `ColorSwatch` est un **reste de l'étape 1, plus utilisé**.

### `ui` : les écrans

| Paquet | Fichiers |
|---|---|
| `auth` | `LoginFragment`, `RegisterFragment`, `ForgotPasswordFragment` |
| `home` | `HomeFragment`, `AnnouncementAdapter` |
| `detail` | `AnnouncementDetailFragment`, `PhotoPagerAdapter` |
| `matches` | `MatchesFragment`, `MatchAdapter` |
| `messages` | `MessagesFragment`, `ConversationAdapter`, `ChatFragment`, `MessageAdapter`, `IntroMessageTemplate` (brouillon du premier message) |
| `profile` | `ProfileFragment`, `EditProfileFragment`, `MyAnnouncementAdapter` |
| `map` | `LocationPickerFragment`, `MapHelper`, `MiniMapController`, `LocationHelper` (GPS) |
| `common` | `Nav` (navigation), `ImageLoader` (Glide), `UiUtils` (badges, avatars) |

Paquet `publish` :

| Fichier | Rôle |
|---|---|
| `PublishFragment` | Formulaire |
| `PublishViewModel` | Brouillon partagé entre les écrans ; survit à une rotation |
| `AnnouncementPublisher` | Envoi des photos puis de l'annonce |
| `PhotoAnalyzer` | Chef d'orchestre de l'IA |
| `ObjectVision` | Modèles MediaPipe |
| `VisionLabels` | Classes utiles des modèles, vers une étiquette commune |
| `ObjectClassifier` | Choix de la catégorie et de la marque |
| `ColorMatcher` | Couleurs dominantes |
| `CropFragment`, `CropView` | Cadrage de l'objet |
| `PhotoPreviewFragment`, `AiAnalysisFragment`, `AiScanView` | Aperçu et écran d'analyse |
| `PhotoThumbAdapter`, `CameraFiles` | Miniatures et fichier de l'appareil photo |

### `util`

`AppExecutors` (threads), `Poller` (interrogation périodique), `PhotoCompressor`, `ColorNames` (37 noms de couleurs), `DateUtil`, `Validators`, `InsetsUtil` (bord à bord), `MotionUtil` (animations réduites).

### Ressources et configuration Android

| Fichier | Rôle |
|---|---|
| `AndroidManifest.xml` | Permissions Internet, état du réseau, localisation ; activité ; `FileProvider` |
| `res/layout` | 32 fichiers XML d'interface |
| `res/navigation/nav_graph.xml` | Les 15 destinations |
| `res/menu/bottom_nav_menu.xml` | Accueil, Publier, Messages, Profil |
| `res/values` | Textes, couleurs, dimensions, styles, thèmes, constantes |
| `res/xml/file_paths.xml` | Dossier partagé avec l'appareil photo |
| `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Excluent les jetons des sauvegardes |
| `assets/*.tflite` | Deux modèles d'IA (14,7 Mo) |
| `app/build.gradle.kts` | Versions Android, dépendances, lecture des clés |
| `gradle/libs.versions.toml` | Versions des bibliothèques |
| `settings.gradle.kts` | Dépôts Maven et module `:app` |

Compilation : `minSdk 24`, `targetSdk` et `compileSdk 37`, Gradle 9.6.0, plugin Android 9.4.1.

## 6. Configuration de la base de données

### Les fichiers

| Fichier | Rôle |
|---|---|
| `local.properties` | URL et clé publique Supabase. **Non envoyé sur GitHub** |
| `local.properties.example` | Modèle à copier |
| `app/build.gradle.kts` | Lit ces valeurs et crée des constantes `BuildConfig` |
| `data/remote/SupabaseClient.java` | Utilise `BuildConfig` pour se connecter |
| `supabase/lostfound_schema.sql` | Crée **toute** la base |
| `supabase/migration_couleur_libre.sql` | Met à jour une ancienne base : couleur en texte libre |
| `supabase/seed_demo.sql` | 6 comptes, 18 annonces, analyses, 2 conversations |
| `supabase/seed_photos.py` | Envoie les photos de démo |

### De la clé à la requête

1. Dans `local.properties` :

   ```properties
   SUPABASE_URL=https://votre-projet.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
   ```

2. `app/build.gradle.kts` les lit et les ajoute à `BuildConfig` :

   ```kotlin
   buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL").trimEnd('/')}\"")
   ```

3. `SupabaseClient` crée trois API : `/auth/v1/`, `/rest/v1/` et `/storage/v1/`.
4. Chaque requête porte `apikey` (identifie le projet). Les requêtes de données portent aussi `Authorization: Bearer <jeton>` (identifie l'utilisateur).

**La clé publishable n'est pas un secret** : elle se trouve dans l'APK. C'est la RLS qui protège les données. La clé *secrète* (`service_role`) ne doit jamais être dans l'app.

### Tables

```text
auth.users 1──1 profiles 1──* announcements 1──* photo_analyses
                   │                │
                   │                └──* conversations 1──* messages
                   └─────────(participants, expéditeur)─────┘
```

| Table | Contenu | Règles principales |
|---|---|---|
| `profiles` | Nom, e-mail, photo | E-mail unique en `@universite.tn` ; `id` = compte Auth |
| `announcements` | Type, statut, titre, catégorie, couleur, lieu, date, photos | 1 à 5 photos ; titre de 80 caractères max, description de 300 max |
| `photo_analyses` | Étiquettes, confiance, textes, couleur | Une ligne par annonce et par photo |
| `conversations` | Annonce et deux participants | Pas de doublon pour la même paire et la même annonce |
| `messages` | Expéditeur, texte, date, lecture | 1 à 2 000 caractères ; `id` croissant |

Suppression : supprimer un compte supprime son profil, ses annonces et ses messages (`on delete cascade`). Supprimer une annonce garde la conversation (`on delete set null`).

### Triggers

| Trigger | Effet |
|---|---|
| `on_auth_user_created` | Crée le profil à l'inscription |
| `profiles_updated_at` | Met à jour la date de modification |
| `announcements_guard` | Refuse une date future (1 jour de tolérance) ; fige l'auteur et la date de création |
| `messages_after_insert` | Met à jour l'aperçu de la conversation |

### Fonctions SQL

| Fonction | Rôle | Appelée par l'app |
|---|---|---|
| `open_conversation` | Ouvre ou crée une conversation | Oui (RPC) |
| `my_conversations` | Liste avec nombre de non lus | Oui (RPC) |
| `mark_conversation_read` | Marque comme lus | Oui (RPC) |
| `match_candidates` | Calcule les correspondances | Oui (RPC) |
| `is_participant` | Vérifie l'accès à une conversation | Utilisée par la RLS |

### Row Level Security

| Donnée | Lecture | Écriture |
|---|---|---|
| Profils | Tout utilisateur connecté | Son propre nom et sa photo |
| Annonces | Connectés ; archivées : auteur seul | Auteur seul |
| Analyses | Connectés | Auteur de l'annonce |
| Conversations | Les deux participants | Via `open_conversation` |
| Messages | Participants | Participants, en leur nom |
| Fichiers Storage | Connectés | Chacun dans son dossier `<uid>/` |

Les visiteurs non connectés n'ont **aucun droit** sur ces tables.

**Stockage :** deux buckets privés, JPEG seulement : `announcement-photos` (2 Mo) et `avatars` (1 Mo).

**Index :** fil, auteur, catégorie, conversations et messages, plus un index trigramme (`pg_trgm`) sur le titre pour la recherche par morceau de texte.

## 7. Calcul des correspondances

Fonction `match_candidates` : compare une annonce **active de l'utilisateur** avec les annonces actives de type opposé d'autres auteurs.

| Critère | Points |
|---|---|
| Même catégorie | 30 |
| Étiquettes communes (indice de Jaccard) | 0 à 25 |
| Distance : 100 m ou moins = 20, 1 km ou plus = 0 | 0 à 20 |
| Couleur identique (15) ou premier mot identique (8) | 0 à 15 |
| Dates cohérentes | 10, 5 ou 0 |
| **Total** | **100** |

- **Jaccard** = étiquettes communes ÷ toutes les étiquettes. Exemple : 2 communes sur 4 = 0,5, donc 13 points (12,5 arrondi).
- **Distance** : formule de Haversine, qui tient compte de la courbure de la Terre.
- Seuil d'affichage : **60**. Un score est un indice, pas une preuve.

**Pourquoi en SQL ?** Le calcul se fait près des données, sans télécharger toutes les annonces.

## 8. Intelligence artificielle

1. **EfficientDet-Lite2** (MediaPipe, classes COCO) **localise** l'objet.
2. **EfficientNet-Lite2** (MediaPipe, 1 000 classes ImageNet) **classe** la zone.
3. **ML Kit Image Labeling** donne un avis secondaire.
4. **ML Kit Text Recognition** lit le texte (OCR) : marque, « carte étudiant ».
5. **Palette** extrait les couleurs ; `ColorNames` trouve le nom le plus proche dans l'espace **CIELAB**.
6. `ObjectClassifier` garde l'étiquette la plus sûre, avec un bonus si l'OCR la confirme.

**Pourquoi sur le téléphone ?** Gratuit, pas de clé d'API, photos non envoyées à une IA externe.

**Résultats mesurés, à présenter honnêtement :** 13 catégories justes sur 17 photos, contre 5 sur 17 avant de corriger la fusion des modèles. Les réglages ont été faits **sur ces mêmes photos** : ce n'est pas une évaluation indépendante. La mesure vient d'un émulateur.

**Limites :** clés, écouteurs, chargeurs et calculatrices sont absents des classes utilisées ; les cartes sont reconnues surtout par leur texte ; les couleurs sont approximatives.

**Vocabulaire :** l'app fait de l'**inférence** avec des modèles **pré-entraînés**. Nous n'avons pas entraîné de modèle.

## 9. Autres mécanismes

| Mécanisme | Explication |
|---|---|
| Session | Jeton d'accès renouvelé 60 s avant expiration, ou après une erreur 401 |
| Threads | Jamais de réseau sur le fil principal : Retrofit asynchrone, `AppExecutors` pour les images |
| Polling | Discussion toutes les 4 s, liste toutes les 15 s ; délai doublé en cas d'erreur, 60 s max ; arrêt écran caché |
| Photos | Orientation EXIF corrigée, compression, cache Glide |
| Localisation | `FusedLocationProviderClient`, après permission |
| Carte | osmdroid et OpenStreetMap, sans clé |
| Tests | 9 tests JUnit locaux (dont l'exemple d'Android Studio) et des tests instrumentés (modèles, images privées) |

### Bibliothèques

| Besoin | Bibliothèque | Version |
|---|---|---|
| Réseau | Retrofit + Gson / OkHttp | 3.0.0 / 4.12.0 |
| Images | Glide | 5.0.5 |
| Interface | Material | 1.14.0 |
| Navigation | Navigation Component | 2.9.8 |
| Galerie | ViewPager2 | 1.1.0 |
| IA | MediaPipe Tasks Vision | 0.10.35 |
| IA | ML Kit étiquettes / texte | 17.0.9 / 16.0.1 |
| Couleurs | Palette | 1.0.0 |
| Carte | osmdroid | 6.1.20 |
| GPS | Play Services Location | 21.3.0 |

## 10. Questions probables du jury

**Pourquoi Supabase plutôt que Firebase ?**
Données relationnelles, contraintes SQL, RLS, et calcul du matching directement en SQL.

**Comment communiquez-vous avec la base ?**
Jamais en connexion PostgreSQL directe : l'app appelle l'API REST de Supabase en HTTPS avec Retrofit.

**Que se passe-t-il si quelqu'un extrait la clé de l'APK ?**
Elle est publique. Sans compte, la RLS et les droits SQL refusent l'accès ; avec un compte, il n'a que les droits de ce compte.

**Où sont les mots de passe ?**
Supabase Auth les stocke hachés. L'app ne garde que les jetons, en SharedPreferences privées. Elles ne sont pas chiffrées par l'app, mais protégées par le bac à sable d'Android et exclues des sauvegardes.

**Pourquoi le polling plutôt que le temps réel ?**
Plus simple et suffisant pour une démo. Inconvénient : plusieurs secondes de délai et des requêtes inutiles. Évolution : Supabase Realtime.

**Que se passe-t-il à la rotation ?**
Le fragment est recréé. Le brouillon et l'envoi en cours restent dans `PublishViewModel`.

**L'app fonctionne-t-elle hors ligne ?**
Non. Glide peut montrer des images en cache, mais les annonces demandent Internet.

**Pourquoi `notifyDataSetChanged()` ?**
Simple pour de petites listes. Amélioration : `ListAdapter` avec `DiffUtil`.

**Comment évitez-vous les doublons de conversation ?**
Paire de participants ordonnée et index unique.

**Pourquoi des identifiants UUID ?**
L'app peut les créer avant l'envoi, ce qui permet de réessayer sans doublon.

**Que signifient les codes RG dans le code ?**
Des règles de gestion du rapport : RG03, l'IA ne fait que suggérer ; RG04 et RG05, les correspondances ; RG02, les droits de l'auteur. Vérifie leurs intitulés exacts dans ton rapport.

## 11. Limites et améliorations

| Limite | Amélioration |
|---|---|
| IA incomplète | Entraîner un modèle sur des objets du campus |
| Messagerie par polling | Supabase Realtime et notifications push |
| Un message renvoyé après coupure peut être doublé | Identifiant client unique en base |
| `sent_at` et `read_at` peuvent être choisis par l'expéditeur | Restreindre le droit d'insertion |
| Les e-mails des profils sont lisibles par tout connecté | Vue publique limitée au nom et à la photo |
| Message en échec perdu si on quitte la discussion | Base locale Room |
| Photo perdue si Android ferme l'app pendant la prise | `SavedStateHandle` |
| Suggestions parfois perdues si on tourne l'écran pendant l'analyse | Déplacer l'analyse dans le ViewModel |
| APK debug lourd (environ 160 Mo) | App Bundle par type de processeur, R8 |
| Pas de modération | Signalement et rôle administrateur |

Sans « Confirm email » dans Supabase, n'importe qui peut s'inscrire avec une adresse `@universite.tn` qui ne lui appartient pas.

## 12. Plan de démonstration (5 minutes)

1. **Connexion** avec Amine.
2. **Accueil :** onglets, filtre « Téléphones », recherche.
3. **Profil > iPhone 13 mini bleu > Voir les correspondances :** expliquer le score de 88.
4. **Contacter** Sarra, puis la discussion.
5. **Publier :** photo d'un ordinateur portable ou d'un sac à dos, analyse, modification d'une suggestion.
6. **Code :** `HomeFragment`, `AnnouncementAdapter`, `SupabaseClient`, puis `match_candidates` en SQL.

**La veille :** ouvrir le tableau de bord Supabase (un projet gratuit se met en pause), tester le Wi-Fi, charger le téléphone, préparer des captures de secours. Ne pas faire la démo IA avec des clés ou des écouteurs.

## 13. Vocabulaire

| Terme | Définition |
|---|---|
| Fragment | Partie d'interface avec son propre cycle de vie |
| ViewBinding | Classes générées pour accéder aux vues sans `findViewById` |
| ViewModel | Garde des données d'écran pendant une rotation |
| DTO | Objet qui reproduit le format réseau |
| REST | API par HTTP : GET, POST, PATCH, DELETE |
| RPC | Appel d'une fonction SQL par l'API |
| JWT | Jeton signé qui identifie l'utilisateur |
| RLS | Filtre de sécurité par ligne dans PostgreSQL |
| Trigger | Code SQL exécuté automatiquement |
| Upsert | Insérer, ou mettre à jour si la ligne existe |
| OCR | Lecture de texte dans une image |
| Inférence | Utilisation d'un modèle déjà entraîné |
