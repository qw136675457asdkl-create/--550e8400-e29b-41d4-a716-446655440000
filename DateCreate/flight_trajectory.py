"""
飞行轨迹生成模块
水平轨迹：直线、二次曲线、三次曲线、二折线、三折线
高度剖面：爬升-巡航（缓慢低频波动）-下降，贴近实际飞行
"""

import numpy as np

# ==================== 飞行轨迹生成器 ====================
class FlightTrajectory:
    """飞行轨迹生成器"""

    def __init__(self, start_coords, end_coords, flight_time=3600, random_seed=42):
        """
        初始化轨迹生成器

        参数:
        start_coords: [经度, 纬度, 高度] 起点坐标
        end_coords: [经度, 纬度, 高度] 终点坐标
        flight_time: 飞行时间（秒），默认3600秒（1小时）
        """
        self.start_coords = start_coords
        self.end_coords = end_coords
        self.flight_time = flight_time

        # 起点和终点参数
        self.lon_start, self.lat_start, self.alt_start = start_coords
        self.lon_end, self.lat_end, self.alt_end = end_coords

        # 计算球面距离（米）
        R = 6371000
        lat1, lon1 = np.radians(self.lat_start), np.radians(self.lon_start)
        lat2, lon2 = np.radians(self.lat_end), np.radians(self.lon_end)
        dlon = lon2 - lon1
        dlat = lat2 - lat1
        a = np.sin(dlat/2)**2 + np.cos(lat1) * np.cos(lat2) * np.sin(dlon/2)**2
        c = 2 * np.arctan2(np.sqrt(a), np.sqrt(1-a))
        self.ground_distance = R * c  # 地面距离（米）

        # 随机种子（使每次运行结果不同）
        self.rng = np.random.default_rng(random_seed)

    def _generate_time_points(self, num_points=721):
        """生成时间点"""
        return np.linspace(0, self.flight_time, num_points)

    def _calculate_distance_along_path(self, lon, lat):
        """计算沿路径的累积距离"""
        # 计算平面坐标
        mean_lat = np.mean(lat)
        lon_to_meter = 111320 * np.cos(np.radians(mean_lat))
        lat_to_meter = 110540

        x = (lon - self.lon_start) * lon_to_meter
        y = (lat - self.lat_start) * lat_to_meter

        # 计算累积距离
        dist = np.zeros_like(x)
        for i in range(1, len(x)):
            dist[i] = dist[i-1] + np.sqrt((x[i]-x[i-1])**2 + (y[i]-y[i-1])**2)

        return dist, x, y

    # ==================== 水平轨迹生成函数 ====================

    def horizontal_straight(self, t):
        """
        直线轨迹
        """
        t_norm = t / self.flight_time
        lon = self.lon_start + (self.lon_end - self.lon_start) * t_norm
        lat = self.lat_start + (self.lat_end - self.lat_start) * t_norm
        return lon, lat, None

    def horizontal_quadratic(self, t):
        """
        二次曲线轨迹（抛物线形转弯）
        在路径中点处有最大偏离，形成明显的弯曲
        """
        t_norm = t / self.flight_time

        # 直线路径
        lon_line = self.lon_start + (self.lon_end - self.lon_start) * t_norm
        lat_line = self.lat_start + (self.lat_end - self.lat_start) * t_norm

        # 计算路径方向
        dx = self.lon_end - self.lon_start
        dy = self.lat_end - self.lat_start
        length = np.sqrt(dx**2 + dy**2)

        if length > 0:
            # 垂直方向单位向量
            perp_x = -dy / length
            perp_y = dx / length

            # 随机选择偏向哪一侧和偏离幅度（明显弯曲）
            side = self.rng.choice([-1, 1])
            # 最大偏离距离：路径长度的10%-20%
            max_deviation_deg = self.rng.uniform(0.1, 0.25) * length

            # 二次曲线偏离因子：在中点处达到最大
            deviation_factor = 4 * t_norm * (1 - t_norm)

            # 计算偏离（度）
            deviation_lon = side * max_deviation_deg * perp_x * deviation_factor
            deviation_lat = side * max_deviation_deg * perp_y * deviation_factor

            lon = lon_line + deviation_lon
            lat = lat_line + deviation_lat
        else:
            lon = lon_line
            lat = lat_line

        # 拐点位置（最高点位置比例）
        inflection_ratio = 0.5

        return lon, lat, {'type': 'quadratic', 'ratio': inflection_ratio}

    def horizontal_cubic(self, t):
        """
        三次曲线轨迹（S形转弯）
        先向一侧弯曲，再向另一侧弯曲，形成S形
        """
        t_norm = t / self.flight_time

        # 直线路径
        lon_line = self.lon_start + (self.lon_end - self.lon_start) * t_norm
        lat_line = self.lat_start + (self.lat_end - self.lat_start) * t_norm

        # 计算路径方向
        dx = self.lon_end - self.lon_start
        dy = self.lat_end - self.lat_start
        length = np.sqrt(dx**2 + dy**2)

        if length > 0:
            perp_x = -dy / length
            perp_y = dx / length

            # 随机生成弯曲幅度（明显弯曲）
            max_deviation_deg = self.rng.uniform(0.12, 0.28) * length

            # 三次曲线偏离因子：S形，先正后负
            # 使用函数: 27/4 * t * (1-t) * (2t-1)
            deviation_factor = 27/4 * t_norm * (1 - t_norm) * (2*t_norm - 1)

            deviation_lon = max_deviation_deg * perp_x * deviation_factor
            deviation_lat = max_deviation_deg * perp_y * deviation_factor

            lon = lon_line + deviation_lon
            lat = lat_line + deviation_lat
        else:
            lon = lon_line
            lat = lat_line

        # 拐点位置（S形中心点）
        inflection_ratio = 0.5

        return lon, lat, {'type': 'cubic', 'ratio': inflection_ratio}

    def horizontal_two_segment(self, t):
        """
        二折线轨迹（一个折点）
        先直飞，然后转向直飞
        """
        t_norm = t / self.flight_time

        # 随机选择折点位置（时间比例）
        bend_ratio = self.rng.uniform(0.4, 0.6)

        # 直线路径的中间点
        lon_mid_line = self.lon_start + (self.lon_end - self.lon_start) * bend_ratio
        lat_mid_line = self.lat_start + (self.lat_end - self.lat_start) * bend_ratio

        # 计算路径方向
        dx = self.lon_end - self.lon_start
        dy = self.lat_end - self.lat_start
        length = np.sqrt(dx**2 + dy**2)

        if length > 0:
            perp_x = -dy / length
            perp_y = dx / length

            # 随机偏移幅度（明显偏移）
            offset_deg = self.rng.uniform(0.15, 0.35) * length
            side = self.rng.choice([-1, 1])

            lon_bend = lon_mid_line + side * offset_deg * perp_x
            lat_bend = lat_mid_line + side * offset_deg * perp_y
        else:
            lon_bend = lon_mid_line
            lat_bend = lat_mid_line

        # 生成折线
        lon = np.zeros_like(t)
        lat = np.zeros_like(t)

        for i, ratio in enumerate(t_norm):
            if ratio <= bend_ratio:
                segment_ratio = ratio / bend_ratio
                lon[i] = self.lon_start + (lon_bend - self.lon_start) * segment_ratio
                lat[i] = self.lat_start + (lat_bend - self.lat_start) * segment_ratio
            else:
                segment_ratio = (ratio - bend_ratio) / (1 - bend_ratio)
                lon[i] = lon_bend + (self.lon_end - lon_bend) * segment_ratio
                lat[i] = lat_bend + (self.lat_end - lat_bend) * segment_ratio

        return lon, lat, {'type': 'two_segment', 'ratio': bend_ratio}

    def horizontal_three_segment(self, t):
        """
        三折线轨迹（两个折点）
        先直飞，然后转向，再转向到终点
        """
        t_norm = t / self.flight_time

        # 随机选择两个折点位置（时间比例）
        bend1_ratio = self.rng.uniform(0.3, 0.45)
        bend2_ratio = self.rng.uniform(0.55, 0.7)

        # 直线路径上的两个点
        lon_mid1_line = self.lon_start + (self.lon_end - self.lon_start) * bend1_ratio
        lat_mid1_line = self.lat_start + (self.lat_end - self.lat_start) * bend1_ratio
        lon_mid2_line = self.lon_start + (self.lon_end - self.lon_start) * bend2_ratio
        lat_mid2_line = self.lat_start + (self.lat_end - self.lat_start) * bend2_ratio

        # 计算路径方向
        dx = self.lon_end - self.lon_start
        dy = self.lat_end - self.lat_start
        length = np.sqrt(dx**2 + dy**2)

        if length > 0:
            perp_x = -dy / length
            perp_y = dx / length

            # 两个折点向不同方向偏移（形成明显的弯曲路径）
            side1 = self.rng.choice([-1, 1])
            side2 = -side1  # 向相反方向偏移，形成S形

            offset1_deg = self.rng.uniform(0.1, 0.25) * length
            offset2_deg = self.rng.uniform(0.1, 0.25) * length

            lon_bend1 = lon_mid1_line + side1 * offset1_deg * perp_x
            lat_bend1 = lat_mid1_line + side1 * offset1_deg * perp_y
            lon_bend2 = lon_mid2_line + side2 * offset2_deg * perp_x
            lat_bend2 = lat_mid2_line + side2 * offset2_deg * perp_y
        else:
            lon_bend1 = lon_mid1_line
            lat_bend1 = lat_mid1_line
            lon_bend2 = lon_mid2_line
            lat_bend2 = lat_mid2_line

        # 生成折线
        lon = np.zeros_like(t)
        lat = np.zeros_like(t)

        for i, ratio in enumerate(t_norm):
            if ratio <= bend1_ratio:
                segment_ratio = ratio / bend1_ratio
                lon[i] = self.lon_start + (lon_bend1 - self.lon_start) * segment_ratio
                lat[i] = self.lat_start + (lat_bend1 - self.lat_start) * segment_ratio
            elif ratio <= bend2_ratio:
                segment_ratio = (ratio - bend1_ratio) / (bend2_ratio - bend1_ratio)
                lon[i] = lon_bend1 + (lon_bend2 - lon_bend1) * segment_ratio
                lat[i] = lat_bend1 + (lat_bend2 - lat_bend1) * segment_ratio
            else:
                segment_ratio = (ratio - bend2_ratio) / (1 - bend2_ratio)
                lon[i] = lon_bend2 + (self.lon_end - lon_bend2) * segment_ratio
                lat[i] = lat_bend2 + (self.lat_end - lat_bend2) * segment_ratio

        return lon, lat, {'type': 'three_segment', 'ratio': (bend1_ratio, bend2_ratio)}

    # ==================== 高度剖面生成函数（贴近实际飞行）====================

    @staticmethod
    def _smooth_step(values):
        """生成平滑的 0 到 1 插值曲线。"""
        values = np.clip(values, 0.0, 1.0)
        return 3 * values ** 2 - 2 * values ** 3

    def _build_vertical_profile_plan(self):
        """根据起终点高度组合选择垂直飞行阶段。"""
        start_alt = float(self.alt_start)
        end_alt = float(self.alt_end)
        altitude_delta = end_alt - start_alt

        takeoff_landing_threshold = 500.0
        level_tolerance = 150.0

        if start_alt <= takeoff_landing_threshold and end_alt <= takeoff_landing_threshold:
            climb_ratio = self.rng.uniform(0.20, 0.30)
            descent_ratio = self.rng.uniform(0.20, 0.30)
            cruise_ratio = 1.0 - climb_ratio - descent_ratio
            cruise_height = self.rng.uniform(max(max(start_alt, end_alt) + 500.0, 6000.0), 9000.0)
            phases = ['climb', 'cruise', 'descent']
            profile_type = 'climb_cruise_descent'
        elif abs(altitude_delta) <= level_tolerance:
            climb_ratio = 0.0
            descent_ratio = 0.0
            cruise_ratio = 1.0
            cruise_height = 0.5 * (start_alt + end_alt)
            phases = ['cruise']
            profile_type = 'cruise_only'
        elif altitude_delta > 0:
            climb_ratio = self.rng.uniform(0.25, 0.45)
            descent_ratio = 0.0
            cruise_ratio = 1.0 - climb_ratio
            cruise_height = end_alt
            phases = ['climb', 'cruise']
            profile_type = 'climb_cruise'
        else:
            climb_ratio = 0.0
            descent_ratio = self.rng.uniform(0.25, 0.45)
            cruise_ratio = 1.0 - descent_ratio
            cruise_height = start_alt
            phases = ['cruise', 'descent']
            profile_type = 'cruise_descent'

        return {
            'profile_type': profile_type,
            'phases': phases,
            'cruise_height': float(cruise_height),
            'climb_ratio': float(climb_ratio),
            'cruise_ratio': float(cruise_ratio),
            'descent_ratio': float(descent_ratio),
        }

    def _generate_cruise_segment(self, t_segment, start_height, end_height):
        """生成带缓慢高度波动的巡航段。"""
        if len(t_segment) == 0:
            return np.array([]), 0.0, 0.0

        if len(t_segment) == 1:
            return np.array([float(start_height)]), 0.0, 0.0

        duration = max(float(t_segment[-1] - t_segment[0]), 1e-6)
        segment_ratio = np.linspace(0.0, 1.0, len(t_segment))
        base_profile = start_height + (end_height - start_height) * self._smooth_step(segment_ratio)

        wave_amplitude = self.rng.uniform(15, 30)
        period = self.rng.uniform(duration * 0.8, duration * 1.5)
        phase = self.rng.uniform(0, 2 * np.pi)
        envelope = np.sin(np.pi * segment_ratio)
        slow_wave = wave_amplitude * np.sin(2 * np.pi * (t_segment - t_segment[0]) / period + phase) * envelope

        if self.rng.random() > 0.5:
            period2 = period * 2
            wave_amplitude2 = wave_amplitude * 0.3
            slow_wave += (
                wave_amplitude2
                * np.sin(2 * np.pi * (t_segment - t_segment[0]) / period2 + phase + 1)
                * envelope
            )

        return base_profile + slow_wave, wave_amplitude, period

    def height_profile_realistic(self, t):
        """
        根据起终点高度构建垂直剖面，而不是强行为每条主机轨迹
        都加入起飞和降落过程。
        """
        plan = self._build_vertical_profile_plan()
        climb_ratio = plan['climb_ratio']
        cruise_ratio = plan['cruise_ratio']
        descent_ratio = plan['descent_ratio']
        cruise_height = plan['cruise_height']

        climb_end_time = climb_ratio * self.flight_time
        descent_start_time = (1.0 - descent_ratio) * self.flight_time

        z = np.zeros_like(t, dtype=float)

        if climb_ratio > 0:
            mask_climb = t <= climb_end_time
            climb_segment_ratio = t[mask_climb] / max(climb_end_time, 1e-6)
            z[mask_climb] = self.alt_start + (cruise_height - self.alt_start) * self._smooth_step(climb_segment_ratio)
        else:
            mask_climb = np.zeros_like(t, dtype=bool)

        cruise_start_height = cruise_height if climb_ratio > 0 else self.alt_start
        cruise_end_height = cruise_height if descent_ratio > 0 else self.alt_end

        if descent_ratio > 0:
            mask_cruise = (t > climb_end_time) & (t < descent_start_time) if climb_ratio > 0 else (t < descent_start_time)
        else:
            mask_cruise = t > climb_end_time if climb_ratio > 0 else np.ones_like(t, dtype=bool)

        t_cruise = t[mask_cruise]
        cruise_heights, wave_amplitude, period = self._generate_cruise_segment(
            t_cruise,
            cruise_start_height,
            cruise_end_height,
        )
        if len(cruise_heights) > 0:
            z[mask_cruise] = cruise_heights

        if descent_ratio > 0:
            mask_descent = t >= descent_start_time
            descent_segment_ratio = (t[mask_descent] - descent_start_time) / max(self.flight_time - descent_start_time, 1e-6)
            z[mask_descent] = cruise_height + (self.alt_end - cruise_height) * self._smooth_step(descent_segment_ratio)
        else:
            mask_descent = np.zeros_like(t, dtype=bool)

        if len(t) > 0:
            z[0] = self.alt_start
            z[-1] = self.alt_end

        z = np.maximum(z, 0)

        params = {
            'cruise_height': cruise_height,
            'climb_ratio': climb_ratio,
            'cruise_ratio': cruise_ratio,
            'descent_ratio': descent_ratio,
            'wave_amplitude': wave_amplitude,
            'wave_period_seconds': period,
            'cruise_std': np.std(cruise_heights) if len(cruise_heights) > 0 else 0.0,
            'vertical_profile_type': plan['profile_type'],
            'vertical_phases': plan['phases'],
            'start_altitude': float(self.alt_start),
            'end_altitude': float(self.alt_end),
        }

        return z, params

    # ==================== 主生成函数 ====================

    def get_trajectory(self, horizontal_type='straight', num_points=721):
        """
        获取完整的三维飞行轨迹

        参数:
        horizontal_type: 水平面轨迹类型 ('straight', 'quadratic', 'cubic', 'two_segment', 'three_segment')
        num_points: 时间点数量

        返回:
        t: 时间数组
        lon, lat: 经纬度数组
        x, y: 平面坐标（米）
        z: 高度数组
        s: 累积距离数组
        params: 轨迹参数
        """
        # 生成时间点
        t = self._generate_time_points(num_points)

        # 生成水平轨迹
        if horizontal_type == 'straight':
            lon, lat, inflection_info = self.horizontal_straight(t)
        elif horizontal_type == 'quadratic':
            lon, lat, inflection_info = self.horizontal_quadratic(t)
        elif horizontal_type == 'cubic':
            lon, lat, inflection_info = self.horizontal_cubic(t)
        elif horizontal_type == 'two_segment':
            lon, lat, inflection_info = self.horizontal_two_segment(t)
        elif horizontal_type == 'three_segment':
            lon, lat, inflection_info = self.horizontal_three_segment(t)
        else:
            raise ValueError(f"不支持的水平轨迹类型: {horizontal_type}")

        # 计算平面坐标和距离
        dist, x, y = self._calculate_distance_along_path(lon, lat)

        # 生成高度剖面（真实剖面：爬升-缓慢巡航波动-下降）
        z, height_params = self.height_profile_realistic(t)

        # 计算实际三维弧长
        s = np.zeros_like(x)
        for i in range(1, len(x)):
            s[i] = s[i-1] + np.sqrt((x[i]-x[i-1])**2 + (y[i]-y[i-1])**2 + (z[i]-z[i-1])**2)

        # 合并参数
        params = {
            'horizontal_type': horizontal_type,
            'inflection_info': inflection_info,
            **height_params,
            'ground_distance_km': self.ground_distance / 1000
        }

        return t, lon, lat, x, y, z, s, params


# ==================== 对外调用接口 ====================
def get_trajectory(horizontal_type='straight', start_coords=None, end_coords=None, flight_time=3600, random_seed=42):
    """
    便捷函数：获取飞行轨迹
    """
    if start_coords is None:
        start_coords = [120.434, 30.229, 10]  # 杭州
    if end_coords is None:
        end_coords = [121.336, 31.198, 5]     # 上海

    generator = FlightTrajectory(start_coords, end_coords, flight_time, random_seed=random_seed)
    return generator.get_trajectory(horizontal_type)
