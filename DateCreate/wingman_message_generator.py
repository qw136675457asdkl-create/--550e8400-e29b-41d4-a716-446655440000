"""
友机 ADS-B 报文生成
"""

from datetime import datetime, timedelta
import os

import numpy as np
import pandas as pd
from scipy.interpolate import interp1d


DEFAULT_MESSAGE_CONFIG = {
    "frame_header": 0x3F4E,
    "message_id": 0x8298,
    "data_type": 0x03,
    "destination_id": 82,
    "source_id": 98,
    "main_type": 0xAE,
    "sub_type": 0x48,
    "frame_tail": 0xB0B9,
    "lon_lat_valid": "是",
    "altitude_valid": "是",
}


# ==================== 时间与采样工具 ====================
def parse_flight_start_datetime(value):
    """解析飞行开始时间，支持带毫秒与不带毫秒两种格式。"""
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(value, fmt)
        except ValueError:
            continue
    raise ValueError(f"Unsupported flight start datetime format: {value}")


def format_clock_time(start_datetime, elapsed_seconds):
    """将采样时刻格式化为带毫秒的时分秒字符串。"""
    current_time = start_datetime + timedelta(seconds=float(elapsed_seconds))
    return current_time.strftime("%H:%M:%S.%f")[:-3]


def format_full_datetime(start_datetime, elapsed_seconds):
    """将采样时刻格式化为完整日期时间字符串。"""
    current_time = start_datetime + timedelta(seconds=float(elapsed_seconds))
    return current_time.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def build_sample_times(start_minute, end_minute, sample_rate):
    """构建报文导出时使用的采样时间序列。"""
    start_time = start_minute * 60
    end_time = end_minute * 60
    sample_interval = 1.0 / sample_rate
    return np.round(np.arange(start_time, end_time + sample_interval, sample_interval), 9)


def degrees_to_raw_u32(value_deg):
    """将经纬度角度值映射为 32 位原始整数。"""
    return int(round((float(value_deg) / 360.0) * (2 ** 32))) & 0xFFFFFFFF


def altitude_to_raw_u32(value_m):
    """将高度值映射为无符号 32 位原始整数。"""
    return int(round(float(value_m))) & 0xFFFFFFFF


def crc16_ccitt_false(data):
    """计算标准 CRC-16/CCITT-FALSE 校验值。"""
    crc = 0xFFFF
    for byte in data:
        crc ^= byte << 8
        for _ in range(8):
            if crc & 0x8000:
                crc = ((crc << 1) ^ 0x1021) & 0xFFFF
            else:
                crc = (crc << 1) & 0xFFFF
    return crc


def build_crc_payload(row_values, message_config):
    """构造用于 CRC 计算的字节序列。"""
    frame_type = ((int(message_config["main_type"]) & 0xFF) << 8) | (int(message_config["sub_type"]) & 0xFF)

    payload = bytearray()
    payload.extend(int(message_config["frame_header"]).to_bytes(2, "big"))
    payload.extend(frame_type.to_bytes(2, "big"))
    payload.extend(int(message_config["message_id"]).to_bytes(2, "big"))
    payload.extend(int(message_config["data_type"]).to_bytes(1, "big"))
    payload.extend(int(row_values["平台经度（原始值）"], 16).to_bytes(4, "big"))
    payload.extend(int(row_values["平台纬度（原始值）"], 16).to_bytes(4, "big"))
    payload.extend(int(row_values["平台高度（原始值）"], 16).to_bytes(4, "big"))
    payload.extend(int(message_config["destination_id"]).to_bytes(1, "big"))
    payload.extend(int(message_config["source_id"]).to_bytes(1, "big"))
    payload.extend(int(message_config["main_type"]).to_bytes(1, "big"))
    payload.extend(int(message_config["sub_type"]).to_bytes(1, "big"))
    payload.extend(int(message_config["frame_tail"]).to_bytes(2, "big"))
    return bytes(payload)


def normalize_message_config(message_config=None):
    """将用户配置与默认报文配置合并。"""
    merged = DEFAULT_MESSAGE_CONFIG.copy()
    if message_config:
        merged.update(message_config)
    return merged


# ==================== 报文筛选与生成 ====================
def select_rows(df, selected_times):
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
    t,
    wingman_lon,
    wingman_lat,
    wingman_z,
    start_minute,
    end_minute,
    sample_rate=1.0,
    flight_start_datetime="2026-03-24 13:50:00.000",
    selected_times="all",
    message_config=None,
):
    """生成友机到主机的报文表格。"""
    start_datetime = parse_flight_start_datetime(flight_start_datetime)
    sample_times = build_sample_times(start_minute, end_minute, sample_rate)
    config = normalize_message_config(message_config)

    interp_lon = interp1d(t, wingman_lon, kind="linear", fill_value="extrapolate")
    interp_lat = interp1d(t, wingman_lat, kind="linear", fill_value="extrapolate")
    interp_z = interp1d(t, wingman_z, kind="linear", fill_value="extrapolate")

    frame_type = ((int(config["main_type"]) & 0xFF) << 8) | (int(config["sub_type"]) & 0xFF)
    sample_lon = interp_lon(sample_times)
    sample_lat = interp_lat(sample_times)
    sample_z = interp_z(sample_times)

    rows = []
    for elapsed_seconds, lon_value, lat_value, alt_value in zip(sample_times, sample_lon, sample_lat, sample_z):
        raw_lon = degrees_to_raw_u32(lon_value)
        raw_lat = degrees_to_raw_u32(lat_value)
        raw_alt = altitude_to_raw_u32(alt_value)

        row = {
            "_elapsed_seconds": float(elapsed_seconds),
            "_full_datetime": format_full_datetime(start_datetime, elapsed_seconds),
            "时间戳(HH:MM:SS.mmm)": format_clock_time(start_datetime, elapsed_seconds),
            "帧头": f"0x{int(config['frame_header']) & 0xFFFF:04X}",
            "帧类型": f"0x{frame_type:04X}",
            "消息ID": f"0x{int(config['message_id']) & 0xFFFF:04X}",
            "数据类型": f"0x{int(config['data_type']) & 0xFF:02X}",
            "平台经度（原始值）": f"0x{raw_lon:08X}",
            "平台纬度（原始值）": f"0x{raw_lat:08X}",
            "平台高度（原始值）": f"0x{raw_alt:08X}",
            "目的ID": int(config["destination_id"]),
            "源ID": int(config["source_id"]),
            "主类型": f"{int(config['main_type']) & 0xFF:02X}",
            "副类型": f"{int(config['sub_type']) & 0xFF:02X}",
            "CRC校验": "",
            "帧尾": f"0x{int(config['frame_tail']) & 0xFFFF:04X}",
            "平台经度（°）": round(float(lon_value), 6),
            "平台纬度（°）": round(float(lat_value), 6),
            "平台高度（m）": round(float(alt_value), 3),
            "经纬度有效": config["lon_lat_valid"],
            "高度有效": config["altitude_valid"],
        }
        row["CRC校验"] = f"0x{crc16_ccitt_false(build_crc_payload(row, config)):04X}"
        rows.append(row)

    df = pd.DataFrame(rows)
    df = select_rows(df, selected_times)
    return df.drop(columns=["_elapsed_seconds", "_full_datetime"])


# ==================== 报文导出接口 ====================
def save_wingman_message_csv(
    t,
    wingman_lon,
    wingman_lat,
    wingman_z,
    start_minute,
    end_minute,
    sample_rate=1.0,
    filename=None,
    flight_start_datetime="2026-03-24 13:50:00.000",
    output_directory="csv_output",
    selected_times="all",
    message_config=None,
):
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
        message_config=message_config,
    )

    if filename is None:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = (
            f"wingman_to_host_messages_{start_minute}min_to_"
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
