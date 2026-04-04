"""
MatchMind v4 hybrid matchmaker — aligned with Model/v4.ipynb (MatchmakerDQN + HybridMatchmaker + process_pair).
"""

from __future__ import annotations

import json
from collections import deque
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim


# Same order as training (defines 16-d state: 5 numeric + 11 category overlaps)
CATEGORY_WEIGHTS: Dict[str, float] = {
    "Relationship Intent": 40.0,
    "Personality & Values": 20.0,
    "Lifestyle": 15.0,
    "Intellectual & Learning": 5.0,
    "Food & Drinks": 5.0,
    "Travel & Culture": 5.0,
    "Gaming & Digital": 5.0,
    "Sports & Outdoor": 5.0,
    "Arts & Creativity": 3.0,
    "Music": 2.0,
    "Movies & Shows": 2.0,
}


class MatchmakerDQN(nn.Module):
    """Input dim 16 — must match training checkpoint."""

    def __init__(self) -> None:
        super().__init__()
        self.network = nn.Sequential(
            nn.Linear(16, 128),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Linear(32, 1),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.network(x)


def calculate_jaccard(list_a: List[str], list_b: List[str]) -> float:
    set_a, set_b = set(list_a), set(list_b)
    union = len(set_a.union(set_b))
    if union == 0:
        return 0.0
    return len(set_a.intersection(set_b)) / union


def _interest_cell_to_list(value: Any) -> List[str]:
    """Normalize Supabase jsonb (list) or CSV JSON string to list[str]."""
    if value is None:
        return []
    if isinstance(value, list):
        return [str(x).strip() for x in value if str(x).strip()]
    if isinstance(value, str):
        s = value.strip()
        if not s:
            return []
        try:
            parsed = json.loads(s)
            if isinstance(parsed, list):
                return [str(x).strip() for x in parsed if str(x).strip()]
        except json.JSONDecodeError:
            pass
        return [s]
    return [str(value).strip()] if str(value).strip() else []


def _gender_pref_matches(pref: Any, candidate_gender: Any) -> bool:
    """App stores e.g. target_gender 'any' / 'female'; seeker must accept candidate's gender."""
    p = str(pref).strip().lower()
    g = str(candidate_gender).strip().lower()
    if not g:
        return False
    if p in ("", "any", "all", "everyone"):
        return True
    return p == g


def process_pair(user_a: Dict[str, Any], user_b: Dict[str, Any]) -> Tuple[Optional[np.ndarray], float]:
    """
    Two user dicts must expose training column names (see row_dict_from_db in main).
    Returns (16-d state vector or None if hard-fail, rule-based raw reward).
    """
    if not _gender_pref_matches(user_a.get("target_gender"), user_b.get("gender")):
        return None, -100.0
    if not _gender_pref_matches(user_b.get("target_gender"), user_a.get("gender")):
        return None, -100.0

    loc_a = str(user_a.get("location", "")).strip().lower()
    loc_b = str(user_b.get("location", "")).strip().lower()
    if not loc_a or loc_a != loc_b:
        return None, -50.0

    category_overlaps: Dict[str, float] = {}
    reward_from_interests = 0.0

    for cat, weight in CATEGORY_WEIGHTS.items():
        items_a = _interest_cell_to_list(user_a.get(cat))
        items_b = _interest_cell_to_list(user_b.get(cat))
        overlap = calculate_jaccard(items_a, items_b)
        category_overlaps[cat] = overlap
        reward_from_interests += overlap * weight

    anxiety_a = float(user_a.get("anxiety", 0.0))
    anxiety_b = float(user_b.get("anxiety", 0.0))
    avoidance_a = float(user_a.get("avoidance", 0.0))
    avoidance_b = float(user_b.get("avoidance", 0.0))

    trap_metric = (anxiety_a * avoidance_b) + (anxiety_b * avoidance_a)
    psych_reward = 50.0
    if trap_metric > 35:
        psych_reward -= 40.0
    if anxiety_a < 3.0 and anxiety_b < 3.0:
        psych_reward += 20.0

    total_reward = reward_from_interests + psych_reward

    age_a = float(user_a.get("age", 0.0))
    age_b = float(user_b.get("age", 0.0))

    state_vector: List[float] = [
        abs(age_a - age_b) / 10.0,
        anxiety_a / 7.0,
        avoidance_a / 7.0,
        anxiety_b / 7.0,
        avoidance_b / 7.0,
    ]
    for cat in CATEGORY_WEIGHTS:
        state_vector.append(category_overlaps[cat])

    return np.array(state_vector, dtype=np.float32), total_reward


class HybridMatchmaker:
    """Blends RL Q-value with normalized rule score (v4 notebook)."""

    def __init__(
        self,
        rl_agent: MatchmakerDQN,
        device: torch.device,
        initial_alpha: float = 0.3,
    ) -> None:
        self.rl_agent = rl_agent
        self.device = device
        self.alpha = initial_alpha
        self.online_memory: deque = deque(maxlen=5000)
        self.online_optimizer = optim.Adam(rl_agent.parameters(), lr=0.0005)
        self.online_criterion = nn.MSELoss()
        self.online_batch_size = 32
        self.total_feedback_count = 0
        self.alpha_schedule = [
            (0, 0.3),
            (50, 0.4),
            (200, 0.5),
            (500, 0.65),
            (1000, 0.75),
            (2000, 0.85),
            (5000, 0.90),
        ]

    def _update_alpha(self) -> None:
        for threshold, alpha_value in reversed(self.alpha_schedule):
            if self.total_feedback_count >= threshold:
                self.alpha = alpha_value
                break

    def get_rule_score(
        self, user_a: Dict[str, Any], user_b: Dict[str, Any]
    ) -> Tuple[Optional[np.ndarray], Optional[float]]:
        state_vector, raw_reward = process_pair(user_a, user_b)
        if state_vector is None:
            return None, None
        normalized_rule_score = float(np.clip(raw_reward / 120.0, -1.0, 1.0))
        return state_vector, normalized_rule_score

    def get_rl_score(self, state_vector: np.ndarray) -> float:
        self.rl_agent.eval()
        with torch.no_grad():
            t = torch.from_numpy(state_vector.astype(np.float32)).to(self.device)
            if t.dim() == 1:
                t = t.unsqueeze(0)
            return float(self.rl_agent(t).squeeze().item())

    def get_hybrid_score(
        self, user_a: Dict[str, Any], user_b: Dict[str, Any]
    ) -> Tuple[Optional[np.ndarray], Optional[float], Optional[float], Optional[float]]:
        state_vector, rule_score = self.get_rule_score(user_a, user_b)
        if state_vector is None or rule_score is None:
            return None, None, None, None
        rl_score = self.get_rl_score(state_vector)
        hybrid = (self.alpha * rl_score) + ((1.0 - self.alpha) * rule_score)
        return state_vector, hybrid, rl_score, rule_score

    def score_candidates(
        self,
        target: Dict[str, Any],
        candidates: List[Dict[str, Any]],
        top_n: int = 5,
    ) -> List[Dict[str, Any]]:
        """Rank candidates by hybrid score; same hard filters as notebook find_matches."""
        tid = str(target.get("user_id", ""))
        tloc = str(target.get("location", "")).strip().lower()

        filtered: List[Dict[str, Any]] = []
        for c in candidates:
            if str(c.get("user_id", "")) == tid:
                continue
            if not _gender_pref_matches(target.get("target_gender"), c.get("gender")):
                continue
            if not _gender_pref_matches(c.get("target_gender"), target.get("gender")):
                continue
            if str(c.get("location", "")).strip().lower() != tloc:
                continue
            filtered.append(c)

        states: List[np.ndarray] = []
        rule_norm: List[float] = []
        ids: List[str] = []
        for cand in filtered:
            state_vector, raw_reward = process_pair(target, cand)
            if state_vector is None:
                continue
            rn = float(np.clip(raw_reward / 120.0, -1.0, 1.0))
            states.append(state_vector)
            rule_norm.append(rn)
            ids.append(str(cand["user_id"]))

        if not states:
            return []

        self.rl_agent.eval()
        with torch.no_grad():
            batch = torch.from_numpy(np.stack(states, axis=0).astype(np.float32)).to(self.device)
            rl_tensor = self.rl_agent(batch).squeeze(-1).detach().cpu().numpy()

        predictions: List[Dict[str, Any]] = []
        for i, cid in enumerate(ids):
            rl_s = float(rl_tensor[i])
            rule_s = rule_norm[i]
            hybrid = float(self.alpha * rl_s + (1.0 - self.alpha) * rule_s)
            predictions.append(
                {
                    "user_id": cid,
                    "hybrid_score": hybrid,
                    "rl_score": rl_s,
                    "rule_score": rule_s,
                }
            )

        predictions.sort(key=lambda x: x["hybrid_score"], reverse=True)
        return predictions[:top_n]


def load_hybrid_from_checkpoint(path: str, device: torch.device) -> HybridMatchmaker:
    try:
        ckpt = torch.load(path, map_location=device, weights_only=False)
    except TypeError:
        ckpt = torch.load(path, map_location=device)
    agent = MatchmakerDQN().to(device)
    if isinstance(ckpt, dict) and "model_state_dict" in ckpt:
        agent.load_state_dict(ckpt["model_state_dict"])
        alpha = float(ckpt.get("alpha", 0.3))
        feedback = int(ckpt.get("total_feedback", 0))
    else:
        agent.load_state_dict(ckpt)
        alpha = 0.3
        feedback = 0
    agent.eval()
    hybrid = HybridMatchmaker(rl_agent=agent, device=device, initial_alpha=alpha)
    hybrid.alpha = alpha
    hybrid.total_feedback_count = feedback
    hybrid._update_alpha()
    return hybrid
