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


def build_timestamp_series_ms(
    timestamp_start_datetime: datetime,
    sample_times: np.ndarray,
) -> list[str]:
    """Format timestamps with millisecond precision."""
    return [
        format_timestamp_text_ms(timestamp_start_datetime + timedelta(seconds=float(value)))
        for value in sample_times
    ]


def format_timestamp_text_ms(value: datetime) -> str:
    """Format timestamps with three fractional digits."""
    return f"{value.strftime('%Y-%m-%d %H:%M:%S')}.{value.microsecond // 1000:03d}"


def save_communication_reconnaissance_csv(
    output_path: str,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    flight_start_datetime: datetime,
    flight_end_datetime: datetime,
    sample_rate_hz: float,
    random_seed: int = 47,
) -> Tuple[pd.DataFrame, str]:
    """Generate placeholder communication reconnaissance timeline data and save it as CSV."""
    columns = [
        "target_id",
        "timestamp",
        "DOA_A",
        "DOA_E",
        "F_est",
        "BW_est",
        "SNR_est",
        "Mod_Type",
        "Rate_est",
        "protocol",
        "source_id",
        "encryption",
        "voice_activity",
        "content_ref",
        "geo_loc_x",
        "geo_loc_y",
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

    timestamps = build_timestamp_series_ms(timestamp_start_datetime, sample_times)
    rng = np.random.default_rng(random_seed)
    modulation_choices = np.array(["FSK", "PSK", "QAM", "OFDM", "AM", "FM"], dtype=object)
    protocol_choices = np.array(["DMR", "Link 11", "LTE", "AIS", "VHF", "UHF"], dtype=object)
    source_prefix_choices = np.array(["MAC", "MMSI", "CALL", "RFID"], dtype=object)

    target_count = int(rng.integers(2, 5))
    duration_seconds = max((flight_end_datetime - flight_start_datetime).total_seconds(), 1.0)
    target_profiles: list[Dict[str, Any]] = []
    for target_id in range(1, target_count + 1):
        modulation = str(rng.choice(modulation_choices))
        protocol = str(rng.choice(protocol_choices))
        source_prefix = str(rng.choice(source_prefix_choices))
        encryption = bool(rng.integers(0, 2))
        voice_activity = bool(rng.integers(0, 2))
        target_profiles.append(
            {
                "target_id": target_id,
                "modulation": modulation,
                "protocol": protocol,
                "source_id": f"{source_prefix}_{rng.integers(100000, 999999)}",
                "encryption": encryption,
                "voice_activity": voice_activity,
                "content_ref": f"captures/target_{target_id:02d}/clip_{rng.integers(1, 50):03d}.dat",
                "base_doa_a": float(rng.uniform(-170.0, 170.0)),
                "base_doa_e": float(rng.uniform(-10.0, 45.0)),
                "base_freq": float(rng.uniform(30.0, 6000.0)),
                "base_bw": float(rng.uniform(0.0125, 20.0)),
                "base_snr": float(rng.uniform(5.0, 35.0)),
                "base_rate": float(rng.uniform(1200.0, 50_000_000.0)),
                "base_geo_x": float(rng.uniform(100.0, 130.0)),
                "base_geo_y": float(rng.uniform(20.0, 45.0)),
                "phase": float(rng.uniform(0.0, 2.0 * math.pi)),
            }
        )

    rows = []
    for index, elapsed_seconds in enumerate(sample_times):
        elapsed_seconds = float(elapsed_seconds)
        waveform = math.sin((elapsed_seconds / duration_seconds) * 2.0 * math.pi)
        for profile in target_profiles:
            phase_wave = waveform + math.cos(
                (elapsed_seconds / duration_seconds) * 2.0 * math.pi + profile["phase"]
            )
            doa_a = ((profile["base_doa_a"] + 5.0 * phase_wave + rng.normal(0.0, 0.7) + 180.0) % 360.0) - 180.0
            doa_e = np.clip(
                profile["base_doa_e"] + 2.0 * phase_wave + rng.normal(0.0, 0.4),
                -15.0,
                85.0,
            )
            frequency = max(1.0, profile["base_freq"] + 8.0 * phase_wave + rng.normal(0.0, 1.5))
            bandwidth = max(0.001, profile["base_bw"] + 0.08 * phase_wave + rng.normal(0.0, 0.02))
            snr = max(0.0, profile["base_snr"] + 1.0 * phase_wave + rng.normal(0.0, 0.5))
            rate = max(1.0, profile["base_rate"] + 5000.0 * phase_wave + rng.normal(0.0, 1000.0))
            geo_x = profile["base_geo_x"] + 0.01 * phase_wave + rng.normal(0.0, 0.002)
            geo_y = profile["base_geo_y"] + 0.01 * phase_wave + rng.normal(0.0, 0.002)

            rows.append(
                {
                    "target_id": int(profile["target_id"]),
                    "timestamp": timestamps[index],
                    "DOA_A": round(float(doa_a), 3),
                    "DOA_E": round(float(doa_e), 3),
                    "F_est": round(float(frequency), 3),
                    "BW_est": round(float(bandwidth), 4),
                    "SNR_est": round(float(snr), 3),
                    "Mod_Type": profile["modulation"],
                    "Rate_est": round(float(rate), 3),
                    "protocol": profile["protocol"],
                    "source_id": profile["source_id"],
                    "encryption": bool(profile["encryption"]),
                    "voice_activity": bool(profile["voice_activity"] and ((index + profile["target_id"]) % 3 != 0)),
                    "content_ref": profile["content_ref"],
                    "geo_loc_x": round(float(geo_x), 6),
                    "geo_loc_y": round(float(geo_y), 6),
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
