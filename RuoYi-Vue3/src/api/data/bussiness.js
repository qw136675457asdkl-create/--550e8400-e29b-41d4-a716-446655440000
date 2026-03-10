import request from '@/utils/request'
//树形结构的实验项目列表
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
export function updatedata(data) {
  return request({
    url: '/data/bussiness/update',
    method: 'put',
    data: data
  })
}
export function deldata(ids) {
  return request({
    url: '/data/bussiness/delete', 
    method: 'delete',
    data: ids
  })
}

// 导入数据
export function adddata(data, file) {
  const formData = new FormData()
  
  // 添加文件
  if (file) {
    formData.append('file', file)
  }
  
  // 直接将数据对象的属性添加到FormData，使后端能够接收到值
  for (const key in data) {
    if (data.hasOwnProperty(key)) {
      formData.append(key, data[key])
    }
  }
  
  return request({
    url: '/data/bussiness/insert',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function previewData(data) {
  return request({
    url: '/data/bussiness/preview',
    method: 'post',
    data: data
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
