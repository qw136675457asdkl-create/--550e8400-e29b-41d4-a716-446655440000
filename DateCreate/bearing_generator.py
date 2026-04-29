from __future__ import annotations

from datetime import datetime, timedelta
import math
from typing import Dict, Tuple

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


def save_bearing_csv(
    output_path: str,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    flight_start_datetime: datetime,
    flight_end_datetime: datetime,
    sample_rate_hz: float,
    random_seed: int = 50,
) -> Tuple[pd.DataFrame, str]:
    """Generate placeholder bearing timeline data and save it as CSV."""
    columns = [
        "target_id",
        "timestamp",
        "sensor_type",
        "Azimuth",
        "Elevation",
        "DF_Quality",
        "Cov_Matrix",
        "SNR",
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
    sensor_choices = np.array(["RWR", "ESM", "IRST", "ACOUSTIC", "OPTICAL"], dtype=object)

    target_count = int(rng.integers(2, 5))
    duration_seconds = max((flight_end_datetime - flight_start_datetime).total_seconds(), 1.0)
    target_profiles: list[Dict[str, float | int | str]] = []
    for target_id in range(1, target_count + 1):
        target_profiles.append(
            {
                "target_id": target_id,
                "sensor_type": str(rng.choice(sensor_choices)),
                "base_azimuth": float(rng.uniform(-180.0, 180.0)),
                "base_elevation": float(rng.uniform(-10.0, 60.0)),
                "base_quality": float(rng.uniform(0.55, 0.95)),
                "base_cov_00": float(rng.uniform(0.05, 4.0)),
                "base_cov_01": float(rng.uniform(-0.2, 0.2)),
                "base_cov_11": float(rng.uniform(0.05, 4.0)),
                "base_snr": float(rng.uniform(5.0, 35.0)),
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
            azimuth = ((float(profile["base_azimuth"]) + 4.0 * phase_wave + rng.normal(0.0, 0.6) + 180.0) % 360.0) - 180.0
            elevation = np.clip(
                float(profile["base_elevation"]) + 1.8 * phase_wave + rng.normal(0.0, 0.3),
                -15.0,
                85.0,
            )
            quality = float(np.clip(float(profile["base_quality"]) + 0.05 * phase_wave + rng.normal(0.0, 0.01), 0.0, 1.0))
            cov_00 = max(0.001, float(profile["base_cov_00"]) + 0.08 * phase_wave + rng.normal(0.0, 0.02))
            cov_01 = float(profile["base_cov_01"]) + 0.01 * phase_wave + rng.normal(0.0, 0.005)
            cov_11 = max(0.001, float(profile["base_cov_11"]) + 0.08 * phase_wave + rng.normal(0.0, 0.02))
            snr = max(0.0, float(profile["base_snr"]) + 1.2 * phase_wave + rng.normal(0.0, 0.4))
            cov_matrix = [[round(cov_00, 4), round(cov_01, 4)], [round(cov_01, 4), round(cov_11, 4)]]

            rows.append(
                {
                    "target_id": int(profile["target_id"]),
                    "timestamp": timestamps[index],
                    "sensor_type": profile["sensor_type"],
                    "Azimuth": round(float(azimuth), 3),
                    "Elevation": round(float(elevation), 3),
                    "DF_Quality": round(float(quality), 4),
                    "Cov_Matrix": str(cov_matrix),
                    "SNR": round(float(snr), 3),
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
