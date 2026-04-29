"""
Flask Web 服务接口，允许远程客户端通过 HTTP POST 请求调用仿真服务。
"""

from __future__ import annotations

import copy
import os
from typing import Any, Dict

from flask import Flask, jsonify, request

from simulation_service import ValidationError, parse_request
from simulation_task_manager import task_manager


# ==================== Flask 应用初始化 ====================
app = Flask(__name__)


def build_submit_response(task_snapshot: Dict[str, Any]) -> Dict[str, Any]:
    """构建提交任务后的精简响应。"""
    response = {
        "task_id": task_snapshot["task_id"],
        "request_id": task_snapshot.get("request_id"),
        "status": task_snapshot.get("status"),
        "progress": copy.deepcopy(task_snapshot.get("progress")),
        "status_url": f"/api/simulations/{task_snapshot['task_id']}",
    }
    if task_snapshot.get("error") is not None:
        response["error"] = task_snapshot["error"]
    if task_snapshot.get("detail") is not None:
        response["detail"] = task_snapshot["detail"]
    return response


def build_task_list_item(task_snapshot: Dict[str, Any]) -> Dict[str, Any]:
    """构建任务列表中的单项响应。"""
    item = {
        "task_id": task_snapshot["task_id"],
        "request_id": task_snapshot.get("request_id"),
        "status": task_snapshot.get("status"),
        "progress": copy.deepcopy(task_snapshot.get("progress")),
    }
    if task_snapshot.get("error") is not None:
        item["error"] = task_snapshot["error"]
    return item


def build_task_detail_response(task_snapshot: Dict[str, Any]) -> Dict[str, Any]:
    """构建单任务查询的精简响应。"""
    response = {
        "task_id": task_snapshot["task_id"],
        "request_id": task_snapshot.get("request_id"),
        "status": task_snapshot.get("status"),
        "progress": copy.deepcopy(task_snapshot.get("progress")),
    }

    result = task_snapshot.get("result") or {}
    files = copy.deepcopy(result.get("files") or {})
    generated_files_directory = result.get("generated_files_directory") or files.get("directory")

    if generated_files_directory is not None:
        response["generated_files_directory"] = generated_files_directory
    if files:
        response["files"] = files
    if task_snapshot.get("error") is not None:
        response["error"] = task_snapshot["error"]
    if task_snapshot.get("detail") is not None:
        response["detail"] = task_snapshot["detail"]
    return response


@app.get("/health")
def health() -> tuple:
    """健康检查接口。"""
    return (
        jsonify(
            {
                "status": "ok",
                "task_queue": {
                    "max_workers": task_manager.max_workers,
                    "task_count": len(task_manager.list_tasks()),
                },
            }
        ),
        200,
    )


# ==================== 仿真请求接口 ====================
@app.get("/api/simulations")
def list_simulations() -> tuple:
    """返回当前已提交任务的列表。"""
    tasks = [build_task_list_item(task) for task in task_manager.list_tasks()]
    return jsonify({"status": "success", "tasks": tasks}), 200


@app.post("/api/simulations")
def create_simulation() -> tuple:
    """从 JSON 请求体创建一个后台仿真任务。"""
    payload = request.get_json(silent=True)
    if payload is None:
        return jsonify({"status": "error", "error": "Request body must be valid JSON."}), 400

    try:
        config = parse_request(payload)
        task_snapshot = task_manager.submit(config)
    except ValidationError as exc:
        return jsonify({"status": "error", "error": str(exc)}), 400
    except Exception as exc:  # pragma: no cover - defensive API guard
        app.logger.exception("Simulation request failed")
        return jsonify({"status": "error", "error": "Simulation failed.", "detail": str(exc)}), 500

    response = build_submit_response(task_snapshot)
    response["message"] = "任务已提交，可通过状态接口轮询处理进度。"
    return jsonify(response), 202


@app.get("/api/simulations/<task_id>")
def get_simulation(task_id: str) -> tuple:
    """查询单个仿真任务的进度与结果。"""
    task_snapshot = task_manager.get_task(task_id)
    if task_snapshot is None:
        return jsonify({"status": "error", "error": f"Task not found: {task_id}"}), 404
    return jsonify(build_task_detail_response(task_snapshot)), 200


# ==================== 本地启动入口 ====================
if __name__ == "__main__":
    host = os.environ.get("SIMULATION_API_HOST", "0.0.0.0")
    port = int(os.environ.get("SIMULATION_API_PORT", "5000"))
    app.run(host=host, port=port, debug=False, threaded=True)
