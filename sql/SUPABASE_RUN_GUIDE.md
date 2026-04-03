# Run Supabase tables — step-by-step

Follow these steps once per Supabase project. The SQL lives in **`supabase_initial_schema.sql`** in this folder.

---

## Product flow (MatchMind — not swipe-based)

1. User completes **home tasks** (ECR-RS, interests, etc.) and taps **Find partner**.
2. **ML** (Edge Function / backend job) queries users with `profiles.current_status = 'looking_for_partner'`, scores them, returns **top 5** — optionally logged in **`match_suggestion_batches`**.
3. User sends **one outbound `match_requests` row** at a time (DB enforces one `pending` per sender).
4. Recipient **accepts** or **declines** (`rpc` recommended) → if accept: both become **`dating`**, **`relationships`** row, **`conversations`** row, **3-day** feedback window.
5. If declined, sender can request the next candidate from their batch.
6. **`mark_found_love()`** (optional) sets both to **`found_love`** so ML stops recommending them.

---

## Step 1 — Open your project

1. Go to [supabase.com/dashboard](https://supabase.com/dashboard) and open **your MatchMind project**.

---

## Step 2 — Open the SQL Editor

1. In the left sidebar, click **SQL Editor**.
2. Click **New query** (empty editor).

---

## Step 3 — Paste and run the schema

1. Open **`sql/supabase_initial_schema.sql`** (full file).
2. **Copy** → **Paste** into the SQL editor → **Run**.

**Expected result:** Green success; no red errors.

### If something fails

| Error | What to do |
|--------|------------|
| `relation "auth.users" does not exist` | Rare on Supabase cloud. Confirm project + Auth enabled. |
| `already exists` on policies | Re-run uses `DROP POLICY IF EXISTS`; use latest file. |
| `raw_answers_length` | Inserts need exactly **9** integers in `raw_answers`. |
| Upgrading from old script with conflicting objects | In SQL Editor, drop new tables manually if needed, or use a fresh Supabase project for coursework. |

---

## Step 4 — Confirm tables & RPCs

**Table Editor → `public`:**

| Table | Purpose |
|--------|---------|
| `profiles` | + `current_status` (`looking_for_partner` / `dating` / `found_love`), `active_partner_id` |
| `user_ecr_assessments` | Nine Likert values + optional anxiety/avoidance |
| `user_interests` | JSONB interest columns |
| `match_suggestion_batches` | Optional audit: up to 5 candidate UUIDs per ML run |
| `match_requests` | Sender → recipient; `pending` / `accepted` / `declined` / `cancelled_by_sender` |
| `relationships` | Active match after accept; `feedback_due_at` = start + 3 days |
| `match_feedback` | Per-user rating/comment/`reward_signal` for RL |
| `conversations` | Linked to `relationship_id` when created by accept RPC |
| `messages` | Chat lines |

**Legacy `swipes`** is **dropped** by this script.

**Database → Functions** (or SQL `list`):  
`accept_match_request`, `decline_match_request`, `cancel_match_request`, `submit_match_feedback`, `mark_found_love` — granted to **`authenticated`**.

---

## Step 5 — Call flows from the app (PostgREST)

Use the user **access token** in `Authorization: Bearer …` plus `apikey: <anon>`.

| Action | Typical call |
|--------|----------------|
| Send request | `POST /rest/v1/match_requests` with `from_user_id`, `to_user_id`, optional `suggestion_batch_id`, `ml_rank` |
| Accept | `POST /rest/v1/rpc/accept_match_request` body `{"p_request_id":"<uuid>"}` |
| Decline | `POST /rest/v1/rpc/decline_match_request` |
| Cancel (sender) | `POST /rest/v1/rpc/cancel_match_request` |
| Feedback (within 3 days) | `POST /rest/v1/rpc/submit_match_feedback` with `p_relationship_id`, `p_rating`, `p_comment`, `p_reward_signal` |
| “Found love” | `POST /rest/v1/rpc/mark_found_love` (no args) |

---

## Step 6 — ML candidate pool

Queries for scoring should filter:

```sql
select id from public.profiles
where current_status = 'looking_for_partner';
```

Prefer running heavy ML **server-side** (Edge Function + `service_role`) so you don’t expose extra profile fields; the script includes a permissive RLS policy **`profiles_select_looking_others`** for prototyping — **remove or replace** with an RPC that returns non-PII columns in production.

---

## JSON column ↔ app

Same as before — see table in previous sections of this file / `DATABASE_ARCHITECTURE.md` (`snake_case` columns in `user_interests`).

---

## Security reminder

- Never ship **service_role** in the app.
- **RLS** is on all listed tables; RPCs are **`SECURITY DEFINER`** — review them when you change business rules.
