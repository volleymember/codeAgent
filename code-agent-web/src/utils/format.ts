export function formatDate(value?: string) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

export function statusType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'COMPLETED' || status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING' || status === 'EXECUTING') return 'warning'
  return 'info'
}
