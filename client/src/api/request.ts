import axios from 'axios'
import { getToken, clearAuth } from '@/utils/auth'
import router from '@/router'

declare module 'axios' {
  interface AxiosRequestConfig {
    silentError?: boolean
  }

  interface InternalAxiosRequestConfig {
    silentError?: boolean
  }
}

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }

    const res = response.data
    if (res.code !== 200) {
      if (res.code === 401) {
        clearAuth()
        router.push('/login')
        ElMessage.error('登录已过期，请重新登录')
      } else if (!res.data && !response.config.silentError) {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(res)
    }

    return res
  },
  (error) => {
    if (axios.isCancel(error) || error?.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }

    const silentError = error.config?.silentError
    if (error.response?.status === 401) {
      clearAuth()
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else if (!silentError) {
      ElMessage.error(error.message || '网络异常')
    }

    return Promise.reject(error)
  },
)

export default request
