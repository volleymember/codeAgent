import axios from 'axios'
import type { ApiResponse } from '../types/api'

export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000
})

request.interceptors.response.use((response) => {
  const payload = response.data as ApiResponse<unknown>
  if (payload && payload.success === false) {
    return Promise.reject(new Error(`${payload.code}: ${payload.message}`))
  }
  return payload?.data ?? response.data
})
