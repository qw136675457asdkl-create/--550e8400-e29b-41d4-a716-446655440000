import request from '@/utils/request'

// 获取服务器当前时间（统一时间源）
export function getServerTime() {
  return request({
    url: '/system/time/now',
    method: 'get'
  })
}
