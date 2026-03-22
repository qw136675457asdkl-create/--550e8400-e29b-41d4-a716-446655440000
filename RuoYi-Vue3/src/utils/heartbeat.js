import { heartbeat } from '@/api/login'

let heartbeatTimer = null

const HEARTBEAT_INTERVAL = 30 * 1000

export function startHeartbeat() {
  if (heartbeatTimer) {
    return
  }

  heartbeatTimer = setInterval(() => {
    heartbeat().catch(() => {
      stopHeartbeat()
    })
  }, HEARTBEAT_INTERVAL)
}

export function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}