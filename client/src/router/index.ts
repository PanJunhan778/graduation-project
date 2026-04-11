import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken, getUserInfo } from '@/utils/auth'

NProgress.configure({ showSpinner: false })

function resolveDefaultRoute(role?: string) {
  if (role === 'admin') return '/admin/company'
  if (role === 'staff') return '/finance'
  if (role === 'owner') return '/home'
  return '/login'
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/register/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'Home',
          component: () => import('@/views/home/HomeView.vue'),
          meta: { title: '首页', roles: ['owner'] },
        },
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '数据看板', roles: ['owner'] },
        },
        {
          path: 'ai-chat',
          name: 'AiChat',
          component: () => import('@/views/common/ComingSoonView.vue'),
          meta: {
            title: 'AI 智能助理',
            roles: ['owner'],
            comingSoonDescription: '全屏对话、流式渲染、工具调用与 HITL 确认会在 M11 模块统一交付。',
          },
        },
        {
          path: 'admin/company',
          name: 'AdminCompany',
          component: () => import('@/views/admin/CompanyManage.vue'),
          meta: { title: '租户管理', roles: ['admin'] },
        },
        {
          path: 'users',
          name: 'UserManage',
          component: () => import('@/views/user/UserManage.vue'),
          meta: { title: '用户管理', roles: ['owner'] },
        },
        {
          path: 'finance',
          name: 'Finance',
          component: () => import('@/views/finance/FinanceManage.vue'),
          meta: { title: '财务账本', roles: ['owner', 'staff'] },
        },
        {
          path: 'tax',
          name: 'Tax',
          component: () => import('@/views/tax/TaxManage.vue'),
          meta: { title: '税务档案', roles: ['owner', 'staff'] },
        },
        {
          path: 'employee',
          name: 'Employee',
          component: () => import('@/views/employee/EmployeeManage.vue'),
          meta: { title: '员工名册', roles: ['owner', 'staff'] },
        },
        {
          path: 'audit',
          name: 'Audit',
          component: () => import('@/views/audit/AuditManage.vue'),
          meta: { title: '审计日志', roles: ['owner'] },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/home',
    },
  ],
})

router.beforeEach((to, _from, next) => {
  NProgress.start()
  const token = getToken()
  const userInfo = getUserInfo()

  if (to.meta.public) {
    if (token && (to.path === '/login' || to.path === '/register')) {
      next(resolveDefaultRoute(userInfo?.role))
    } else {
      next()
    }
  } else {
    if (!token) {
      next(`/login?redirect=${to.path}`)
    } else {
      const allowedRoles = to.meta.roles as string[] | undefined
      if (allowedRoles && userInfo?.role && !allowedRoles.includes(userInfo.role)) {
        next(resolveDefaultRoute(userInfo.role))
      } else {
        next()
      }
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
