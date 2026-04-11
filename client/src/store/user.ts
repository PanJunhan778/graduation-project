import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, removeToken, setUserInfo, clearAuth } from '@/utils/auth'
import { login as loginApi } from '@/api/auth'
import type { LoginForm, UserInfo } from '@/types'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const role = ref<string>('')
  const realName = ref<string>('')
  const companyName = ref<string>('')
  const companyCode = ref<string>('')
  const industry = ref<string>('')
  const taxpayerType = ref<string>('')

  const isLoggedIn = computed(() => !!token.value)

  function setUserState(info: UserInfo) {
    token.value = info.token
    role.value = info.role
    realName.value = info.realName
    companyName.value = info.companyName || ''
    companyCode.value = info.companyCode || ''
    industry.value = info.industry || ''
    taxpayerType.value = info.taxpayerType || ''

    setToken(info.token)
    setUserInfo({
      role: info.role,
      realName: info.realName,
      companyName: info.companyName || '',
      companyCode: info.companyCode || '',
      industry: info.industry || '',
      taxpayerType: info.taxpayerType || '',
    })
  }

  async function login(form: LoginForm) {
    const res = await loginApi(form)
    setUserState(res.data)
    return res.data
  }

  function logout() {
    token.value = null
    role.value = ''
    realName.value = ''
    companyName.value = ''
    companyCode.value = ''
    industry.value = ''
    taxpayerType.value = ''
    clearAuth()
    router.push('/login')
  }

  function restoreFromStorage() {
    const storedToken = getToken()
    if (storedToken) {
      token.value = storedToken
      try {
        const raw = localStorage.getItem('ems_user')
        if (raw) {
          const info = JSON.parse(raw)
          role.value = info.role || ''
          realName.value = info.realName || ''
          companyName.value = info.companyName || ''
          companyCode.value = info.companyCode || ''
          industry.value = info.industry || ''
          taxpayerType.value = info.taxpayerType || ''
        }
      } catch {
        removeToken()
        token.value = null
      }
    }
  }

  return {
    token,
    role,
    realName,
    companyName,
    companyCode,
    industry,
    taxpayerType,
    isLoggedIn,
    setUserState,
    login,
    logout,
    restoreFromStorage,
  }
})
