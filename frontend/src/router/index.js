import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录 - 气象数据可视化平台', requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { title: '注册 - 气象数据可视化平台', requiresAuth: false }
  },
  {
    // 主布局：包含左侧风琴导航 + 顶部用户栏
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页 - 气象数据可视化平台' }
      },
      {
        path: 'weather',
        name: 'WeatherDashboard',
        component: () => import('@/views/weather/WeatherDashboard.vue'),
        meta: { title: '气象数据可视化' }
      },
      {
        path: 'geo',
        name: 'GeoDashboard',
        component: () => import('@/views/geo/GeoDashboard.vue'),
        meta: { title: '地图可视化看板' }
      },
      {
        path: 'ai',
        name: 'AiChat',
        component: () => import('@/views/ai/AiChat.vue'),
        meta: { title: '天气智能助手' }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/user/Profile.vue'),
        meta: { title: '个人中心 - 气象数据可视化平台', requiresAdmin: false }
      }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminDashboard.vue'),
    meta: { title: '后台管理 - 气象数据可视化平台', requiresAuth: true, requiresAdmin: true },
    redirect: '/admin/users',
    children: [
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理 - 后台管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'data',
        name: 'DataManage',
        component: () => import('@/views/admin/DataManage.vue'),
        meta: { title: '数据管理 - 后台管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/admin/SystemConfig.vue'),
        meta: { title: '系统配置 - 后台管理', requiresAuth: true, requiresAdmin: true }
      }
    ]
  },
  // 兜底：未知路径跳转首页
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：登录校验 + 权限校验 + 页面标题
router.beforeEach(async (to, _from, next) => {
  // 设置页面标题（优先使用子路由标题，否则用父路由）
  const title = to.meta?.title || to.matched[0]?.meta?.title
  if (title) {
    document.title = title
  }

  const authStore = useAuthStore()
  const isLoggedIn = authStore.isLoggedIn

  // 1. 访问登录/注册页：已登录则根据角色跳转，未登录放行
  if (to.path === '/login' || to.path === '/register') {
    if (isLoggedIn) {
      next(authStore.isAdmin ? '/admin' : '/')
    } else {
      next()
    }
    return
  }

  // 2. 其他页面：未登录则跳转登录页（携带redirect）
  if (!isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 3. 已登录状态下，异步验证后端 Session 是否有效
  try {
    await authStore.fetchCurrentUser()
  } catch (e) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (!authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 4. 管理员页面权限校验：普通用户不可访问
  const requiresAdmin = to.matched.some(r => r.meta?.requiresAdmin)
  if (requiresAdmin && !authStore.isAdmin) {
    next('/')
    return
  }

  // 5. 管理员不可访问个人中心
  if (to.path === '/profile' && authStore.isAdmin) {
    next('/admin')
    return
  }

  next()
})

export default router
