-- =============================================================================
-- MatchMind — bulk demo users (auth + profiles + ECR + interests)
-- =============================================================================
-- Run in: Supabase Dashboard → SQL Editor (postgres role; bypasses RLS).
--
-- Creates 30 email/password users you can sign in with in the app:
--   Emails:    matchmind-seed-01@demo.matchmind … matchmind-seed-30@demo.matchmind
--   Password:  MatchMindDemo2026!
--
-- Profile mix (for ML same-city + mutual gender filters):
--   Users 01–15: female → male,  location Colombo
--   Users 16–30: male   → female, location Colombo
--
-- Safe to re-run: reuses existing auth user by email; upserts profiles & interests.
-- Adds a new ECR row each run (backend uses latest by completed_at).
--
-- ⚠️  Demo / staging only.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

DO $$
DECLARE
  i            int;
  uid          uuid;
  v_email      text;
  demo_pw      text := crypt('MatchMindDemo2026!', gen_salt('bf'));
  gender       text;
  target       text;
  display      text;
  occupation   text;
BEGIN
  FOR i IN 1..30 LOOP
    v_email := format('matchmind-seed-%s@demo.matchmind', lpad(i::text, 2, '0'));

    -- Reuse auth user if this email already exists (e.g. partial / repeat run)
    SELECT u.id INTO uid FROM auth.users u WHERE u.email = v_email LIMIT 1;

    IF uid IS NULL THEN
      uid := gen_random_uuid();
      INSERT INTO auth.users (
        id,
        instance_id,
        aud,
        role,
        email,
        encrypted_password,
        email_confirmed_at,
        raw_app_meta_data,
        raw_user_meta_data,
        created_at,
        updated_at
      ) VALUES (
        uid,
        '00000000-0000-0000-0000-000000000000',
        'authenticated',
        'authenticated',
        v_email,
        demo_pw,
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        jsonb_build_object('seed_index', i),
        now(),
        now()
      );
    ELSE
      -- Optional: reset demo password on re-run so login still works
      UPDATE auth.users
      SET encrypted_password = demo_pw,
          updated_at = now()
      WHERE id = uid;
    END IF;

    -- Identity row (skip if already present)
    IF NOT EXISTS (
      SELECT 1 FROM auth.identities idt
      WHERE idt.user_id = uid AND idt.provider = 'email'
    ) THEN
      INSERT INTO auth.identities (
        id,
        user_id,
        identity_data,
        provider,
        provider_id,
        last_sign_in_at,
        created_at,
        updated_at
      ) VALUES (
        gen_random_uuid(),
        uid,
        jsonb_build_object('sub', uid::text, 'email', v_email),
        'email',
        uid::text,
        now(),
        now(),
        now()
      );
    END IF;

    IF i <= 15 THEN
      gender := 'female';
      target := 'male';
      display := format('Demo Seeker F%s', lpad(i::text, 2, '0'));
      occupation :=
        CASE (i % 5)
          WHEN 0 THEN 'Teacher'
          WHEN 1 THEN 'Designer'
          WHEN 2 THEN 'Nurse'
          WHEN 3 THEN 'Developer'
          ELSE 'Marketer'
        END;
    ELSE
      gender := 'male';
      target := 'female';
      display := format('Demo Seeker M%s', lpad(i::text, 2, '0'));
      occupation :=
        CASE (i % 5)
          WHEN 0 THEN 'Engineer'
          WHEN 1 THEN 'Analyst'
          WHEN 2 THEN 'Doctor'
          WHEN 3 THEN 'Chef'
          ELSE 'Officer'
        END;
    END IF;

    INSERT INTO public.profiles (
      id,
      display_name,
      email,
      date_of_birth,
      bio,
      gender,
      target_gender,
      location,
      occupation,
      current_status,
      updated_at
    ) VALUES (
      uid,
      display,
      v_email,
      '01/15/1998',
      'Bulk-seeded demo user',
      gender,
      target,
      'Colombo',
      occupation,
      'looking_for_partner',
      now()
    )
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

    INSERT INTO public.user_ecr_assessments (
      user_id,
      raw_answers,
      anxiety_score,
      avoidance_score,
      completed_at
    ) VALUES (
      uid,
      ARRAY[3, 3, 4, 3, 4, 3, 4, 3, 3]::int[],
      3.0 + ((i % 5)::double precision * 0.2),
      3.2 + ((i % 4)::double precision * 0.15),
      now()
    );

    INSERT INTO public.user_interests (
      user_id,
      location,
      occupation,
      lifestyle,
      arts_creativity,
      music,
      movies_shows,
      intellectual_learning,
      food_drinks,
      sports_outdoor,
      gaming_digital,
      travel_culture,
      personality_values,
      relationship_intent,
      updated_at
    ) VALUES (
      uid,
      'Colombo',
      occupation,
      to_jsonb(ARRAY['Fitness', 'Coffee', 'Early Bird']),
      to_jsonb(ARRAY['Photography', 'Writing']),
      to_jsonb(ARRAY['Indie', 'Pop']),
      to_jsonb(ARRAY['Documentaries', 'Romance']),
      to_jsonb(ARRAY['Books & Reading', 'Technology']),
      to_jsonb(ARRAY['Cooking', 'Street Food']),
      to_jsonb(ARRAY['Swimming', 'Cricket']),
      to_jsonb(ARRAY['Board Games', 'PC Gaming']),
      to_jsonb(ARRAY['Beaches', 'Road Trips']),
      to_jsonb(ARRAY['Family-Oriented', 'Career-Focused']),
      to_jsonb(ARRAY['Long-Term Relationship']),
      now()
    )
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
  END LOOP;

  RAISE NOTICE 'Upserted 30 demo users. Login: matchmind-seed-01@demo.matchmind … -30, password MatchMindDemo2026!';
END $$;

-- Optional: remove all seeded demo users
-- delete from auth.users where email like 'matchmind-seed-%@demo.matchmind';

-- Troubleshooting: auth.identities columns, instance_id — see previous version comments.
