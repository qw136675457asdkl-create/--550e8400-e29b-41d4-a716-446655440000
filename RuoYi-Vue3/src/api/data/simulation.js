import request from '@/utils/request'

export function listSimulationTasks(query) {
  return request({
    url: '/data/simulation/task/list',
    method: 'get',
    params: query
  })
}

export function getSimulationTask(id) {
  return request({
    url: `/data/simulation/task/${id}`,
    method: 'get'
  })
}

export function submitSimulationTask(data) {
  return request({
    url: '/data/simulation/task/submit',
    method: 'post',
    data
  })
}

export function deleteSimulationTask(id) {
  return request({
    url: `/data/simulation/task/${id}`,
    method: 'delete'
  })
}

export function listExperimentFiles(experimentId) {
  return request({
    url: `/data/simulation/experiment/${experimentId}/files`,
    method: 'get'
  })
}

export function getSimulationMetricTemplates() {
  return request({
    url: '/data/simulation/metric/templates',
    method: 'get'
  })
}
