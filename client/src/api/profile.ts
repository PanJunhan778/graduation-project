import request from './request'
import type {
  ChangePasswordForm,
  CompanySettingsForm,
  ProfileVO,
  Result,
  UpdateProfileForm,
} from '@/types'

export function getCurrentProfile(): Promise<Result<ProfileVO>> {
  return request.get('/profile/me')
}

export function updateCurrentProfile(data: UpdateProfileForm): Promise<Result<ProfileVO>> {
  return request.put('/profile/me', data)
}

export function changePassword(data: ChangePasswordForm): Promise<Result<null>> {
  return request.put('/profile/password', data)
}

export function updateCompanySettings(data: Partial<CompanySettingsForm>): Promise<Result<ProfileVO>> {
  return request.put('/profile/company', data)
}
