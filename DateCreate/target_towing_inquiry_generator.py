from __future__ import annotations

from datetime import datetime, timedelta
import math
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


def save_target_towing_inquiry_csv(
    output_path: str,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    flight_start_datetime: datetime,
    flight_end_datetime: datetime,
    sample_rate_hz: float,
    random_seed: int = 49,
) -> Tuple[pd.DataFrame, str]:
    """Generate placeholder target towing and inquiry timeline data and save it as CSV."""
    columns = [
        "target_id",
        "timestamp",
        "Message ID",
        "Instruction Type",
        "Task Para",
        "Task_Priority",
        "towing_status",
        "inquiry_status",
        "towing_distance",
        "towing_acceleration",
        "target_lon",
        "target_lat",
        "target_alt",
        "target_v_x",
        "target_v_y",
        "target_v_z",
        "response_status",
        "Task_Progress",
        "Remaining Resources",
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
    instruction_choices = np.array(["ATTACK", "RECON", "MANEUVER", "ESCORT"], dtype=object)

    target_count = int(rng.integers(2, 5))
    duration_seconds = max((flight_end_datetime - flight_start_datetime).total_seconds(), 1.0)
    target_profiles: list[Dict[str, Any]] = []
    for target_id in range(1, target_count + 1):
        target_profiles.append(
            {
                "target_id": target_id,
                "message_id": int(rng.integers(1, 65536)),
                "instruction_type": str(rng.choice(instruction_choices)),
                "task_priority": int(rng.integers(1, 6)),
                "base_towing_status": int(rng.integers(0, 4)),
                "base_inquiry_status": int(rng.integers(0, 4)),
                "base_distance": float(rng.uniform(100.0, 12000.0)),
                "base_acceleration": float(rng.uniform(-3.0, 3.0)),
                "base_lon": float(rng.uniform(119.0, 124.0)),
                "base_lat": float(rng.uniform(28.0, 33.0)),
                "base_alt": float(rng.uniform(500.0, 12000.0)),
                "base_vx": float(rng.uniform(-250.0, 250.0)),
                "base_vy": float(rng.uniform(-250.0, 250.0)),
                "base_vz": float(rng.uniform(-20.0, 20.0)),
                "base_response_status": int(rng.integers(0, 4)),
                "task_progress_start": float(rng.uniform(0.0, 40.0)),
                "resources": np.array(
                    [
                        float(rng.uniform(40.0, 100.0)),
                        float(rng.uniform(40.0, 100.0)),
                        float(rng.uniform(40.0, 100.0)),
                    ],
                    dtype=float,
                ),
                "phase": float(rng.uniform(0.0, 2.0 * math.pi)),
            }
        )

    rows = []
    for index, elapsed_seconds in enumerate(sample_times):
        elapsed_seconds = float(elapsed_seconds)
        elapsed_ratio = elapsed_seconds / duration_seconds
        progress_wave = math.sin(elapsed_ratio * 2.0 * math.pi)
        for profile in target_profiles:
            phase_wave = progress_wave + math.cos(elapsed_ratio * 2.0 * math.pi + profile["phase"])
            task_progress = int(
                np.clip(
                    round(profile["task_progress_start"] + 60.0 * elapsed_ratio + 4.0 * phase_wave),
                    0,
                    100,
                )
            )
            resources = np.clip(
                profile["resources"] - np.array([10.0, 8.0, 6.0]) * elapsed_ratio + rng.normal(0.0, 0.8, 3),
                0.0,
                100.0,
            )
            task_para_bytes = [
                int(rng.integers(0, 256)),
                int(rng.integers(0, 256)),
                int(rng.integers(0, 256)),
                int(rng.integers(0, 256)),
            ]

            rows.append(
                {
                    "target_id": int(profile["target_id"]),
                    "timestamp": timestamps[index],
                    "Message ID": int(profile["message_id"]),
                    "Instruction Type": profile["instruction_type"],
                    "Task Para": str(task_para_bytes),
                    "Task_Priority": int(profile["task_priority"]),
                    "towing_status": int(np.clip(profile["base_towing_status"] + (task_progress > 50), 0, 4)),
                    "inquiry_status": int(np.clip(profile["base_inquiry_status"] + (index % 2), 0, 4)),
                    "towing_distance": round(float(max(0.0, profile["base_distance"] - 80.0 * elapsed_ratio + 5.0 * phase_wave)), 3),
                    "towing_acceleration": round(float(profile["base_acceleration"] + 0.2 * phase_wave + rng.normal(0.0, 0.05)), 3),
                    "target_lon": round(float(profile["base_lon"] + 0.02 * phase_wave + 0.001 * elapsed_ratio), 6),
                    "target_lat": round(float(profile["base_lat"] + 0.02 * phase_wave + 0.001 * elapsed_ratio), 6),
                    "target_alt": round(float(max(0.0, profile["base_alt"] + 10.0 * phase_wave + rng.normal(0.0, 2.0))), 3),
                    "target_v_x": round(float(profile["base_vx"] + 1.5 * phase_wave + rng.normal(0.0, 0.5)), 3),
                    "target_v_y": round(float(profile["base_vy"] + 1.5 * phase_wave + rng.normal(0.0, 0.5)), 3),
                    "target_v_z": round(float(profile["base_vz"] + 0.3 * phase_wave + rng.normal(0.0, 0.1)), 3),
                    "response_status": int(np.clip(profile["base_response_status"] + (task_progress > 75), 0, 4)),
                    "Task_Progress": task_progress,
                    "Remaining Resources": str(np.round(resources, 2).tolist()),
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
