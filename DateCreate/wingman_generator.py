"""
友机飞行轨迹生成
"""

import numpy as np


# ==================== 友机轨迹生成器 ====================
class WingmanGenerator:
    """基于主机轨迹生成友机轨迹。"""

    def __init__(self, main_lon, main_lat, main_z, t, flight_time=3600, random_seed=24):
        self.main_lon = np.asarray(main_lon)
        self.main_lat = np.asarray(main_lat)
        self.main_z = np.asarray(main_z)
        self.t = np.asarray(t)
        self.flight_time = flight_time
        self.t_norm = self.t / flight_time if flight_time else np.zeros_like(self.t)
        self.rng = np.random.default_rng(random_seed)

        self.reference_lon = float(self.main_lon[0])
        self.reference_lat = float(self.main_lat[0])
        mean_lat = float(np.mean(self.main_lat))
        self.lon_to_meter = 111320 * np.cos(np.radians(mean_lat))
        self.lat_to_meter = 110540

        self.main_x = (self.main_lon - self.reference_lon) * self.lon_to_meter
        self.main_y = (self.main_lat - self.reference_lat) * self.lat_to_meter

    # ==================== 坐标与噪声辅助 ====================
    def _meters_to_geo(self, x, y):
        lon = x / self.lon_to_meter + self.reference_lon
        lat = y / self.lat_to_meter + self.reference_lat
        return lon, lat

    def _smoothed_noise(self, scale, window_size):
        noise = self.rng.normal(0, scale, len(self.t))
        kernel = np.ones(max(window_size, 1)) / max(window_size, 1)
        return np.convolve(noise, kernel, mode='same')

    def generate_wingman(
        self,
        start_lateral_offset_km=-4.0,
        end_lateral_offset_km=5.0,
        s_amplitude_km=6.0,
        altitude_offset_m=260.0,
        altitude_wave_amplitude=35.0,
        s_periods=2.0,
    ):
        """按与敌机相似的 S 形规则生成单架友机。"""
        start_x = float(self.main_x[0])
        start_y = float(self.main_y[0])
        end_x = float(self.main_x[-1])
        end_y = float(self.main_y[-1])

        course_dx = end_x - start_x
        course_dy = end_y - start_y
        course_length = np.hypot(course_dx, course_dy)
        if course_length < 1e-6:
            raise ValueError("Main trajectory is too short to derive a wingman path.")

        unit_x = course_dx / course_length
        unit_y = course_dy / course_length
        perp_x = -unit_y
        perp_y = unit_x

        wingman_start_x = start_x + start_lateral_offset_km * 1000 * perp_x + 1200 * unit_x
        wingman_start_y = start_y + start_lateral_offset_km * 1000 * perp_y + 1200 * unit_y
        wingman_end_x = end_x + end_lateral_offset_km * 1000 * perp_x - 1500 * unit_x
        wingman_end_y = end_y + end_lateral_offset_km * 1000 * perp_y - 1500 * unit_y

        base_x = wingman_start_x + (wingman_end_x - wingman_start_x) * self.t_norm
        base_y = wingman_start_y + (wingman_end_y - wingman_start_y) * self.t_norm

        s_factor = np.sin(2 * np.pi * s_periods * self.t_norm)
        s_factor = s_factor + 0.3 * np.sin(4 * np.pi * s_periods * self.t_norm)

        wingman_x = base_x + s_amplitude_km * 1000 * s_factor * perp_x
        wingman_y = base_y + s_amplitude_km * 1000 * s_factor * perp_y

        fixed_altitude = float(np.mean(self.main_z) + altitude_offset_m)
        altitude_wave = altitude_wave_amplitude * np.sin(2 * np.pi * (1 / 0.35) * self.t_norm)
        altitude_noise = self._smoothed_noise(altitude_wave_amplitude * 0.3, 81)
        wingman_z = fixed_altitude + altitude_wave + altitude_noise * 0.5
        wingman_z = np.clip(
            wingman_z,
            fixed_altitude - altitude_wave_amplitude * 1.2,
            fixed_altitude + altitude_wave_amplitude * 1.2,
        )

        wingman_lon, wingman_lat = self._meters_to_geo(wingman_x, wingman_y)
        start_lon, start_lat = self._meters_to_geo(np.array([wingman_start_x]), np.array([wingman_start_y]))
        end_lon, end_lat = self._meters_to_geo(np.array([wingman_end_x]), np.array([wingman_end_y]))

        info = {
            'formation_type': 'enemy_like',
            'start_point': {'lon': float(start_lon[0]), 'lat': float(start_lat[0])},
            'end_point': {'lon': float(end_lon[0]), 'lat': float(end_lat[0])},
            'course_length_km': np.hypot(wingman_end_x - wingman_start_x, wingman_end_y - wingman_start_y) / 1000,
            'altitude': fixed_altitude,
            'altitude_fluctuation': altitude_wave_amplitude,
            's_amplitude_km': s_amplitude_km,
            's_periods': s_periods,
        }

        return {
            'lon': wingman_lon,
            'lat': wingman_lat,
            'z': wingman_z,
            'active_info': info,
            'label': '僚机',
        }


# ==================== 对外调用接口 ====================
def create_wingman(main_lon, main_lat, main_z, t, flight_time=3600, random_seed=24):
    """生成单架友机的便捷入口。"""
    generator = WingmanGenerator(
        main_lon,
        main_lat,
        main_z,
        t,
        flight_time=flight_time,
        random_seed=random_seed,
    )
    wingman = generator.generate_wingman()
    wingman["id"] = 1
    wingman["label"] = "友机"
    wingman.setdefault("active_info", {})["id"] = 1
    return {'wingman': wingman}


def create_wingmen(main_lon, main_lat, main_z, t, count=1, flight_time=3600, random_seed=24):
    """基于主机轨迹批量生成多架友机。"""
    count = int(count)
    if count < 1:
        return {}

    wingmen = {}
    for index in range(count):
        generator = WingmanGenerator(
            main_lon,
            main_lat,
            main_z,
            t,
            flight_time=flight_time,
            random_seed=int(random_seed) + index,
        )

        direction = -1 if index % 2 == 0 else 1
        base_offset = 3.0 + index * 1.5
        wingman = generator.generate_wingman(
            start_lateral_offset_km=direction * base_offset,
            end_lateral_offset_km=-direction * (base_offset + 1.0),
            s_amplitude_km=4.0 + (index % 3),
            altitude_offset_m=220.0 + direction * (120.0 + index * 35.0),
            altitude_wave_amplitude=35.0 + (index % 2) * 5.0,
            s_periods=1.6 + (index % 3) * 0.2,
        )
        wingman_id = index + 1
        wingman["id"] = wingman_id
        wingman["label"] = f"友机{wingman_id}"
        wingman.setdefault("active_info", {})["id"] = wingman_id
        wingmen[f"friendly_{wingman_id}"] = wingman

    return wingmen
