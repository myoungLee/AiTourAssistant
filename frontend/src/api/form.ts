/*
 * @author myoung
 */

/**
 * 构建字段表单参数，保持参数名与后端字段一致。
 *
 * @author myoung
 */
export function formParams<T extends object>(values: T): URLSearchParams {
  const params = new URLSearchParams()

  Object.entries(values as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return
    }

    if (Array.isArray(value)) {
      value.forEach((item) => {
        if (item !== undefined && item !== null && item !== '') {
          params.append(key, String(item))
        }
      })
      return
    }

    params.append(key, String(value))
  })

  return params
}
