# Supabase Backend Package Map

This folder now follows a layered package split for easier navigation:

- `com.mad.cw.supabase.core`
  - `SessionStore` - JWT/session persistence
  - `SupabaseRestClient` - PostgREST HTTP client + auth headers
  - `SupabaseUrls` - project/auth endpoint builders
  - `PostgrestError` - response error parsing helpers

- `com.mad.cw.supabase.auth`
  - `SupabaseAuthApi` - signup/login (GoTrue REST)

- `com.mad.cw.supabase.repositories`
  - `ProfileRemoteRepository`, `ProfileRecord`
  - `EcrAssessmentRepository`
  - `UserInterestsRepository`
  - `MatchRequestRepository`

## Usage rule of thumb

- UI / feature layers should import:
  - `core` for session/config client concerns,
  - `auth` for authentication actions,
  - `repositories` for table/domain data operations.
