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


def save_electronic_warfare_csv(
    output_path: str,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    flight_start_datetime: datetime,
    flight_end_datetime: datetime,
    sample_rate_hz: float,
    random_seed: int = 46,
) -> Tuple[pd.DataFrame, str]:
    """Generate placeholder electronic warfare timeline data and save it as CSV."""
    columns = [
        "target_id",
        "timestamp",
        "toa",
        "AOA_A",
        "AOA_E",
        "center_freq",
        "pulse_width",
        "Amplitude",
        "SNR",
        "pri_type",
        "pri_mean",
        "scan_type",
        "emitter_mode",
        "confidence",
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
    pri_type_choices = np.array(["STABLE", "STAGGERED", "JITTERED", "D&S"], dtype=object)
    scan_type_choices = np.array(["CIRCULAR", "SECTOR", "STEADY"], dtype=object)
    emitter_mode_map = {
        "CIRCULAR": np.array(["SEARCH"], dtype=object),
        "SECTOR": np.array(["SEARCH", "ACQUISITION"], dtype=object),
        "STEADY": np.array(["ACQUISITION", "TRACK GUIDANCE"], dtype=object),
    }

    target_count = int(rng.integers(1, 4))
    duration_seconds = max((flight_end_datetime - flight_start_datetime).total_seconds(), 1.0)
    target_profiles: list[Dict[str, Any]] = []
    for target_id in range(1, target_count + 1):
        scan_type = str(rng.choice(scan_type_choices))
        target_profiles.append(
            {
                "target_id": target_id,
                "scan_type": scan_type,
                "emitter_mode": str(rng.choice(emitter_mode_map[scan_type])),
                "pri_type": str(rng.choice(pri_type_choices)),
                "base_aoa_a": float(rng.uniform(-170.0, 170.0)),
                "base_aoa_e": float(rng.uniform(-8.0, 45.0)),
                "base_center_freq": float(rng.uniform(2000.0, 18000.0)),
                "base_pulse_width": float(rng.uniform(0.5, 80.0)),
                "base_amplitude": float(rng.uniform(-85.0, -25.0)),
                "base_snr": float(rng.uniform(6.0, 35.0)),
                "base_pri_mean": float(rng.uniform(50.0, 5000.0)),
                "base_confidence": int(rng.integers(60, 98)),
                "toa_offset": int(rng.integers(0, 1000)),
                "phase": float(rng.uniform(0.0, 2.0 * math.pi)),
            }
        )

    rows = []
    for index, elapsed_seconds in enumerate(sample_times):
        elapsed_seconds = float(elapsed_seconds)
        elapsed_us = int(round(elapsed_seconds * 1_000_000.0))
        waveform = math.sin((elapsed_seconds / duration_seconds) * 2.0 * math.pi)
        for profile in target_profiles:
            phase_wave = waveform + math.cos(
                (elapsed_seconds / duration_seconds) * 2.0 * math.pi + profile["phase"]
            )
            aoa_a = ((profile["base_aoa_a"] + 6.0 * phase_wave + rng.normal(0.0, 0.8) + 180.0) % 360.0) - 180.0
            aoa_e = np.clip(
                profile["base_aoa_e"] + 2.5 * phase_wave + rng.normal(0.0, 0.5),
                -15.0,
                85.0,
            )
            center_freq = max(
                100.0,
                profile["base_center_freq"] + 12.0 * phase_wave + rng.normal(0.0, 2.0),
            )
            pulse_width = max(
                0.1,
                profile["base_pulse_width"] + 0.2 * phase_wave + rng.normal(0.0, 0.05),
            )
            amplitude = profile["base_amplitude"] + 1.8 * phase_wave + rng.normal(0.0, 0.8)
            snr = max(0.0, profile["base_snr"] + 1.2 * phase_wave + rng.normal(0.0, 0.6))
            pri_mean = max(
                1.0,
                profile["base_pri_mean"] + 20.0 * phase_wave + rng.normal(0.0, 8.0),
            )
            confidence = int(
                np.clip(
                    round(profile["base_confidence"] + 4.0 * phase_wave + rng.normal(0.0, 2.0)),
                    0,
                    100,
                )
            )

            rows.append(
                {
                    "target_id": int(profile["target_id"]),
                    "timestamp": timestamps[index],
                    "toa": max(0, elapsed_us + profile["toa_offset"] + int(rng.integers(-20, 21))),
                    "AOA_A": round(float(aoa_a), 3),
                    "AOA_E": round(float(aoa_e), 3),
                    "center_freq": round(float(center_freq), 3),
                    "pulse_width": round(float(pulse_width), 3),
                    "Amplitude": round(float(amplitude), 3),
                    "SNR": round(float(snr), 3),
                    "pri_type": profile["pri_type"],
                    "pri_mean": round(float(pri_mean), 3),
                    "scan_type": profile["scan_type"],
                    "emitter_mode": profile["emitter_mode"],
                    "confidence": confidence,
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
