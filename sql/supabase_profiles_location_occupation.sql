-- Run once if you already applied an older schema without these columns.
alter table public.profiles
  add column if not exists location text,
  add column if not exists occupation text;
