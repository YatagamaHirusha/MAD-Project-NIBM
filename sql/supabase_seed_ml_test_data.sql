-- =============================================================================
-- MatchMind — seed ML test data (profiles + ECR + interests)
-- =============================================================================
-- Supabase Dashboard → SQL Editor → paste → Run once (or adjust and re-run).
--
-- ⚠️  BEFORE RUNNING: In the DO $$ ... DECLARE block below, replace ALL five UUIDs
--     with real ids from:  select id, email from auth.users order by created_at desc;
--     The defaults (00000001-...) are NOT real users — you will get an error until you replace them.
--
-- Rules:
--   * profiles.id, user_ecr_assessments.user_id, user_interests.user_id must exist
--     in auth.users first (sign up in the app or Authentication → Add user).
--   * Backend hard-filters: same location (case-insensitive), mutual gender prefs.
--   * Default scenario below: SEEKER = male → female, CANDIDATES = female → male,
--     all location "Colombo". Change strings if your seeker differs.
-- =============================================================================

-- 0) List auth users (pick UUIDs from the result)
-- select id, email, created_at from auth.users order by created_at desc limit 30;

-- 1) EDIT THESE FIVE UUIDs — each must already exist in auth.users (Dashboard → Authentication)
DO $$
DECLARE
  seeker uuid := '00000001-0000-4000-8000-000000000001'::uuid; -- your logged-in app user
  c1     uuid := '00000001-0000-4000-8000-000000000002'::uuid;
  c2     uuid := '00000001-0000-4000-8000-000000000003'::uuid;
  c3     uuid := '00000001-0000-4000-8000-000000000004'::uuid;
  c4     uuid := '00000001-0000-4000-8000-000000000005'::uuid;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = seeker) THEN
    RAISE EXCEPTION 'Seeker id % not in auth.users — paste your real user UUID from Authentication → Users.', seeker;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM auth.users WHERE id = c1)
     OR NOT EXISTS (SELECT 1 FROM auth.users WHERE id = c2)
     OR NOT EXISTS (SELECT 1 FROM auth.users WHERE id = c3)
     OR NOT EXISTS (SELECT 1 FROM auth.users WHERE id = c4) THEN
    RAISE EXCEPTION 'One or more candidate UUIDs are missing from auth.users — create 4 test accounts first.';
  END IF;

  -- ---------- PROFILES ----------
  INSERT INTO public.profiles (
    id, display_name, email, date_of_birth, bio,
    gender, target_gender, location, occupation, current_status, updated_at
  ) VALUES
    (seeker, 'Seeker Test', 'seeker@test.local', '01/15/1998', 'Seed seeker',
     'male', 'female', 'Colombo', 'Developer', 'looking_for_partner', now()),
    (c1, 'Seed Ayesha', 'seed-a@test.local', '03/22/1999', 'Seed user',
     'female', 'male', 'Colombo', 'Teacher', 'looking_for_partner', now()),
    (c2, 'Seed Nimal', 'seed-b@test.local', '07/10/1997', 'Seed user',
     'female', 'male', 'Colombo', 'Designer', 'looking_for_partner', now()),
    (c3, 'Seed Ruwan', 'seed-c@test.local', '11/05/2000', 'Seed user',
     'female', 'male', 'Colombo', 'Nurse', 'looking_for_partner', now()),
    (c4, 'Seed Malini', 'seed-d@test.local', '05/18/1996', 'Seed user',
     'female', 'male', 'Colombo', 'Marketer', 'looking_for_partner', now())
  ON CONFLICT (id) DO UPDATE SET
    display_name   = EXCLUDED.display_name,
    email          = EXCLUDED.email,
    date_of_birth  = EXCLUDED.date_of_birth,
    bio            = EXCLUDED.bio,
    gender         = EXCLUDED.gender,
    target_gender  = EXCLUDED.target_gender,
    location       = EXCLUDED.location,
    occupation     = EXCLUDED.occupation,
    current_status = EXCLUDED.current_status,
    updated_at     = now();

  -- ---------- ECR (9 answers each; scores in typical 1–7 range) ----------
  INSERT INTO public.user_ecr_assessments (user_id, raw_answers, anxiety_score, avoidance_score, completed_at)
  VALUES
    (seeker, ARRAY[3,4,3,4,3,4,3,4,3]::int[], 3.4, 3.6, now()),
    (c1,     ARRAY[2,3,3,3,2,3,3,3,2]::int[], 2.8, 3.2, now()),
    (c2,     ARRAY[4,4,4,5,4,4,4,4,4]::int[], 4.1, 4.3, now()),
    (c3,     ARRAY[3,3,3,3,3,3,3,3,3]::int[], 3.0, 3.5, now()),
    (c4,     ARRAY[5,5,4,5,5,4,5,4,5]::int[], 4.8, 4.2, now());

  -- ---------- INTERESTS (jsonb = text arrays; aligned with app / ML categories) ----------
  INSERT INTO public.user_interests (
    user_id, location, occupation,
    lifestyle, arts_creativity, music, movies_shows, intellectual_learning,
    food_drinks, sports_outdoor, gaming_digital, travel_culture,
    personality_values, relationship_intent, updated_at
  ) VALUES
    (seeker, 'Colombo', 'Developer',
     '["Gym","Early Bird"]'::jsonb,
     '["Photography"]'::jsonb,
     '["Indie","EDM"]'::jsonb,
     '["Sci-Fi","Documentaries"]'::jsonb,
     '["Technology","Books & Reading"]'::jsonb,
     '["Coffee","Street Food"]'::jsonb,
     '["Cricket","Swimming"]'::jsonb,
     '["PC Gaming"]'::jsonb,
     '["Road Trips"]'::jsonb,
     '["Career-Focused"]'::jsonb,
     '["Long-Term Relationship"]'::jsonb,
     now()),
    (c1, 'Colombo', 'Teacher',
     '["Yoga","Fitness"]'::jsonb,
     '["Writing"]'::jsonb,
     '["Classical"]'::jsonb,
     '["Romance"]'::jsonb,
     '["Psychology"]'::jsonb,
     '["Cooking"]'::jsonb,
     '["Swimming"]'::jsonb,
     '["Board Games"]'::jsonb,
     '["Beaches"]'::jsonb,
     '["Family-Oriented"]'::jsonb,
     '["Long-Term Relationship"]'::jsonb,
     now()),
    (c2, 'Colombo', 'Designer',
     '["Night Owl"]'::jsonb,
     '["Graphic Design"]'::jsonb,
     '["Pop"]'::jsonb,
     '["Thriller"]'::jsonb,
     '["AI & Machine Learning"]'::jsonb,
     '["Coffee"]'::jsonb,
     '["Football"]'::jsonb,
     '["eSports"]'::jsonb,
     '["Backpacking"]'::jsonb,
     '["Environmentalist"]'::jsonb,
     '["Casual Dating"]'::jsonb,
     now()),
    (c3, 'Colombo', 'Nurse',
     '["Meditation","Pet Lover"]'::jsonb,
     '["Interior Design"]'::jsonb,
     '["Jazz"]'::jsonb,
     '["K-Dramas"]'::jsonb,
     '["Science"]'::jsonb,
     '["Fine Dining"]'::jsonb,
     '["Hiking"]'::jsonb,
     '["Mobile Gaming"]'::jsonb,
     '["Mountains"]'::jsonb,
     '["Spiritual"]'::jsonb,
     '["Marriage"]'::jsonb,
     now()),
    (c4, 'Colombo', 'Marketer',
     '["Fitness","Nightlife"]'::jsonb,
     '["Fashion"]'::jsonb,
     '["Hip-Hop"]'::jsonb,
     '["Anime"]'::jsonb,
     '["Startups"]'::jsonb,
     '["Craft Beer"]'::jsonb,
     '["Basketball"]'::jsonb,
     '["VR"]'::jsonb,
     '["Cultural Festivals"]'::jsonb,
     '["Feminist"]'::jsonb,
     '["Long-Term Relationship"]'::jsonb,
     now())
  ON CONFLICT (user_id) DO UPDATE SET
    location              = EXCLUDED.location,
    occupation            = EXCLUDED.occupation,
    lifestyle             = EXCLUDED.lifestyle,
    arts_creativity       = EXCLUDED.arts_creativity,
    music                 = EXCLUDED.music,
    movies_shows          = EXCLUDED.movies_shows,
    intellectual_learning = EXCLUDED.intellectual_learning,
    food_drinks           = EXCLUDED.food_drinks,
    sports_outdoor        = EXCLUDED.sports_outdoor,
    gaming_digital        = EXCLUDED.gaming_digital,
    travel_culture        = EXCLUDED.travel_culture,
    personality_values    = EXCLUDED.personality_values,
    relationship_intent   = EXCLUDED.relationship_intent,
    updated_at            = now();

  RAISE NOTICE 'Seed complete for seeker % and 4 candidates.', seeker;
END $$;

-- 2) Verify
-- select id, display_name, gender, target_gender, location, current_status from public.profiles
--   where id in ('00000001-0000-4000-8000-000000000001', ...);
