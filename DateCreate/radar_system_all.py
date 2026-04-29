"""
雷达系统
"""

import os
from math import radians, sin, cos, sqrt, atan2, degrees

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.patches import Circle


# ==================== 单站雷达模型 ====================
class RadarStation:
    """单个雷达站模型。"""

    def __init__(self, name, lon, lat, range_km=150):
        self.name = name
        self.lon = lon
        self.lat = lat
        self.range_km = range_km

    # ==================== 坐标与探测计算 ====================
    def calculate_polar_coordinates(self, target_lon, target_lat, target_z):
        """计算目标相对当前雷达站的距离、方位角和俯仰角。"""
        lat1, lon1 = radians(self.lat), radians(self.lon)
        lat2, lon2 = radians(target_lat), radians(target_lon)

        dlon = lon2 - lon1
        dlat = lat2 - lat1

        a = sin(dlat / 2) ** 2 + cos(lat1) * cos(lat2) * sin(dlon / 2) ** 2
        c = 2 * atan2(sqrt(a), sqrt(1 - a))
        horizontal_dist = 6371 * c

        alt_diff = target_z / 1000
        distance_km = sqrt(horizontal_dist ** 2 + alt_diff ** 2)

        x = sin(dlon) * cos(lat2)
        y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dlon)
        azimuth_deg = degrees(atan2(x, y))
        if azimuth_deg < 0:
            azimuth_deg += 360

        elevation_deg = degrees(atan2(alt_diff, horizontal_dist))
        return distance_km, azimuth_deg, elevation_deg

    def track_target(self, target_lon, target_lat, target_z, t, target_name):
        """对单个目标在完整时间序列上进行跟踪。"""
        distances = []
        azimuths = []
        elevations = []
        times = []

        for i in range(len(t)):
            if np.isnan(target_lon[i]) or np.isnan(target_lat[i]):
                distances.append(np.nan)
                azimuths.append(np.nan)
                elevations.append(np.nan)
                times.append(t[i] / 60)
                continue

            distance, azimuth, elevation = self.calculate_polar_coordinates(
                target_lon[i], target_lat[i], target_z[i]
            )
            distances.append(distance)
            azimuths.append(azimuth)
            elevations.append(elevation)
            times.append(t[i] / 60)

        distances = np.array(distances)
        return {
            'target_name': target_name,
            'times': np.array(times),
            'distances': distances,
            'azimuths': np.array(azimuths),
            'elevations': np.array(elevations),
            'is_detected': distances <= self.range_km,
        }


# ==================== 三站雷达系统 ====================
class ThreeRadarSystem:
    """由起点、中点、终点三座雷达站组成的雷达网络。"""

    def __init__(self, start_coords, end_coords, range_km=150):
        self.start_coords = start_coords
        self.end_coords = end_coords
        self.range_km = range_km

        self.mid_lon = (start_coords[0] + end_coords[0]) / 2
        self.mid_lat = (start_coords[1] + end_coords[1]) / 2

        self.stations = [
            RadarStation("Start Radar", start_coords[0], start_coords[1], range_km),
            RadarStation("Mid Radar", self.mid_lon, self.mid_lat, range_km),
            RadarStation("End Radar", end_coords[0], end_coords[1], range_km),
        ]

    # ==================== 目标样式与扫描 ====================
    def _target_style(self, target_name, target):
        default_label = target.get('label', target_name)
        return {
            'host': {
                'color': 'blue',
                'marker': 'o',
                'linestyle': '-',
                'label': target.get('label', 'Host'),
                'linewidth': 2.5,
            },
            'enemy': {
                'color': 'red',
                'marker': 's',
                'linestyle': '-',
                'label': target.get('label', 'Enemy'),
                'linewidth': 2.0,
            },
            'wingman': {
                'color': 'magenta',
                'marker': 'D',
                'linestyle': '-.',
                'label': target.get('label', 'Wingman'),
                'linewidth': 2.0,
            },
        }.get(
            target_name,
            {
                'color': 'gray',
                'marker': 'o',
                'linestyle': '-',
                'label': default_label,
                'linewidth': 1.8,
            }
        )

    def scan_all_targets(self, targets, t):
        """扫描目标字典中的全部目标。"""
        all_tracks = {}

        for station in self.stations:
            station_tracks = {}
            for target_name, target in targets.items():
                if not all(axis in target for axis in ('lon', 'lat', 'z')):
                    continue

                station_tracks[target_name] = station.track_target(
                    target['lon'],
                    target['lat'],
                    target['z'],
                    t,
                    target.get('label', target_name),
                )

            all_tracks[station.name] = {
                'station': station,
                'tracks': station_tracks,
            }

        return all_tracks

    # ==================== 雷达绘图 ====================
    def plot_radar_map(self, targets, save_path=None):
        """绘制雷达覆盖范围和所有目标轨迹。"""
        fig, ax = plt.subplots(figsize=(14, 12))

        for station in self.stations:
            ax.scatter(
                station.lon,
                station.lat,
                c='green',
                s=200,
                marker='s',
                edgecolors='black',
                linewidth=2,
                zorder=5,
                label=station.name,
            )

            radius_deg = station.range_km / 111.32
            circle = Circle(
                (station.lon, station.lat),
                radius_deg,
                fill=False,
                color='green',
                linestyle='--',
                alpha=0.5,
                linewidth=1.5,
            )
            ax.add_patch(circle)
            ax.text(
                station.lon,
                station.lat + 0.05,
                station.name,
                fontsize=10,
                ha='center',
                va='bottom',
                fontweight='bold',
            )

        for target_name, target in targets.items():
            if not all(axis in target for axis in ('lon', 'lat', 'z')):
                continue

            style = self._target_style(target_name, target)
            ax.plot(
                target['lon'],
                target['lat'],
                color=style['color'],
                linewidth=style['linewidth'],
                linestyle=style['linestyle'],
                label=f"{style['label']} Track",
            )
            ax.scatter(
                target['lon'][0],
                target['lat'][0],
                color=style['color'],
                s=100,
                marker=style['marker'],
                edgecolors='black',
                zorder=5,
            )
            ax.scatter(
                target['lon'][-1],
                target['lat'][-1],
                color=style['color'],
                s=100,
                marker=style['marker'],
                edgecolors='black',
                zorder=5,
            )

        ax.scatter(
            self.start_coords[0],
            self.start_coords[1],
            color='red',
            s=150,
            marker='*',
            label='Start',
            edgecolors='black',
            zorder=5,
        )
        ax.scatter(
            self.end_coords[0],
            self.end_coords[1],
            color='blue',
            s=150,
            marker='*',
            label='End',
            edgecolors='black',
            zorder=5,
        )
        ax.scatter(
            self.mid_lon,
            self.mid_lat,
            color='purple',
            s=150,
            marker='*',
            label='Mid',
            edgecolors='black',
            zorder=5,
        )

        ax.set_xlabel('Longitude (deg)', fontsize=12)
        ax.set_ylabel('Latitude (deg)', fontsize=12)
        ax.set_title('Three-Station Radar Coverage Map', fontsize=14)
        ax.grid(True, alpha=0.3)
        ax.legend(fontsize=10)
        ax.axis('equal')

        all_lons = [s.lon for s in self.stations]
        all_lats = [s.lat for s in self.stations]
        for target in targets.values():
            if not all(axis in target for axis in ('lon', 'lat', 'z')):
                continue
            all_lons.extend(target['lon'])
            all_lats.extend(target['lat'])

        if all_lons and all_lats:
            margin_lon = (max(all_lons) - min(all_lons)) * 0.1
            margin_lat = (max(all_lats) - min(all_lats)) * 0.1
            ax.set_xlim(min(all_lons) - margin_lon, max(all_lons) + margin_lon)
            ax.set_ylim(min(all_lats) - margin_lat, max(all_lats) + margin_lat)

        plt.tight_layout()

        if save_path:
            plt.savefig(save_path, dpi=150, bbox_inches='tight')
            print(f"Radar coverage map saved: {save_path}")

        plt.show()
        return fig

    def plot_combined_radar_track(self, station_index, targets, t, save_path=None):
        """在单个雷达站的极坐标图上绘制所有目标。"""
        station = self.stations[station_index]
        fig, ax = plt.subplots(figsize=(12, 10), subplot_kw={'projection': 'polar'})

        ax.set_theta_zero_location('N')
        ax.set_theta_direction(-1)
        ax.set_rlim(0, station.range_km)
        ax.set_rticks(np.arange(0, station.range_km + 20, 20))
        ax.set_rlabel_position(45)
        ax.grid(True, alpha=0.3)

        ax.text(0, station.range_km * 0.95, 'N', ha='center', va='center', fontsize=10, fontweight='bold')
        ax.text(np.pi / 2, station.range_km * 0.95, 'E', ha='center', va='center', fontsize=10, fontweight='bold')
        ax.text(np.pi, station.range_km * 0.95, 'S', ha='center', va='center', fontsize=10, fontweight='bold')
        ax.text(3 * np.pi / 2, station.range_km * 0.95, 'W', ha='center', va='center', fontsize=10, fontweight='bold')

        ax.scatter(0, 0, c='green', s=250, marker='s', edgecolors='white', linewidth=2, zorder=10, label=station.name)

        for r in [20, 40, 60, 80, 100, 120, 140]:
            if r <= station.range_km:
                ax.text(0, r, f'{r}km', ha='center', va='bottom', fontsize=8, alpha=0.7)

        theta_range = np.linspace(0, 2 * np.pi, 100)
        ax.plot(
            theta_range,
            np.full_like(theta_range, station.range_km),
            'r--',
            linewidth=1.5,
            alpha=0.5,
            label=f'Range {station.range_km}km',
        )

        for target_name, target in targets.items():
            if not all(axis in target for axis in ('lon', 'lat', 'z')):
                continue

            track = station.track_target(
                target['lon'],
                target['lat'],
                target['z'],
                t,
                target.get('label', target_name),
            )
            style = self._target_style(target_name, target)

            valid_mask = ~np.isnan(track['azimuths'])
            if not np.any(valid_mask):
                continue

            azimuths_rad = np.radians(track['azimuths'][valid_mask])
            distances = track['distances'][valid_mask]
            detected_mask = track['is_detected'][valid_mask]

            ax.plot(
                azimuths_rad,
                distances,
                color=style['color'],
                linewidth=style['linewidth'],
                linestyle=style['linestyle'],
                alpha=0.8,
                label=style['label'],
            )

            point_colors = [style['color'] if detected else 'lightgray' for detected in detected_mask]
            ax.scatter(azimuths_rad, distances, c=point_colors, s=15, alpha=0.5)

            ax.scatter(
                azimuths_rad[0],
                distances[0],
                c=style['color'],
                s=120,
                marker=style['marker'],
                edgecolors='black',
                linewidth=1.5,
                zorder=5,
                label=f"{style['label']} Start",
            )
            ax.scatter(
                azimuths_rad[-1],
                distances[-1],
                c=style['color'],
                s=120,
                marker=style['marker'],
                edgecolors='black',
                linewidth=1.5,
                zorder=5,
                label=f"{style['label']} End",
            )

        ax.legend(loc='upper right', bbox_to_anchor=(1.35, 1.0), fontsize=10)

        info_text = (
            f"{station.name}\n"
            f"Position: ({station.lon:.4f}, {station.lat:.4f})\n"
            f"Range: {station.range_km} km"
        )
        props = dict(boxstyle='round', facecolor='lightblue', alpha=0.8)
        ax.text(1.05, 0.5, info_text, transform=ax.transAxes, fontsize=10, verticalalignment='center', bbox=props)

        ax.set_title(f'{station.name} - Combined Radar Tracks', fontsize=14, pad=20)
        plt.tight_layout()

        if save_path:
            plt.savefig(save_path, dpi=150, bbox_inches='tight')
            print(f"Combined radar track saved: {save_path}")

        plt.show()
        return fig

    def plot_all_combined_radar_tracks(self, targets, t, save_dir=None):
        """为所有雷达站绘制融合后的雷达航迹图。"""
        for station_index, station in enumerate(self.stations):
            save_path = None
            if save_dir:
                if not os.path.exists(save_dir):
                    os.makedirs(save_dir)
                safe_name = station.name.lower().replace(' ', '_')
                save_path = os.path.join(save_dir, f'combined_radar_track_{safe_name}.png')

            self.plot_combined_radar_track(station_index, targets, t, save_path)

    # ==================== 探测报告输出 ====================
    def print_detection_report(self, targets, t):
        """输出所有目标的雷达探测结果报告。"""
        all_tracks = self.scan_all_targets(targets, t)

        print("\n" + "=" * 80)
        print("Three-Station Radar Detection Report")
        print("=" * 80)

        for station_name, station_data in all_tracks.items():
            station = station_data['station']
            tracks = station_data['tracks']

            print(f"\n[{station_name}]")
            print(f"  Position: ({station.lon:.4f}, {station.lat:.4f})")
            print(f"  Range: {station.range_km} km")
            print("-" * 50)

            for _, track in tracks.items():
                valid_distances = track['distances'][~np.isnan(track['distances'])]
                target_label = track['target_name']

                if len(valid_distances) == 0:
                    print(f"\n  {target_label}: no valid data")
                    continue

                min_dist = np.min(valid_distances)
                max_dist = np.max(valid_distances)
                avg_dist = np.mean(valid_distances)
                detected_ratio = np.sum(track['is_detected']) / len(track['is_detected']) * 100

                print(f"\n  {target_label}:")
                print(f"    Min distance: {min_dist:.2f} km")
                print(f"    Max distance: {max_dist:.2f} km")
                print(f"    Avg distance: {avg_dist:.2f} km")
                print(f"    Detectable ratio: {detected_ratio:.1f}%")

                if detected_ratio > 80:
                    status = "Fully detectable"
                elif detected_ratio > 50:
                    status = "Mostly detectable"
                elif detected_ratio > 0:
                    status = "Partially detectable"
                else:
                    status = "Not detectable"
                print(f"    Status: {status}")

        print("=" * 80)


# ==================== 对外调用接口 ====================
def create_three_radar_system(start_coords, end_coords, range_km=150):
    """创建三站雷达系统的便捷入口。"""
    return ThreeRadarSystem(start_coords, end_coords, range_km)
