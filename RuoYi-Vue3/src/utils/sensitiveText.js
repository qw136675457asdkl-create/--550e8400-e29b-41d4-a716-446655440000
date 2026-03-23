const HIDDEN_TEXT = '\u8d85\u7ea7\u7ba1\u7406\u5458'
const REPLACEMENT_TEXT = '\u7cfb\u7edf\u5f00\u53d1\u4eba\u5458'

export function sanitizeSensitiveText(value, fallback) {
  if (typeof value !== 'string') {
    return value
  }

  const sanitized = value.split(HIDDEN_TEXT).join(REPLACEMENT_TEXT)
  if (fallback !== undefined && sanitized === '') {
    return fallback
  }
  return sanitized
}

export function sanitizeSensitiveData(value, seen = new WeakSet()) {
  if (value === null || value === undefined) {
    return value
  }

  if (typeof value === 'string') {
    return sanitizeSensitiveText(value)
  }

  if (typeof value !== 'object') {
    return value
  }

  if (seen.has(value)) {
    return value
  }
  seen.add(value)

  if (Array.isArray(value)) {
    value.forEach((item, index) => {
      value[index] = sanitizeSensitiveData(item, seen)
    })
    return value
  }

  Object.keys(value).forEach((key) => {
    value[key] = sanitizeSensitiveData(value[key], seen)
  })
  return value
}
