"""
主机机载雷达模块。
"""

from __future__ import annotations

from datetime import datetime, timedelta
import math
import os
from typing import Any, Mapping, Optional, Sequence

import numpy as np
import pandas as pd


# ==================== 机载雷达模型 ====================
class AirborneRadar:
    """安装在主机上的机载雷达。"""

    def __init__(self, range_km: float = 150.0):
        self.range_km = float(range_km)
        if self.range_km <= 0:
            raise ValueError("机载雷达扫描距离必须大于 0。")

    # ==================== 时间与目标整理 ====================
    def _parse_start_datetime(self, value: Any) -> Optional[datetime]:
        if value is None:
            return None
        if isinstance(value, datetime):
            return value
        if not isinstance(value, str):
            raise ValueError("flight_start_datetime 必须是 datetime 或字符串。")
        for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S"):
            try:
                return datetime.strptime(value, fmt)
            except ValueError:
                continue
        raise ValueError("flight_start_datetime 格式必须是 YYYY-MM-DD HH:MM:SS 或 YYYY-MM-DD HH:MM:SS.mmm。")

    def _build_timestamp_series(
        self,
        t: np.ndarray,
        flight_start_datetime: Any = None,
    ) -> Sequence[str]:
        start_datetime = self._parse_start_datetime(flight_start_datetime)
        if start_datetime is None:
            return [f"{float(seconds):.3f}s" for seconds in t]
        has_millisecond = not np.allclose(t, np.round(t))
        timestamps = []
        for seconds in t:
            current_time = start_datetime + timedelta(seconds=float(seconds))
            if has_millisecond:
                timestamps.append(current_time.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3])
            else:
                timestamps.append(current_time.strftime("%Y-%m-%d %H:%M:%S"))
        return timestamps

    def _build_target_number(self, target_name: str, target: Mapping[str, Any]) -> str:
        label = target.get("label")
        if label:
            return str(label)
        target_id = target.get("id")
        if target_id is not None:
            return str(target_id)
        return str(target_name)

    def _should_skip_target(self, target_name: str, target: Mapping[str, Any]) -> bool:
        normalized_name = str(target_name).strip().lower()
        normalized_label = str(target.get("label", "")).strip()
        return normalized_name == "host" or normalized_label == "主机"

    # ==================== 坐标与方位角计算 ====================
    def _calculate_distance_and_bearing(
        self,
        host_lon: float,
        host_lat: float,
        host_alt: float,
        target_lon: float,
        target_lat: float,
        target_alt: float,
    ) -> tuple[float, float]:
        lat1, lon1 = math.radians(host_lat), math.radians(host_lon)
        lat2, lon2 = math.radians(target_lat), math.radians(target_lon)

        dlon = lon2 - lon1
        dlat = lat2 - lat1

        a = math.sin(dlat / 2.0) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2.0) ** 2
        c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
        horizontal_distance_km = 6371.0 * c

        altitude_delta_km = (float(target_alt) - float(host_alt)) / 1000.0
        distance_km = math.sqrt(horizontal_distance_km ** 2 + altitude_delta_km ** 2)

        x = math.sin(dlon) * math.cos(lat2)
        y = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(dlon)
        absolute_bearing_deg = (math.degrees(math.atan2(x, y)) + 360.0) % 360.0
        return distance_km, absolute_bearing_deg

    def _derive_host_heading(self, host_lon: np.ndarray, host_lat: np.ndarray) -> np.ndarray:
        mean_lat = float(np.nanmean(host_lat))
        lon_to_meter = 111_320.0 * math.cos(math.radians(mean_lat))
        lat_to_meter = 110_540.0
        x = (host_lon - host_lon[0]) * lon_to_meter
        y = (host_lat - host_lat[0]) * lat_to_meter

        dx = np.gradient(x)
        dy = np.gradient(y)
        heading = (np.degrees(np.arctan2(dx, dy)) + 360.0) % 360.0

        if len(heading) == 0:
            return heading

        valid_mask = np.isfinite(heading)
        if not np.any(valid_mask):
            return np.zeros_like(heading)

        first_valid = heading[valid_mask][0]
        heading = heading.copy()
        heading[~valid_mask] = first_valid
        return heading

    def _relative_azimuth(self, absolute_bearing_deg: float, host_heading_deg: float) -> float:
        return ((absolute_bearing_deg - host_heading_deg + 180.0) % 360.0) - 180.0

    # ==================== 机载扫描主流程 ====================
    def scan_targets(
        self,
        t: Sequence[float],
        host_lon: Sequence[float],
        host_lat: Sequence[float],
        host_z: Sequence[float],
        targets: Mapping[str, Mapping[str, Any]],
        flight_start_datetime: Any = None,
        host_heading_deg: Optional[Sequence[float]] = None,
    ) -> pd.DataFrame:
        """
        扫描范围内目标并返回按时间戳排序的探测结果。

        方位角采用机载雷达口径，表示目标相对主机当前航向的角度：
        机头方向为 0 度，右侧为正，左侧为负，范围为 [-180, 180]。
        """
        t = np.asarray(t, dtype=float)
        host_lon = np.asarray(host_lon, dtype=float)
        host_lat = np.asarray(host_lat, dtype=float)
        host_z = np.asarray(host_z, dtype=float)

        if not (len(t) == len(host_lon) == len(host_lat) == len(host_z)):
            raise ValueError("主机时间轴与经纬高序列长度必须一致。")

        if host_heading_deg is None:
            host_heading_deg = self._derive_host_heading(host_lon, host_lat)
        else:
            host_heading_deg = np.asarray(host_heading_deg, dtype=float)
            if len(host_heading_deg) != len(t):
                raise ValueError("host_heading_deg 与时间轴长度必须一致。")

        timestamps = self._build_timestamp_series(t, flight_start_datetime)
        rows = []

        for target_name, target in targets.items():
            if self._should_skip_target(target_name, target):
                continue
            if not all(key in target for key in ("lon", "lat", "z")):
                continue

            target_lon = np.asarray(target["lon"], dtype=float)
            target_lat = np.asarray(target["lat"], dtype=float)
            target_z = np.asarray(target["z"], dtype=float)

            if not (len(target_lon) == len(target_lat) == len(target_z) == len(t)):
                raise ValueError(f"目标 {target_name} 的轨迹长度与主机时间轴不一致。")

            target_number = self._build_target_number(target_name, target)

            for index, seconds in enumerate(t):
                values = (
                    host_lon[index],
                    host_lat[index],
                    host_z[index],
                    target_lon[index],
                    target_lat[index],
                    target_z[index],
                )
                if not np.all(np.isfinite(values)):
                    continue

                distance_km, absolute_bearing_deg = self._calculate_distance_and_bearing(
                    host_lon=host_lon[index],
                    host_lat=host_lat[index],
                    host_alt=host_z[index],
                    target_lon=target_lon[index],
                    target_lat=target_lat[index],
                    target_alt=target_z[index],
                )
                if distance_km > self.range_km:
                    continue

                rows.append(
                    {
                        "飞机编号": target_number,
                        "时间戳": timestamps[index],
                        "方位角(度)": round(self._relative_azimuth(absolute_bearing_deg, host_heading_deg[index]), 3),
                        "距离(公里)": round(distance_km, 3),
                        "_排序时间": float(seconds),
                        "_排序编号": str(target_number),
                    }
                )

        if not rows:
            return pd.DataFrame(columns=["飞机编号", "时间戳", "方位角(度)", "距离(公里)"])

        df = pd.DataFrame(rows)
        df = df.sort_values(by=["_排序时间", "_排序编号"], kind="stable").reset_index(drop=True)
        return df.drop(columns=["_排序时间", "_排序编号"])

    # ==================== 探测结果导出 ====================
    def save_scan_csv(
        self,
        df: pd.DataFrame,
        filename: Optional[str] = None,
        output_directory: str = "csv_output",
    ) -> str:
        """将机载雷达探测结果保存为 CSV。"""
        if filename is None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"airborne_radar_scan_{timestamp}.csv"

        os.makedirs(output_directory, exist_ok=True)
        output_path = filename
        if not os.path.isabs(output_path) and not os.path.dirname(output_path):
            output_path = os.path.join(output_directory, output_path)

        df.to_csv(output_path, index=False, encoding="utf-8-sig")
        return output_path


# ==================== 对外调用接口 ====================
def scan_airborne_targets(
    t: Sequence[float],
    host_lon: Sequence[float],
    host_lat: Sequence[float],
    host_z: Sequence[float],
    targets: Mapping[str, Mapping[str, Any]],
    range_km: float = 150.0,
    flight_start_datetime: Any = None,
    host_heading_deg: Optional[Sequence[float]] = None,
) -> pd.DataFrame:
    """扫描目标并返回机载雷达探测结果表。"""
    radar = AirborneRadar(range_km=range_km)
    return radar.scan_targets(
        t=t,
        host_lon=host_lon,
        host_lat=host_lat,
        host_z=host_z,
        targets=targets,
        flight_start_datetime=flight_start_datetime,
        host_heading_deg=host_heading_deg,
    )
