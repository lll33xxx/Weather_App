<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AuthBrandPanel from '@/components/AuthBrandPanel.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = ref({
  username: '',
  password: ''
})
const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.value.password) {
    ElMessage.warning('请输入密码')
    return
  }
  loading.value = true
  try {
    const user = await authStore.login(form.value.username.trim(), form.value.password)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    if (redirect) {
      router.push(redirect)
    } else if (user.role === 'admin') {
      router.push('/admin')
    } else {
      router.push('/')
    }
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const goRegister = () => {
  router.push('/register')
}
</script>

<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <AuthBrandPanel />

    <!-- 右侧表单 -->
    <div class="right-panel">
      <div class="form-wrap">
        <h2 class="form-title">欢迎回来</h2>
        <p class="form-sub">登录以继续使用</p>

        <el-form @submit.prevent="handleLogin" class="auth-form">
          <el-form-item>
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" clearable @keyup.enter="handleLogin">
              <template #prefix><span class="input-icon">👤</span></template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password @keyup.enter="handleLogin">
              <template #prefix><span class="input-icon">🔒</span></template>
            </el-input>
          </el-form-item>
          <el-button type="primary" size="large" class="auth-btn" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form>

        <div class="auth-footer">
          <span>还没有账号？</span>
          <span class="auth-link" @click="goRegister">立即注册</span>
        </div>

        <div class="auth-tip">
          <p>测试账号：admin / admin123（管理员）</p>
          <p>测试账号：user / user123（普通用户）</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
}
.right-panel {
  width: 440px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}
.form-wrap {
  width: 100%;
  max-width: 320px;
}
.form-title {
  font-size: 22px;
  color: #1f2937;
  margin-bottom: 6px;
}
.form-sub {
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 28px;
}
.auth-form { margin-bottom: 20px; }
.input-icon { font-size: 16px; }
.auth-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
  margin-top: 8px;
}
.auth-footer {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  margin-bottom: 20px;
}
.auth-link {
  color: #3b82f6;
  cursor: pointer;
  margin-left: 4px;
}
.auth-link:hover { text-decoration: underline; }
.auth-tip {
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.8;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
}
@media (max-width: 900px) {
  .right-panel { width: 100%; }
}
</style>
