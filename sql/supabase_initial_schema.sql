-- =============================================================================
-- MatchMind — Supabase schema (ML-led matching, not swipe-based)
-- =============================================================================
-- Run in: Supabase Dashboard → SQL Editor → New query → Paste → Run
--
-- Flow: looking_for_partner → ML suggests top 5 → one outbound request at a time
--       → recipient accepts/declines → on accept: dating + conversation + 3-day feedback
--
-- Upgrading from an older script: this file DROPs swipes and adds new objects.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0) Remove legacy swipe table (Tinder-style — not used in MatchMind)
-- -----------------------------------------------------------------------------
drop policy if exists "swipes_select_own" on public.swipes;
drop policy if exists "swipes_insert_own" on public.swipes;
drop policy if exists "swipes_update_own" on public.swipes;
drop policy if exists "swipes_delete_own" on public.swipes;
drop table if exists public.swipes cascade;

-- -----------------------------------------------------------------------------
-- 1) PROFILES — + matching lifecycle (ML uses current_status)
-- -----------------------------------------------------------------------------
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  email text,
  date_of_birth text,
  bio text,
  gender text,
  target_gender text,
  updated_at timestamptz not null default now()
);

alter table public.profiles
  add column if not exists current_status text not null default 'looking_for_partner',
  add column if not exists active_partner_id uuid references auth.users (id) on delete set null;

alter table public.profiles
  add column if not exists location text,
  add column if not exists occupation text;

alter table public.profiles drop constraint if exists profiles_current_status_check;
alter table public.profiles add constraint profiles_current_status_check
  check (current_status in ('looking_for_partner', 'dating', 'found_love'));

update public.profiles
set current_status = 'looking_for_partner'
where current_status is null or current_status = '';

create index if not exists profiles_current_status_idx
  on public.profiles (current_status)
  where current_status = 'looking_for_partner';

create index if not exists profiles_updated_at_idx on public.profiles (updated_at desc);

alter table public.profiles enable row level security;

drop policy if exists "profiles_select_own" on public.profiles;
drop policy if exists "profiles_insert_own" on public.profiles;
drop policy if exists "profiles_update_own" on public.profiles;
drop policy if exists "profiles_delete_own" on public.profiles;
drop policy if exists "profiles_select_looking_others" on public.profiles;

create policy "profiles_select_own"
  on public.profiles for select
  using (auth.uid() = id);

-- Other users who are actively "on the market" (for discovery cards / ML client-side lists).
-- TODO production: prefer a SECURITY DEFINER RPC that returns only safe columns (hide email).
create policy "profiles_select_looking_others"
  on public.profiles for select
  using (
    id <> auth.uid()
    and current_status = 'looking_for_partner'
  );

create policy "profiles_insert_own"
  on public.profiles for insert
  with check (auth.uid() = id);

create policy "profiles_update_own"
  on public.profiles for update
  using (auth.uid() = id);

create policy "profiles_delete_own"
  on public.profiles for delete
  using (auth.uid() = id);

-- -----------------------------------------------------------------------------
-- 2) ECR-RS ASSESSMENTS
-- -----------------------------------------------------------------------------
create table if not exists public.user_ecr_assessments (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  completed_at timestamptz not null default now(),
  raw_answers int[] not null,
  anxiety_score double precision,
  avoidance_score double precision,
  constraint raw_answers_length check (array_length(raw_answers, 1) = 9)
);

create index if not exists user_ecr_user_id_idx on public.user_ecr_assessments (user_id);

alter table public.user_ecr_assessments enable row level security;

drop policy if exists "ecr_select_own" on public.user_ecr_assessments;
drop policy if exists "ecr_insert_own" on public.user_ecr_assessments;
drop policy if exists "ecr_update_own" on public.user_ecr_assessments;
drop policy if exists "ecr_delete_own" on public.user_ecr_assessments;

create policy "ecr_select_own"
  on public.user_ecr_assessments for select
  using (auth.uid() = user_id);

create policy "ecr_insert_own"
  on public.user_ecr_assessments for insert
  with check (auth.uid() = user_id);

create policy "ecr_update_own"
  on public.user_ecr_assessments for update
  using (auth.uid() = user_id);

create policy "ecr_delete_own"
  on public.user_ecr_assessments for delete
  using (auth.uid() = user_id);

-- -----------------------------------------------------------------------------
-- 3) USER INTERESTS (JSONB — training CSV semantics)
-- -----------------------------------------------------------------------------
create table if not exists public.user_interests (
  user_id uuid primary key references auth.users (id) on delete cascade,
  location text,
  occupation text,
  lifestyle jsonb,
  arts_creativity jsonb,
  music jsonb,
  movies_shows jsonb,
  intellectual_learning jsonb,
  food_drinks jsonb,
  sports_outdoor jsonb,
  gaming_digital jsonb,
  travel_culture jsonb,
  personality_values jsonb,
  relationship_intent jsonb,
  updated_at timestamptz not null default now()
);

alter table public.user_interests enable row level security;

drop policy if exists "interests_select_own" on public.user_interests;
drop policy if exists "interests_insert_own" on public.user_interests;
drop policy if exists "interests_update_own" on public.user_interests;
drop policy if exists "interests_delete_own" on public.user_interests;

create policy "interests_select_own"
  on public.user_interests for select
  using (auth.uid() = user_id);

create policy "interests_insert_own"
  on public.user_interests for insert
  with check (auth.uid() = user_id);

create policy "interests_update_own"
  on public.user_interests for update
  using (auth.uid() = user_id);

create policy "interests_delete_own"
  on public.user_interests for delete
  using (auth.uid() = user_id);

-- -----------------------------------------------------------------------------
-- 4) ML SUGGESTION BATCH (top-5 runs — audit + RL features)
-- -----------------------------------------------------------------------------
create table if not exists public.match_suggestion_batches (
  id uuid primary key default gen_random_uuid(),
  seeker_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  candidate_user_ids uuid[] not null,
  model_scores jsonb,
  model_version text,
  constraint match_suggestion_batches_candidate_count
    check (cardinality(candidate_user_ids) >= 1 and cardinality(candidate_user_ids) <= 5)
);

create index if not exists match_suggestion_batches_seeker_idx
  on public.match_suggestion_batches (seeker_id, created_at desc);

alter table public.match_suggestion_batches enable row level security;

drop policy if exists "suggestion_batches_select_seeker" on public.match_suggestion_batches;
drop policy if exists "suggestion_batches_insert_seeker" on public.match_suggestion_batches;

create policy "suggestion_batches_select_seeker"
  on public.match_suggestion_batches for select
  using (auth.uid() = seeker_id);

create policy "suggestion_batches_insert_seeker"
  on public.match_suggestion_batches for insert
  with check (auth.uid() = seeker_id);

-- -----------------------------------------------------------------------------
-- 5) MATCH REQUESTS (one pending outbound per seeker)
-- -----------------------------------------------------------------------------
create table if not exists public.match_requests (
  id uuid primary key default gen_random_uuid(),
  from_user_id uuid not null references auth.users (id) on delete cascade,
  to_user_id uuid not null references auth.users (id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending', 'accepted', 'declined', 'cancelled_by_sender')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  suggestion_batch_id uuid references public.match_suggestion_batches (id) on delete set null,
  ml_rank int check (ml_rank is null or (ml_rank >= 1 and ml_rank <= 5)),
  constraint match_requests_no_self check (from_user_id <> to_user_id)
);

create unique index if not exists match_requests_one_pending_outbound
  on public.match_requests (from_user_id)
  where status = 'pending';

create index if not exists match_requests_to_user_idx
  on public.match_requests (to_user_id)
  where status = 'pending';

alter table public.match_requests enable row level security;

drop policy if exists "match_requests_select_parties" on public.match_requests;
drop policy if exists "match_requests_insert_sender" on public.match_requests;
drop policy if exists "match_requests_update_parties" on public.match_requests;

create policy "match_requests_select_parties"
  on public.match_requests for select
  using (auth.uid() = from_user_id or auth.uid() = to_user_id);

create policy "match_requests_insert_sender"
  on public.match_requests for insert
  with check (
    auth.uid() = from_user_id
    and exists (
      select 1 from public.profiles p
      where p.id = from_user_id and p.current_status = 'looking_for_partner'
    )
    and exists (
      select 1 from public.profiles p
      where p.id = to_user_id and p.current_status = 'looking_for_partner'
    )
  );

-- Recipient can mark declined; sender can cancel pending; full accept via RPC
create policy "match_requests_update_parties"
  on public.match_requests for update
  using (auth.uid() = from_user_id or auth.uid() = to_user_id);

-- -----------------------------------------------------------------------------
-- 6) RELATIONSHIPS (accepted match — drives "dating" + feedback window)
-- -----------------------------------------------------------------------------
create table if not exists public.relationships (
  id uuid primary key default gen_random_uuid(),
  user_low uuid not null references auth.users (id) on delete cascade,
  user_high uuid not null references auth.users (id) on delete cascade,
  accepted_request_id uuid not null references public.match_requests (id) on delete restrict,
  started_at timestamptz not null default now(),
  feedback_due_at timestamptz not null,
  status text not null default 'active'
    check (status in ('active', 'feedback_complete', 'ended')),
  constraint relationships_ordered check (user_low < user_high),
  constraint relationships_unique_pair unique (user_low, user_high),
  constraint relationships_unique_request unique (accepted_request_id)
);

create index if not exists relationships_user_low_idx on public.relationships (user_low);
create index if not exists relationships_user_high_idx on public.relationships (user_high);

alter table public.relationships enable row level security;

drop policy if exists "relationships_select_participants" on public.relationships;
drop policy if exists "relationships_update_participants" on public.relationships;

create policy "relationships_select_participants"
  on public.relationships for select
  using (auth.uid() = user_low or auth.uid() = user_high);

-- Status updates (e.g. after feedback) — participants only; creation via RPC
create policy "relationships_update_participants"
  on public.relationships for update
  using (auth.uid() = user_low or auth.uid() = user_high);

-- -----------------------------------------------------------------------------
-- 7) MATCH FEEDBACK (within 3 days — RL signal)
-- -----------------------------------------------------------------------------
create table if not exists public.match_feedback (
  id bigint generated always as identity primary key,
  relationship_id uuid not null references public.relationships (id) on delete cascade,
  author_user_id uuid not null references auth.users (id) on delete cascade,
  rating integer check (rating >= 1 and rating <= 5),
  comment text,
  reward_signal smallint check (reward_signal is null or reward_signal between -1 and 1),
  created_at timestamptz not null default now(),
  constraint match_feedback_one_per_author unique (relationship_id, author_user_id)
);

create index if not exists match_feedback_relationship_idx on public.match_feedback (relationship_id);

alter table public.match_feedback enable row level security;

drop policy if exists "match_feedback_select_participants" on public.match_feedback;
drop policy if exists "match_feedback_insert_author" on public.match_feedback;

create policy "match_feedback_select_participants"
  on public.match_feedback for select
  using (
    exists (
      select 1 from public.relationships r
      where r.id = match_feedback.relationship_id
        and (auth.uid() = r.user_low or auth.uid() = r.user_high)
    )
  );

create policy "match_feedback_insert_author"
  on public.match_feedback for insert
  with check (
    auth.uid() = author_user_id
    and exists (
      select 1 from public.relationships r
      where r.id = match_feedback.relationship_id
        and (auth.uid() = r.user_low or auth.uid() = r.user_high)
    )
  );

-- -----------------------------------------------------------------------------
-- 8) CONVERSATIONS (+ link to relationship)
-- -----------------------------------------------------------------------------
create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  user_low uuid not null references auth.users (id) on delete cascade,
  user_high uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  last_message_at timestamptz,
  constraint conversations_ordered check (user_low < user_high),
  constraint conversations_unique_pair unique (user_low, user_high)
);

alter table public.conversations
  add column if not exists relationship_id uuid;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'conversations_relationship_id_fkey'
  ) then
    alter table public.conversations
      add constraint conversations_relationship_id_fkey
      foreign key (relationship_id) references public.relationships (id) on delete set null;
  end if;
end $$;

create unique index if not exists conversations_relationship_id_key
  on public.conversations (relationship_id)
  where relationship_id is not null;

create index if not exists conversations_user_low_idx on public.conversations (user_low);
create index if not exists conversations_user_high_idx on public.conversations (user_high);

alter table public.conversations enable row level security;

drop policy if exists "conversations_select_participant" on public.conversations;
drop policy if exists "conversations_insert_participant" on public.conversations;
drop policy if exists "conversations_update_participant" on public.conversations;
drop policy if exists "conversations_delete_participant" on public.conversations;

create policy "conversations_select_participant"
  on public.conversations for select
  using (auth.uid() = user_low or auth.uid() = user_high);

create policy "conversations_insert_participant"
  on public.conversations for insert
  with check (auth.uid() = user_low or auth.uid() = user_high);

create policy "conversations_update_participant"
  on public.conversations for update
  using (auth.uid() = user_low or auth.uid() = user_high);

create policy "conversations_delete_participant"
  on public.conversations for delete
  using (auth.uid() = user_low or auth.uid() = user_high);

-- -----------------------------------------------------------------------------
-- 9) MESSAGES
-- -----------------------------------------------------------------------------
create table if not exists public.messages (
  id bigint generated always as identity primary key,
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  sender_id uuid not null references auth.users (id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now(),
  client_message_id uuid
);

create index if not exists messages_conversation_id_idx on public.messages (conversation_id, created_at desc);

alter table public.messages enable row level security;

drop policy if exists "messages_select_participant" on public.messages;
drop policy if exists "messages_insert_sender" on public.messages;
drop policy if exists "messages_update_sender" on public.messages;
drop policy if exists "messages_delete_sender" on public.messages;

create policy "messages_select_participant"
  on public.messages for select
  using (
    exists (
      select 1 from public.conversations c
      where c.id = messages.conversation_id
        and (auth.uid() = c.user_low or auth.uid() = c.user_high)
    )
  );

create policy "messages_insert_sender"
  on public.messages for insert
  with check (
    auth.uid() = sender_id
    and exists (
      select 1 from public.conversations c
      where c.id = messages.conversation_id
        and (auth.uid() = c.user_low or auth.uid() = c.user_high)
    )
  );

create policy "messages_update_sender"
  on public.messages for update
  using (auth.uid() = sender_id);

create policy "messages_delete_sender"
  on public.messages for delete
  using (auth.uid() = sender_id);

-- -----------------------------------------------------------------------------
-- 10) RPC — accept / decline / cancel (enforces business rules)
-- -----------------------------------------------------------------------------
create or replace function public.accept_match_request(p_request_id uuid)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
  u_low uuid;
  u_high uuid;
  conv_id uuid;
  rel_id uuid;
begin
  select * into r from public.match_requests where id = p_request_id for update;
  if not found then
    return json_build_object('ok', false, 'error', 'request_not_found');
  end if;
  if r.to_user_id <> auth.uid() then
    return json_build_object('ok', false, 'error', 'not_recipient');
  end if;
  if r.status <> 'pending' then
    return json_build_object('ok', false, 'error', 'not_pending');
  end if;

  if not exists (
    select 1 from public.profiles where id = r.from_user_id and current_status = 'looking_for_partner'
  ) or not exists (
    select 1 from public.profiles where id = r.to_user_id and current_status = 'looking_for_partner'
  ) then
    return json_build_object('ok', false, 'error', 'participant_not_looking');
  end if;

  update public.match_requests
  set status = 'accepted', responded_at = now()
  where id = p_request_id;

  if r.from_user_id < r.to_user_id then
    u_low := r.from_user_id;
    u_high := r.to_user_id;
  else
    u_low := r.to_user_id;
    u_high := r.from_user_id;
  end if;

  insert into public.relationships (
    user_low, user_high, accepted_request_id, started_at, feedback_due_at, status
  ) values (
    u_low, u_high, p_request_id, now(), now() + interval '3 days', 'active'
  )
  returning id into rel_id;

  update public.profiles
  set current_status = 'dating', active_partner_id = r.to_user_id, updated_at = now()
  where id = r.from_user_id;

  update public.profiles
  set current_status = 'dating', active_partner_id = r.from_user_id, updated_at = now()
  where id = r.to_user_id;

  insert into public.conversations (user_low, user_high, relationship_id, created_at)
  values (u_low, u_high, rel_id, now())
  on conflict (user_low, user_high) do update
  set relationship_id = excluded.relationship_id
  returning id into conv_id;

  if conv_id is null then
    select c.id into conv_id
    from public.conversations c
    where c.user_low = u_low and c.user_high = u_high;
  end if;

  return json_build_object(
    'ok', true,
    'relationship_id', rel_id,
    'conversation_id', conv_id
  );
end;
$$;

create or replace function public.decline_match_request(p_request_id uuid)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
begin
  select * into r from public.match_requests where id = p_request_id for update;
  if not found then
    return json_build_object('ok', false, 'error', 'request_not_found');
  end if;
  if r.to_user_id <> auth.uid() then
    return json_build_object('ok', false, 'error', 'not_recipient');
  end if;
  if r.status <> 'pending' then
    return json_build_object('ok', false, 'error', 'not_pending');
  end if;

  update public.match_requests
  set status = 'declined', responded_at = now()
  where id = p_request_id;

  return json_build_object('ok', true);
end;
$$;

create or replace function public.cancel_match_request(p_request_id uuid)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
begin
  select * into r from public.match_requests where id = p_request_id for update;
  if not found then
    return json_build_object('ok', false, 'error', 'request_not_found');
  end if;
  if r.from_user_id <> auth.uid() then
    return json_build_object('ok', false, 'error', 'not_sender');
  end if;
  if r.status <> 'pending' then
    return json_build_object('ok', false, 'error', 'not_pending');
  end if;

  update public.match_requests
  set status = 'cancelled_by_sender', responded_at = now()
  where id = p_request_id;

  return json_build_object('ok', true);
end;
$$;

create or replace function public.submit_match_feedback(
  p_relationship_id uuid,
  p_rating int,
  p_comment text,
  p_reward_signal smallint
)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
  r record;
begin
  select * into r from public.relationships where id = p_relationship_id for update;
  if not found then
    return json_build_object('ok', false, 'error', 'relationship_not_found');
  end if;
  if auth.uid() not in (r.user_low, r.user_high) then
    return json_build_object('ok', false, 'error', 'not_participant');
  end if;
  if r.status <> 'active' then
    return json_build_object('ok', false, 'error', 'relationship_not_active');
  end if;
  if now() > r.feedback_due_at then
    return json_build_object('ok', false, 'error', 'feedback_window_closed');
  end if;

  insert into public.match_feedback (relationship_id, author_user_id, rating, comment, reward_signal)
  values (p_relationship_id, auth.uid(), p_rating, p_comment, p_reward_signal)
  on conflict (relationship_id, author_user_id)
  do update set
    rating = excluded.rating,
    comment = excluded.comment,
    reward_signal = excluded.reward_signal,
    created_at = now();

  if (
    select count(*) from public.match_feedback where relationship_id = p_relationship_id
  ) >= 2 then
    update public.relationships set status = 'feedback_complete' where id = p_relationship_id;
  end if;

  return json_build_object('ok', true);
end;
$$;

create or replace function public.mark_found_love()
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
  pid uuid := auth.uid();
  partner uuid;
begin
  select active_partner_id into partner from public.profiles where id = pid;

  update public.profiles
  set
    current_status = 'found_love',
    active_partner_id = null,
    updated_at = now()
  where id = pid;

  if partner is not null then
    update public.profiles
    set
      current_status = 'found_love',
      active_partner_id = null,
      updated_at = now()
    where id = partner;
  end if;

  update public.relationships
  set status = 'ended'
  where (user_low = pid or user_high = pid) and status = 'active';

  return json_build_object('ok', true);
end;
$$;

grant execute on function public.accept_match_request(uuid) to authenticated;
grant execute on function public.decline_match_request(uuid) to authenticated;
grant execute on function public.cancel_match_request(uuid) to authenticated;
grant execute on function public.submit_match_feedback(uuid, int, text, smallint) to authenticated;
grant execute on function public.mark_found_love() to authenticated;

-- -----------------------------------------------------------------------------
-- 11) API grants
-- -----------------------------------------------------------------------------
grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on all tables in schema public to anon, authenticated;
grant usage, select on all sequences in schema public to anon, authenticated;

-- =============================================================================
-- Done. Tables: profiles (+status), user_ecr_assessments, user_interests,
-- match_suggestion_batches, match_requests, relationships, match_feedback,
-- conversations (+relationship_id), messages
-- RPC: accept_match_request, decline_match_request, cancel_match_request,
--      submit_match_feedback, mark_found_love
-- =============================================================================
