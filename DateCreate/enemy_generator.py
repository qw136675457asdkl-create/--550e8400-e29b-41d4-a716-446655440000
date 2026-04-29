"""基于共享圆心与等分扇形区域的敌机轨迹生成模块。"""

from __future__ import annotations

import math
from typing import Any, Dict

import numpy as np


# ==================== 参数配置区 ====================
FORMATION_AREA_MARGIN_M = 80_000.0
FORMATION_CENTER_MARGIN_M = 55_000.0
CRUISE_ALTITUDE_RANGE_M = (8_200.0, 11_200.0)
START_RADIUS_RANGE_KM = (3.0, 8.0)
END_RADIUS_RANGE_KM = (22.0, 45.0)
S_PERIODS_RANGE = (1.6, 2.4)
S_AMPLITUDE_RANGE_KM = (2.5, 9.0)
ALTITUDE_WAVE_AMPLITUDE_M = 40.0
ALTITUDE_OFFSET_RANGE_M = (-250.0, 250.0)
DEFAULT_MAX_DISTANCE_KM = 50.0


# ==================== 敌机轨迹生成器 ====================
class EnemyGenerator:
    """围绕共享圆心生成敌机轨迹。"""

    def __init__(
        self,
        main_lon,
        main_lat,
        main_z,
        t,
        start_coords,
        end_coords,
        flight_time=3600,
        random_seed=42,
    ):
        self.main_lon = np.asarray(main_lon, dtype=float)
        self.main_lat = np.asarray(main_lat, dtype=float)
        self.main_z = np.asarray(main_z, dtype=float)
        self.t = np.asarray(t, dtype=float)
        self.flight_time = float(flight_time)
        if self.flight_time > 0:
            self.t_norm = self.t / self.flight_time
        else:
            self.t_norm = np.zeros_like(self.t)

        self.start_coords = start_coords
        self.end_coords = end_coords
        self.rng = np.random.default_rng(random_seed)

        self.reference_lon = float(self.main_lon[0])
        self.reference_lat = float(self.main_lat[0])
        mean_lat = float(np.mean(self.main_lat))
        self.lon_to_meter = 111_320.0 * np.cos(np.radians(mean_lat))
        self.lat_to_meter = 110_540.0

        self.main_x = (self.main_lon - self.reference_lon) * self.lon_to_meter
        self.main_y = (self.main_lat - self.reference_lat) * self.lat_to_meter

        self.min_x = float(np.min(self.main_x) - FORMATION_AREA_MARGIN_M)
        self.max_x = float(np.max(self.main_x) + FORMATION_AREA_MARGIN_M)
        self.min_y = float(np.min(self.main_y) - FORMATION_AREA_MARGIN_M)
        self.max_y = float(np.max(self.main_y) + FORMATION_AREA_MARGIN_M)

    def _meters_to_geo(self, x, y):
        lon = np.asarray(x, dtype=float) / self.lon_to_meter + self.reference_lon
        lat = np.asarray(y, dtype=float) / self.lat_to_meter + self.reference_lat
        return lon, lat

    # ==================== 圆心与扇形采样 ====================
    def _sample_center_coordinate(self, minimum: float, maximum: float, margin: float) -> float:
        if maximum - minimum <= margin * 2:
            return (minimum + maximum) / 2.0
        return float(self.rng.uniform(minimum + margin, maximum - margin))

    def pick_formation_center(self, max_distance_km: float = DEFAULT_MAX_DISTANCE_KM) -> Dict[str, float]:
        """在正常巡航高度随机生成一个编队圆心。"""
        margin = max(float(max_distance_km) * 1000.0 + 5_000.0, FORMATION_CENTER_MARGIN_M)
        center_x = self._sample_center_coordinate(self.min_x, self.max_x, margin)
        center_y = self._sample_center_coordinate(self.min_y, self.max_y, margin)
        center_alt = float(self.rng.uniform(*CRUISE_ALTITUDE_RANGE_M))
        center_lon, center_lat = self._meters_to_geo(np.array([center_x]), np.array([center_y]))
        return {
            "x": center_x,
            "y": center_y,
            "lon": float(center_lon[0]),
            "lat": float(center_lat[0]),
            "alt": center_alt,
            "rotation_rad": float(self.rng.uniform(0.0, 2.0 * math.pi)),
        }

    # ==================== S 形曲线辅助计算 ====================
    def _build_s_curve(self, s_periods: float) -> np.ndarray:
        envelope = np.sin(np.pi * self.t_norm)
        primary = np.sin(2.0 * np.pi * s_periods * self.t_norm)
        harmonic = 0.25 * np.sin(4.0 * np.pi * s_periods * self.t_norm)
        return (primary + harmonic) * envelope

    def _smoothed_noise(self, scale: float, window_size: int) -> np.ndarray:
        noise = self.rng.normal(0.0, scale, len(self.t))
        kernel = np.ones(max(window_size, 1), dtype=float) / max(window_size, 1)
        return np.convolve(noise, kernel, mode="same")

    def _angle_delta(self, values: np.ndarray, reference: float) -> np.ndarray:
        return (values - reference + np.pi) % (2.0 * np.pi) - np.pi

    # ==================== 扇形轨迹采样 ====================
    def _sample_sector_path(
        self,
        center_x: float,
        center_y: float,
        sector_center: float,
        sector_span: float,
        max_distance_km: float,
    ) -> Dict[str, float]:
        angle_jitter = min(sector_span * 0.22, math.radians(24.0))

        for _ in range(80):
            start_radius_km = float(self.rng.uniform(*START_RADIUS_RANGE_KM))
            end_radius_km = float(self.rng.uniform(*END_RADIUS_RANGE_KM))
            start_angle = sector_center + float(self.rng.uniform(-angle_jitter, angle_jitter))
            end_angle = sector_center + float(self.rng.uniform(-angle_jitter, angle_jitter))

            start_x = center_x + start_radius_km * 1000.0 * math.cos(start_angle)
            start_y = center_y + start_radius_km * 1000.0 * math.sin(start_angle)
            end_x = center_x + end_radius_km * 1000.0 * math.cos(end_angle)
            end_y = center_y + end_radius_km * 1000.0 * math.sin(end_angle)

            course_length_km = math.hypot(end_x - start_x, end_y - start_y) / 1000.0
            if 8.0 <= course_length_km <= max_distance_km:
                return {
                    "start_x": start_x,
                    "start_y": start_y,
                    "end_x": end_x,
                    "end_y": end_y,
                    "start_radius_km": start_radius_km,
                    "end_radius_km": end_radius_km,
                    "course_length_km": course_length_km,
                }

        fallback_end_radius_km = min(float(max_distance_km) - 2.0, 28.0)
        start_radius_km = 4.0
        start_x = center_x + start_radius_km * 1000.0 * math.cos(sector_center)
        start_y = center_y + start_radius_km * 1000.0 * math.sin(sector_center)
        end_x = center_x + fallback_end_radius_km * 1000.0 * math.cos(sector_center)
        end_y = center_y + fallback_end_radius_km * 1000.0 * math.sin(sector_center)
        return {
            "start_x": start_x,
            "start_y": start_y,
            "end_x": end_x,
            "end_y": end_y,
            "start_radius_km": start_radius_km,
            "end_radius_km": fallback_end_radius_km,
            "course_length_km": math.hypot(end_x - start_x, end_y - start_y) / 1000.0,
        }

    # ==================== 扇形内 S 形约束 ====================
    def _fit_s_curve_to_sector(
        self,
        center_x: float,
        center_y: float,
        sector_center: float,
        sector_span: float,
        base_x: np.ndarray,
        base_y: np.ndarray,
        perp_x: float,
        perp_y: float,
        requested_amplitude_km: float,
        s_periods: float,
    ) -> Dict[str, Any]:
        s_factor = self._build_s_curve(s_periods)
        amplitude_km = float(requested_amplitude_km)

        for _ in range(8):
            enemy_x = base_x + amplitude_km * 1000.0 * s_factor * perp_x
            enemy_y = base_y + amplitude_km * 1000.0 * s_factor * perp_y

            angles = np.arctan2(enemy_y - center_y, enemy_x - center_x)
            max_offset = np.max(np.abs(self._angle_delta(angles, sector_center)))
            if max_offset <= sector_span * 0.49:
                return {
                    "x": enemy_x,
                    "y": enemy_y,
                    "s_amplitude_km": amplitude_km,
                }

            amplitude_km *= 0.65

        return {
            "x": base_x,
            "y": base_y,
            "s_amplitude_km": 0.0,
        }

    # ==================== 单架敌机生成 ====================
    def generate_sector_enemy(
        self,
        formation_center: Dict[str, float],
        sector_index: int,
        sector_count: int,
        max_distance_km: float = DEFAULT_MAX_DISTANCE_KM,
        altitude_wave_amplitude: float = ALTITUDE_WAVE_AMPLITUDE_M,
    ):
        """在指定扇形区域内生成一架敌机的 S 形轨迹。"""
        sector_span = 2.0 * math.pi / max(int(sector_count), 1)
        sector_start = formation_center["rotation_rad"] + sector_index * sector_span
        sector_end = sector_start + sector_span
        sector_center = sector_start + sector_span / 2.0

        path = self._sample_sector_path(
            formation_center["x"],
            formation_center["y"],
            sector_center,
            sector_span,
            max_distance_km,
        )

        dx = path["end_x"] - path["start_x"]
        dy = path["end_y"] - path["start_y"]
        course_angle = math.atan2(dy, dx)
        perp_x = -math.sin(course_angle)
        perp_y = math.cos(course_angle)

        base_x = path["start_x"] + dx * self.t_norm
        base_y = path["start_y"] + dy * self.t_norm

        mean_radius_km = (path["start_radius_km"] + path["end_radius_km"]) / 2.0
        sector_half_width_km = max(mean_radius_km * math.tan(sector_span / 2.0), 1.0)
        requested_amplitude_km = min(
            float(self.rng.uniform(*S_AMPLITUDE_RANGE_KM)),
            sector_half_width_km * 0.55,
            path["course_length_km"] * 0.28,
        )
        requested_amplitude_km = max(requested_amplitude_km, 0.6)
        s_periods = float(self.rng.uniform(*S_PERIODS_RANGE))

        fitted = self._fit_s_curve_to_sector(
            formation_center["x"],
            formation_center["y"],
            sector_center,
            sector_span,
            base_x,
            base_y,
            perp_x,
            perp_y,
            requested_amplitude_km,
            s_periods,
        )

        fixed_altitude = formation_center["alt"] + float(self.rng.uniform(*ALTITUDE_OFFSET_RANGE_M))
        altitude_wave = altitude_wave_amplitude * np.sin(2.0 * np.pi * (1.0 / 0.35) * self.t_norm)
        altitude_noise = self._smoothed_noise(altitude_wave_amplitude * 0.3, 100)
        enemy_z = fixed_altitude + altitude_wave + altitude_noise * 0.5
        enemy_z = np.clip(
            enemy_z,
            fixed_altitude - altitude_wave_amplitude * 1.2,
            fixed_altitude + altitude_wave_amplitude * 1.2,
        )

        enemy_lon, enemy_lat = self._meters_to_geo(fitted["x"], fitted["y"])
        start_lon, start_lat = self._meters_to_geo(np.array([fitted["x"][0]]), np.array([fitted["y"][0]]))
        end_lon, end_lat = self._meters_to_geo(np.array([fitted["x"][-1]]), np.array([fitted["y"][-1]]))

        info = {
            "location": "扇形区域S形轨迹",
            "start_point": {"lon": float(start_lon[0]), "lat": float(start_lat[0])},
            "end_point": {"lon": float(end_lon[0]), "lat": float(end_lat[0])},
            "center_point": {
                "lon": float(formation_center["lon"]),
                "lat": float(formation_center["lat"]),
                "alt": float(formation_center["alt"]),
            },
            "course_angle_deg": float(math.degrees(course_angle)),
            "course_length_km": float(path["course_length_km"]),
            "altitude": float(fixed_altitude),
            "altitude_fluctuation": float(altitude_wave_amplitude),
            "s_amplitude_km": float(fitted["s_amplitude_km"]),
            "s_periods": float(s_periods),
            "max_distance_km": float(max_distance_km),
            "sector_index": int(sector_index + 1),
            "sector_count": int(sector_count),
            "sector_start_angle_deg": float((math.degrees(sector_start) + 360.0) % 360.0),
            "sector_end_angle_deg": float((math.degrees(sector_end) + 360.0) % 360.0),
            "sector_center_angle_deg": float((math.degrees(sector_center) + 360.0) % 360.0),
            "start_radius_km": float(path["start_radius_km"]),
            "end_radius_km": float(path["end_radius_km"]),
        }

        return enemy_lon, enemy_lat, enemy_z, info


# ==================== 对外调用接口 ====================
def create_single_enemy(
    main_lon,
    main_lat,
    main_z,
    t,
    start_coords,
    end_coords,
    flight_time=3600,
    random_seed=42,
):
    """按共享圆心与扇形规则生成单架敌机。"""
    enemies = create_enemies(
        main_lon=main_lon,
        main_lat=main_lat,
        main_z=main_z,
        t=t,
        start_coords=start_coords,
        end_coords=end_coords,
        count=1,
        flight_time=flight_time,
        random_seed=random_seed,
    )
    enemy = next(iter(enemies.values()))
    enemy["label"] = "敌机"
    return enemy


def create_enemies(
    main_lon,
    main_lat,
    main_z,
    t,
    start_coords,
    end_coords,
    count=1,
    flight_time=3600,
    random_seed=42,
):
    """以同一随机圆心为基准，按等分扇形批量生成敌机。"""
    count = int(count)
    if count < 1:
        return {}

    generator = EnemyGenerator(
        main_lon,
        main_lat,
        main_z,
        t,
        start_coords,
        end_coords,
        flight_time=flight_time,
        random_seed=random_seed,
    )

    formation_center = generator.pick_formation_center(max_distance_km=DEFAULT_MAX_DISTANCE_KM)

    enemies = {}
    for index in range(count):
        enemy_lon, enemy_lat, enemy_z, info = generator.generate_sector_enemy(
            formation_center=formation_center,
            sector_index=index,
            sector_count=count,
            max_distance_km=DEFAULT_MAX_DISTANCE_KM,
        )
        enemy_id = index + 1
        info["id"] = enemy_id
        enemy_name = f"enemy_{index + 1}"
        enemies[enemy_name] = {
            "id": enemy_id,
            "lon": enemy_lon,
            "lat": enemy_lat,
            "z": enemy_z,
            "active_info": info,
            "label": f"敌机{enemy_id}",
        }

    return enemies
