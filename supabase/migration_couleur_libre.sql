-- =====================================================================
-- Migration : couleur en texte libre (ex. « Lilas », « Bleu marine »)
-- À exécuter UNE fois dans Supabase > SQL Editor (base déjà créée).
-- =====================================================================

-- 1. Retirer l'ancien nuancier fixe
alter table public.announcements  drop constraint if exists announcements_color_check;
alter table public.photo_analyses drop constraint if exists photo_analyses_dominant_color_check;

-- 2. Convertir les anciennes valeurs en français
update public.announcements set color = case color
    when 'BLACK' then 'Noir'  when 'WHITE' then 'Blanc' when 'GREY'  then 'Gris'
    when 'RED'   then 'Rouge' when 'BLUE'  then 'Bleu'  when 'GREEN' then 'Vert'
    when 'BROWN' then 'Marron' when 'OTHER' then null else color end;
update public.photo_analyses set dominant_color = case dominant_color
    when 'BLACK' then 'Noir'  when 'WHITE' then 'Blanc' when 'GREY'  then 'Gris'
    when 'RED'   then 'Rouge' when 'BLUE'  then 'Bleu'  when 'GREEN' then 'Vert'
    when 'BROWN' then 'Marron' when 'OTHER' then null else dominant_color end;

-- 3. Nouvelles contraintes : texte de 1 à 30 caractères
alter table public.announcements
  add constraint announcements_color_check check (char_length(btrim(color)) between 1 and 30);
alter table public.photo_analyses
  add constraint photo_analyses_dominant_color_check check (char_length(dominant_color) <= 30);

-- 4. Correspondances : même couleur = 15 pts, même famille (« Bleu marine » / « Bleu ») = 8 pts
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
