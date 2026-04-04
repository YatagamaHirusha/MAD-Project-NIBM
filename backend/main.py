"""
MatchMind FastAPI inference server: load Hybrid DQN once, score candidates from Supabase.
"""

from __future__ import annotations

import asyncio
import os
import threading
from contextlib import asynccontextmanager
from datetime import date, datetime
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import torch
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict
from supabase import Client, create_client

from matchmind_ml import HybridMatchmaker, load_hybrid_from_checkpoint, process_pair

_hybrid_load_lock = threading.Lock()

# --- DB column (jsonb snake_case) → training CSV category column names (Model/updated_data.csv)
INTEREST_JSONB_TO_CATEGORY: Dict[str, str] = {
    "lifestyle": "Lifestyle",
    "arts_creativity": "Arts & Creativity",
    "music": "Music",
    "movies_shows": "Movies & Shows",
    "intellectual_learning": "Intellectual & Learning",
    "food_drinks": "Food & Drinks",
    "sports_outdoor": "Sports & Outdoor",
    "gaming_digital": "Gaming & Digital",
    "travel_culture": "Travel & Culture",
    "personality_values": "Personality & Values",
    "relationship_intent": "Relationship Intent",
}


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    supabase_url: str
    supabase_service_role_key: str
    # Default matches common v4 checkpoint name when the .pth is committed at repo root (e.g. Render).
    model_path: str = "matchmind_hybrid_v4.pth"
    torch_device: str = "cpu"
    candidate_status: str = "looking_for_partner"
    hybrid_alpha_override: Optional[float] = None
    # Load .pth during lifespan. Set false on slow hosts so the server binds $PORT before PyTorch init (Render).
    eager_model_load: bool = Field(default=True)
    # Optional debug endpoint guard for non-shell environments (Render free tier).
    debug_token: Optional[str] = None


settings = Settings()


def _load_hybrid_at_runtime() -> HybridMatchmaker:
    if not os.path.isfile(settings.model_path):
        raise RuntimeError(f"MODEL_PATH does not exist: {settings.model_path}")
    device = torch.device(settings.torch_device)
    hybrid = load_hybrid_from_checkpoint(settings.model_path, device)
    if settings.hybrid_alpha_override is not None:
        hybrid.alpha = float(settings.hybrid_alpha_override)
    return hybrid


def _should_eager_load_model() -> bool:
    """Render deploy health checks often time out if PyTorch loads before the process listens on $PORT."""
    if os.environ.get("MATCHMIND_FORCE_EAGER_MODEL_LOAD", "").lower() in ("1", "true", "yes"):
        return True
    if os.environ.get("RENDER", "").lower() in ("1", "true", "yes"):
        return False
    return settings.eager_model_load


def ensure_hybrid(app: FastAPI) -> HybridMatchmaker:
    """Thread-safe one-time load when not using eager lifespan (e.g. on Render)."""
    existing: Optional[HybridMatchmaker] = getattr(app.state, "hybrid", None)
    if existing is not None:
        return existing
    with _hybrid_load_lock:
        existing = getattr(app.state, "hybrid", None)
        if existing is not None:
            return existing
        hybrid = _load_hybrid_at_runtime()
        app.state.hybrid = hybrid
        return hybrid


class MatchItem(BaseModel):
    user_id: str
    hybrid_score: float
    rl_score: float
    rule_score: float
    match_percent: int


class GetMatchesResponse(BaseModel):
    seeker_id: str
    alpha: float
    matches: List[MatchItem]


def _parse_date_of_birth(raw: Optional[str]) -> Optional[date]:
    if not raw or not str(raw).strip():
        return None
    s = str(raw).strip()
    for fmt in ("%m/%d/%Y", "%m/%d/%y", "%Y-%m-%d", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%dT%H:%M:%S.%f"):
        try:
            return datetime.strptime(s[:19] if "T" in s and len(s) > 10 else s, fmt).date()
        except ValueError:
            continue
    try:
        if s.endswith("Z") and "T" in s:
            return datetime.fromisoformat(s.replace("Z", "+00:00")).date()
    except ValueError:
        pass
    return None


def age_from_dob(dob_raw: Optional[str], default: float = 25.0) -> float:
    d = _parse_date_of_birth(dob_raw)
    if d is None:
        return default
    today = date.today()
    years = today.year - d.year - ((today.month, today.day) < (d.month, d.day))
    return float(max(18, years))


def db_rows_to_user_row(
    profile: Dict[str, Any],
    ecr: Optional[Dict[str, Any]],
    interests: Optional[Dict[str, Any]],
) -> Dict[str, Any]:
    """
    Merge `profiles` + latest `user_ecr_assessments` + `user_interests` into one dict
    compatible with training CSV columns and `process_pair`.
    """
    uid = str(profile.get("id", ""))
    loc = (
        profile.get("location")
        or (interests.get("location") if interests else None)
        or ""
    )
    occ = (
        profile.get("occupation")
        or (interests.get("occupation") if interests else None)
        or ""
    )

    anxiety = 3.5
    avoidance = 3.5
    if ecr:
        try:
            if ecr.get("anxiety_score") is not None:
                anxiety = float(ecr["anxiety_score"])
            if ecr.get("avoidance_score") is not None:
                avoidance = float(ecr["avoidance_score"])
        except (TypeError, ValueError):
            pass

    row: Dict[str, Any] = {
        "user_id": uid,
        "name": profile.get("display_name") or "",
        "age": age_from_dob(profile.get("date_of_birth")),
        "gender": profile.get("gender") or "",
        "target_gender": profile.get("target_gender") or "",
        "location": str(loc).strip(),
        "occupation": str(occ).strip(),
        "anxiety": anxiety,
        "avoidance": avoidance,
    }

    for jsonb_key, csv_name in INTEREST_JSONB_TO_CATEGORY.items():
        if interests and jsonb_key in interests:
            row[csv_name] = interests[jsonb_key]
        else:
            row[csv_name] = []

    return row


def preprocess_pair_features(
    target_row: Dict[str, Any],
    candidate_row: Dict[str, Any],
) -> Optional[torch.Tensor]:
    """
    Convert a pair of merged user rows into a single (16,) feature tensor for the DQN.
    Returns None if hard filters fail (same semantics as training `process_pair`).
    """
    state, _ = process_pair(target_row, candidate_row)
    if state is None:
        return None
    return torch.from_numpy(state.astype(np.float32))


def preprocess_candidate_pool_tensors(
    target_row: Dict[str, Any],
    candidate_rows: List[Dict[str, Any]],
) -> Tuple[torch.Tensor, List[str], List[float]]:
    """
    Batch-convert eligible pairs to a tensor stack [N, 16], aligned candidate ids,
    and normalized rule scores (for hybrid scoring if needed outside HybridMatchmaker).
    """
    states: List[np.ndarray] = []
    ids: List[str] = []
    rules: List[float] = []
    for cr in candidate_rows:
        st, raw = process_pair(target_row, cr)
        if st is None:
            continue
        states.append(st)
        ids.append(str(cr["user_id"]))
        rules.append(float(np.clip(raw / 120.0, -1.0, 1.0)))
    if not states:
        return torch.zeros(0, 16), [], []
    t = torch.from_numpy(np.stack(states, axis=0).astype(np.float32))
    return t, ids, rules


def _latest_ecr_by_user(supabase: Client, user_ids: List[str]) -> Dict[str, Dict[str, Any]]:
    if not user_ids:
        return {}
    resp = (
        supabase.table("user_ecr_assessments")
        .select("user_id, anxiety_score, avoidance_score, completed_at")
        .in_("user_id", user_ids)
        .order("completed_at", desc=True)
        .execute()
    )
    out: Dict[str, Dict[str, Any]] = {}
    for row in resp.data or []:
        uid = row["user_id"]
        if uid not in out:
            out[uid] = row
    return out


def _interests_by_user(supabase: Client, user_ids: List[str]) -> Dict[str, Dict[str, Any]]:
    if not user_ids:
        return {}
    resp = supabase.table("user_interests").select("*").in_("user_id", user_ids).execute()
    return {str(r["user_id"]): r for r in (resp.data or [])}


def fetch_merged_user(supabase: Client, user_id: str) -> Dict[str, Any]:
    pres = supabase.table("profiles").select("*").eq("id", user_id).limit(1).execute()
    rows = pres.data or []
    if not rows:
        raise KeyError("profile_not_found")
    profile = rows[0]
    ecr_map = _latest_ecr_by_user(supabase, [user_id])
    int_map = _interests_by_user(supabase, [user_id])
    return db_rows_to_user_row(profile, ecr_map.get(user_id), int_map.get(user_id))


def fetch_candidate_pool(
    supabase: Client,
    seeker_id: str,
    eligible_status: str,
) -> List[Dict[str, Any]]:
    pres = (
        supabase.table("profiles")
        .select("id, date_of_birth, gender, target_gender, location, occupation, current_status")
        .eq("current_status", eligible_status)
        .neq("id", seeker_id)
        .execute()
    )
    profiles = pres.data or []
    ids = [str(p["id"]) for p in profiles]
    ecr_map = _latest_ecr_by_user(supabase, ids)
    int_map = _interests_by_user(supabase, ids)
    return [db_rows_to_user_row(p, ecr_map.get(str(p["id"])), int_map.get(str(p["id"]))) for p in profiles]


def _hybrid_scores_to_match_items(rows: List[Dict[str, Any]]) -> List[MatchItem]:
    if not rows:
        return []
    hyb = [r["hybrid_score"] for r in rows]
    lo, hi = min(hyb), max(hyb)

    def percent(h: float) -> int:
        if hi <= lo:
            return 85
        x = (h - lo) / (hi - lo)
        return int(round(max(0.0, min(100.0, x * 100.0))))

    return [
        MatchItem(
            user_id=r["user_id"],
            hybrid_score=float(r["hybrid_score"]),
            rl_score=float(r["rl_score"]),
            rule_score=float(r["rule_score"]),
            match_percent=percent(float(r["hybrid_score"])),
        )
        for r in rows
    ]


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.supabase = create_client(settings.supabase_url, settings.supabase_service_role_key)
    app.state.hybrid = None
    if _should_eager_load_model():
        app.state.hybrid = _load_hybrid_at_runtime()
    yield


app = FastAPI(title="MatchMind ML API", lifespan=lifespan)


@app.get("/")
async def root():
    """Avoid 404 on `/` (Render probes and browsers); model is not served from HTTP paths."""
    return {"service": "matchmind-ml", "docs": "/docs", "health": "/health"}


@app.get("/health")
async def health():
    return {"ok": True}


@app.get("/debug/profile/{user_id}")
async def debug_profile_lookup(user_id: str, request: Request, token: str = ""):
    """Temporary diagnostics endpoint. Enable by setting DEBUG_TOKEN in env."""
    if not settings.debug_token:
        raise HTTPException(status_code=404, detail="Debug endpoint disabled")
    if token != settings.debug_token:
        raise HTTPException(status_code=403, detail="Forbidden")

    supabase: Client = request.app.state.supabase

    def _run():
        profile_resp = (
            supabase.table("profiles")
            .select("id,email,current_status,gender,target_gender,location")
            .eq("id", user_id)
            .limit(1)
            .execute()
        )
        ecr_resp = (
            supabase.table("user_ecr_assessments")
            .select("user_id,completed_at")
            .eq("user_id", user_id)
            .order("completed_at", desc=True)
            .limit(1)
            .execute()
        )
        interests_resp = (
            supabase.table("user_interests")
            .select("user_id,location,occupation")
            .eq("user_id", user_id)
            .limit(1)
            .execute()
        )
        return profile_resp.data or [], ecr_resp.data or [], interests_resp.data or []

    profile_rows, ecr_rows, interest_rows = await asyncio.to_thread(_run)
    return {
        "supabase_url": settings.supabase_url,
        "user_id": user_id,
        "profile_found": len(profile_rows) > 0,
        "ecr_found": len(ecr_rows) > 0,
        "interests_found": len(interest_rows) > 0,
        "profile": profile_rows[0] if profile_rows else None,
    }


@app.get("/get_matches/{user_id}", response_model=GetMatchesResponse)
async def get_matches(user_id: str, request: Request):
    supabase: Client = request.app.state.supabase

    def _run():
        hybrid = ensure_hybrid(request.app)
        try:
            target = fetch_merged_user(supabase, user_id)
        except KeyError:
            raise HTTPException(status_code=404, detail="Target user profile not found")
        candidates = fetch_candidate_pool(supabase, user_id, settings.candidate_status)
        top = hybrid.score_candidates(target, candidates, top_n=5)
        return hybrid, target, top

    try:
        hybrid, _target_row, top = await asyncio.to_thread(_run)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e

    return GetMatchesResponse(
        seeker_id=user_id,
        alpha=float(hybrid.alpha),
        matches=_hybrid_scores_to_match_items(top),
    )
