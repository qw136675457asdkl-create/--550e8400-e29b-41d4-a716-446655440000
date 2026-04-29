"""
本地直接调用仿真模块的示例脚本。
不走 HTTP 接口，直接在 Python 中调用 simulation_service。
"""

from __future__ import annotations

from typing import Any, Dict, Tuple

import matplotlib.pyplot as plt
import numpy as np

from enemy_generator import create_enemies
from flight_trajectory import FlightTrajectory
from wingman_generator import create_wingmen


# ==================== 参数配置区 ====================
MAIN_LABEL = "主机"

# 杭州萧山国际机场附近坐标
START_COORDS = [120.4333, 30.2361, 10.0]
# 上海虹桥国际机场附近坐标
END_COORDS = [121.3347, 31.1979, 5.0]

MOTION_MODEL = "straight"
FLIGHT_TIME_SECONDS = 5 * 60
POINT_COUNT = 721

ENEMY_NUM = 12
FRIENDLY_NUM = 2

MAIN_RANDOM_SEED = 42
ENEMY_RANDOM_SEED = 52
FRIENDLY_RANDOM_SEED = 24

SHOW_PLOT = True


# def build_payload() -> Dict[str, Any]:
#     """组装本地调用使用的请求体。"""
#     return {
#         "request_id": REQUEST_ID,
#         "basic": BASIC_CONFIG,
#         "datasets": {
#             "aircraft_inertial": AIRCRAFT_INERTIAL_CONFIG,
#             "attitude": ATTITUDE_CONFIG,
#             "radar_track": RADAR_TRACK_CONFIG,
#             "ads_b": ADS_B_CONFIG,
#         },
#     }
#
#
# def progress_callback(percent: int, stage: str, message: str) -> None:
#     """打印处理进度。"""
#     print(f"[{percent:3d}%] {stage}: {message}")
#
#
# def main() -> None:
#     payload = build_payload()
#     config = parse_request(payload)
#     result = run_simulation(config, progress_callback=progress_callback)
#
#     print("\n生成完成")
#     print(f"request_id: {result['request_id']}")
#     print(f"output_dir: {result['generated_files_directory']}")
#
#     print("\n生成文件:")
#     for name, path in result.get("files", {}).items():
#         print(f"  {name}: {path}")
#
#     print("\n结果摘要:")
#     print(json.dumps(result.get("summary", {}), ensure_ascii=False, indent=2))


def configure_matplotlib() -> None:
    """配置 Matplotlib，尽量避免中文显示乱码。"""
    plt.rcParams["font.sans-serif"] = [
        "Microsoft YaHei",
        "SimHei",
        "Noto Sans CJK SC",
        "Arial Unicode MS",
        "DejaVu Sans",
    ]
    plt.rcParams["axes.unicode_minus"] = False


def build_trajectories() -> Dict[str, Any]:
    """生成主机、敌机、友机轨迹数据。"""
    generator = FlightTrajectory(
        start_coords=START_COORDS,
        end_coords=END_COORDS,
        flight_time=FLIGHT_TIME_SECONDS,
        random_seed=MAIN_RANDOM_SEED,
    )
    t, lon, lat, x, y, z, s, params = generator.get_trajectory(
        horizontal_type=MOTION_MODEL,
        num_points=POINT_COUNT,
    )

    enemies = create_enemies(
        main_lon=lon,
        main_lat=lat,
        main_z=z,
        t=t,
        start_coords=START_COORDS,
        end_coords=END_COORDS,
        count=ENEMY_NUM,
        flight_time=FLIGHT_TIME_SECONDS,
        random_seed=ENEMY_RANDOM_SEED,
    )

    friendlies = create_wingmen(
        main_lon=lon,
        main_lat=lat,
        main_z=z,
        t=t,
        count=FRIENDLY_NUM,
        flight_time=FLIGHT_TIME_SECONDS,
        random_seed=FRIENDLY_RANDOM_SEED,
    )

    return {
        "main": {
            "t": t,
            "lon": lon,
            "lat": lat,
            "z": z,
            "x": x,
            "y": y,
            "s": s,
            "params": params,
            "label": MAIN_LABEL,
        },
        "enemies": enemies,
        "friendlies": friendlies,
    }


def plot_trajectories(
    trajectory_bundle: Dict[str, Any],
    show_plot: bool = True,
) -> Tuple[plt.Figure, plt.Axes]:
    """绘制主机、敌机、友机三维轨迹图。"""
    configure_matplotlib()

    figure = plt.figure(figsize=(14, 9))
    axis = figure.add_subplot(111, projection="3d")

    main_track = trajectory_bundle["main"]
    axis.plot(
        main_track["lon"],
        main_track["lat"],
        main_track["z"],
        color="#1f77b4",
        linewidth=2.8,
        label=main_track["label"],
    )

    enemy_colors = plt.cm.Reds(np.linspace(0.45, 0.9, max(ENEMY_NUM, 1)))
    for index, enemy in enumerate(trajectory_bundle["enemies"].values()):
        axis.plot(
            enemy["lon"],
            enemy["lat"],
            enemy["z"],
            color=enemy_colors[index],
            linewidth=1.4,
            alpha=0.9,
            label=enemy["label"],
        )

    friendly_colors = plt.cm.Greens(np.linspace(0.45, 0.85, max(FRIENDLY_NUM, 1)))
    for index, friendly in enumerate(trajectory_bundle["friendlies"].values()):
        axis.plot(
            friendly["lon"],
            friendly["lat"],
            friendly["z"],
            color=friendly_colors[index],
            linewidth=1.8,
            alpha=0.95,
            label=friendly["label"],
        )

    main_lon = np.asarray(main_track["lon"], dtype=float)
    main_lat = np.asarray(main_track["lat"], dtype=float)
    main_alt = np.asarray(main_track["z"], dtype=float)
    axis.scatter(main_lon[0], main_lat[0], main_alt[0], color="#0d47a1", s=55, marker="o")
    axis.scatter(main_lon[-1], main_lat[-1], main_alt[-1], color="#b71c1c", s=65, marker="^")

    axis.set_title("主机、敌机、友机三维轨迹图", pad=18)
    axis.set_xlabel("经度")
    axis.set_ylabel("纬度")
    axis.set_zlabel("高度 (m)")
    axis.view_init(elev=24, azim=-58)
    axis.grid(True, alpha=0.35)
    axis.legend(loc="upper left", fontsize=9)
    figure.tight_layout()

    if show_plot:
        plt.show()

    return figure, axis


def main() -> None:
    trajectory_bundle = build_trajectories()
    print("三维轨迹数据已生成，正在绘图...")
    print(f"起点坐标: {START_COORDS}")
    print(f"终点坐标: {END_COORDS}")
    print(f"主机轨迹模型: {MOTION_MODEL}")
    print(f"敌机数量: {ENEMY_NUM}")
    print(f"友机数量: {FRIENDLY_NUM}")
    plot_trajectories(trajectory_bundle, show_plot=SHOW_PLOT)


if __name__ == "__main__":
    main()
