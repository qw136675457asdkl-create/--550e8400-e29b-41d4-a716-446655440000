import request from '@/utils/request'

// 查询系统日志文件列表
export function listSystemLogFiles() {
  return request({
    url: '/monitor/systemlog/files',
    method: 'get'
  })
}

// 查询系统日志内容
export function getSystemLogContent(fileToken, lines) {
  return request({
    url: '/monitor/systemlog/content',
    method: 'get',
    params: {
      fileToken,
      lines
    }
  })
}
