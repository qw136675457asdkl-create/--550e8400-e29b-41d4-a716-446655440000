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
export function adddata(data) {
  return request({
    url: '/data/bussiness/insert',
    method: 'post',
    data: data
  })
}