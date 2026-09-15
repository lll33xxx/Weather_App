<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const handleMenuSelect = (index) => {
  router.push(index)
}

const handleLogout = async () => {
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

const goHome = () => {
  router.push('/')
}
</script>

<template>
  <el-container class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="admin-aside">
      <div class="aside-logo">
        <span class="logo-icon">⚙️</span>
        <span class="logo-text">后台管理</span>
      </div>
      <el-menu
        :default-active="route.path"
        class="aside-menu"
        background-color="transparent"
        text-color="#6b7280"
        active-text-color="#3b82f6"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/admin/users">
          <span class="menu-icon">👥</span>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/data">
          <span class="menu-icon">📊</span>
          <span>数据管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/config">
          <span class="menu-icon">🔧</span>
          <span>系统配置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <span class="header-title">气象数据可视化平台 · 管理后台</span>
        </div>
        <div class="header-right">
          <div class="user-block">
            <span class="user-avatar">👤</span>
            <span class="user-name">{{ authStore.username }}</span>
            <span class="admin-badge">管理员</span>
          </div>
          <div class="header-divider"></div>
          <el-button size="small" @click="goHome">返回前台</el-button>
          <el-button size="small" type="danger" plain @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-aside {
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.aside-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.logo-icon {
  font-size: 24px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.aside-menu {
  border-right: none;
  flex: 1;
  padding-top: 10px;
}

.menu-icon {
  margin-right: 8px;
  font-size: 16px;
}

:deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  margin: 4px 8px;
  border-radius: 4px;
}

:deep(.el-menu-item:hover) {
  background: #f3f4f6 !important;
}

:deep(.el-menu-item.is-active) {
  background: #eff6ff !important;
  border-left: 3px solid #3b82f6;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 24px;
  height: 56px;
}

.header-title {
  color: #1f2937;
  font-size: 15px;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-block {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  background: #f9fafb;
  border-radius: 20px;
  cursor: default;
}

.user-avatar {
  font-size: 16px;
}

.user-name {
  color: #1f2937;
  font-size: 14px;
  font-weight: 500;
}

.admin-badge {
  font-size: 11px;
  color: #ef4444;
  background: #fef2f2;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.header-divider {
  width: 1px;
  height: 24px;
  background: #e5e7eb;
}

.admin-main {
  background: #f8fafc;
  padding: 0;
  overflow-y: auto;
}
</style>
