"""
仿真并行任务管理器。
"""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from datetime import datetime
import copy
import os
import threading
import uuid
from typing import Any, Dict, List, Optional

from simulation_service import (
    StructuredRequestParams,
    ValidationError,
    build_progress_payload,
    run_simulation,
)


def _current_time_text() -> str:
    """返回带毫秒的当前时间文本。"""
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


@dataclass
class SimulationTaskState:
    """单个仿真任务的运行状态。"""

    task_id: str
    request_id: str
    status: str = "queued"
    progress: Dict[str, Any] = field(
        default_factory=lambda: build_progress_payload(0, "queued", "任务已进入队列")
    )
    created_at: str = field(default_factory=_current_time_text)
    updated_at: str = field(default_factory=_current_time_text)
    started_at: Optional[str] = None
    completed_at: Optional[str] = None
    result: Optional[Dict[str, Any]] = None
    error: Optional[str] = None
    detail: Optional[str] = None


class SimulationTaskManager:
    """负责调度并行仿真任务并维护进度。"""

    def __init__(self, max_workers: Optional[int] = None):
        self.max_workers = self._resolve_max_workers(max_workers)
        self._executor = ThreadPoolExecutor(
            max_workers=self.max_workers,
            thread_name_prefix="simulation_task",
        )
        self._tasks: Dict[str, SimulationTaskState] = {}
        self._lock = threading.Lock()

    # ==================== 任务池配置 ====================
    def _resolve_max_workers(self, max_workers: Optional[int]) -> int:
        if max_workers is not None:
            return max(1, int(max_workers))

        env_value = os.environ.get("SIMULATION_MAX_WORKERS")
        if env_value:
            try:
                return max(1, int(env_value))
            except ValueError:
                pass

        cpu_count = os.cpu_count() or 1
        return max(2, min(8, cpu_count))

    # ==================== 任务创建与查询 ====================
    def submit(self, config: StructuredRequestParams) -> Dict[str, Any]:
        """提交一个后台仿真任务。"""
        task_id = f"task_{uuid.uuid4().hex[:12]}"
        state = SimulationTaskState(task_id=task_id, request_id=config.request_id)

        with self._lock:
            self._tasks[task_id] = state

        self._executor.submit(self._run_task, task_id, config)
        return self.get_task(task_id)

    def get_task(self, task_id: str) -> Optional[Dict[str, Any]]:
        """返回指定任务的当前状态快照。"""
        with self._lock:
            state = self._tasks.get(task_id)
            if state is None:
                return None
            return self._snapshot_locked(state)

    def list_tasks(self) -> List[Dict[str, Any]]:
        """返回当前所有任务的状态列表。"""
        with self._lock:
            states = sorted(
                self._tasks.values(),
                key=lambda item: item.created_at,
                reverse=True,
            )
            return [self._snapshot_locked(state) for state in states]

    # ==================== 任务执行与进度维护 ====================
    def _run_task(self, task_id: str, config: StructuredRequestParams) -> None:
        self._mark_running(task_id)

        try:
            result = run_simulation(
                config,
                progress_callback=lambda percent, stage, message: self._update_progress(
                    task_id,
                    percent,
                    stage,
                    message,
                ),
            )
        except ValidationError as exc:
            self._mark_failed(task_id, str(exc), detail=str(exc))
            return
        except Exception as exc:
            self._mark_failed(task_id, "Simulation failed.", detail=str(exc))
            return

        self._mark_completed(task_id, result)

    def _mark_running(self, task_id: str) -> None:
        with self._lock:
            state = self._tasks.get(task_id)
            if state is None:
                return
            now_text = _current_time_text()
            state.status = "running"
            state.started_at = now_text
            state.updated_at = now_text
            state.progress = build_progress_payload(1, "running", "任务已开始处理")

    def _update_progress(self, task_id: str, percent: int, stage: str, message: str) -> None:
        with self._lock:
            state = self._tasks.get(task_id)
            if state is None or state.status in {"completed", "failed"}:
                return
            state.status = "running"
            state.updated_at = _current_time_text()
            state.progress = build_progress_payload(percent, stage, message)

    def _mark_completed(self, task_id: str, result: Dict[str, Any]) -> None:
        with self._lock:
            state = self._tasks.get(task_id)
            if state is None:
                return
            now_text = _current_time_text()
            state.status = "completed"
            state.updated_at = now_text
            state.completed_at = now_text
            state.progress = build_progress_payload(100, "completed", "任务处理完成")
            state.result = result
            state.error = None
            state.detail = None

    def _mark_failed(self, task_id: str, error: str, detail: Optional[str] = None) -> None:
        with self._lock:
            state = self._tasks.get(task_id)
            if state is None:
                return
            now_text = _current_time_text()
            failed_percent = int(state.progress.get("percent", 0))
            state.status = "failed"
            state.updated_at = now_text
            state.completed_at = now_text
            state.progress = build_progress_payload(failed_percent, "failed", "任务处理失败")
            state.result = None
            state.error = str(error)
            state.detail = str(detail) if detail is not None else None

    # ==================== 序列化输出 ====================
    def _snapshot_locked(self, state: SimulationTaskState) -> Dict[str, Any]:
        payload: Dict[str, Any] = {
            "task_id": state.task_id,
            "request_id": state.request_id,
            "status": state.status,
            "progress": copy.deepcopy(state.progress),
            "created_at": state.created_at,
            "updated_at": state.updated_at,
            "started_at": state.started_at,
            "completed_at": state.completed_at,
        }
        if state.result is not None:
            payload["result"] = copy.deepcopy(state.result)
        if state.error is not None:
            payload["error"] = state.error
        if state.detail is not None:
            payload["detail"] = state.detail
        return payload


# ==================== 全局任务管理器 ====================
task_manager = SimulationTaskManager()
