import request from '@/utils/request'

const CODE_EXECUTOR_URLS = {
  python: '/api/python/execute',
  matlab: '/api/matlab/execute'
}

const CODE_EXECUTOR_TIMEOUTS = {
  python: 60000,
  matlab: 310000
}

export function saveMachineLearningConfiguration(data) {
  return request({
    url: '/system/machineLearning/save',
    method: 'post',
    data
  })
}

export function getMachineLearningConfiguration() {
  return request({
    url: '/system/machineLearning/get',
    method: 'get'
  })
}

export function resetMachineLearningConfiguration() {
  return request({
    url: '/system/machineLearning/reset',
    method: 'post'
  })
}

function executeCode(language, code) {
  return request({
    url: CODE_EXECUTOR_URLS[language],
    method: 'post',
    data: { code },
    timeout: CODE_EXECUTOR_TIMEOUTS[language] || 60000,
    silent: true,
    headers: {
      repeatSubmit: false
    }
  })
}

export function executePythonCode(code) {
  return executeCode('python', code)
}

export function executeMatlabCode(code) {
  return executeCode('matlab', code)
}

export function cancelMatlabTask() {
  return request({
    url: '/api/matlab/cancel',
    method: 'post',
    timeout: 10000,
    silent: true,
    headers: {
      repeatSubmit: false
    }
  })
}
