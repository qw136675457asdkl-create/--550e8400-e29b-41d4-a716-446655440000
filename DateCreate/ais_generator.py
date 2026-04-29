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


def format_eta(value: datetime) -> str:
    """Format ETA with second precision."""
    return value.strftime("%Y-%m-%d %H:%M:%S")


def save_ais_csv(
    output_path: str,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    flight_start_datetime: datetime,
    flight_end_datetime: datetime,
    sample_rate_hz: float,
    random_seed: int = 48,
) -> Tuple[pd.DataFrame, str]:
    """Generate placeholder AIS timeline data and save it as CSV."""
    columns = [
        "timestamp",
        "Message ID",
        "mmsi",
        "imo_num",
        "ship_name",
        "call_sign",
        "ship_type",
        "dimensions",
        "lat",
        "lon",
        "cog",
        "sog",
        "heading",
        "rot",
        "nav_status",
        "dest",
        "eta",
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
    ship_type_choices = np.array(["CARGO", "TANKER", "PASSENGER", "FISHING", "MILITARY"], dtype=object)
    nav_status_choices = np.array(["UNDERWAY_ENGINE", "AT_ANCHOR", "NOT_UNDER_COMMAND"], dtype=object)
    destination_choices = np.array(["NINGBO", "SHANGHAI", "QINGDAO", "DALIAN", "ZHOUSHAN"], dtype=object)
    message_id_choices = np.array([1, 2, 3, 5, 18], dtype=int)

    target_count = int(rng.integers(2, 5))
    duration_seconds = max((flight_end_datetime - flight_start_datetime).total_seconds(), 1.0)
    target_profiles: list[Dict[str, Any]] = []
    for target_id in range(1, target_count + 1):
        ship_type = str(rng.choice(ship_type_choices))
        length = float(rng.uniform(35.0, 260.0))
        width = float(rng.uniform(8.0, 42.0))
        bow = round(length * float(rng.uniform(0.45, 0.6)), 1)
        stern = round(length - bow, 1)
        port = round(width * float(rng.uniform(0.45, 0.55)), 1)
        starboard = round(width - port, 1)
        base_sog = float(rng.uniform(0.0, 24.0))
        base_cog = float(rng.uniform(0.0, 360.0))
        target_profiles.append(
            {
                "message_id": int(rng.choice(message_id_choices)),
                "mmsi": int(rng.integers(100000000, 999999999)),
                "imo_num": int(rng.integers(1000000, 9999999)),
                "ship_name": f"VESSEL_{target_id:02d}",
                "call_sign": f"CALL{rng.integers(1000, 9999)}",
                "ship_type": ship_type,
                "dimensions": f"[{bow:.1f}, {stern:.1f}, {port:.1f}, {starboard:.1f}]",
                "base_lat": float(rng.uniform(28.0, 33.0)),
                "base_lon": float(rng.uniform(119.0, 124.0)),
                "base_cog": base_cog,
                "base_sog": base_sog,
                "base_heading": (base_cog + float(rng.uniform(-4.0, 4.0))) % 360.0,
                "base_rot": float(rng.uniform(-8.0, 8.0)),
                "nav_status": str(rng.choice(nav_status_choices)),
                "dest": str(rng.choice(destination_choices)),
                "eta": flight_end_datetime + timedelta(hours=float(rng.uniform(2.0, 18.0))),
                "phase": float(rng.uniform(0.0, 2.0 * math.pi)),
            }
        )

    rows = []
    for index, elapsed_seconds in enumerate(sample_times):
        elapsed_seconds = float(elapsed_seconds)
        elapsed_ratio = elapsed_seconds / duration_seconds
        for profile in target_profiles:
            phase_wave = math.sin(elapsed_ratio * 2.0 * math.pi + profile["phase"])
            drift = elapsed_seconds / 3600.0
            lat = profile["base_lat"] + 0.015 * phase_wave + 0.01 * drift * math.cos(math.radians(profile["base_cog"]))
            lon = profile["base_lon"] + 0.020 * phase_wave + 0.01 * drift * math.sin(math.radians(profile["base_cog"]))
            cog = (profile["base_cog"] + 2.0 * phase_wave + rng.normal(0.0, 0.4)) % 360.0
            sog = max(0.0, profile["base_sog"] + 0.6 * phase_wave + rng.normal(0.0, 0.15))
            heading = (profile["base_heading"] + 3.0 * phase_wave + rng.normal(0.0, 0.6)) % 360.0
            rot = profile["base_rot"] + 1.5 * phase_wave + rng.normal(0.0, 0.3)

            rows.append(
                {
                    "timestamp": timestamps[index],
                    "Message ID": int(profile["message_id"]),
                    "mmsi": int(profile["mmsi"]),
                    "imo_num": int(profile["imo_num"]),
                    "ship_name": profile["ship_name"],
                    "call_sign": profile["call_sign"],
                    "ship_type": profile["ship_type"],
                    "dimensions": profile["dimensions"],
                    "lat": round(float(lat), 6),
                    "lon": round(float(lon), 6),
                    "cog": round(float(cog), 3),
                    "sog": round(float(sog), 3),
                    "heading": round(float(heading), 3),
                    "rot": round(float(rot), 3),
                    "nav_status": profile["nav_status"],
                    "dest": profile["dest"],
                    "eta": format_eta(profile["eta"]),
                    "_elapsed_seconds": elapsed_seconds,
                }
            )

    df = pd.DataFrame(rows)
    if not df.empty:
        df = df.sort_values(by=["_elapsed_seconds", "mmsi"], kind="stable").drop(columns=["_elapsed_seconds"])
        df = df[columns]
    else:
        df = pd.DataFrame(columns=columns)
    df.to_csv(output_path, index=False, encoding="utf-8-sig")
    return df, output_path
