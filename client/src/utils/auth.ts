const TOKEN_KEY = 'ems_token'
const USER_KEY = 'ems_user'
const LOGIN_SESSION_KEY = 'ems_login_session_id'
const STAFF_FINANCE_GUIDE_SEEN_KEY = 'ems_staff_finance_guide_seen_session_id'
const OWNER_ONBOARDING_PROMPTED_PREFIX = 'ems_owner_onboarding_prompted_session_id'
const OWNER_ONBOARDING_COMPLETED_PREFIX = 'ems_owner_onboarding_completed'
const OWNER_ONBOARDING_DISMISSED_PREFIX = 'ems_owner_onboarding_dismissed'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function getUserInfo(): Record<string, string> | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setUserInfo(info: Record<string, unknown>): void {
  localStorage.setItem(USER_KEY, JSON.stringify(info))
}

export function removeUserInfo(): void {
  localStorage.removeItem(USER_KEY)
}

export function getLoginSessionId(): string | null {
  return localStorage.getItem(LOGIN_SESSION_KEY)
}

export function setLoginSessionId(sessionId: string): void {
  localStorage.setItem(LOGIN_SESSION_KEY, sessionId)
}

export function removeLoginSessionId(): void {
  localStorage.removeItem(LOGIN_SESSION_KEY)
}

export function getStaffFinanceGuideSeenSessionId(): string | null {
  return localStorage.getItem(STAFF_FINANCE_GUIDE_SEEN_KEY)
}

export function setStaffFinanceGuideSeenSessionId(sessionId: string): void {
  localStorage.setItem(STAFF_FINANCE_GUIDE_SEEN_KEY, sessionId)
}

export function removeStaffFinanceGuideSeenSessionId(): void {
  localStorage.removeItem(STAFF_FINANCE_GUIDE_SEEN_KEY)
}

function normalizeCompanyScope(companyCode?: string | null): string {
  const trimmed = companyCode?.trim()
  return trimmed || 'current-company'
}

function scopedOwnerGuideKey(prefix: string, companyCode?: string | null): string {
  return `${prefix}:${normalizeCompanyScope(companyCode)}`
}

export function getOwnerOnboardingPromptedSessionId(companyCode?: string | null): string | null {
  return localStorage.getItem(scopedOwnerGuideKey(OWNER_ONBOARDING_PROMPTED_PREFIX, companyCode))
}

export function setOwnerOnboardingPromptedSessionId(sessionId: string, companyCode?: string | null): void {
  localStorage.setItem(scopedOwnerGuideKey(OWNER_ONBOARDING_PROMPTED_PREFIX, companyCode), sessionId)
}

export function hasCompletedOwnerOnboarding(companyCode?: string | null): boolean {
  return localStorage.getItem(scopedOwnerGuideKey(OWNER_ONBOARDING_COMPLETED_PREFIX, companyCode)) === '1'
}

export function setOwnerOnboardingCompleted(companyCode?: string | null): void {
  localStorage.setItem(scopedOwnerGuideKey(OWNER_ONBOARDING_COMPLETED_PREFIX, companyCode), '1')
}

export function hasDismissedOwnerOnboarding(companyCode?: string | null): boolean {
  return localStorage.getItem(scopedOwnerGuideKey(OWNER_ONBOARDING_DISMISSED_PREFIX, companyCode)) === '1'
}

export function setOwnerOnboardingDismissed(companyCode?: string | null): void {
  localStorage.setItem(scopedOwnerGuideKey(OWNER_ONBOARDING_DISMISSED_PREFIX, companyCode), '1')
}

export function clearAuth(): void {
  removeToken()
  removeUserInfo()
  removeLoginSessionId()
  removeStaffFinanceGuideSeenSessionId()
}
