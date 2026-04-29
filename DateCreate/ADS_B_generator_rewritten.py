"""
友机 ADS-B 报文生成。
"""

from __future__ import annotations

from datetime import datetime, timedelta
from functools import lru_cache
import os
from pathlib import Path
from typing import Any, Dict, List, Mapping, Optional, Tuple
import xml.etree.ElementTree as ET

import numpy as np
import pandas as pd
from scipy.interpolate import interp1d


MODULE_DIR = Path(__file__).resolve().parent
ICD_PATH = MODULE_DIR / "icd.xml"
UINT16_MASK = 0xFFFF
INT32_MIN = -(2 ** 31)
INT32_MAX = (2 ** 31) - 1

DEFAULT_MESSAGE_CONFIG = {
    "message_id": 0x8298,
    "frame_type": 0xAE48,
    "destination_id": 0x82,
    "source_id": 0x98,
    "main_type": 0xAE,
    "sub_type": 0x48,
    "response_required": False,
    "data_type": 0x00,
    "lon_lat_valid": True,
    "altitude_valid": True,
}


@lru_cache(maxsize=1)
def load_icd_definition() -> Dict[str, Any]:
    """读取 icd.xml，作为报文编码的单一来源。"""
    root = ET.parse(ICD_PATH).getroot()
    struct_node = root.find("./struct")
    if struct_node is None:
        raise ValueError(f"Invalid ICD file, missing <struct>: {ICD_PATH}")

    fields: Dict[str, Dict[str, Any]] = {}
    for field_node in struct_node.findall("./field"):
        name = str(field_node.attrib["name"])
        fields[name] = {
            "name": name,
            "type": str(field_node.attrib["type"]),
            "offset": int(field_node.attrib["offset"]),
            "size": int(field_node.attrib["size"]),
            "fixed_value": parse_fixed_value(field_node.attrib.get("fixed_value")),
        }

    return {
        "byte_order": str(root.attrib.get("byte_order", "big_endian")),
        "size": int(struct_node.attrib["size"]),
        "fields": fields,
    }


def parse_fixed_value(value: Optional[str]) -> Optional[int]:
    """解析 ICD 中的 fixed_value。"""
    if value is None or value == "":
        return None
    return int(value, 16) if str(value).lower().startswith("0x") else int(value)


def get_field_definition(field_name: str) -> Dict[str, Any]:
    """返回指定字段的 ICD 定义。"""
    definition = load_icd_definition()
    try:
        return definition["fields"][field_name]
    except KeyError as exc:  # pragma: no cover - defensive guard
        raise KeyError(f"Unknown ICD field: {field_name}") from exc


def get_fixed_field_value(field_name: str) -> int:
    """读取 ICD 中声明的固定字段值。"""
    field = get_field_definition(field_name)
    if field["fixed_value"] is None:
        raise ValueError(f"Field {field_name} does not define a fixed value.")
    return int(field["fixed_value"])


# ==================== 时间与采样工具 ====================
def parse_flight_start_datetime(value: str) -> datetime:
    """解析飞行开始时间，支持带毫秒与不带毫秒两种格式。"""
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(value, fmt)
        except ValueError:
            continue
    raise ValueError(f"Unsupported flight start datetime format: {value}")


def format_clock_time(start_datetime: datetime, elapsed_seconds: float) -> str:
    """将采样时刻格式化为带毫秒的时分秒字符串。"""
    current_time = start_datetime + timedelta(seconds=float(elapsed_seconds))
    return current_time.strftime("%H:%M:%S.%f")[:-3]


def format_full_datetime(start_datetime: datetime, elapsed_seconds: float) -> str:
    """将采样时刻格式化为完整日期时间字符串。"""
    current_time = start_datetime + timedelta(seconds=float(elapsed_seconds))
    return current_time.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def build_sample_times(start_minute: float, end_minute: float, sample_rate: float) -> np.ndarray:
    """构建报文导出时使用的采样时间序列。"""
    start_time = start_minute * 60
    end_time = end_minute * 60
    sample_interval = 1.0 / sample_rate
    return np.round(np.arange(start_time, end_time + sample_interval, sample_interval), 9)


def crc16_ccitt_false(data: bytes) -> int:
    """计算标准 CRC-16/CCITT-FALSE 校验值。"""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = ((crc << 1) ^ 0x1021) & UINT16_MASK
            else:
                crc = (crc << 1) & UINT16_MASK
    return crc


def normalize_message_config(message_config: Optional[Mapping[str, Any]] = None) -> Dict[str, Any]:
    """将用户配置与默认报文配置合并，并兼容组合字段写法。"""
    merged = DEFAULT_MESSAGE_CONFIG.copy()
    overrides = dict(message_config or {})
    merged.update(overrides)

    if "message_id" in overrides and "destination_id" not in overrides and "source_id" not in overrides:
        message_id = int(overrides["message_id"]) & UINT16_MASK
        merged["destination_id"] = (message_id >> 8) & 0xFF
        merged["source_id"] = message_id & 0xFF

    if "frame_type" in overrides and "main_type" not in overrides and "sub_type" not in overrides:
        frame_type = int(overrides["frame_type"]) & UINT16_MASK
        merged["main_type"] = (frame_type >> 8) & 0xFF
        merged["sub_type"] = frame_type & 0xFF

    merged["destination_id"] = int(merged["destination_id"]) & 0xFF
    merged["source_id"] = int(merged["source_id"]) & 0xFF
    merged["main_type"] = int(merged["main_type"]) & 0xFF
    merged["sub_type"] = int(merged["sub_type"]) & 0xFF
    merged["response_required"] = normalize_boolean(merged.get("response_required", False))
    merged["lon_lat_valid"] = normalize_boolean(merged.get("lon_lat_valid", True))
    merged["altitude_valid"] = normalize_boolean(merged.get("altitude_valid", True))
    merged["data_type"] = int(merged.get("data_type", 0)) & 0xFF
    return merged


def normalize_boolean(value: Any) -> bool:
    """将多种常见输入归一化为布尔值。"""
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, np.integer)):
        return bool(int(value))
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"1", "true", "yes", "y", "是", "有效"}:
            return True
        if normalized in {"0", "false", "no", "n", "否", "无效"}:
            return False
    return bool(value)


def clip_int32(value: float) -> int:
    """将数值裁剪到 int32 可表示范围。"""
    rounded = int(round(float(value)))
    return max(INT32_MIN, min(INT32_MAX, rounded))


def longitude_to_raw_i32(value_deg: float) -> int:
    """按 ICD 将经度编码为有符号 int32。"""
    bounded = max(-180.0, min(180.0, float(value_deg)))
    scaled = (bounded / 180.0) * (2 ** 31)
    return clip_int32(scaled)


def latitude_to_raw_i32(value_deg: float) -> int:
    """按 ICD 将纬度编码为有符号 int32。"""
    bounded = max(-90.0, min(90.0, float(value_deg)))
    scaled = (bounded / 180.0) * (2 ** 31)
    return clip_int32(scaled)


def altitude_to_raw_i32(value_m: float) -> int:
    """按 ICD 将高度编码为有符号 int32。"""
    return clip_int32(value_m)


def to_hex_string(value: int, width: int) -> str:
    """将整数格式化为固定宽度十六进制。"""
    return f"0x{value:0{width}X}"


def to_signed_hex_string(value: int, width: int) -> str:
    """将有符号整数以补码形式格式化为十六进制。"""
    bit_width = width * 4
    mask = (1 << bit_width) - 1
    return f"0x{value & mask:0{width}X}"


def get_byte_order() -> str:
    """从 ICD 中读取字节序。"""
    byte_order = str(load_icd_definition()["byte_order"]).lower()
    return "big" if byte_order == "big_endian" else "little"


def build_msg_length(config: Mapping[str, Any]) -> int:
    """根据 ICD 结构自动构造消息长度字段。"""
    definition = load_icd_definition()
    excluded = {"frame_header", "crc_check", "frame_tail"}
    body_length = sum(
        field["size"]
        for name, field in definition["fields"].items()
        if name not in excluded
    )
    response_flag = 0 if bool(config["response_required"]) else 1
    return ((response_flag & 0x1) << 15) | (body_length & 0x7FFF)


def build_message_id(config: Mapping[str, Any]) -> int:
    """将目的 ID 与源 ID 组合为消息 ID。"""
    return ((int(config["destination_id"]) & 0xFF) << 8) | (int(config["source_id"]) & 0xFF)


def build_frame_type(config: Mapping[str, Any]) -> int:
    """将主类型与子类型组合为帧类型。"""
    return ((int(config["main_type"]) & 0xFF) << 8) | (int(config["sub_type"]) & 0xFF)


def build_data_type(config: Mapping[str, Any]) -> int:
    """按 ICD 位定义构造数据类型字段。"""
    value = int(config.get("data_type", 0)) & 0xFC
    if bool(config["altitude_valid"]):
        value |= 0x02
    if bool(config["lon_lat_valid"]):
        value |= 0x01
    return value & 0xFF


def pack_field_value(field_name: str, value: int) -> bytes:
    """按 ICD 字段类型打包单个字段。"""
    field = get_field_definition(field_name)
    signed = str(field["type"]).startswith("int")
    return int(value).to_bytes(field["size"], get_byte_order(), signed=signed)


def build_crc_bytes(field_values: Mapping[str, int]) -> bytes:
    """按 ICD 规定构造 CRC 计算范围。"""
    definition = load_icd_definition()
    included_fields = [
        field
        for field in definition["fields"].values()
        if field["name"] not in {"frame_header", "crc_check", "frame_tail"}
    ]
    included_fields.sort(key=lambda item: int(item["offset"]))
    return b"".join(pack_field_value(field["name"], int(field_values[field["name"]])) for field in included_fields)


def build_packet_bytes(field_values: Mapping[str, int]) -> bytes:
    """按 ICD 字段偏移生成完整报文字节串。"""
    definition = load_icd_definition()
    packet = bytearray(int(definition["size"]))
    for field in definition["fields"].values():
        name = str(field["name"])
        offset = int(field["offset"])
        size = int(field["size"])
        packet[offset: offset + size] = pack_field_value(name, int(field_values[name]))
    return bytes(packet)


def encode_message(lon_value: float, lat_value: float, alt_value: float, config: Mapping[str, Any]) -> Tuple[Dict[str, int], bytes]:
    """将一条轨迹采样编码为 ICD 定义的二进制报文。"""
    field_values = {
        "frame_header": get_fixed_field_value("frame_header"),
        "msg_length": build_msg_length(config),
        "msg_id": build_message_id(config),
        "frame_type": build_frame_type(config),
        "data_type": build_data_type(config),
        "platform_longitude": longitude_to_raw_i32(lon_value),
        "platform_latitude": latitude_to_raw_i32(lat_value),
        "platform_altitude": altitude_to_raw_i32(alt_value),
        "crc_check": 0,
        "frame_tail": get_fixed_field_value("frame_tail"),
    }
    field_values["crc_check"] = crc16_ccitt_false(build_crc_bytes(field_values))
    return field_values, build_packet_bytes(field_values)


# ==================== 报文筛选与生成 ====================
def select_rows(df: pd.DataFrame, selected_times: Any) -> pd.DataFrame:
    """
    按配置的发送时刻筛选报文行。

    支持的形式：
    - "all" / None：保留全部行
    - 秒数列表，例如 [24.0, 25.0]
    - 时分秒字符串列表，例如 ["13:50:24.000"]
    - 完整日期时间列表，例如 ["2026-03-24 13:50:24.000"]
    """
    if selected_times is None or selected_times == "all":
        return df

    if isinstance(selected_times, (int, float, str)):
        selected_times = [selected_times]

    values = list(selected_times)
    if not values:
        return df.iloc[0:0].copy()

    if all(isinstance(item, (int, float, np.integer, np.floating)) for item in values):
        numeric_values = np.asarray(values, dtype=float)
        mask = np.isclose(
            df["_elapsed_seconds"].to_numpy()[:, None],
            numeric_values[None, :],
            atol=1e-6,
            rtol=0.0,
        ).any(axis=1)
        return df.loc[mask].copy()

    string_values = {str(item) for item in values}
    mask = df["时间戳(HH:MM:SS.mmm)"].isin(string_values) | df["_full_datetime"].isin(string_values)
    return df.loc[mask].copy()


def generate_wingman_message_dataframe(
    t: np.ndarray,
    wingman_lon: np.ndarray,
    wingman_lat: np.ndarray,
    wingman_z: np.ndarray,
    start_minute: float,
    end_minute: float,
    sample_rate: float = 1.0,
    flight_start_datetime: str = "2026-03-24 13:50:00.000",
    selected_times: Any = "all",
    source_id: Optional[int] = None,
    message_config: Optional[Mapping[str, Any]] = None,
) -> pd.DataFrame:
    """生成友机到主机的报文表格，并附带完整十六进制报文。"""
    start_datetime = parse_flight_start_datetime(flight_start_datetime)
    sample_times = build_sample_times(start_minute, end_minute, sample_rate)
    config = normalize_message_config(message_config)
    if source_id is not None:
        config["source_id"] = int(source_id) & 0xFF

    interp_lon = interp1d(t, wingman_lon, kind="linear", fill_value="extrapolate")
    interp_lat = interp1d(t, wingman_lat, kind="linear", fill_value="extrapolate")
    interp_z = interp1d(t, wingman_z, kind="linear", fill_value="extrapolate")

    sample_lon = interp_lon(sample_times)
    sample_lat = interp_lat(sample_times)
    sample_z = interp_z(sample_times)

    rows: List[Dict[str, Any]] = []
    for elapsed_seconds, lon_value, lat_value, alt_value in zip(sample_times, sample_lon, sample_lat, sample_z):
        field_values, packet_bytes = encode_message(float(lon_value), float(lat_value), float(alt_value), config)
        row = {
            "_elapsed_seconds": float(elapsed_seconds),
            "_full_datetime": format_full_datetime(start_datetime, elapsed_seconds),
            "时间戳(HH:MM:SS.mmm)": format_clock_time(start_datetime, elapsed_seconds),
            "帧头": to_hex_string(field_values["frame_header"] & UINT16_MASK, 4),
            "消息长度": to_hex_string(field_values["msg_length"] & UINT16_MASK, 4),
            "消息ID": to_hex_string(field_values["msg_id"] & UINT16_MASK, 4),
            "帧类型": to_hex_string(field_values["frame_type"] & UINT16_MASK, 4),
            "数据类型": to_hex_string(field_values["data_type"] & 0xFF, 2),
            "平台经度（原始值）": to_signed_hex_string(field_values["platform_longitude"], 8),
            "平台纬度（原始值）": to_signed_hex_string(field_values["platform_latitude"], 8),
            "平台高度（原始值）": to_signed_hex_string(field_values["platform_altitude"], 8),
            "目的ID": int(config["destination_id"]),
            "源ID": int(config["source_id"]),
            "主类型": f"{int(config['main_type']) & 0xFF:02X}",
            "子类型": f"{int(config['sub_type']) & 0xFF:02X}",
            "CRC校验": to_hex_string(field_values["crc_check"] & UINT16_MASK, 4),
            "帧尾": to_hex_string(field_values["frame_tail"] & UINT16_MASK, 4),
            "平台经度(度)": round(float(lon_value), 6),
            "平台纬度(度)": round(float(lat_value), 6),
            "平台高度(m)": round(float(alt_value), 3),
            "经纬度有效": "是" if bool(config["lon_lat_valid"]) else "否",
            "高度有效": "是" if bool(config["altitude_valid"]) else "否",
            "十六进制报文": packet_bytes.hex().upper(),
        }
        rows.append(row)

    df = pd.DataFrame(rows)
    df = select_rows(df, selected_times)
    return df.drop(columns=["_elapsed_seconds", "_full_datetime"])


# ==================== 报文导出接口 ====================
def save_wingman_message_csv(
    t: np.ndarray,
    wingman_lon: np.ndarray,
    wingman_lat: np.ndarray,
    wingman_z: np.ndarray,
    start_minute: float,
    end_minute: float,
    sample_rate: float = 1.0,
    filename: Optional[str] = None,
    flight_start_datetime: str = "2026-03-24 13:50:00.000",
    output_directory: str = "csv_output",
    selected_times: Any = "all",
    source_id: Optional[int] = None,
    message_config: Optional[Mapping[str, Any]] = None,
) -> Tuple[pd.DataFrame, str]:
    """生成并保存友机到主机的报文 CSV。"""
    df = generate_wingman_message_dataframe(
        t=t,
        wingman_lon=wingman_lon,
        wingman_lat=wingman_lat,
        wingman_z=wingman_z,
        start_minute=start_minute,
        end_minute=end_minute,
        sample_rate=sample_rate,
        flight_start_datetime=flight_start_datetime,
        selected_times=selected_times,
        source_id=source_id,
        message_config=message_config,
    )

    if filename is None:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = (
            f"ADS_B_{start_minute}min_to_"
            f"{end_minute}min_{sample_rate}Hz_{timestamp}.csv"
        )

    if output_directory:
        os.makedirs(output_directory, exist_ok=True)
        if not os.path.isabs(filename) and not os.path.dirname(filename):
            filename = os.path.join(output_directory, filename)

    df.to_csv(filename, index=False, encoding="utf-8-sig")
    print("\n友机报文 CSV 已保存：")
    print(f"  记录数: {len(df)}")
    print(f"  文件: {os.path.abspath(filename)}")
    return df, filename
