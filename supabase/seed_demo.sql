-- =====================================================================
--  LostFound Campus — données de démonstration
--  À exécuter dans Supabase > SQL Editor APRÈS lostfound_schema.sql
--  (et migration_couleur_libre.sql si la base est ancienne).
--  Ré-exécutable : identifiants fixes, « on conflict do nothing ».
--
--  6 comptes de test, même mot de passe : remplacer REMPLACER_MOT_DE_PASSE ci-dessous
--  avant d'exécuter, et mettre le même dans local.properties (DEMO_PASSWORD).
--    amine.bensalah@universite.tn   sarra.trabelsi@universite.tn
--    youssef.gharbi@universite.tn   ines.jaziri@universite.tn
--    mehdi.chaabane@universite.tn   rania.bouazizi@universite.tn
--
--  18 annonces autour du campus (dont 5 paires perdu/trouvé qui se correspondent),
--  leurs analyses de photos, 2 conversations avec messages.
--  Les photos elles-mêmes sont envoyées ensuite par : python supabase/seed_photos.py
--
--  Pour tout retirer : voir la section « Nettoyage » en fin de fichier.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Comptes (auth.users + identité e-mail). Le trigger on_auth_user_created crée les profils.
-- ---------------------------------------------------------------------
with u(id, email, full_name) as (values
  ('11111111-1111-4111-8111-000000000001'::uuid, 'amine.bensalah@universite.tn', 'Amine Ben Salah'),
  ('11111111-1111-4111-8111-000000000002'::uuid, 'sarra.trabelsi@universite.tn', 'Sarra Trabelsi'),
  ('11111111-1111-4111-8111-000000000003'::uuid, 'youssef.gharbi@universite.tn', 'Youssef Gharbi'),
  ('11111111-1111-4111-8111-000000000004'::uuid, 'ines.jaziri@universite.tn',    'Ines Jaziri'),
  ('11111111-1111-4111-8111-000000000005'::uuid, 'mehdi.chaabane@universite.tn', 'Mehdi Chaabane'),
  ('11111111-1111-4111-8111-000000000006'::uuid, 'rania.bouazizi@universite.tn', 'Rania Bouazizi')
)
insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                        raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                        confirmation_token, recovery_token, email_change_token_new, email_change)
select '00000000-0000-0000-0000-000000000000', u.id, 'authenticated', 'authenticated', u.email,
       extensions.crypt('REMPLACER_MOT_DE_PASSE', extensions.gen_salt('bf')), now(),
       '{"provider":"email","providers":["email"]}'::jsonb,
       jsonb_build_object('full_name', u.full_name),
       now() - interval '20 days', now(), '', '', '', ''
  from u
on conflict (id) do nothing;

insert into auth.identities (id, user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at)
select gen_random_uuid(), au.id, au.id::text, 'email',
       jsonb_build_object('sub', au.id::text, 'email', au.email, 'email_verified', true),
       now(), now(), now()
  from auth.users au
 where au.id::text like '11111111-1111-4111-8111-%'
   and not exists (select 1 from auth.identities i where i.user_id = au.id and i.provider = 'email');

-- Filet de sécurité si le trigger de profil n'était pas encore installé.
insert into public.profiles (id, full_name, university_email)
select au.id, au.raw_user_meta_data ->> 'full_name', au.email
  from auth.users au
 where au.id::text like '11111111-1111-4111-8111-%'
on conflict (id) do nothing;


-- ---------------------------------------------------------------------
-- 2. Annonces. Campus : 36.8368 N, 10.1475 E. Dates relatives à aujourd'hui.
--    Chemin photo : <auteur>/<annonce>/1.jpg (envoyé par seed_photos.py).
-- ---------------------------------------------------------------------
with a(id, author, type, status, title, category, color, description, lat, lon, place, days_ago) as (values
  -- Paire 1 : iPhone bleu (bibliothèque)
  ('a0000000-0000-4000-8000-000000000001'::uuid, '11111111-1111-4111-8111-000000000001'::uuid, 'LOST', 'ACTIVE',
   'iPhone 13 bleu', 'PHONES', 'Bleu',
   'Oublié sur une table du 1er étage de la bibliothèque. Coque transparente, fond d''écran avec un chat.',
   36.8375, 10.1468, 'Bibliothèque centrale, 1er étage', 2),
  ('a0000000-0000-4000-8000-000000000002'::uuid, '11111111-1111-4111-8111-000000000002'::uuid, 'FOUND', 'ACTIVE',
   'Téléphone iPhone', 'PHONES', 'Bleu',
   'Trouvé près des prises électriques de la bibliothèque. Déposé à l''accueil, demandez Sarra.',
   36.8376, 10.1469, 'Bibliothèque centrale, accueil', 1),
  -- Paire 2 : clés au porte-clés rouge (restaurant / cafétéria)
  ('a0000000-0000-4000-8000-000000000003'::uuid, '11111111-1111-4111-8111-000000000003'::uuid, 'LOST', 'ACTIVE',
   'Clés avec porte-clés rouge', 'KEYS', 'Rouge',
   'Trois clés (dont une de boîte aux lettres) sur un porte-clés rouge en forme de cœur.',
   36.8359, 10.1489, 'Restaurant universitaire', 4),
  ('a0000000-0000-4000-8000-000000000004'::uuid, '11111111-1111-4111-8111-000000000004'::uuid, 'FOUND', 'ACTIVE',
   'Trousseau de clés', 'KEYS', 'Rouge',
   'Trousseau trouvé par terre devant la cafétéria, avec un porte-clés rouge.',
   36.8366, 10.1477, 'Cafétéria, entrée', 3),
  -- Paire 3 : sac à dos noir (amphi A)
  ('a0000000-0000-4000-8000-000000000005'::uuid, '11111111-1111-4111-8111-000000000005'::uuid, 'LOST', 'ACTIVE',
   'Sac à dos Eastpak noir', 'BAGS', 'Noir',
   'Contient un classeur de TD d''analyse et une trousse grise. Laissé sous un banc de l''amphi A.',
   36.8371, 10.1482, 'Amphi A, dernier rang', 6),
  ('a0000000-0000-4000-8000-000000000006'::uuid, '11111111-1111-4111-8111-000000000006'::uuid, 'FOUND', 'ACTIVE',
   'Sac à dos noir', 'BAGS', 'Noir',
   'Sac à dos noir resté dans l''amphi A après le cours de 14 h. Je l''ai confié au service de scolarité.',
   36.8372, 10.1481, 'Amphi A', 5),
  -- Paire 4 : carte étudiant (salle informatique)
  ('a0000000-0000-4000-8000-000000000007'::uuid, '11111111-1111-4111-8111-000000000004'::uuid, 'LOST', 'ACTIVE',
   'Carte étudiant', 'CARDS', 'Blanc',
   'Carte étudiant perdue pendant le TP de réseaux, probablement près des postes du fond.',
   36.8364, 10.1463, 'Salle informatique B12', 1),
  ('a0000000-0000-4000-8000-000000000008'::uuid, '11111111-1111-4111-8111-000000000003'::uuid, 'FOUND', 'ACTIVE',
   'Carte étudiant trouvée', 'CARDS', 'Blanc',
   'Carte étudiant trouvée sur un clavier de la salle B12. Contactez-moi avec votre nom pour la récupérer.',
   36.8364, 10.1464, 'Salle informatique B12', 0),
  -- Paire 5 : écouteurs blancs (terrain de sport / foyer)
  ('a0000000-0000-4000-8000-000000000009'::uuid, '11111111-1111-4111-8111-000000000006'::uuid, 'LOST', 'ACTIVE',
   'Écouteurs AirPods blancs', 'ELECTRONICS', 'Blanc',
   'Boîtier blanc avec un autocollant étoile au dos. Perdu après le match de foot.',
   36.8350, 10.1455, 'Terrain de sport, vestiaires', 8),
  ('a0000000-0000-4000-8000-000000000010'::uuid, '11111111-1111-4111-8111-000000000005'::uuid, 'FOUND', 'ACTIVE',
   'Boîtier AirPods', 'ELECTRONICS', 'Blanc',
   'Boîtier d''écouteurs blanc trouvé sur un canapé du foyer.',
   36.8357, 10.1470, 'Foyer étudiant', 7),
  -- Annonces isolées
  ('a0000000-0000-4000-8000-000000000011'::uuid, '11111111-1111-4111-8111-000000000001'::uuid, 'LOST', 'ACTIVE',
   'Portefeuille marron', 'WALLETS', 'Marron',
   'Portefeuille en cuir marron avec permis de conduire et carte bancaire. Récompense prévue.',
   36.8382, 10.1491, 'Parking principal', 10),
  ('a0000000-0000-4000-8000-000000000012'::uuid, '11111111-1111-4111-8111-000000000002'::uuid, 'FOUND', 'ACTIVE',
   'Veste en jean', 'CLOTHES', 'Bleu',
   'Veste en jean taille M oubliée sur le banc de l''arrêt de bus.',
   36.8390, 10.1478, 'Arrêt de bus, entrée nord', 3),
  ('a0000000-0000-4000-8000-000000000013'::uuid, '11111111-1111-4111-8111-000000000003'::uuid, 'LOST', 'ACTIVE',
   'Ordinateur portable Dell', 'ELECTRONICS', 'Gris',
   'Dell Latitude gris dans une housse noire. Contient mon mémoire de fin d''études, merci !',
   36.8375, 10.1468, 'Bibliothèque centrale, salle de travail', 5),
  ('a0000000-0000-4000-8000-000000000014'::uuid, '11111111-1111-4111-8111-000000000004'::uuid, 'FOUND', 'ACTIVE',
   'Lunettes de vue', 'CLOTHES', 'Noir',
   'Lunettes à monture noire dans un étui bleu, trouvées dans l''amphi A.',
   36.8371, 10.1482, 'Amphi A, premier rang', 2),
  ('a0000000-0000-4000-8000-000000000015'::uuid, '11111111-1111-4111-8111-000000000005'::uuid, 'LOST', 'ACTIVE',
   'Gourde en métal', 'OTHER', 'Vert',
   'Gourde isotherme verte avec des autocollants de montagne.',
   36.8350, 10.1455, 'Terrain de sport', 12),
  ('a0000000-0000-4000-8000-000000000016'::uuid, '11111111-1111-4111-8111-000000000006'::uuid, 'FOUND', 'ACTIVE',
   'Parapluie violet', 'OTHER', 'Violet',
   'Parapluie pliable violet laissé à l''arrêt de bus un jour de pluie.',
   36.8390, 10.1478, 'Arrêt de bus', 1),
  ('a0000000-0000-4000-8000-000000000017'::uuid, '11111111-1111-4111-8111-000000000002'::uuid, 'LOST', 'RETURNED',
   'Calculatrice Casio', 'ELECTRONICS', 'Noir',
   'Casio fx-92 avec mon nom gravé au dos. Retrouvée grâce à l''application, merci !',
   36.8364, 10.1463, 'Salle informatique B12', 15),
  ('a0000000-0000-4000-8000-000000000018'::uuid, '11111111-1111-4111-8111-000000000003'::uuid, 'FOUND', 'ACTIVE',
   'Écharpe beige', 'CLOTHES', 'Beige',
   'Écharpe en laine beige trouvée sur une chaise de la cafétéria.',
   36.8366, 10.1477, 'Cafétéria', 9)
)
insert into public.announcements (id, author_id, type, status, title, category, color, description,
                                  latitude, longitude, location_precision, event_date, photo_paths,
                                  created_at, updated_at)
select a.id, a.author, a.type, a.status, a.title, a.category, a.color, a.description,
       a.lat, a.lon, a.place, current_date - a.days_ago,
       array[a.author::text || '/' || a.id::text || '/1.jpg'],
       now() - make_interval(days => a.days_ago, hours => 2), now()
  from a
on conflict (id) do nothing;


-- ---------------------------------------------------------------------
-- 3. Analyses des photos (étiquettes ML Kit en anglais, comme l'app les enregistre)
-- ---------------------------------------------------------------------
insert into public.photo_analyses (announcement_id, photo_index, labels, label_confidence, detected_texts,
                                   dominant_color, user_confirmed)
select v.id::uuid, 0, v.labels::text[], v.conf, v.texts::text[], an.color, true
  from (values
    ('a0000000-0000-4000-8000-000000000001', '{mobile phone,gadget,electronic device}', 86, '{}'),
    ('a0000000-0000-4000-8000-000000000002', '{mobile phone,smartphone,gadget}',        91, '{iPhone}'),
    ('a0000000-0000-4000-8000-000000000003', '{key,keychain}',                          82, '{}'),
    ('a0000000-0000-4000-8000-000000000004', '{key,keychain}',                          88, '{}'),
    ('a0000000-0000-4000-8000-000000000005', '{backpack,bag}',                          79, '{EASTPAK}'),
    ('a0000000-0000-4000-8000-000000000006', '{backpack,bag,luggage}',                  84, '{EASTPAK}'),
    ('a0000000-0000-4000-8000-000000000007', '{id card}',                               73, '{CARTE ETUDIANT}'),
    ('a0000000-0000-4000-8000-000000000008', '{id card}',                               81, '{UNIVERSITE,CARTE ETUDIANT}'),
    ('a0000000-0000-4000-8000-000000000009', '{headphones,gadget}',                     77, '{}'),
    ('a0000000-0000-4000-8000-000000000010', '{headphones,gadget,electronic device}',   80, '{}'),
    ('a0000000-0000-4000-8000-000000000011', '{wallet}',                                85, '{}'),
    ('a0000000-0000-4000-8000-000000000012', '{jacket,jeans}',                          83, '{}'),
    ('a0000000-0000-4000-8000-000000000013', '{laptop,computer,gadget}',                90, '{DELL}'),
    ('a0000000-0000-4000-8000-000000000014', '{glasses}',                               88, '{}'),
    ('a0000000-0000-4000-8000-000000000015', '{bottle}',                                76, '{}'),
    ('a0000000-0000-4000-8000-000000000016', '{umbrella}',                              92, '{}'),
    ('a0000000-0000-4000-8000-000000000017', '{calculator,gadget}',                     74, '{CASIO}'),
    ('a0000000-0000-4000-8000-000000000018', '{scarf}',                                 69, '{}')
  ) as v(id, labels, conf, texts)
  join public.announcements an on an.id = v.id::uuid
on conflict (announcement_id, photo_index) do nothing;


-- ---------------------------------------------------------------------
-- 4. Conversations et messages
-- ---------------------------------------------------------------------
-- Amine (perdu) contacte Sarra (trouvé) au sujet de l'iPhone ; Youssef contacte Ines pour les clés.
insert into public.conversations (id, announcement_id, participant_a, participant_b, created_at)
values
  ('c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002',
   least('11111111-1111-4111-8111-000000000001'::uuid, '11111111-1111-4111-8111-000000000002'::uuid),
   greatest('11111111-1111-4111-8111-000000000001'::uuid, '11111111-1111-4111-8111-000000000002'::uuid),
   now() - interval '20 hours'),
  ('c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000004',
   least('11111111-1111-4111-8111-000000000003'::uuid, '11111111-1111-4111-8111-000000000004'::uuid),
   greatest('11111111-1111-4111-8111-000000000003'::uuid, '11111111-1111-4111-8111-000000000004'::uuid),
   now() - interval '2 days')
on conflict do nothing;

insert into public.messages (conversation_id, sender_id, body, sent_at, read_at)
select m.conv::uuid, m.sender::uuid, m.body, now() - m.ago, case when m.read then now() - m.ago + interval '5 minutes' end
  from (values
    ('c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-000000000001',
     'Bonjour Sarra, je crois que c''est mon iPhone ! Il a une coque transparente et un chat en fond d''écran.', interval '20 hours', true),
    ('c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-000000000002',
     'Bonjour Amine, oui c''est bien ça. Il est à l''accueil de la bibliothèque.', interval '19 hours', true),
    ('c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-000000000001',
     'Super, merci beaucoup ! Je passe demain matin vers 9 h.', interval '18 hours', true),
    ('c0000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-000000000002',
     'Parfait, je préviens la personne de l''accueil.', interval '17 hours', false),
    ('c0000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-000000000003',
     'Salut Ines, le porte-clés est en forme de cœur ? Si oui ce sont mes clés.', interval '2 days', true),
    ('c0000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-000000000004',
     'Oui, un cœur rouge ! On peut se retrouver à la cafétéria à midi ?', interval '1 day 22 hours', false)
  ) as m(conv, sender, body, ago, read)
 where not exists (select 1 from public.messages x where x.conversation_id = m.conv::uuid);


-- ---------------------------------------------------------------------
-- Vérification rapide
-- ---------------------------------------------------------------------
select (select count(*) from public.profiles       where id::text like '11111111-1111-4111-8111-%') as profils,
       (select count(*) from public.announcements  where id::text like 'a0000000-%')               as annonces,
       (select count(*) from public.photo_analyses where announcement_id::text like 'a0000000-%')  as analyses,
       (select count(*) from public.messages       where conversation_id::text like 'c0000000-%')  as messages;


-- ---------------------------------------------------------------------
-- Nettoyage (à exécuter seul pour retirer les données de démonstration)
-- ---------------------------------------------------------------------
-- Photos : Storage > announcement-photos > supprimer les dossiers 11111111-1111-4111-8111-…
-- delete from auth.users where id::text like '11111111-1111-4111-8111-%';   -- cascade : profils, annonces, messages…
