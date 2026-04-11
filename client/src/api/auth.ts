import request from './request'
import type { Result, LoginForm, LoginVO, RegisterForm } from '@/types'

export function login(data: LoginForm): Promise<Result<LoginVO>> {
  return request.post('/auth/login', data)
}

export function register(data: Omit<RegisterForm, 'confirmPassword'>): Promise<Result<null>> {
  return request.post('/auth/register', data)
}
