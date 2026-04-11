import request from './request'
import type { Result, PageResult, UserVO, StaffCreateForm, ResetPasswordForm } from '@/types'

export function getUserList(params: {
  page: number
  size: number
  keyword?: string
}): Promise<Result<PageResult<UserVO>>> {
  return request.get('/user/list', { params })
}

export function createStaff(data: StaffCreateForm): Promise<Result<null>> {
  return request.post('/user', data)
}

export function updateUserStatus(id: number, status: number): Promise<Result<null>> {
  return request.put(`/user/${id}/status`, { status })
}

export function resetPassword(id: number, data: ResetPasswordForm): Promise<Result<null>> {
  return request.put(`/user/${id}/reset-password`, data)
}
