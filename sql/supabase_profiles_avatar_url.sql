-- Optional: profile photo URL (e.g. Supabase Storage public URL). Safe to run if column already exists.
alter table public.profiles
  add column if not exists avatar_url text;
