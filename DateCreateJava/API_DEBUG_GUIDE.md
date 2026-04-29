# Java 联调说明

## 启动顺序

先启动 Python 仿真服务：

```powershell
d:\code\Date\DateCreate\.venv\Scripts\python.exe d:\code\Date\DateCreate\simulation_api.py
```

再启动 Java 后端：

```powershell
cd d:\code\Date\DateCreateJava
.\mvnw.cmd spring-boot:run
```

默认地址：

- Java 后端：`http://127.0.0.1:8080`
- Python 仿真服务：`http://127.0.0.1:5000`

## 调试用请求体

请求体文件：

- `d:\code\Date\DateCreate\payload_debug_async.json`

## Java 侧接口 URL

- 健康检查：`GET http://127.0.0.1:8080/health`
- 提交任务：`POST http://127.0.0.1:8080/api/simulations`
- 查询任务列表：`GET http://127.0.0.1:8080/api/simulations`
- 查询单个任务：`GET http://127.0.0.1:8080/api/simulations/{taskId}`

## PowerShell 示例

提交任务：

```powershell
$body = Get-Content 'd:\code\Date\DateCreate\payload_debug_async.json' -Raw
$submit = Invoke-RestMethod `
  -Uri 'http://127.0.0.1:8080/api/simulations' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body

$submit
```

查询单个任务：

```powershell
$taskId = $submit.task_id
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/simulations/$taskId" -Method Get
```

轮询直到完成：

```powershell
$taskId = $submit.task_id
do {
  Start-Sleep -Milliseconds 500
  $task = Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/simulations/$taskId" -Method Get
  $task.progress
} while ($task.status -eq 'queued' -or $task.status -eq 'running')

$task
$task.generated_files_directory
```

## curl 示例

提交任务：

```bash
curl.exe -X POST "http://127.0.0.1:8080/api/simulations" ^
  -H "Content-Type: application/json" ^
  --data-binary "@d:\code\Date\DateCreate\payload_debug_async.json"
```

查询任务：

```bash
curl.exe "http://127.0.0.1:8080/api/simulations/task_xxx"
```

## 前端 fetch 示例

```javascript
const payload = {
  request_id: "frontend_debug_demo",
  start_coords: { lon: 120.0, lat: 30.0, alt: 1000 },
  end_coords: { lon: 121.0, lat: 31.0, alt: 2000 },
  start_velocity: { vx: 10, vy: 0, vz: 0 },
  start_attitude: { roll: 0, pitch: 0, yaw: 0 },
  flight_start_datetime: "2026-03-30 10:00:00",
  flight_end_datetime: "2026-03-30 10:10:00",
  sample_rate_hz: 2,
  num: 7,
  host_trajectory_type: "cubic",
  output_directory: "csv_output/api_requests"
};

const submitResp = await fetch("http://127.0.0.1:8080/api/simulations", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(payload)
});

const submitData = await submitResp.json();
const taskId = submitData.task_id;

let taskData = submitData;
while (taskData.status === "queued" || taskData.status === "running") {
  await new Promise((resolve) => setTimeout(resolve, 500));
  const taskResp = await fetch(`http://127.0.0.1:8080/api/simulations/${taskId}`);
  taskData = await taskResp.json();
  console.log(taskData.progress);
}

console.log(taskData.generated_files_directory);
console.log(taskData.files.flight_csv);
console.log(taskData.files.wingman_message_csv);
```

## 关键返回字段

提交任务后会先返回：

- `task_id`
- `request_id`
- `status`
- `progress.percent`
- `progress.stage`
- `progress.message`
- `status_url`

查询任务列表时，每个任务项重点看：

- `task_id`
- `request_id`
- `status`
- `progress.percent`
- `progress.stage`
- `progress.message`

查询单个任务时，重点看：

- `task_id`
- `request_id`
- `status`
- `progress`
- `generated_files_directory`
- `files.directory`
- `files.flight_csv`
- `files.wingman_message_csv`

## 常见状态

- `queued`：已进入队列，等待执行
- `running`：正在处理
- `completed`：处理完成
- `failed`：处理失败

## 备注

- Java 现在是代理 Python HTTP 接口，不再直接起本地 Python 子进程。
- 如果 Java 调用失败，先检查 Python 服务是否已经启动，并确认 `http://127.0.0.1:5000/health` 可访问。
