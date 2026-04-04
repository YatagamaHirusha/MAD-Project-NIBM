-- Allow authenticated users to read user_interests for peers who are visible for matching
-- (same cohort as profiles_select_looking_others). Run after supabase_initial_schema.sql.
-- Safe for classroom / demo; tighten in production if needed.

drop policy if exists "interests_select_looking_peers" on public.user_interests;

create policy "interests_select_looking_peers"
  on public.user_interests for select
  using (
    user_id <> auth.uid()
    and exists (
      select 1
      from public.profiles p
      where p.id = user_interests.user_id
        and p.current_status = 'looking_for_partner'
    )
  );
