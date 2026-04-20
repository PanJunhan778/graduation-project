import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getToken,
  setToken,
  setUserInfo,
  clearAuth,
  getLoginSessionId,
  setLoginSessionId,
  getStaffFinanceGuideSeenSessionId,
  setStaffFinanceGuideSeenSessionId,
  removeStaffFinanceGuideSeenSessionId,
} from '@/utils/auth'
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
  const loginSessionId = ref<string>(getLoginSessionId() || '')
  const staffFinanceGuideSeenSessionId = ref<string>(getStaffFinanceGuideSeenSessionId() || '')

  const isLoggedIn = computed(() => !!token.value)
  const isOwner = computed(() => role.value === 'owner')

  function createSessionId() {
    return `session_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
  }

  function ensureLoginSession() {
    if (!loginSessionId.value) {
      loginSessionId.value = createSessionId()
      setLoginSessionId(loginSessionId.value)
    }
    return loginSessionId.value
  }

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

    loginSessionId.value = createSessionId()
    setLoginSessionId(loginSessionId.value)
    staffFinanceGuideSeenSessionId.value = ''
    removeStaffFinanceGuideSeenSessionId()
  }

  function patchDisplayProfile(info: Partial<Pick<UserInfo, 'realName' | 'companyName' | 'companyCode' | 'industry' | 'taxpayerType'>>) {
    if (Object.prototype.hasOwnProperty.call(info, 'realName')) {
      realName.value = info.realName || ''
    }
    if (Object.prototype.hasOwnProperty.call(info, 'companyName')) {
      companyName.value = info.companyName || ''
    }
    if (Object.prototype.hasOwnProperty.call(info, 'companyCode')) {
      companyCode.value = info.companyCode || ''
    }
    if (Object.prototype.hasOwnProperty.call(info, 'industry')) {
      industry.value = info.industry || ''
    }
    if (Object.prototype.hasOwnProperty.call(info, 'taxpayerType')) {
      taxpayerType.value = info.taxpayerType || ''
    }

    setUserInfo({
      role: role.value,
      realName: realName.value,
      companyName: companyName.value,
      companyCode: companyCode.value,
      industry: industry.value,
      taxpayerType: taxpayerType.value,
    })
  }

  async function login(form: LoginForm) {
    const res = await loginApi(form)
    setUserState(res.data)
    return res.data
  }

  function hasSeenStaffFinanceGuideInCurrentSession() {
    return !!loginSessionId.value && loginSessionId.value === staffFinanceGuideSeenSessionId.value
  }

  function markStaffFinanceGuideSeen() {
    const sessionId = ensureLoginSession()
    staffFinanceGuideSeenSessionId.value = sessionId
    setStaffFinanceGuideSeenSessionId(sessionId)
  }

  function logout() {
    token.value = null
    role.value = ''
    realName.value = ''
    companyName.value = ''
    companyCode.value = ''
    industry.value = ''
    taxpayerType.value = ''
    loginSessionId.value = ''
    staffFinanceGuideSeenSessionId.value = ''
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
        loginSessionId.value = getLoginSessionId() || ''
        if (!loginSessionId.value) {
          loginSessionId.value = createSessionId()
          setLoginSessionId(loginSessionId.value)
        }
        staffFinanceGuideSeenSessionId.value = getStaffFinanceGuideSeenSessionId() || ''
      } catch {
        role.value = ''
        realName.value = ''
        companyName.value = ''
        companyCode.value = ''
        industry.value = ''
        taxpayerType.value = ''
        token.value = null
        loginSessionId.value = ''
        staffFinanceGuideSeenSessionId.value = ''
        clearAuth()
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
    loginSessionId,
    staffFinanceGuideSeenSessionId,
    isLoggedIn,
    isOwner,
    setUserState,
    patchDisplayProfile,
    login,
    hasSeenStaffFinanceGuideInCurrentSession,
    markStaffFinanceGuideSeen,
    logout,
    restoreFromStorage,
  }
})
