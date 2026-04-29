from __future__ import annotations

from datetime import datetime, timedelta
import math
import uuid
from typing import Any, Dict, Tuple

import numpy as np
import pandas as pd


def build_sample_times(
    reference_start_datetime: datetime,
    flight_start_datetime: datetime,
    flight_end_datetime: datetime,
    sample_rate_hz: float,
) -> np.ndarray:
    """Build sampling timestamps relative to the common reference start time."""
    start_seconds = (flight_start_datetime - reference_start_datetime).total_seconds()
    end_seconds = (flight_end_datetime - reference_start_datetime).total_seconds()
    sample_interval = 1.0 / float(sample_rate_hz)
    sample_times = np.round(np.arange(start_seconds, end_seconds + sample_interval, sample_interval), 9)
    return sample_times[sample_times <= end_seconds + 1e-9]


def build_timestamp_series_ns(
    timestamp_start_datetime: datetime,
    sample_times: np.ndarray,
) -> list[str]:
    """Format timestamps with 9 fractional digits as a nanosecond-style placeholder."""
    return [
        format_timestamp_text_ns(timestamp_start_datetime + timedelta(seconds=float(value)))
        for value in sample_times
    ]


def format_timestamp_text_ns(value: datetime) -> str:
    """Format timestamps with microseconds padded to nanosecond-style text."""
    return f"{value.strftime('%Y-%m-%d %H:%M:%S')}.{value.microsecond:06d}000"


def save_lock_information_csv(
    output_path: str,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    flight_start_datetime: datetime,
    flight_end_datetime: datetime,
    sample_rate_hz: float,
    random_seed: int = 51,
) -> Tuple[pd.DataFrame, str]:
    """Generate placeholder lock-information timeline data and save it as CSV."""
    columns = [
        "target_id",
        "timestamp",
        "lock_id",
        "lock_state",
        "lock_sub_state",
        "lock_quality",
        "Cause_State_Change",
        "Miss_Distance_Estimation",
        "Lead_Calculation",
        "Launch_Envelope_Check",
        "Target_Maneuver_Overload",
        "weapon_chan",
        "radar_mode",
        "time_to_go",
        "launch_zone",
        "jam_indicator",
    ]
    sample_times = build_sample_times(
        reference_start_datetime=reference_start_datetime,
        flight_start_datetime=flight_start_datetime,
        flight_end_datetime=flight_end_datetime,
        sample_rate_hz=sample_rate_hz,
    )
    if len(sample_times) == 0:
        df = pd.DataFrame(columns=columns)
        df.to_csv(output_path, index=False, encoding="utf-8-sig")
        return df, output_path

    timestamps = build_timestamp_series_ns(timestamp_start_datetime, sample_times)
    rng = np.random.default_rng(random_seed)
    lock_state_choices = np.array(["ACQUIRING", "LOCKED", "MEMORY", "BREAK_LOCK"], dtype=object)
    radar_mode_choices = np.array(["STT", "TWS", "SAM", "FLOOD"], dtype=object)
    launch_zone_choices = np.array(["R_MAX", "R_NE", "R_MIN", "OUT_OF_ZONE"], dtype=object)
    sub_state_map = {
        "ACQUIRING": np.array(["SEARCH_CONFIRM", "GATE_BUILD", "TRACK_FORMING"], dtype=object),
        "LOCKED": np.array(["STABLE_TRACK", "WEAPON_SUPPORT", "TERMINAL_GUIDANCE"], dtype=object),
        "MEMORY": np.array(["COAST", "INERTIAL_PROPAGATION", "REACQUIRE"], dtype=object),
        "BREAK_LOCK": np.array(["JAM_BREAK", "MANEUVER_BREAK", "DROP_TRACK"], dtype=object),
    }

    target_count = int(rng.integers(2, 5))
    duration_seconds = max((flight_end_datetime - flight_start_datetime).total_seconds(), 1.0)
    target_profiles: list[Dict[str, Any]] = []
    for target_id in range(1, target_count + 1):
        base_state = str(rng.choice(lock_state_choices, p=np.array([0.18, 0.58, 0.16, 0.08])))
        base_zone = str(rng.choice(launch_zone_choices, p=np.array([0.18, 0.34, 0.28, 0.20])))
        target_profiles.append(
            {
                "target_id": target_id,
                "lock_id": str(uuid.uuid4()),
                "base_state": base_state,
                "base_lock_quality": float(rng.uniform(0.62, 0.96)),
                "base_miss_distance": float(rng.uniform(3.0, 120.0)),
                "base_lead_azimuth": float(rng.uniform(-6.0, 6.0)),
                "base_lead_elevation": float(rng.uniform(-4.0, 4.0)),
                "base_overload": float(rng.uniform(1.5, 10.0)),
                "weapon_chan": int(rng.integers(1, 5)),
                "radar_mode": str(rng.choice(radar_mode_choices)),
                "base_time_to_go": float(rng.uniform(8.0, 110.0)),
                "launch_zone": base_zone,
                "cause_state_change": int(rng.integers(0, 16)),
                "phase": float(rng.uniform(0.0, 2.0 * math.pi)),
            }
        )

    rows = []
    for index, elapsed_seconds in enumerate(sample_times):
        elapsed_seconds = float(elapsed_seconds)
        elapsed_ratio = elapsed_seconds / duration_seconds
        waveform = math.sin(elapsed_ratio * 2.0 * math.pi)
        for profile in target_profiles:
            phase_wave = waveform + math.cos(elapsed_ratio * 2.0 * math.pi + float(profile["phase"]))
            state_roll = float(rng.random())
            if state_roll < 0.06:
                lock_state = "BREAK_LOCK"
            elif state_roll < 0.18:
                lock_state = "MEMORY"
            elif state_roll < 0.36:
                lock_state = "ACQUIRING"
            else:
                lock_state = str(profile["base_state"])
            lock_sub_state = str(rng.choice(sub_state_map[lock_state]))

            lock_quality = float(
                np.clip(
                    float(profile["base_lock_quality"]) + 0.06 * phase_wave + rng.normal(0.0, 0.02),
                    0.0,
                    1.0,
                )
            )
            miss_distance = max(
                0.0,
                float(profile["base_miss_distance"]) + 6.5 * phase_wave + rng.normal(0.0, 1.8),
            )
            lead_azimuth = float(profile["base_lead_azimuth"]) + 0.7 * phase_wave + rng.normal(0.0, 0.12)
            lead_elevation = float(profile["base_lead_elevation"]) + 0.5 * phase_wave + rng.normal(0.0, 0.1)
            target_overload = max(
                0.0,
                float(profile["base_overload"]) + 0.8 * phase_wave + rng.normal(0.0, 0.25),
            )
            time_to_go = max(
                0.0,
                float(profile["base_time_to_go"]) - elapsed_ratio * float(profile["base_time_to_go"]) + rng.normal(0.0, 1.2),
            )
            launch_envelope_check = bool(
                lock_state == "LOCKED"
                and lock_quality >= 0.72
                and profile["launch_zone"] != "OUT_OF_ZONE"
                and time_to_go <= float(profile["base_time_to_go"])
            )
            jam_indicator = bool(lock_state in {"MEMORY", "BREAK_LOCK"} and rng.random() < 0.65)
            cause_state_change = int(profile["cause_state_change"])
            if lock_state == "BREAK_LOCK":
                cause_state_change = int(rng.integers(10, 16))
            elif lock_state == "MEMORY":
                cause_state_change = int(rng.integers(6, 11))
            elif lock_state == "ACQUIRING":
                cause_state_change = int(rng.integers(1, 6))

            rows.append(
                {
                    "target_id": int(profile["target_id"]),
                    "timestamp": timestamps[index],
                    "lock_id": profile["lock_id"],
                    "lock_state": lock_state,
                    "lock_sub_state": lock_sub_state,
                    "lock_quality": round(float(lock_quality), 4),
                    "Cause_State_Change": cause_state_change,
                    "Miss_Distance_Estimation": round(float(miss_distance), 3),
                    "Lead_Calculation": str([round(float(lead_azimuth), 3), round(float(lead_elevation), 3)]),
                    "Launch_Envelope_Check": launch_envelope_check,
                    "Target_Maneuver_Overload": round(float(target_overload), 3),
                    "weapon_chan": int(profile["weapon_chan"]),
                    "radar_mode": profile["radar_mode"],
                    "time_to_go": round(float(time_to_go), 3),
                    "launch_zone": profile["launch_zone"],
                    "jam_indicator": jam_indicator,
                    "_elapsed_seconds": elapsed_seconds,
                }
            )

    df = pd.DataFrame(rows)
    if not df.empty:
        df = df.sort_values(by=["_elapsed_seconds", "target_id"], kind="stable").drop(columns=["_elapsed_seconds"])
        df = df[columns]
    else:
        df = pd.DataFrame(columns=columns)
    df.to_csv(output_path, index=False, encoding="utf-8-sig")
    return df, output_path
