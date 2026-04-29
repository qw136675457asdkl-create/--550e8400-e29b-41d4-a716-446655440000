import request from '@/utils/request'

// Tree data
export function getExperimentList() {
  return request({
    url: '/data/bussiness/experimentInfoTree',
    method: 'get'
  })
}

export function getdataList(query) {
  return request({
    url: '/data/bussiness/datalist',
    method: 'get',
    params: query
  })
}

export function getdataDetail(id) {
  return request({
    url: '/data/bussiness/' + id,
    method: 'get'
  })
}

export function RenameDataName(data) {
  const payload = Array.isArray(data) ? data : (data ? [data] : [])
  return request({
    url: '/data/bussiness/rename',
    method: 'put',
    data: payload
  })
}

export function backupData(id, config = {}) {
  return request({
    url: '/data/bussiness/backup/' + id,
    method: 'put',
    ...config
  })
}

export function getBackupDataList(query, config = {}) {
  return request({
    url: '/data/bussiness/backup/list',
    method: 'get',
    params: query,
    ...config
  })
}

export function restoreBackupData(id, config = {}) {
  return request({
    url: '/data/bussiness/back/restore/' + id,
    method: 'post',
    ...config
  })
}

export function getMovePathTree() {
  return request({
    url: '/data/bussiness/movePathTree',
    method: 'get'
  })
}

export function updatedata(data) {
  return request({
    url: '/data/bussiness/update',
    method: 'put',
    data: data
  })
}

export function deldata(data) {
  const isBatchDelete = Array.isArray(data)
  return request({
    url: isBatchDelete ? '/data/bussiness/delete' : `/data/bussiness/delete/${data}`,
    method: 'delete',
    ...(isBatchDelete ? { data } : {})
  })
}

// Upload business data
export function adddata(data, config = {}) {
  const requestConfig = {
    url: '/data/bussiness/insert',
    method: 'post',
    data,
    timeout: config.timeout ?? 6 * 60 * 60 * 1000,
    ...config
  }

  if (data instanceof FormData) {
    requestConfig.headers = {
      'Content-Type': 'multipart/form-data',
      repeatSubmit: false,
      ...(config.headers || {})
    }
  }

  return request(requestConfig)
}

export function initiateBusinessDataUpload(data, config = {}) {
  return request({
    url: '/minio/direct-upload/initiate',
    method: 'post',
    data,
    ...config
  })
}

export function completeBusinessDataUpload(data, config = {}) {
  return request({
    url: '/minio/direct-upload/complete',
    method: 'post',
    data,
    ...config
  })
}

export function previewData(data) {
  return request({
    url: '/data/bussiness/preview',
    method: 'post',
    data: data
  })
}

export function previewFile(url, config = {}) {
  return request({
    url,
    method: 'get',
    responseType: 'blob',
    ...config
  })
}

export function downloadData(data) {
  return request({
    url: '/data/bussiness/download',
    method: 'post',
    data: data,
    responseType: 'blob'
  })
}
