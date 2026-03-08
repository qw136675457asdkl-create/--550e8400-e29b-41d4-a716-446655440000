import request from '@/utils/request'

// 查询行政区划树（与后端 keyword 参数一致）
export function listRegion(keyword) {
  return request({
    url: '/system/region/tree',
    method: 'get',
    params: { keyword }
  })
}

// 根据父级行政区代码获取子级列表
export function getRegionChildren(parentCode) {
  return request({
    url: '/system/region/children/' + parentCode,
    method: 'get'
  })
}