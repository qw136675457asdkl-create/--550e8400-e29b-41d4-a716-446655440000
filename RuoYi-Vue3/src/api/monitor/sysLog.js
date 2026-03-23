import request from '@/utils/request'

export function listSysLogs() {
  return request({
    url: '/monitor/system/log/list',
    method: 'get',
  })
}

export function previewLog(logName) {
  return request({
    url: '/monitor/system/log/preview/' + encodeURIComponent(logName),
    method: 'post',
  })
}

export function downloadLog(fileName) {
  return request({
    url: '/monitor/system/log/download/' + encodeURIComponent(fileName),
    method: 'post',
    responseType: 'blob'
  })
}

export function downloadLogWord(fileName) {
  return request({
    url: '/monitor/system/log/export/word/' + encodeURIComponent(fileName),
    method: 'post',
    responseType: 'blob'
  })
}

export function downloadLogPdf(fileName) {
  return request({
    url: '/monitor/system/log/export/pdf/' + encodeURIComponent(fileName),
    method: 'post',
    responseType: 'blob'
  })
}
