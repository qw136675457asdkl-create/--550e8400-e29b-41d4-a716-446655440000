"""
核心业务逻辑层 
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from datetime import datetime, timedelta
import math
import os
import re
import shutil
import uuid
from typing import Any, Callable, Dict, Optional, Tuple

import numpy as np
import pandas as pd
from scipy.interpolate import UnivariateSpline, interp1d

import enemy_generator
import flight_trajectory
import wingman_generator as wingman_generator
import ADS_B_generator
import electronic_warfare_generator
import communication_reconnaissance_generator
import ais_generator
import target_towing_inquiry_generator
import bearing_generator
import lock_information_generator


# ==================== 默认配置 ====================
DEFAULT_HOST_TRAJECTORY_TYPE = "cubic"
ALLOWED_HOST_TRAJECTORY_TYPES = {"straight", "quadratic", "cubic", "two_segment", "three_segment"}
BASE_DIRECTORY = os.path.dirname(os.path.abspath(__file__))
# 如需修改生成文件的输出目录，请调整此常量。
DEFAULT_OUTPUT_DIRECTORY = os.path.join(BASE_DIRECTORY, "csv_output", "api_requests")
DEFAULT_RANDOM_SEEDS = {
    "host": 42,
    "enemy": 43,
    "wingman": 44,
    "attitude": 45,
    "electronic_warfare": 46,
    "communication_reconnaissance": 47,
    "ais": 48,
    "target_towing_inquiry": 49,
    "bearing": 50,
    "lock_information": 51,
}
ProgressCallback = Optional[Callable[[int, str, str], None]]
ENGLISH_MONTH_ABBREVIATIONS = {
    "jan": 1,
    "feb": 2,
    "mar": 3,
    "apr": 4,
    "may": 5,
    "jun": 6,
    "jul": 7,
    "aug": 8,
    "sep": 9,
    "oct": 10,
    "nov": 11,
    "dec": 12,
}
ENGLISH_WEEKDAY_ABBREVIATIONS = ("mon", "tue", "wed", "thu", "fri", "sat", "sun")
JAVA_STYLE_DATETIME_PATTERN = re.compile(
    r"^(?P<weekday>[A-Za-z]{3})\s+"
    r"(?P<month>[A-Za-z]{3})\s+"
    r"(?P<day>\d{1,2})\s+"
    r"(?P<hour>\d{2}):(?P<minute>\d{2}):(?P<second>\d{2})\s+"
    r"(?P<timezone>[A-Za-z0-9_:+/-]+)\s+"
    r"(?P<year>\d{4})$"
)


class ValidationError(ValueError):
    """请求载荷校验失败时抛出的异常。"""


@dataclass
class Coordinate3D:
    """通用三维坐标容器。"""

    x: float
    y: float
    z: float

    def __post_init__(self) -> None:
        self.x = float(self.x)
        self.y = float(self.y)
        self.z = float(self.z)

    @classmethod
    def from_value(cls, value: Any, field_name: str) -> "Coordinate3D":
        return cls(*parse_coordinate_triplet(value, field_name))

    @property
    def as_tuple(self) -> Tuple[float, float, float]:
        return self.x, self.y, self.z


@dataclass
class BasicRequestParams:
    """所有数据类型共用的基础请求参数。"""

    motion_model: str
    start_coords: Coordinate3D
    end_coords: Coordinate3D

    def __post_init__(self) -> None:
        self.motion_model = str(self.motion_model).strip()
        if not self.motion_model:
            raise ValidationError("motion_model must not be empty.")
        if self.motion_model not in ALLOWED_HOST_TRAJECTORY_TYPES:
            raise ValidationError(
                "motion_model must be one of: "
                + ", ".join(sorted(ALLOWED_HOST_TRAJECTORY_TYPES))
            )
        if not isinstance(self.start_coords, Coordinate3D):
            self.start_coords = Coordinate3D.from_value(self.start_coords, "start_coords")
        if not isinstance(self.end_coords, Coordinate3D):
            self.end_coords = Coordinate3D.from_value(self.end_coords, "end_coords")


@dataclass
class DatasetRequestParams:
    """单个数据类型共用的请求参数。"""

    enabled: bool
    filename: str
    flight_start_datetime: datetime
    flight_end_datetime: datetime
    sample_rate_hz: float

    def __post_init__(self) -> None:
        self.enabled = parse_boolean(self.enabled, "enabled")
        self.filename = str(self.filename or "").strip()
        self.flight_start_datetime = coerce_datetime(
            self.flight_start_datetime,
            "flight_start_datetime",
        )
        self.flight_end_datetime = coerce_datetime(
            self.flight_end_datetime,
            "flight_end_datetime",
        )
        self.sample_rate_hz = float(self.sample_rate_hz)
        if self.enabled and not self.filename:
            raise ValidationError("filename must not be empty when enabled is true.")
        if self.sample_rate_hz <= 0:
            raise ValidationError("sample_rate_hz must be greater than 0.")
        if self.flight_end_datetime <= self.flight_start_datetime:
            raise ValidationError(
                "flight_end_datetime must be later than flight_start_datetime."
            )

    @property
    def duration_seconds(self) -> float:
        return (self.flight_end_datetime - self.flight_start_datetime).total_seconds()


@dataclass
class RadarTrackRequestParams(DatasetRequestParams):
    """雷达航迹数据的请求参数。"""

    enemy_num: int = 0

    def __post_init__(self) -> None:
        super().__post_init__()
        self.enemy_num = int(self.enemy_num)
        if self.enemy_num < 0:
            raise ValidationError("enemy_num must be 0 or greater.")


@dataclass
class ADSBRequestParams(DatasetRequestParams):
    """ADS-B 数据的请求参数。"""

    friendly_num: int = 0

    def __post_init__(self) -> None:
        super().__post_init__()
        self.friendly_num = int(self.friendly_num)
        if self.friendly_num < 0:
            raise ValidationError("friendly_num must be 0 or greater.")


@dataclass
class StructuredRequestParams:
    """多数据类型接口使用的结构化请求参数。"""

    request_id: str
    basic: BasicRequestParams
    aircraft_inertial: DatasetRequestParams
    attitude: DatasetRequestParams
    radar_track: RadarTrackRequestParams
    ads_b: ADSBRequestParams
    electronic_warfare: DatasetRequestParams
    communication_reconnaissance: DatasetRequestParams
    ais: DatasetRequestParams
    target_towing_inquiry: DatasetRequestParams
    bearing: DatasetRequestParams
    lock_information: DatasetRequestParams
    random_seeds: Dict[str, int] = field(default_factory=lambda: DEFAULT_RANDOM_SEEDS.copy())

    def __post_init__(self) -> None:
        self.request_id = sanitize_request_id(self.request_id)
        self.basic = ensure_instance(self.basic, BasicRequestParams, "basic")
        self.aircraft_inertial = ensure_instance(
            self.aircraft_inertial,
            DatasetRequestParams,
            "aircraft_inertial",
        )
        self.attitude = ensure_instance(self.attitude, DatasetRequestParams, "attitude")
        self.radar_track = ensure_instance(self.radar_track, RadarTrackRequestParams, "radar_track")
        self.ads_b = ensure_instance(self.ads_b, ADSBRequestParams, "ads_b")
        self.electronic_warfare = ensure_instance(
            self.electronic_warfare,
            DatasetRequestParams,
            "electronic_warfare",
        )
        self.communication_reconnaissance = ensure_instance(
            self.communication_reconnaissance,
            DatasetRequestParams,
            "communication_reconnaissance",
        )
        self.ais = ensure_instance(self.ais, DatasetRequestParams, "ais")
        self.target_towing_inquiry = ensure_instance(
            self.target_towing_inquiry,
            DatasetRequestParams,
            "target_towing_inquiry",
        )
        self.bearing = ensure_instance(self.bearing, DatasetRequestParams, "bearing")
        self.lock_information = ensure_instance(
            self.lock_information,
            DatasetRequestParams,
            "lock_information",
        )

    @property
    def dataset_map(self) -> Dict[str, DatasetRequestParams]:
        return {
            "aircraft_inertial": self.aircraft_inertial,
            "attitude": self.attitude,
            "radar_track": self.radar_track,
            "ads_b": self.ads_b,
            "electronic_warfare": self.electronic_warfare,
            "communication_reconnaissance": self.communication_reconnaissance,
            "ais": self.ais,
            "target_towing_inquiry": self.target_towing_inquiry,
            "bearing": self.bearing,
            "lock_information": self.lock_information,
        }

    @property
    def enabled_dataset_map(self) -> Dict[str, DatasetRequestParams]:
        return {
            name: params
            for name, params in self.dataset_map.items()
            if params.enabled
        }

    @property
    def overall_start_time(self) -> datetime:
        datasets = list(self.enabled_dataset_map.values()) or list(self.dataset_map.values())
        return min(item.flight_start_datetime for item in datasets)

    @property
    def overall_end_time(self) -> datetime:
        datasets = list(self.enabled_dataset_map.values()) or list(self.dataset_map.values())
        return max(item.flight_end_datetime for item in datasets)

    @property
    def request_output_directory(self) -> str:
        return os.path.join(DEFAULT_OUTPUT_DIRECTORY, self.request_id)

    @property
    def flight_seconds(self) -> float:
        return (self.overall_end_time - self.overall_start_time).total_seconds()


def build_progress_payload(percent: int, stage: str, message: str) -> Dict[str, Any]:
    """构建统一的进度信息结构。"""
    bounded_percent = max(0, min(100, int(percent)))
    return {
        "percent": bounded_percent,
        "stage": str(stage),
        "message": str(message),
    }


def report_progress(progress_callback: ProgressCallback, percent: int, stage: str, message: str) -> None:
    """安全地上报当前处理进度。"""
    if progress_callback is None:
        return
    payload = build_progress_payload(percent, stage, message)
    try:
        progress_callback(payload["percent"], payload["stage"], payload["message"])
    except Exception:
        return


# ==================== 请求解析与校验 ====================
def parse_structured_request(payload: Mapping[str, Any]) -> StructuredRequestParams:
    """将新的嵌套请求体解析为强类型参数对象。"""
    if not isinstance(payload, Mapping):
        raise ValidationError("Request body must be a JSON object.")

    basic_payload = get_optional_mapping(
        payload,
        "basic",
    )
    if basic_payload is None:
        basic_payload = payload
    data_payload = get_optional_mapping(
        payload,
        "datasets",
    )
    if data_payload is None:
        data_payload = payload

    aircraft_inertial = parse_dataset_request_params(
        get_required_mapping(
            data_payload,
            "aircraft_inertial",
        ),
        "aircraft_inertial",
    )
    attitude = parse_dataset_request_params(
        get_required_mapping(
            data_payload,
            "attitude",
        ),
        "attitude",
    )
    radar_track = parse_radar_track_request_params(
        get_required_mapping(
            data_payload,
            "radar_track",
        )
    )
    ads_b = parse_ads_b_request_params(
        get_required_mapping(
            data_payload,
            "ads_b",
        )
    )
    electronic_warfare_payload = get_optional_mapping(
        data_payload,
        "electronic_warfare",
    )
    if electronic_warfare_payload is None:
        electronic_warfare = DatasetRequestParams(
            enabled=False,
            filename="electronic_warfare.csv",
            flight_start_datetime=aircraft_inertial.flight_start_datetime,
            flight_end_datetime=aircraft_inertial.flight_end_datetime,
            sample_rate_hz=aircraft_inertial.sample_rate_hz,
        )
    else:
        electronic_warfare = parse_dataset_request_params(
            electronic_warfare_payload,
            "electronic_warfare",
        )
    communication_reconnaissance_payload = get_optional_mapping(
        data_payload,
        "communication_reconnaissance",
    )
    if communication_reconnaissance_payload is None:
        communication_reconnaissance = DatasetRequestParams(
            enabled=False,
            filename="communication_reconnaissance.csv",
            flight_start_datetime=aircraft_inertial.flight_start_datetime,
            flight_end_datetime=aircraft_inertial.flight_end_datetime,
            sample_rate_hz=aircraft_inertial.sample_rate_hz,
        )
    else:
        communication_reconnaissance = parse_dataset_request_params(
            communication_reconnaissance_payload,
            "communication_reconnaissance",
        )
    ais_payload = get_optional_mapping(
        data_payload,
        "ais",
    )
    if ais_payload is None:
        ais = DatasetRequestParams(
            enabled=False,
            filename="ais.csv",
            flight_start_datetime=aircraft_inertial.flight_start_datetime,
            flight_end_datetime=aircraft_inertial.flight_end_datetime,
            sample_rate_hz=aircraft_inertial.sample_rate_hz,
        )
    else:
        ais = parse_dataset_request_params(
            ais_payload,
            "ais",
        )
    target_towing_inquiry_payload = get_optional_mapping(
        data_payload,
        "target_towing_inquiry",
    )
    if target_towing_inquiry_payload is None:
        target_towing_inquiry = DatasetRequestParams(
            enabled=False,
            filename="target_towing_inquiry.csv",
            flight_start_datetime=aircraft_inertial.flight_start_datetime,
            flight_end_datetime=aircraft_inertial.flight_end_datetime,
            sample_rate_hz=aircraft_inertial.sample_rate_hz,
        )
    else:
        target_towing_inquiry = parse_dataset_request_params(
            target_towing_inquiry_payload,
            "target_towing_inquiry",
        )
    bearing_payload = get_optional_mapping(
        data_payload,
        "bearing",
    )
    if bearing_payload is None:
        bearing = DatasetRequestParams(
            enabled=False,
            filename="bearing.csv",
            flight_start_datetime=aircraft_inertial.flight_start_datetime,
            flight_end_datetime=aircraft_inertial.flight_end_datetime,
            sample_rate_hz=aircraft_inertial.sample_rate_hz,
        )
    else:
        bearing = parse_dataset_request_params(
            bearing_payload,
            "bearing",
        )
    lock_information_payload = get_optional_mapping(
        data_payload,
        "lock_information",
    )
    if lock_information_payload is None:
        lock_information = DatasetRequestParams(
            enabled=False,
            filename="lock_information.csv",
            flight_start_datetime=aircraft_inertial.flight_start_datetime,
            flight_end_datetime=aircraft_inertial.flight_end_datetime,
            sample_rate_hz=aircraft_inertial.sample_rate_hz,
        )
    else:
        lock_information = parse_dataset_request_params(
            lock_information_payload,
            "lock_information",
        )

    return StructuredRequestParams(
        request_id=payload.get("request_id") or payload.get("requestId") or build_request_id(),
        basic=parse_basic_request_params(basic_payload),
        aircraft_inertial=aircraft_inertial,
        attitude=attitude,
        radar_track=radar_track,
        ads_b=ads_b,
        electronic_warfare=electronic_warfare,
        communication_reconnaissance=communication_reconnaissance,
        ais=ais,
        target_towing_inquiry=target_towing_inquiry,
        bearing=bearing,
        lock_information=lock_information,
    )


def parse_basic_request_params(payload: Mapping[str, Any]) -> BasicRequestParams:
    """解析基础参数部分。"""
    return BasicRequestParams(
        motion_model=get_required(
            payload,
            "motion_model",
            "motionModel",
            "host_trajectory_type",
            "hostTrajectoryType",
            "运动模型",
        ),
        start_coords=Coordinate3D.from_value(
            get_required(
                payload,
                "start_coords",
                "startCoords",
                "start_position",
                "startPosition",
                "起点三坐标",
                "起点坐标",
            ),
            "start_coords",
        ),
        end_coords=Coordinate3D.from_value(
            get_required(
                payload,
                "end_coords",
                "endCoords",
                "end_position",
                "endPosition",
                "终点三坐标",
                "终点坐标",
            ),
            "end_coords",
        ),
    )


def parse_dataset_request_params(payload: Mapping[str, Any], field_prefix: str) -> DatasetRequestParams:
    """解析通用数据类型参数。"""
    return DatasetRequestParams(**build_dataset_request_kwargs(payload, field_prefix))


def parse_radar_track_request_params(payload: Mapping[str, Any]) -> RadarTrackRequestParams:
    """解析雷达航迹参数。"""
    return RadarTrackRequestParams(
        **build_dataset_request_kwargs(payload, "radar_track"),
        enemy_num=int(get_required(payload, "enemyNum", "enemy_num")),
    )


def parse_ads_b_request_params(payload: Mapping[str, Any]) -> ADSBRequestParams:
    """解析 ADS-B 参数。"""
    return ADSBRequestParams(
        **build_dataset_request_kwargs(payload, "ads_b"),
        friendly_num=int(get_required(payload, "friendlyNum", "friendly_num")),
    )


def build_dataset_request_kwargs(payload: Mapping[str, Any], field_prefix: str) -> Dict[str, Any]:
    """提取某类数据的公共字段。"""
    enabled_value = get_optional(payload, "enabled")
    return {
        "enabled": parse_boolean(
            False if enabled_value is None else enabled_value,
            f"{field_prefix}.enabled",
        ),
        "filename": str(get_required(payload, "filename")).strip(),
        "flight_start_datetime": coerce_datetime(
            get_required(payload, "flightStartDatetime", "flight_start_datetime"),
            f"{field_prefix}.flight_start_datetime",
        ),
        "flight_end_datetime": coerce_datetime(
            get_required(
                payload,
                "flightEnd Datetime",
                "flightEndDatetime",
                "flight_end_datetime",
            ),
            f"{field_prefix}.flight_end_datetime",
        ),
        "sample_rate_hz": float(get_required(payload, "sampleRateHz", "sample_rate_hz")),
    }


def parse_request(payload: Mapping[str, Any]) -> StructuredRequestParams:
    """校验并标准化类 JSON 请求载荷。"""
    return parse_structured_request(payload)


# ==================== 仿真主流程 ====================
def run_simulation_from_payload(
    payload: Mapping[str, Any],
    progress_callback: ProgressCallback = None,
) -> Dict[str, Any]:
    """面向接口调用方的仿真入口。"""
    report_progress(progress_callback, 5, "validated", "正在校验请求参数")
    config = parse_request(payload)
    return run_simulation(config, progress_callback=progress_callback)


def run_simulation(
    config: StructuredRequestParams,
    progress_callback: ProgressCallback = None,
) -> Dict[str, Any]:
    """执行仿真并按请求生成对应的数据文件。"""
    report_progress(progress_callback, 10, "preparing", "正在初始化任务目录")
    enabled_datasets = config.enabled_dataset_map
    if not enabled_datasets:
        raise ValidationError("至少需要启用一种数据类型。")

    max_sample_rate_hz = max(params.sample_rate_hz for params in enabled_datasets.values())
    timestamp_start_datetime = datetime.now()
    request_output_directory = config.request_output_directory
    request_directory_preexisting = os.path.isdir(request_output_directory)
    os.makedirs(request_output_directory, exist_ok=True)

    report_progress(progress_callback, 20, "host_trajectory", "正在生成载机轨迹")
    point_count = compute_trajectory_point_count(config.flight_seconds, max_sample_rate_hz)
    generator = flight_trajectory.FlightTrajectory(
        config.basic.start_coords.as_tuple,
        config.basic.end_coords.as_tuple,
        flight_time=config.flight_seconds,
        random_seed=config.random_seeds["host"],
    )
    t, lon, lat, x, y, z, s, params = generator.get_trajectory(
        config.basic.motion_model,
        num_points=point_count,
    )

    v_x, v_y, v_z, v_total, roll, pitch, yaw, _ = calculate_velocities_and_angles(
        t,
        x,
        y,
        z,
        s,
        random_seed=config.random_seeds["attitude"],
    )
    heading_true = calculate_heading_true(v_x, v_y)

    report_progress(progress_callback, 40, "targets", "正在生成目标轨迹")
    enemy_num = int(config.radar_track.enemy_num) if config.radar_track.enabled else 0
    need_friendlies = config.radar_track.enabled or config.ads_b.enabled
    friendly_num = int(config.ads_b.friendly_num) if need_friendlies else 0

    enemies = enemy_generator.create_enemies(
        main_lon=lon,
        main_lat=lat,
        main_z=z,
        t=t,
        start_coords=config.basic.start_coords.as_tuple,
        end_coords=config.basic.end_coords.as_tuple,
        count=enemy_num,
        flight_time=config.flight_seconds,
        random_seed=config.random_seeds["enemy"],
    ) if enemy_num > 0 else {}
    friendlies = wingman_generator.create_wingmen(
        main_lon=lon,
        main_lat=lat,
        main_z=z,
        t=t,
        count=friendly_num,
        flight_time=config.flight_seconds,
        random_seed=config.random_seeds["wingman"],
    ) if friendly_num > 0 else {}

    report_progress(progress_callback, 55, "aircraft_inertial", "正在导出载机惯导数据")
    generated_files_directory = os.path.abspath(request_output_directory)
    files: Dict[str, str] = {"directory": generated_files_directory}
    summary: Dict[str, Any] = {
        "flight_duration_seconds": round(config.flight_seconds, 3),
        "trajectory_point_count": int(len(t)),
        "enemy_num": enemy_num,
        "friendly_num": friendly_num,
        "generated_target_total": enemy_num + friendly_num,
        "output_directory": generated_files_directory,
    }
    tracked_output_paths: Dict[str, str] = {}

    if config.aircraft_inertial.enabled:
        try:
            tracked_output_paths["aircraft_inertial"] = resolve_output_path(
                request_output_directory,
                config.aircraft_inertial.filename,
            )
            inertial_df, inertial_path = save_aircraft_inertial_csv(
                t=t,
                reference_start_datetime=config.overall_start_time,
                timestamp_start_datetime=timestamp_start_datetime,
                dataset_config=config.aircraft_inertial,
                host_lon=lon,
                host_lat=lat,
                host_alt=z,
                host_vel_east=v_x,
                host_vel_north=v_y,
                host_vel_vertical=v_z,
                host_true_airspeed=v_total,
                host_heading_true=heading_true,
                output_directory=request_output_directory,
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["aircraft_inertial_csv"] = os.path.abspath(inertial_path)
        summary["aircraft_inertial_row_count"] = int(len(inertial_df))

    report_progress(progress_callback, 70, "attitude", "正在导出姿态数据")
    if config.attitude.enabled:
        try:
            tracked_output_paths["attitude"] = resolve_output_path(
                request_output_directory,
                config.attitude.filename,
            )
            attitude_df, attitude_path = save_attitude_csv(
                t=t,
                reference_start_datetime=config.overall_start_time,
                timestamp_start_datetime=timestamp_start_datetime,
                dataset_config=config.attitude,
                roll=roll,
                pitch=pitch,
                yaw=yaw,
                output_directory=request_output_directory,
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["attitude_csv"] = os.path.abspath(attitude_path)
        summary["attitude_row_count"] = int(len(attitude_df))

    report_progress(progress_callback, 82, "radar_track", "正在导出雷达航迹数据")
    if config.radar_track.enabled:
        try:
            tracked_output_paths["radar_track"] = resolve_output_path(
                request_output_directory,
                config.radar_track.filename,
            )
            radar_df, radar_path = save_radar_track_csv(
                t=t,
                reference_start_datetime=config.overall_start_time,
                timestamp_start_datetime=timestamp_start_datetime,
                dataset_config=config.radar_track,
                host_lon=lon,
                host_lat=lat,
                host_alt=z,
                host_vel_east=v_x,
                host_vel_north=v_y,
                host_vel_vertical=v_z,
                host_heading_true=heading_true,
                enemies=enemies,
                friendlies=friendlies,
                output_directory=request_output_directory,
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["radar_track_csv"] = os.path.abspath(radar_path)
        summary["radar_track_row_count"] = int(len(radar_df))

    report_progress(progress_callback, 92, "ads_b", "正在导出 ADS_B 数据")
    if config.ads_b.enabled:
        try:
            tracked_output_paths["ads_b"] = resolve_output_path(
                request_output_directory,
                config.ads_b.filename,
            )
            ads_b_df, ads_b_path = save_ads_b_csv(
                t=t,
                reference_start_datetime=config.overall_start_time,
                timestamp_start_datetime=timestamp_start_datetime,
                dataset_config=config.ads_b,
                friendlies=friendlies,
                output_directory=request_output_directory,
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["ads_b_csv"] = os.path.abspath(ads_b_path)
        summary["ads_b_row_count"] = int(len(ads_b_df))

    report_progress(progress_callback, 96, "electronic_warfare", "正在导出电子战时序数据")
    if config.electronic_warfare.enabled:
        try:
            tracked_output_paths["electronic_warfare"] = resolve_output_path(
                request_output_directory,
                config.electronic_warfare.filename,
            )
            electronic_warfare_df, electronic_warfare_path = electronic_warfare_generator.save_electronic_warfare_csv(
                output_path=tracked_output_paths["electronic_warfare"],
                reference_start_datetime=config.overall_start_time,
                timestamp_start_datetime=timestamp_start_datetime,
                flight_start_datetime=config.electronic_warfare.flight_start_datetime,
                flight_end_datetime=config.electronic_warfare.flight_end_datetime,
                sample_rate_hz=config.electronic_warfare.sample_rate_hz,
                random_seed=config.random_seeds["electronic_warfare"],
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["electronic_warfare_csv"] = os.path.abspath(electronic_warfare_path)
        summary["electronic_warfare_row_count"] = int(len(electronic_warfare_df))
        summary["electronic_warfare_target_count"] = int(
            electronic_warfare_df["target_id"].nunique()
        ) if not electronic_warfare_df.empty else 0

    report_progress(progress_callback, 98, "communication_reconnaissance", "正在导出通侦时序数据")
    if config.communication_reconnaissance.enabled:
        try:
            tracked_output_paths["communication_reconnaissance"] = resolve_output_path(
                request_output_directory,
                config.communication_reconnaissance.filename,
            )
            communication_reconnaissance_df, communication_reconnaissance_path = (
                communication_reconnaissance_generator.save_communication_reconnaissance_csv(
                    output_path=tracked_output_paths["communication_reconnaissance"],
                    reference_start_datetime=config.overall_start_time,
                    timestamp_start_datetime=timestamp_start_datetime,
                    flight_start_datetime=config.communication_reconnaissance.flight_start_datetime,
                    flight_end_datetime=config.communication_reconnaissance.flight_end_datetime,
                    sample_rate_hz=config.communication_reconnaissance.sample_rate_hz,
                    random_seed=config.random_seeds["communication_reconnaissance"],
                )
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["communication_reconnaissance_csv"] = os.path.abspath(communication_reconnaissance_path)
        summary["communication_reconnaissance_row_count"] = int(len(communication_reconnaissance_df))
        summary["communication_reconnaissance_target_count"] = int(
            communication_reconnaissance_df["target_id"].nunique()
        ) if not communication_reconnaissance_df.empty else 0

    report_progress(progress_callback, 99, "ais", "正在导出AIS时序数据")
    if config.ais.enabled:
        try:
            tracked_output_paths["ais"] = resolve_output_path(
                request_output_directory,
                config.ais.filename,
            )
            ais_df, ais_path = ais_generator.save_ais_csv(
                output_path=tracked_output_paths["ais"],
                reference_start_datetime=config.overall_start_time,
                timestamp_start_datetime=timestamp_start_datetime,
                flight_start_datetime=config.ais.flight_start_datetime,
                flight_end_datetime=config.ais.flight_end_datetime,
                sample_rate_hz=config.ais.sample_rate_hz,
                random_seed=config.random_seeds["ais"],
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["ais_csv"] = os.path.abspath(ais_path)
        summary["ais_row_count"] = int(len(ais_df))
        summary["ais_target_count"] = int(ais_df["mmsi"].nunique()) if not ais_df.empty else 0

    report_progress(progress_callback, 99, "target_towing_inquiry", "正在导出目标牵引与询问时序数据")
    if config.target_towing_inquiry.enabled:
        try:
            tracked_output_paths["target_towing_inquiry"] = resolve_output_path(
                request_output_directory,
                config.target_towing_inquiry.filename,
            )
            target_towing_inquiry_df, target_towing_inquiry_path = (
                target_towing_inquiry_generator.save_target_towing_inquiry_csv(
                    output_path=tracked_output_paths["target_towing_inquiry"],
                    reference_start_datetime=config.overall_start_time,
                    timestamp_start_datetime=timestamp_start_datetime,
                    flight_start_datetime=config.target_towing_inquiry.flight_start_datetime,
                    flight_end_datetime=config.target_towing_inquiry.flight_end_datetime,
                    sample_rate_hz=config.target_towing_inquiry.sample_rate_hz,
                    random_seed=config.random_seeds["target_towing_inquiry"],
                )
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["target_towing_inquiry_csv"] = os.path.abspath(target_towing_inquiry_path)
        summary["target_towing_inquiry_row_count"] = int(len(target_towing_inquiry_df))
        summary["target_towing_inquiry_target_count"] = int(
            target_towing_inquiry_df["target_id"].nunique()
        ) if not target_towing_inquiry_df.empty else 0

    report_progress(progress_callback, 99, "bearing", "正在导出方位时序数据")
    if config.bearing.enabled:
        try:
            tracked_output_paths["bearing"] = resolve_output_path(
                request_output_directory,
                config.bearing.filename,
            )
            bearing_df, bearing_path = bearing_generator.save_bearing_csv(
                output_path=tracked_output_paths["bearing"],
                reference_start_datetime=config.overall_start_time,
                timestamp_start_datetime=timestamp_start_datetime,
                flight_start_datetime=config.bearing.flight_start_datetime,
                flight_end_datetime=config.bearing.flight_end_datetime,
                sample_rate_hz=config.bearing.sample_rate_hz,
                random_seed=config.random_seeds["bearing"],
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["bearing_csv"] = os.path.abspath(bearing_path)
        summary["bearing_row_count"] = int(len(bearing_df))
        summary["bearing_target_count"] = int(bearing_df["target_id"].nunique()) if not bearing_df.empty else 0

    report_progress(progress_callback, 99, "lock_information", "正在导出闭锁信息时序数据")
    if config.lock_information.enabled:
        try:
            tracked_output_paths["lock_information"] = resolve_output_path(
                request_output_directory,
                config.lock_information.filename,
            )
            lock_information_df, lock_information_path = lock_information_generator.save_lock_information_csv(
                output_path=tracked_output_paths["lock_information"],
                reference_start_datetime=config.overall_start_time,
                timestamp_start_datetime=timestamp_start_datetime,
                flight_start_datetime=config.lock_information.flight_start_datetime,
                flight_end_datetime=config.lock_information.flight_end_datetime,
                sample_rate_hz=config.lock_information.sample_rate_hz,
                random_seed=config.random_seeds["lock_information"],
            )
        except Exception:
            cleanup_failed_outputs(
                output_paths=build_cleanup_output_paths(files, tracked_output_paths),
                request_output_directory=request_output_directory,
                remove_request_directory=not request_directory_preexisting,
            )
            raise
        files["lock_information_csv"] = os.path.abspath(lock_information_path)
        summary["lock_information_row_count"] = int(len(lock_information_df))
        summary["lock_information_target_count"] = int(
            lock_information_df["target_id"].nunique()
        ) if not lock_information_df.empty else 0

    result = {
        "status": "success",
        "request_id": config.request_id,
        "progress": build_progress_payload(100, "completed", "任务处理完成"),
        "generated_files_directory": generated_files_directory,
        "input": build_structured_request_payload(config),
        "summary": summary,
        "files": files,
        "targets": {
            "host": build_target_summary("host", lon, lat, z),
            "enemies": build_target_collection(enemies),
            "friendlies": build_target_collection(friendlies),
        },
    }
    report_progress(progress_callback, 100, "completed", "任务处理完成")
    return result


def build_structured_request_payload(config: StructuredRequestParams) -> Dict[str, Any]:
    """构建结构化请求的回显信息。"""
    return {
        "request_id": config.request_id,
        "basic": {
            "motion_model": config.basic.motion_model,
            "start_coords": coordinate_to_dict(config.basic.start_coords),
            "end_coords": coordinate_to_dict(config.basic.end_coords),
        },
        "aircraft_inertial": build_dataset_request_payload(config.aircraft_inertial),
        "attitude": build_dataset_request_payload(config.attitude),
        "radar_track": {
            **build_dataset_request_payload(config.radar_track),
            "enemy_num": int(config.radar_track.enemy_num),
        },
        "ads_b": {
            **build_dataset_request_payload(config.ads_b),
            "friendly_num": int(config.ads_b.friendly_num),
        },
        "electronic_warfare": build_dataset_request_payload(config.electronic_warfare),
        "communication_reconnaissance": build_dataset_request_payload(config.communication_reconnaissance),
        "ais": build_dataset_request_payload(config.ais),
        "target_towing_inquiry": build_dataset_request_payload(config.target_towing_inquiry),
        "bearing": build_dataset_request_payload(config.bearing),
        "lock_information": build_dataset_request_payload(config.lock_information),
    }


def build_dataset_request_payload(config: DatasetRequestParams) -> Dict[str, Any]:
    """构建单类数据请求的回显信息。"""
    return {
        "enabled": bool(config.enabled),
        "filename": str(config.filename),
        "flight_start_datetime": format_datetime(config.flight_start_datetime),
        "flight_end_datetime": format_datetime(config.flight_end_datetime),
        "sample_rate_hz": float(config.sample_rate_hz),
    }


def coordinate_to_dict(value: Coordinate3D) -> Dict[str, float]:
    """将三维坐标转换为经纬高字典。"""
    return {
        "lon": float(value.x),
        "lat": float(value.y),
        "alt": float(value.z),
    }


def resolve_output_path(output_directory: str, filename: str) -> str:
    """根据请求文件名解析最终输出路径。"""
    output_path = str(filename).strip()
    if not output_path:
        raise ValidationError("filename must not be empty.")
    if not os.path.isabs(output_path):
        output_path = os.path.join(output_directory, output_path)
    parent_directory = os.path.dirname(output_path)
    if parent_directory:
        os.makedirs(parent_directory, exist_ok=True)
    return output_path


def build_cleanup_output_paths(
    files: Mapping[str, str],
    tracked_output_paths: Mapping[str, str],
) -> Tuple[str, ...]:
    """汇总本次任务已生成和计划生成的文件路径，用于失败回滚。"""
    cleanup_paths = []
    seen_paths = set()

    for output_path in tracked_output_paths.values():
        absolute_path = os.path.abspath(output_path)
        if absolute_path in seen_paths:
            continue
        seen_paths.add(absolute_path)
        cleanup_paths.append(absolute_path)

    for name, output_path in files.items():
        if name == "directory":
            continue
        absolute_path = os.path.abspath(output_path)
        if absolute_path in seen_paths:
            continue
        seen_paths.add(absolute_path)
        cleanup_paths.append(absolute_path)

    return tuple(cleanup_paths)


def cleanup_failed_outputs(
    output_paths: Sequence[str],
    request_output_directory: str,
    remove_request_directory: bool = False,
) -> None:
    """任务失败时删除本次任务已生成的文件，并尽量清理空目录。"""
    request_directory = os.path.abspath(request_output_directory)

    for output_path in output_paths:
        absolute_path = os.path.abspath(output_path)
        try:
            if os.path.isfile(absolute_path) or os.path.islink(absolute_path):
                os.remove(absolute_path)
        except OSError:
            continue
        cleanup_empty_parent_directories(absolute_path, request_directory)

    if remove_request_directory and os.path.isdir(request_directory):
        try:
            shutil.rmtree(request_directory)
        except OSError:
            try:
                os.rmdir(request_directory)
            except OSError:
                return


def cleanup_empty_parent_directories(path: str, boundary_directory: str) -> None:
    """删除指定边界目录内已经变空的父目录。"""
    absolute_boundary = os.path.abspath(boundary_directory)
    current_directory = os.path.dirname(os.path.abspath(path))

    while current_directory and is_path_within_directory(current_directory, absolute_boundary):
        if current_directory == absolute_boundary:
            return
        try:
            os.rmdir(current_directory)
        except OSError:
            return
        current_directory = os.path.dirname(current_directory)


def is_path_within_directory(path: str, directory: str) -> bool:
    """判断路径是否位于给定目录内部。"""
    absolute_path = os.path.abspath(path)
    absolute_directory = os.path.abspath(directory)
    try:
        return os.path.commonpath([absolute_path, absolute_directory]) == absolute_directory
    except ValueError:
        return False


def build_dataset_sample_times(
    reference_start_datetime: datetime,
    dataset_config: DatasetRequestParams,
) -> np.ndarray:
    """构建某类数据相对于统一起始时间的采样时刻。"""
    start_seconds = (dataset_config.flight_start_datetime - reference_start_datetime).total_seconds()
    end_seconds = (dataset_config.flight_end_datetime - reference_start_datetime).total_seconds()
    sample_interval = 1.0 / float(dataset_config.sample_rate_hz)
    sample_times = np.round(np.arange(start_seconds, end_seconds + sample_interval, sample_interval), 9)
    return sample_times[sample_times <= end_seconds + 1e-9]


def interpolate_series(
    t: np.ndarray,
    values: np.ndarray,
    sample_times: np.ndarray,
) -> np.ndarray:
    """将序列插值到指定采样时刻。"""
    return interp1d(
        t,
        np.asarray(values, dtype=float),
        kind="linear",
        fill_value="extrapolate",
    )(sample_times)


def build_timestamp_series(
    timestamp_start_datetime: datetime,
    sample_times: np.ndarray,
) -> list[str]:
    """生成包含年月日时分秒的四位毫秒时间戳文本。"""
    return [
        format_timestamp_text(timestamp_start_datetime + timedelta(seconds=float(value)))
        for value in sample_times
    ]


def format_timestamp_text(value: datetime) -> str:
    """将时间格式化为带四位毫秒的时间戳文本。"""
    return f"{value.strftime('%Y-%m-%d %H:%M:%S')}.{value.microsecond // 1000:04d}"


def calculate_heading_true(vel_east: np.ndarray, vel_north: np.ndarray) -> np.ndarray:
    """根据东向和北向速度计算真航向角。"""
    return (np.degrees(np.arctan2(vel_east, vel_north)) + 360.0) % 360.0


def calculate_rate_series(
    t: np.ndarray,
    values: np.ndarray,
    circular: bool = False,
) -> np.ndarray:
    """计算序列对时间的一阶导数。"""
    values = np.asarray(values, dtype=float)
    if len(values) < 2:
        return np.zeros_like(values)
    if circular:
        values = np.degrees(np.unwrap(np.radians(values)))
    return np.gradient(values, np.asarray(t, dtype=float))


def geo_to_xy(
    lon: np.ndarray,
    lat: np.ndarray,
    reference_lon: float,
    reference_lat: float,
    mean_lat: float,
) -> Tuple[np.ndarray, np.ndarray]:
    """将经纬度转换为局部平面坐标。"""
    lon_to_meter = 111_320.0 * np.cos(np.radians(mean_lat))
    lat_to_meter = 110_540.0
    x = (np.asarray(lon, dtype=float) - float(reference_lon)) * lon_to_meter
    y = (np.asarray(lat, dtype=float) - float(reference_lat)) * lat_to_meter
    return x, y


def calculate_target_velocity_components(
    t: np.ndarray,
    lon: np.ndarray,
    lat: np.ndarray,
    alt: np.ndarray,
    reference_lon: float,
    reference_lat: float,
    mean_lat: float,
) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """计算目标在局部坐标系下的三速度。"""
    x, y = geo_to_xy(lon, lat, reference_lon, reference_lat, mean_lat)
    vx = calculate_rate_series(t, x)
    vy = calculate_rate_series(t, y)
    vz = calculate_rate_series(t, np.asarray(alt, dtype=float))
    return vx, vy, vz


def save_aircraft_inertial_csv(
    t: np.ndarray,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    dataset_config: DatasetRequestParams,
    host_lon: np.ndarray,
    host_lat: np.ndarray,
    host_alt: np.ndarray,
    host_vel_east: np.ndarray,
    host_vel_north: np.ndarray,
    host_vel_vertical: np.ndarray,
    host_true_airspeed: np.ndarray,
    host_heading_true: np.ndarray,
    output_directory: str,
) -> Tuple[pd.DataFrame, str]:
    """导出载机惯导数据。"""
    sample_times = build_dataset_sample_times(reference_start_datetime, dataset_config)
    df = pd.DataFrame(
        {
            "timestamp": build_timestamp_series(timestamp_start_datetime, sample_times),
            "lat": np.round(interpolate_series(t, host_lat, sample_times), 6),
            "lon": np.round(interpolate_series(t, host_lon, sample_times), 6),
            "alt": np.round(interpolate_series(t, host_alt, sample_times), 3),
            "true_airspeed": np.round(interpolate_series(t, host_true_airspeed, sample_times), 3),
            "heading_true": np.round(interpolate_series(t, host_heading_true, sample_times), 3),
            "vel_north": np.round(interpolate_series(t, host_vel_north, sample_times), 3),
            "vel_east": np.round(interpolate_series(t, host_vel_east, sample_times), 3),
            "vel_vertical": np.round(interpolate_series(t, host_vel_vertical, sample_times), 3),
            "nav_mode": "",
            "ins_status_word": "",
        }
    )
    output_path = resolve_output_path(output_directory, dataset_config.filename)
    df.to_csv(output_path, index=False, encoding="utf-8-sig")
    return df, output_path


def save_attitude_csv(
    t: np.ndarray,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    dataset_config: DatasetRequestParams,
    roll: np.ndarray,
    pitch: np.ndarray,
    yaw: np.ndarray,
    output_directory: str,
) -> Tuple[pd.DataFrame, str]:
    """导出姿态数据。"""
    rate_p = calculate_rate_series(t, roll)
    rate_q = calculate_rate_series(t, pitch)
    rate_r = calculate_rate_series(t, yaw, circular=True)
    sample_times = build_dataset_sample_times(reference_start_datetime, dataset_config)
    df = pd.DataFrame(
        {
            "timestamp": build_timestamp_series(timestamp_start_datetime, sample_times),
            "euler_pitch": np.round(interpolate_series(t, pitch, sample_times), 3),
            "euler_roll": np.round(interpolate_series(t, roll, sample_times), 3),
            "euler_yaw": np.round(interpolate_series(t, yaw, sample_times), 3),
            "rate_p": np.round(interpolate_series(t, rate_p, sample_times), 3),
            "rate_q": np.round(interpolate_series(t, rate_q, sample_times), 3),
            "rate_r": np.round(interpolate_series(t, rate_r, sample_times), 3),
        }
    )
    output_path = resolve_output_path(output_directory, dataset_config.filename)
    df.to_csv(output_path, index=False, encoding="utf-8-sig")
    return df, output_path


def build_radar_targets(
    t: np.ndarray,
    enemies: Mapping[str, Mapping[str, Any]],
    friendlies: Mapping[str, Mapping[str, Any]],
    reference_lon: float,
    reference_lat: float,
    mean_lat: float,
) -> list[Dict[str, Any]]:
    """组装雷达航迹导出所需的目标信息。"""
    targets: list[Dict[str, Any]] = []
    for prefix, identity, collection in (
        ("enemy", "敌机", enemies),
        ("friendly", "友机", friendlies),
    ):
        for index, (_, target) in enumerate(collection.items(), start=1):
            numeric_id = int(target.get("id", index))
            velocity_x, velocity_y, velocity_z = calculate_target_velocity_components(
                t=t,
                lon=np.asarray(target["lon"], dtype=float),
                lat=np.asarray(target["lat"], dtype=float),
                alt=np.asarray(target["z"], dtype=float),
                reference_lon=reference_lon,
                reference_lat=reference_lat,
                mean_lat=mean_lat,
            )
            targets.append(
                {
                    "target_id": f"{prefix}_{numeric_id}",
                    "identity": identity,
                    "lon": np.asarray(target["lon"], dtype=float),
                    "lat": np.asarray(target["lat"], dtype=float),
                    "alt": np.asarray(target["z"], dtype=float),
                    "velocity_x": velocity_x,
                    "velocity_y": velocity_y,
                    "velocity_z": velocity_z,
                }
            )
    return targets


def save_radar_track_csv(
    t: np.ndarray,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    dataset_config: RadarTrackRequestParams,
    host_lon: np.ndarray,
    host_lat: np.ndarray,
    host_alt: np.ndarray,
    host_vel_east: np.ndarray,
    host_vel_north: np.ndarray,
    host_vel_vertical: np.ndarray,
    host_heading_true: np.ndarray,
    enemies: Mapping[str, Mapping[str, Any]],
    friendlies: Mapping[str, Mapping[str, Any]],
    output_directory: str,
) -> Tuple[pd.DataFrame, str]:
    """导出雷达航迹数据。"""
    columns = [
        "target_id",
        "timestamp",
        "range_slant",
        "azimuth_rel",
        "elevation_rel",
        "Range_Rate",
        "lon",
        "lat",
        "alt",
        "velocity_x",
        "velocity_y",
        "velocity_z",
        "identity",
    ]
    sample_times = build_dataset_sample_times(reference_start_datetime, dataset_config)
    output_path = resolve_output_path(output_directory, dataset_config.filename)
    if not enemies and not friendlies:
        df = pd.DataFrame(columns=columns)
        df.to_csv(output_path, index=False, encoding="utf-8-sig")
        return df, output_path

    reference_lon = float(host_lon[0])
    reference_lat = float(host_lat[0])
    mean_lat = float(np.mean(host_lat))

    host_lon_sample = interpolate_series(t, host_lon, sample_times)
    host_lat_sample = interpolate_series(t, host_lat, sample_times)
    host_alt_sample = interpolate_series(t, host_alt, sample_times)
    host_heading_sample = interpolate_series(t, host_heading_true, sample_times)
    host_vel_x_sample = interpolate_series(t, host_vel_east, sample_times)
    host_vel_y_sample = interpolate_series(t, host_vel_north, sample_times)
    host_vel_z_sample = interpolate_series(t, host_vel_vertical, sample_times)
    host_x_sample, host_y_sample = geo_to_xy(
        host_lon_sample,
        host_lat_sample,
        reference_lon=reference_lon,
        reference_lat=reference_lat,
        mean_lat=mean_lat,
    )
    timestamps = build_timestamp_series(timestamp_start_datetime, sample_times)
    radar_targets = build_radar_targets(
        t=t,
        enemies=enemies,
        friendlies=friendlies,
        reference_lon=reference_lon,
        reference_lat=reference_lat,
        mean_lat=mean_lat,
    )

    rows = []
    for target in radar_targets:
        target_lon_sample = interpolate_series(t, target["lon"], sample_times)
        target_lat_sample = interpolate_series(t, target["lat"], sample_times)
        target_alt_sample = interpolate_series(t, target["alt"], sample_times)
        target_vel_x_sample = interpolate_series(t, target["velocity_x"], sample_times)
        target_vel_y_sample = interpolate_series(t, target["velocity_y"], sample_times)
        target_vel_z_sample = interpolate_series(t, target["velocity_z"], sample_times)
        target_x_sample, target_y_sample = geo_to_xy(
            target_lon_sample,
            target_lat_sample,
            reference_lon=reference_lon,
            reference_lat=reference_lat,
            mean_lat=mean_lat,
        )

        for index, elapsed_seconds in enumerate(sample_times):
            delta_x = float(target_x_sample[index] - host_x_sample[index])
            delta_y = float(target_y_sample[index] - host_y_sample[index])
            delta_z = float(target_alt_sample[index] - host_alt_sample[index])
            horizontal_distance = math.hypot(delta_x, delta_y)
            range_slant = math.sqrt(delta_x ** 2 + delta_y ** 2 + delta_z ** 2)
            azimuth_rel = (
                (math.degrees(math.atan2(delta_x, delta_y)) - float(host_heading_sample[index]) + 180.0) % 360.0
            ) - 180.0
            elevation_rel = math.degrees(math.atan2(delta_z, horizontal_distance))

            relative_velocity = np.array(
                [
                    float(target_vel_x_sample[index] - host_vel_x_sample[index]),
                    float(target_vel_y_sample[index] - host_vel_y_sample[index]),
                    float(target_vel_z_sample[index] - host_vel_z_sample[index]),
                ]
            )
            if range_slant > 1e-9:
                los_unit = np.array([delta_x, delta_y, delta_z], dtype=float) / range_slant
                range_rate = float(np.dot(relative_velocity, los_unit))
            else:
                range_rate = 0.0

            rows.append(
                {
                    "target_id": target["target_id"],
                    "timestamp": timestamps[index],
                    "range_slant": round(range_slant, 3),
                    "azimuth_rel": round(azimuth_rel, 3),
                    "elevation_rel": round(elevation_rel, 3),
                    "Range_Rate": round(range_rate, 3),
                    "lon": round(float(target_lon_sample[index]), 6),
                    "lat": round(float(target_lat_sample[index]), 6),
                    "alt": round(float(target_alt_sample[index]), 3),
                    "velocity_x": round(float(target_vel_x_sample[index]), 3),
                    "velocity_y": round(float(target_vel_y_sample[index]), 3),
                    "velocity_z": round(float(target_vel_z_sample[index]), 3),
                    "identity": target["identity"],
                    "_elapsed_seconds": float(elapsed_seconds),
                }
            )

    df = pd.DataFrame(rows)
    if not df.empty:
        df = df.sort_values(by=["_elapsed_seconds", "target_id"], kind="stable").drop(columns=["_elapsed_seconds"])
    else:
        df = pd.DataFrame(columns=columns)
    df.to_csv(output_path, index=False, encoding="utf-8-sig")
    return df, output_path


def save_ads_b_csv(
    t: np.ndarray,
    reference_start_datetime: datetime,
    timestamp_start_datetime: datetime,
    dataset_config: ADSBRequestParams,
    friendlies: Mapping[str, Mapping[str, Any]],
    output_directory: str,
) -> Tuple[pd.DataFrame, str]:
    """导出 ADS_B 十六进制数据。"""
    output_path = resolve_output_path(output_directory, dataset_config.filename)
    if not friendlies:
        df = pd.DataFrame(columns=["hex_data"])
        df.to_csv(output_path, index=False, encoding="utf-8-sig")
        return df, output_path

    start_minute = (dataset_config.flight_start_datetime - reference_start_datetime).total_seconds() / 60.0
    end_minute = (dataset_config.flight_end_datetime - reference_start_datetime).total_seconds() / 60.0
    frames = []
    for index, (_, friendly) in enumerate(friendlies.items(), start=1):
        friendly_id = int(friendly.get("id", index))
        frame = ADS_B_generator.generate_wingman_message_dataframe(
            t=t,
            wingman_lon=friendly["lon"],
            wingman_lat=friendly["lat"],
            wingman_z=friendly["z"],
            start_minute=start_minute,
            end_minute=end_minute,
            sample_rate=dataset_config.sample_rate_hz,
            flight_start_datetime=format_timestamp_text(timestamp_start_datetime),
            selected_times="all",
            source_id=friendly_id,
            message_config=None,
        )
        if frame.empty:
            continue
        frames.append(pd.DataFrame({"hex_data": frame.iloc[:, -1].astype(str)}))

    df = pd.concat(frames, ignore_index=True) if frames else pd.DataFrame(columns=["hex_data"])
    df.to_csv(output_path, index=False, encoding="utf-8-sig")
    return df, output_path


# ==================== 轨迹后处理与姿态计算 ====================
def calculate_velocities_and_angles(
    t: np.ndarray,
    x: np.ndarray,
    y: np.ndarray,
    z: np.ndarray,
    s: np.ndarray,
    random_seed: int = 45,
) -> Tuple[np.ndarray, ...]:
    """根据轨迹计算速度量与姿态角。"""
    spline_x = UnivariateSpline(t, x, s=0, k=3)
    spline_y = UnivariateSpline(t, y, s=0, k=3)
    spline_z = UnivariateSpline(t, z, s=0, k=3)
    spline_s = UnivariateSpline(t, s, s=0, k=3)

    v_x = spline_x.derivative()(t)
    v_y = spline_y.derivative()(t)
    v_z = spline_z.derivative()(t)
    v_s = spline_s.derivative()(t)
    v_total = np.sqrt(v_x ** 2 + v_y ** 2 + v_z ** 2)

    v_horizontal = np.sqrt(v_x ** 2 + v_y ** 2)
    pitch_base = np.degrees(np.arctan2(v_z, v_horizontal))
    yaw_base = np.degrees(np.arctan2(v_y, v_x))

    roll_base = np.zeros_like(t)
    yaw_gradient = np.gradient(yaw_base)
    for index in range(1, len(t) - 1):
        yaw_rate = yaw_gradient[index] * np.pi / 180.0
        if v_total[index] > 0:
            roll_base[index] = np.degrees(np.arctan2(v_total[index] * yaw_rate, 9.81))

    rng = np.random.default_rng(random_seed)
    roll = roll_base + smooth_random_series(rng, len(t))
    pitch = pitch_base + smooth_random_series(rng, len(t))
    yaw = yaw_base + smooth_random_series(rng, len(t))

    roll = np.clip(roll, -30, 30)
    pitch = np.clip(pitch, -20, 20)

    return v_x, v_y, v_z, v_total, roll, pitch, yaw, v_s


def smooth_random_series(rng: np.random.Generator, length: int) -> np.ndarray:
    """生成带轻微平滑效果的随机扰动序列。"""
    values = rng.normal(0, 0.8, length)
    values = np.clip(values, -2, 2)
    return np.convolve(values, np.ones(10) / 10.0, mode="same")


def apply_initial_velocity_constraints(
    t: np.ndarray,
    x: np.ndarray,
    y: np.ndarray,
    z: np.ndarray,
    current_start_velocity: Tuple[float, float, float],
    target_start_velocity: Tuple[float, float, float],
) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    在轨迹起始段平滑融合目标初始速度。

    轨迹会在短时间内重新回到原始路径附近，从而尽量保持终点条件稳定。
    """
    blend_seconds = min(10.0, max(float(t[-1]) * 0.1, 1.0))
    if blend_seconds <= 0 or len(t) < 2:
        return x, y, z

    adjusted = []
    for values, current_value, target_value in zip(
        (x, y, z),
        current_start_velocity,
        target_start_velocity,
    ):
        delta_v = float(target_value) - float(current_value)
        adjusted.append(apply_velocity_blend(values, t, delta_v, blend_seconds))
    return tuple(adjusted)


def apply_velocity_blend(values: np.ndarray, t: np.ndarray, delta_v: float, blend_seconds: float) -> np.ndarray:
    """应用三次修正，仅在起始附近改变轨迹导数。"""
    corrected = np.asarray(values, dtype=float).copy()
    mask = t <= blend_seconds
    tau = t[mask]
    correction = delta_v * ((tau ** 3) / (blend_seconds ** 2) - 2.0 * (tau ** 2) / blend_seconds + tau)
    corrected[mask] += correction
    return corrected


def apply_initial_attitude_constraints(
    t: np.ndarray,
    roll: np.ndarray,
    pitch: np.ndarray,
    yaw: np.ndarray,
    target_attitude: Tuple[float, float, float],
) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """将姿态角序列起始段平滑融合到目标初始角度。"""
    blend_seconds = min(10.0, max(float(t[-1]) * 0.1, 1.0))
    corrected_roll = apply_angle_blend(roll, t, float(target_attitude[0]), blend_seconds)
    corrected_pitch = apply_angle_blend(pitch, t, float(target_attitude[1]), blend_seconds)
    corrected_yaw = apply_angle_blend(yaw, t, float(target_attitude[2]), blend_seconds, circular=True)
    corrected_roll = np.clip(corrected_roll, -30, 30)
    corrected_pitch = np.clip(corrected_pitch, -20, 20)
    return corrected_roll, corrected_pitch, corrected_yaw


def apply_angle_blend(
    values: np.ndarray,
    t: np.ndarray,
    target_start: float,
    blend_seconds: float,
    circular: bool = False,
) -> np.ndarray:
    """平滑地将首个采样点修正到目标角度。"""
    corrected = np.asarray(values, dtype=float).copy()
    if blend_seconds <= 0 or len(t) < 2:
        corrected[0] = target_start
        return corrected

    current_start = float(corrected[0])
    delta = angular_difference(target_start, current_start) if circular else target_start - current_start
    mask = t <= blend_seconds
    u = t[mask] / blend_seconds
    correction = delta * (1.0 - 3.0 * (u ** 2) + 2.0 * (u ** 3))
    corrected[mask] += correction
    return corrected


def angular_difference(target: float, current: float) -> float:
    """返回角度差的最短路径值。"""
    return ((target - current + 180.0) % 360.0) - 180.0


def calculate_cumulative_distance(x: np.ndarray, y: np.ndarray, z: np.ndarray) -> np.ndarray:
    """计算沿轨迹的三维累计距离。"""
    distance = np.zeros_like(x, dtype=float)
    for index in range(1, len(x)):
        distance[index] = distance[index - 1] + math.sqrt(
            (x[index] - x[index - 1]) ** 2
            + (y[index] - y[index - 1]) ** 2
            + (z[index] - z[index - 1]) ** 2
        )
    return distance


def xy_to_geo(
    x: np.ndarray,
    y: np.ndarray,
    reference_lon: float,
    reference_lat: float,
    mean_lat: float,
) -> Tuple[np.ndarray, np.ndarray]:
    """将局部平面坐标 x/y 反算为经纬度。"""
    lon_to_meter = 111320.0 * np.cos(np.radians(mean_lat))
    lat_to_meter = 110540.0
    lon = x / lon_to_meter + reference_lon
    lat = y / lat_to_meter + reference_lat
    return lon, lat


def compute_trajectory_point_count(flight_seconds: float, sample_rate_hz: float) -> int:
    """为内部轨迹生成选择稳定的采样点数。"""
    capped_rate = min(max(sample_rate_hz, 1.0), 5.0)
    return max(721, min(20001, int(math.ceil(flight_seconds * capped_rate)) + 1))


def dataframe_preview(df: pd.DataFrame, rows: int) -> Sequence[Dict[str, Any]]:
    """返回适合 JSON 序列化的数据预览。"""
    if rows <= 0:
        return []
    preview_df = df.head(rows).copy()
    preview_df = preview_df.replace({np.nan: None})
    return normalize_mapping(preview_df.to_dict(orient="records"))


def build_target_summary(
    name: str,
    lon: np.ndarray,
    lat: np.ndarray,
    z: np.ndarray,
    active_info: Optional[Mapping[str, Any]] = None,
    label: Optional[str] = None,
    target_id: Optional[int] = None,
) -> Dict[str, Any]:
    """为单个生成目标构建摘要信息。"""
    summary = {
        "name": name,
        "start": {"lon": float(lon[0]), "lat": float(lat[0]), "alt": float(z[0])},
        "end": {"lon": float(lon[-1]), "lat": float(lat[-1]), "alt": float(z[-1])},
    }
    if target_id is not None:
        summary["id"] = int(target_id)
    if label:
        summary["label"] = str(label)
    if active_info:
        summary["active_info"] = normalize_mapping(active_info)
    return summary


def build_target_collection(targets: Mapping[str, Mapping[str, Any]]) -> Sequence[Dict[str, Any]]:
    """为多个生成目标构建可序列化摘要列表。"""
    return [
        build_target_summary(
            name,
            target["lon"],
            target["lat"],
            target["z"],
            target.get("active_info"),
            target.get("label"),
            target.get("id"),
        )
        for name, target in targets.items()
    ]


def normalize_mapping(value: Any) -> Any:
    """将包含 numpy 类型的数据转换为普通 Python 值。"""
    if isinstance(value, Mapping):
        return {str(key): normalize_mapping(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [normalize_mapping(item) for item in value]
    if isinstance(value, np.generic):
        return value.item()
    return value


def vector_to_dict(values: Tuple[float, float, float], names: Tuple[str, str, str]) -> Dict[str, float]:
    """将三元组转换为带字段名的字典。"""
    return {
        names[0]: float(values[0]),
        names[1]: float(values[1]),
        names[2]: float(values[2]),
    }


def ensure_instance(value: Any, expected_type: type, field_name: str) -> Any:
    """校验解析后的请求段是否为期望类型。"""
    if not isinstance(value, expected_type):
        raise ValidationError(
            f"{field_name} must be an instance of {expected_type.__name__}."
        )
    return value


def coerce_datetime(value: Any, field_name: str) -> datetime:
    """将时间值转换为 datetime，支持 datetime 对象或标准字符串。"""
    if isinstance(value, datetime):
        return value
    try:
        return parse_datetime(value)
    except ValidationError as exc:
        raise ValidationError(f"{field_name}: {exc}") from exc


def parse_boolean(value: Any, field_name: str) -> bool:
    """将请求中的常见布尔表示统一转换为 bool。"""
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, np.integer)):
        return bool(int(value))
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "yes", "y", "on", "是"}:
            return True
        if normalized in {"0", "false", "no", "n", "off", "否"}:
            return False
    raise ValidationError(f"{field_name} must be a boolean value.")


def get_optional(payload: Mapping[str, Any], *aliases: str) -> Any:
    """返回首个字段名对应的值；若不存在则返回 None。"""
    for alias in aliases:
        if alias in payload:
            return payload[alias]
    return None


def get_optional_mapping(payload: Mapping[str, Any], *aliases: str) -> Optional[Mapping[str, Any]]:
    """返回可选的嵌套对象字段。"""
    value = get_optional(payload, *aliases)
    if value is None:
        return None
    if not isinstance(value, Mapping):
        field_name = aliases[0] if aliases else "<unknown>"
        raise ValidationError(f"{field_name} 必须是对象。")
    return value


def get_required_mapping(payload: Mapping[str, Any], *aliases: str) -> Mapping[str, Any]:
    """返回必填的嵌套对象字段。"""
    value = get_required(payload, *aliases)
    if not isinstance(value, Mapping):
        field_name = aliases[0] if aliases else "<unknown>"
        raise ValidationError(f"{field_name} 必须是对象。")
    return value


def parse_coordinate_triplet(value: Any, field_name: str) -> Tuple[float, float, float]:
    """从数组或对象中解析三维坐标。"""
    if isinstance(value, Sequence) and not isinstance(value, (str, bytes, bytearray, Mapping)):
        if len(value) != 3:
            raise ValidationError(f"{field_name} must contain exactly 3 values.")
        return tuple(float(item) for item in value)

    if isinstance(value, Mapping):
        key_groups = (
            (("x", "y", "z"), ("X坐标", "Y坐标", "Z坐标")),
            (("lon", "lat", "alt"), ("经度", "纬度", "高度")),
        )
        for english_keys, chinese_keys in key_groups:
            if all(key in value for key in english_keys):
                return tuple(float(value[key]) for key in english_keys)
            if all(key in value for key in chinese_keys):
                return tuple(float(value[key]) for key in chinese_keys)

    raise ValidationError(
        f"{field_name} must be a 3-item array or object with x/y/z or lon/lat/alt keys."
    )


def parse_datetime(value: Any) -> datetime:
    """解析日期时间字符串，支持带毫秒与不带毫秒格式。"""
    if not isinstance(value, str):
        raise ValidationError("Datetime values must be strings.")
    normalized_value = value.strip()
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(normalized_value, fmt)
        except ValueError:
            continue
    java_style_match = JAVA_STYLE_DATETIME_PATTERN.fullmatch(normalized_value)
    if java_style_match:
        month = ENGLISH_MONTH_ABBREVIATIONS.get(java_style_match.group("month").lower())
        if month is None:
            raise ValidationError(f"Unsupported month abbreviation in datetime: {normalized_value}")
        try:
            parsed_value = datetime(
                year=int(java_style_match.group("year")),
                month=month,
                day=int(java_style_match.group("day")),
                hour=int(java_style_match.group("hour")),
                minute=int(java_style_match.group("minute")),
                second=int(java_style_match.group("second")),
            )
        except ValueError as exc:
            raise ValidationError(f"Invalid datetime value: {normalized_value}") from exc

        weekday = java_style_match.group("weekday").lower()
        expected_weekday = ENGLISH_WEEKDAY_ABBREVIATIONS[parsed_value.weekday()]
        if weekday != expected_weekday:
            raise ValidationError(
                f"Weekday '{java_style_match.group('weekday')}' does not match date '{normalized_value}'."
            )
        return parsed_value
    raise ValidationError(
        "Datetime must use 'YYYY-MM-DD HH:MM:SS', 'YYYY-MM-DD HH:MM:SS.mmm', "
        "or 'Thu Apr 16 16:46:51 CST 2026' format."
    )


def format_datetime(value: datetime) -> str:
    """将日期时间格式化为毫秒精度字符串。"""
    return value.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def get_required(payload: Mapping[str, Any], *aliases: str) -> Any:
    """返回首个字段名对应的值；若不存在则抛出校验异常。"""
    for alias in aliases:
        if alias in payload:
            return payload[alias]
    required_name = aliases[0] if aliases else "<unknown>"
    raise ValidationError(f"缺少必填字段: {required_name}")


def parse_triplet(
    value: Any,
    english_keys: Tuple[str, str, str],
    chinese_keys: Tuple[str, str, str],
    field_name: str,
) -> Tuple[float, float, float]:
    """从列表或字典中解析三维向量。"""
    if isinstance(value, Sequence) and not isinstance(value, (str, bytes, bytearray, Mapping)):
        if len(value) != 3:
            raise ValidationError(f"{field_name} must contain exactly 3 values.")
        return tuple(float(item) for item in value)

    if isinstance(value, Mapping):
        if all(key in value for key in english_keys):
            return tuple(float(value[key]) for key in english_keys)
        if all(key in value for key in chinese_keys):
            return tuple(float(value[key]) for key in chinese_keys)

    raise ValidationError(
        f"{field_name} must be a 3-item array or object with keys {english_keys}."
    )


def build_request_id() -> str:
    """生成适合文件系统使用的请求标识。"""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return f"sim_{timestamp}_{uuid.uuid4().hex[:8]}"


def sanitize_request_id(value: str) -> str:
    """清洗用户传入的请求标识，确保可用于文件系统路径。"""
    cleaned = re.sub(r"[^A-Za-z0-9_-]+", "_", str(value)).strip("_")
    if not cleaned:
        raise ValidationError("request_id must contain at least one valid character.")
    return cleaned
