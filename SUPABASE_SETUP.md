# Supabase setup — MatchMind Android

This project uses **Supabase** (managed **PostgreSQL**) as the remote database. The Android app reads **only the anon (public) key** from `local.properties` at build time and injects it into `BuildConfig` (see `app/build.gradle.kts`).

## 1. Create a Supabase project

1. Go to [supabase.com](https://supabase.com) and create a project.
2. Wait for the database to finish provisioning.
3. Open **Project Settings → API** and copy:
   - **Project URL** → `supabase.url`
   - **anon public** key → `supabase.anon.key`

## 2. Configure the Android app

1. Copy `local.properties.example` entries into your real **`local.properties`** (project root, next to `settings.gradle.kts`).
2. Replace `YOUR_PROJECT_REF` and `YOUR_SUPABASE_ANON_KEY` with your values.
3. Sync Gradle. `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY` will populate.

Use **`SupabaseRestClient.getInstance()`** for OkHttp calls to `.../rest/v1/<table>`.

## 3. Security (required)

- **Never** put the **service_role** key or database password in the app.
- The **anon** key is expected to be in the client; **Row Level Security (RLS)** on every user-facing table is mandatory.
- Typical pattern: tables keyed by `user id` = `auth.uid()`, and policies like `USING (id = auth.uid())` for `select` / `insert` / `update` as appropriate.

Without RLS, anyone with the anon key could read or write all rows exposed through the API.

## 4. Create remote tables

**Step-by-step:** follow **`sql/SUPABASE_RUN_GUIDE.md`** (dashboard → SQL Editor → paste → run → verify).

The full script is **`sql/supabase_initial_schema.sql`** (profiles, ECR, interests, swipes, conversations, messages + RLS + grants).

## 5. Auth

Use **Supabase Auth** (email/password, OAuth, etc.). After sign-in, the client receives a **JWT**. For PostgREST, send:

```http
Authorization: Bearer <user_access_token>
apikey: <anon_key>
```

Today’s `SupabaseRestClient` only attaches the **anon** key. When you wire login, add a second interceptor (or swap the `Authorization` header) to use the **user’s access token** while keeping `apikey` as the anon key — see [Supabase docs: API requests with user JWT](https://supabase.com/docs/guides/api#authentication).

## 6. Optional alternatives

- **Official Supabase Kotlin SDK** — if you add Kotlin to the app module.
- **Edge Functions** — for match scoring / calling your ML service with secrets server-side.

## 7. Implemented in the Android app (after DB migration)

- **`INTERNET`** permission in the manifest.
- **`SessionStore`** — saves access/refresh tokens and user id after sign-up / sign-in.
- **`SupabaseAuthApi`** — email + password `signup` and `token?grant_type=password`.
- **`SupabaseRestClient`** — sends **`Authorization: Bearer <user JWT>`** when a session exists, otherwise the anon key.
- **`ProfileRemoteRepository.upsertMyProfile`** — creates/merges `public.profiles` on **register** (`current_status = looking_for_partner`).
- **`Splash` / `Welcome`** — if `SessionStore.isLoggedIn()`, open **`MainActivity`** directly.

### Supabase dashboard checklist for smooth auth

1. **Authentication → Providers → Email** — enabled.
2. If sign-up returns “check your email” and no session: **Authentication → Providers → Email → Confirm email** — turn **off** for class demos, or handle confirmation in-app.

### Suggested next implementation steps

1. **Refresh tokens** — when `SessionStore.getAccessToken()` is expired, call `auth/v1/token?grant_type=refresh_token` and save a new access token.
2. **Sync local prefs** — push `ProfilePreferences`, `AssessmentPreferences`, `UserInterestStore` to Supabase after login (and pull on cold start).
3. **Find partner** — Edge Function (service role) runs ML, writes `match_suggestion_batches`, app reads candidates.
4. **Match requests + RPC** — wire `match_requests` insert and `accept_match_request` / `decline_match_request` from the UI.
5. **Logout** — `SessionStore.clear()` + return to `Welcome` (e.g. from Profile).
