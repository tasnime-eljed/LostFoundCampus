-- =====================================================================
--  LostFound Campus — base de données Supabase (PostgreSQL + RLS + Storage)
--  À exécuter en une fois dans Supabase : SQL Editor > New query > Run.
--  Le script est ré-exécutable (create if not exists / drop … if exists).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 0. Extensions
-- ---------------------------------------------------------------------
create extension if not exists pg_trgm with schema extensions;   -- recherche par mot-clé (ILIKE indexé)


-- ---------------------------------------------------------------------
-- 1. Fonction utilitaire : mise à jour automatique de updated_at
-- ---------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;


-- ---------------------------------------------------------------------
-- 2. Profils (1 ligne par compte Supabase Auth)
-- ---------------------------------------------------------------------
create table if not exists public.profiles (
  id               uuid primary key references auth.users (id) on delete cascade,
  full_name        text not null check (char_length(btrim(full_name)) between 1 and 80),
  university_email text not null unique
                   check (university_email ~* '^[^@[:space:]]+@universite\.tn$'),
  photo_path       text,                                   -- chemin dans le bucket « avatars »
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

drop trigger if exists profiles_updated_at on public.profiles;
create trigger profiles_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

-- Création automatique du profil à l'inscription.
-- L'app envoie le nom dans les métadonnées : { "data": { "full_name": "…" } }.
-- Une adresse hors @universite.tn fait échouer l'inscription (contrainte ci-dessus).
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, full_name, university_email)
  values (
    new.id,
    coalesce(nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''), split_part(new.email, '@', 1)),
    lower(new.email)
  );
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();


-- ---------------------------------------------------------------------
-- 3. Annonces
-- ---------------------------------------------------------------------
create table if not exists public.announcements (
  id                 uuid primary key default gen_random_uuid(),
  author_id          uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  type               text not null check (type in ('LOST', 'FOUND')),
  status             text not null default 'ACTIVE' check (status in ('ACTIVE', 'RETURNED', 'ARCHIVED')),
  title              text not null check (char_length(btrim(title)) between 1 and 80),
  category           text not null check (category in
                       ('PHONES', 'KEYS', 'BAGS', 'WALLETS', 'CARDS', 'ELECTRONICS', 'CLOTHES', 'OTHER')),
  color              text check (char_length(btrim(color)) between 1 and 30),   -- saisie libre
  description        text not null default '' check (char_length(description) <= 300),
  latitude           double precision not null check (latitude between -90 and 90),
  longitude          double precision not null check (longitude between -180 and 180),
  location_precision text check (char_length(location_precision) <= 80),
  event_date         date not null,
  photo_paths        text[] not null check (cardinality(photo_paths) between 1 and 5),
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);

create index if not exists announcements_feed_idx     on public.announcements (status, type, created_at desc);
create index if not exists announcements_author_idx   on public.announcements (author_id, created_at desc);
create index if not exists announcements_category_idx on public.announcements (category);
create index if not exists announcements_title_trgm   on public.announcements using gin (title extensions.gin_trgm_ops);

-- Règles qui ne s'expriment pas en CHECK : date non future, auteur et date de création figés.
create or replace function public.announcements_guard()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.event_date > current_date + 1 then             -- +1 jour : tolérance de fuseau horaire
    raise exception 'La date de l''événement ne peut pas être dans le futur.' using errcode = '22023';
  end if;
  if tg_op = 'UPDATE' then
    new.author_id  := old.author_id;
    new.created_at := old.created_at;
    new.updated_at := now();
  end if;
  return new;
end;
$$;

drop trigger if exists announcements_guard on public.announcements;
create trigger announcements_guard
  before insert or update on public.announcements
  for each row execute function public.announcements_guard();


-- ---------------------------------------------------------------------
-- 4. Analyse de chaque photo (résultats ML Kit confirmés par l'utilisateur)
-- ---------------------------------------------------------------------
create table if not exists public.photo_analyses (
  id               uuid primary key default gen_random_uuid(),
  announcement_id  uuid not null references public.announcements (id) on delete cascade,
  photo_index      smallint not null check (photo_index between 0 and 4),
  labels           text[] not null default '{}',             -- étiquettes normalisées en minuscules
  label_confidence smallint check (label_confidence between 0 and 100),
  detected_texts   text[] not null default '{}',
  dominant_color   text check (char_length(dominant_color) <= 30),             -- ex. « Lilas »
  user_confirmed   boolean not null default false,
  analyzed_at      timestamptz not null default now(),
  unique (announcement_id, photo_index)
);


-- ---------------------------------------------------------------------
-- 5. Conversations (une par annonce et par paire d'utilisateurs)
-- ---------------------------------------------------------------------
create table if not exists public.conversations (
  id                   uuid primary key default gen_random_uuid(),
  announcement_id      uuid references public.announcements (id) on delete set null,
  participant_a        uuid not null references public.profiles (id) on delete cascade,
  participant_b        uuid not null references public.profiles (id) on delete cascade,
  last_message_at      timestamptz not null default now(),
  last_message_preview text,
  created_at           timestamptz not null default now(),
  check (participant_a < participant_b)                     -- paire ordonnée : pas de doublon A/B – B/A
);

create unique index if not exists conversations_pair_uq
  on public.conversations (announcement_id, participant_a, participant_b);
create index if not exists conversations_a_idx on public.conversations (participant_a, last_message_at desc);
create index if not exists conversations_b_idx on public.conversations (participant_b, last_message_at desc);


-- ---------------------------------------------------------------------
-- 6. Messages (id croissant : le polling demande « id > dernier id reçu »)
-- ---------------------------------------------------------------------
create table if not exists public.messages (
  id              bigint generated always as identity primary key,
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  sender_id       uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  body            text not null check (char_length(btrim(body)) between 1 and 2000),
  sent_at         timestamptz not null default now(),
  read_at         timestamptz
);

create index if not exists messages_conversation_idx on public.messages (conversation_id, id);
create index if not exists messages_unread_idx on public.messages (conversation_id) where read_at is null;

-- Met à jour l'aperçu et la date du dernier message de la conversation.
create or replace function public.messages_after_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.conversations
     set last_message_at = new.sent_at,
         last_message_preview = left(new.body, 140)
   where id = new.conversation_id;
  return new;
end;
$$;

drop trigger if exists messages_after_insert on public.messages;
create trigger messages_after_insert
  after insert on public.messages
  for each row execute function public.messages_after_insert();


-- ---------------------------------------------------------------------
-- 7. Fonctions appelées par l'app (RPC : POST /rest/v1/rpc/<nom>)
-- ---------------------------------------------------------------------

-- L'utilisateur courant participe-t-il à la conversation ? (utilisé par les politiques RLS)
create or replace function public.is_participant(p_conversation uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.conversations c
     where c.id = p_conversation
       and (select auth.uid()) in (c.participant_a, c.participant_b)
  );
$$;

-- « Contacter l'utilisateur » : ouvre la conversation existante ou la crée. Renvoie son id.
create or replace function public.open_conversation(p_announcement uuid)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_me     uuid := auth.uid();
  v_author uuid;
  v_a      uuid;
  v_b      uuid;
  v_id     uuid;
begin
  if v_me is null then
    raise exception 'Connexion requise.' using errcode = '28000';
  end if;
  select author_id into v_author
    from public.announcements
   where id = p_announcement and status = 'ACTIVE';
  if v_author is null then
    raise exception 'Annonce introuvable ou plus active.' using errcode = 'P0002';
  end if;
  if v_author = v_me then
    raise exception 'Vous êtes l''auteur de cette annonce.' using errcode = '22023';
  end if;

  v_a := least(v_me, v_author);
  v_b := greatest(v_me, v_author);

  insert into public.conversations (announcement_id, participant_a, participant_b)
  values (p_announcement, v_a, v_b)
  on conflict (announcement_id, participant_a, participant_b) do nothing
  returning id into v_id;

  if v_id is null then
    select id into v_id
      from public.conversations
     where announcement_id = p_announcement and participant_a = v_a and participant_b = v_b;
  end if;
  return v_id;
end;
$$;

-- Marque comme lus les messages reçus dans une conversation.
create or replace function public.mark_conversation_read(p_conversation uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not public.is_participant(p_conversation) then
    raise exception 'Conversation inaccessible.' using errcode = '42501';
  end if;
  update public.messages
     set read_at = now()
   where conversation_id = p_conversation
     and sender_id <> auth.uid()
     and read_at is null;
end;
$$;

-- Liste des conversations de l'utilisateur, prête pour l'écran Messages.
create or replace function public.my_conversations(p_search text default null)
returns table (
  conversation_id      uuid,
  announcement_id      uuid,
  announcement_title   text,
  announcement_type    text,
  announcement_photo   text,
  other_user_id        uuid,
  other_full_name      text,
  other_photo_path     text,
  last_message_at      timestamptz,
  last_message_preview text,
  unread_count         integer
)
language sql
stable
security invoker
set search_path = ''
as $$
  select c.id,
         c.announcement_id,
         a.title,
         a.type,
         a.photo_paths[1],
         o.id,
         o.full_name,
         o.photo_path,
         c.last_message_at,
         c.last_message_preview,
         (select count(*)::integer
            from public.messages m
           where m.conversation_id = c.id
             and m.sender_id <> (select auth.uid())
             and m.read_at is null)
    from public.conversations c
    join public.profiles o
      on o.id = case when c.participant_a = (select auth.uid()) then c.participant_b else c.participant_a end
    left join public.announcements a on a.id = c.announcement_id
   where (select auth.uid()) in (c.participant_a, c.participant_b)
     and (coalesce(btrim(p_search), '') = ''
          or o.full_name ilike '%' || btrim(p_search) || '%'
          or a.title     ilike '%' || btrim(p_search) || '%')
   order by c.last_message_at desc;
$$;

-- Correspondances (RG04, RG05) : score sur 100 entre une annonce de l'utilisateur
-- et les annonces ACTIVE du type opposé. Barème :
--   catégorie identique 30 · étiquettes communes 25 (indice de Jaccard) · couleur 15
--   distance 20 (≤ 100 m : 20, ≥ 1 km : 0, linéaire entre) · dates cohérentes 10 (5 si proches)
create or replace function public.match_candidates(p_announcement uuid, p_threshold integer default 60)
returns table (announcement_id uuid, score integer)
language sql
stable
security invoker
set search_path = ''
as $$
  with src as (
    select a.*,
           coalesce((select array_agg(distinct lower(l))
                       from public.photo_analyses pa, unnest(pa.labels) as l
                      where pa.announcement_id = a.id), '{}') as lbls
      from public.announcements a
     where a.id = p_announcement
       and a.author_id = (select auth.uid())             -- seul l'auteur consulte ses correspondances
       and a.status = 'ACTIVE'
  ),
  cand as (
    select c.*,
           coalesce((select array_agg(distinct lower(l))
                       from public.photo_analyses pa, unnest(pa.labels) as l
                      where pa.announcement_id = c.id), '{}') as lbls
      from public.announcements c
      join src on c.type <> src.type and c.author_id <> src.author_id
     where c.status = 'ACTIVE'
  ),
  parts as (
    select c.id,
           case when c.category = s.category then 30 else 0 end as p_category,
           case when cardinality(s.lbls) = 0 or cardinality(c.lbls) = 0 then 0
                else round(25.0
                     * cardinality(array(select unnest(s.lbls) intersect select unnest(c.lbls)))
                     / cardinality(array(select unnest(s.lbls) union     select unnest(c.lbls))))
           end as p_labels,
           case when c.color is null or s.color is null then 0
                when lower(btrim(c.color)) = lower(btrim(s.color)) then 15
                -- « Bleu marine » / « Bleu », « Gris anthracite » / « Gris » : même famille
                when split_part(lower(btrim(c.color)), ' ', 1) = split_part(lower(btrim(s.color)), ' ', 1) then 8
                else 0 end as p_color,
           2 * 6371000 * asin(least(1.0, sqrt(
               power(sin(radians(c.latitude - s.latitude) / 2), 2)
             + cos(radians(s.latitude)) * cos(radians(c.latitude))
             * power(sin(radians(c.longitude - s.longitude) / 2), 2)))) as meters,
           case when s.type = 'LOST' then c.event_date - s.event_date
                else s.event_date - c.event_date end as days_after_loss
      from cand c cross join src s
  ),
  scored as (
    select id,
           (p_category + p_labels + p_color
            + case when meters <= 100 then 20
                   when meters >= 1000 then 0
                   else round(20.0 * (1000 - meters) / 900) end
            + case when days_after_loss between -1 and 14 then 10
                   when days_after_loss between -3 and 30 then 5
                   else 0 end)::integer as total
      from parts
  )
  select id, total
    from scored
   where total >= p_threshold
   order by total desc;
$$;


-- ---------------------------------------------------------------------
-- 8. Sécurité : Row Level Security (RLS)
-- ---------------------------------------------------------------------
alter table public.profiles       enable row level security;
alter table public.announcements  enable row level security;
alter table public.photo_analyses enable row level security;
alter table public.conversations  enable row level security;
alter table public.messages       enable row level security;

-- Profils : lisibles par les utilisateurs connectés, modifiables par leur propriétaire.
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
  for select to authenticated using (true);

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- Annonces : lecture réservée aux connectés, écriture réservée à l'auteur (RG01, RG02).
drop policy if exists announcements_select on public.announcements;
create policy announcements_select on public.announcements
  for select to authenticated
  using (status <> 'ARCHIVED' or author_id = (select auth.uid()));

drop policy if exists announcements_insert_own on public.announcements;
create policy announcements_insert_own on public.announcements
  for insert to authenticated
  with check (author_id = (select auth.uid()) and status = 'ACTIVE');

drop policy if exists announcements_update_own on public.announcements;
create policy announcements_update_own on public.announcements
  for update to authenticated
  using (author_id = (select auth.uid())) with check (author_id = (select auth.uid()));

drop policy if exists announcements_delete_own on public.announcements;
create policy announcements_delete_own on public.announcements
  for delete to authenticated
  using (author_id = (select auth.uid()));

-- Analyses : lisibles par les connectés, écrites par l'auteur de l'annonce.
drop policy if exists analyses_select on public.photo_analyses;
create policy analyses_select on public.photo_analyses
  for select to authenticated using (true);

drop policy if exists analyses_write_author on public.photo_analyses;
create policy analyses_write_author on public.photo_analyses
  for all to authenticated
  using (exists (select 1 from public.announcements a
                  where a.id = announcement_id and a.author_id = (select auth.uid())))
  with check (exists (select 1 from public.announcements a
                       where a.id = announcement_id and a.author_id = (select auth.uid())));

-- Conversations : visibles par leurs deux participants. Création via open_conversation().
drop policy if exists conversations_select on public.conversations;
create policy conversations_select on public.conversations
  for select to authenticated
  using ((select auth.uid()) in (participant_a, participant_b));

-- Messages : lus et envoyés par les participants (RG07). Lecture marquée via mark_conversation_read().
drop policy if exists messages_select on public.messages;
create policy messages_select on public.messages
  for select to authenticated
  using (public.is_participant(conversation_id));

drop policy if exists messages_insert on public.messages;
create policy messages_insert on public.messages
  for insert to authenticated
  with check (sender_id = (select auth.uid()) and public.is_participant(conversation_id));

-- Droits SQL : rien pour les visiteurs non connectés (anon), le strict nécessaire pour les connectés.
revoke all on public.profiles, public.announcements, public.photo_analyses,
              public.conversations, public.messages from anon, authenticated;

grant select                         on public.profiles       to authenticated;
grant update (full_name, photo_path) on public.profiles       to authenticated;  -- l'e-mail n'est pas modifiable
grant select, insert, update, delete on public.announcements  to authenticated;
grant select, insert, update, delete on public.photo_analyses to authenticated;
grant select                         on public.conversations  to authenticated;
grant select, insert                 on public.messages       to authenticated;

revoke execute on function public.is_participant(uuid)                from public, anon;
revoke execute on function public.open_conversation(uuid)             from public, anon;
revoke execute on function public.mark_conversation_read(uuid)        from public, anon;
revoke execute on function public.my_conversations(text)              from public, anon;
revoke execute on function public.match_candidates(uuid, integer)     from public, anon;
grant  execute on function public.is_participant(uuid)                to authenticated;
grant  execute on function public.open_conversation(uuid)             to authenticated;
grant  execute on function public.mark_conversation_read(uuid)        to authenticated;
grant  execute on function public.my_conversations(text)              to authenticated;
grant  execute on function public.match_candidates(uuid, integer)     to authenticated;


-- ---------------------------------------------------------------------
-- 9. Storage : buckets privés et politiques
--    Chemins : announcement-photos/<uid>/<id annonce>/<n>.jpg
--              avatars/<uid>/avatar.jpg
-- ---------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('announcement-photos', 'announcement-photos', false, 2097152, array['image/jpeg']),
       ('avatars',             'avatars',             false, 1048576, array['image/jpeg'])
on conflict (id) do update
  set public             = excluded.public,
      file_size_limit    = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists lf_storage_select on storage.objects;
create policy lf_storage_select on storage.objects
  for select to authenticated
  using (bucket_id in ('announcement-photos', 'avatars'));

drop policy if exists lf_storage_insert_own on storage.objects;
create policy lf_storage_insert_own on storage.objects
  for insert to authenticated
  with check (bucket_id in ('announcement-photos', 'avatars')
              and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists lf_storage_update_own on storage.objects;
create policy lf_storage_update_own on storage.objects
  for update to authenticated
  using      (bucket_id in ('announcement-photos', 'avatars')
              and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id in ('announcement-photos', 'avatars')
              and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists lf_storage_delete_own on storage.objects;
create policy lf_storage_delete_own on storage.objects
  for delete to authenticated
  using (bucket_id in ('announcement-photos', 'avatars')
         and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Fin du script.
