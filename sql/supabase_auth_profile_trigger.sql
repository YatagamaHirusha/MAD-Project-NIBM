-- =============================================================================
-- Optional: auto-create public.profiles when a row is inserted into auth.users
-- =============================================================================
-- Run once in Supabase SQL Editor after supabase_initial_schema.sql.
-- Covers: email-confirm-only signups (no client session yet) and backs up the app.
--
-- Existing auth users without a profile row: run the backfill at the bottom.
-- =============================================================================

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, display_name, current_status)
  values (
    new.id,
    new.email,
    coalesce(
      new.raw_user_meta_data->>'display_name',
      split_part(new.email, '@', 1),
      'User'
    ),
    'looking_for_partner'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row
  execute function public.handle_new_user();

-- Backfill (run once): profiles for users who signed up before this trigger existed
-- insert into public.profiles (id, email, display_name, current_status)
-- select
--   u.id,
--   u.email,
--   coalesce(u.raw_user_meta_data->>'display_name', split_part(u.email, '@', 1), 'User'),
--   'looking_for_partner'
-- from auth.users u
-- where not exists (select 1 from public.profiles p where p.id = u.id)
-- on conflict (id) do nothing;
