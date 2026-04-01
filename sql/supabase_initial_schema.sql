-- Starter schema for MatchMind + Supabase.
-- Run in Supabase SQL Editor after reviewing DATABASE_ARCHITECTURE.md.
-- RLS policies below are minimal examples — tighten for production.

-- Profiles (1:1 with auth.users)
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  email text,
  date_of_birth text,
  bio text,
  gender text,
  target_gender text,
  updated_at timestamptz default now()
);

alter table public.profiles enable row level security;

create policy "profiles_select_own"
  on public.profiles for select
  using (auth.uid() = id);

create policy "profiles_insert_own"
  on public.profiles for insert
  with check (auth.uid() = id);

create policy "profiles_update_own"
  on public.profiles for update
  using (auth.uid() = id);

-- ECR-RS raw answers + derived scores (extend as needed)
create table if not exists public.user_ecr_assessments (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  completed_at timestamptz default now(),
  raw_answers int[] not null, -- length 9, Likert 1–7
  anxiety_score double precision,
  avoidance_score double precision
);

alter table public.user_ecr_assessments enable row level security;

create policy "ecr_select_own"
  on public.user_ecr_assessments for select
  using (auth.uid() = user_id);

create policy "ecr_insert_own"
  on public.user_ecr_assessments for insert
  with check (auth.uid() = user_id);

create policy "ecr_update_own"
  on public.user_ecr_assessments for update
  using (auth.uid() = user_id);

-- Interest vectors (wide JSON columns — mirrors CSV / InterestTaxonomy)
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
  updated_at timestamptz default now()
);

alter table public.user_interests enable row level security;

create policy "interests_select_own"
  on public.user_interests for select
  using (auth.uid() = user_id);

create policy "interests_insert_own"
  on public.user_interests for insert
  with check (auth.uid() = user_id);

create policy "interests_update_own"
  on public.user_interests for update
  using (auth.uid() = user_id);

-- Optional: expose minimal read for matching (implement carefully)
-- create policy "interests_read_for_matching" ...
