const POSITIVE_ID = /^[1-9]\d*$/

export function requireStringId(value, name = 'id') {
  if (typeof value !== 'string' || !POSITIVE_ID.test(value)) {
    throw new TypeError(`${name} must be a positive integer string`)
  }
  return value
}

export function encodeStringId(value, name = 'id') {
  return encodeURIComponent(requireStringId(value, name))
}

export function withStringIds(data, names) {
  const result = { ...data }
  names.forEach(name => {
    result[name] = requireStringId(result[name], name)
  })
  return result
}

export function normalizeListQuery(query, allowedSort, defaultSort) {
  const params = {
    pageNum: 1,
    pageSize: 10,
    sortBy: defaultSort,
    sortDirection: 'desc',
    ...query
  }
  if (!allowedSort.includes(params.sortBy)) {
    throw new TypeError('sortBy is not allowed')
  }
  if (!['asc', 'desc'].includes(params.sortDirection)) {
    throw new TypeError('sortDirection is not allowed')
  }
  return params
}

export function requireOptionalStringId(value, name) {
  if (value === undefined || value === null || value === '') {
    return undefined
  }
  return requireStringId(value, name)
}
