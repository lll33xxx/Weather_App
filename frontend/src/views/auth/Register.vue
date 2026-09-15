<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { uploadAvatar } from '@/api/auth'
import AuthBrandPanel from '@/components/AuthBrandPanel.vue'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({
  username: '',
  password: '',
  confirmPassword: ''
})
const loading = ref(false)
const avatarFile = ref(null)
const avatarPreview = ref('')
const fileInput = ref(null)

const triggerAvatar = () => fileInput.value?.click()

const onAvatarChange = (e) => {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 2MB')
    return
  }
  avatarFile.value = file
  avatarPreview.value = URL.createObjectURL(file)
  e.target.value = ''
}

const handleRegister = async () => {
  if (!form.value.username.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (form.value.username.trim().length < 3) {
    ElMessage.warning('用户名至少3个字符')
    return
  }
  if (!form.value.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (form.value.password.length < 6) {
    ElMessage.warning('密码长度不能少于6位')
    return
  }
  if (form.value.password !== form.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await authStore.register(form.value.username.trim(), form.value.password)
    if (avatarFile.value) {
      await authStore.login(form.value.username.trim(), form.value.password)
      await uploadAvatar(avatarFile.value)
    }
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}

const goLogin = () => {
  router.push('/login')
}
</script>

<template>
  <div class="auth-page">
    <AuthBrandPanel />

    <div class="right-panel">
      <div class="form-wrap">
        <h2 class="form-title">创建账号</h2>
        <p class="form-sub">注册新用户</p>

        <el-form @submit.prevent="handleRegister" class="auth-form">
          <el-form-item>
            <div class="avatar-upload" @click="triggerAvatar">
              <img v-if="avatarPreview" :src="avatarPreview" alt="头像" />
              <span v-else>👤</span>
              <div class="avatar-tip">上传头像（可选）</div>
            </div>
            <input ref="fileInput" type="file" accept="image/*" hidden @change="onAvatarChange" />
          </el-form-item>

          <el-form-item>
            <el-input v-model="form.username" placeholder="请输入用户名（至少3个字符）" size="large" clearable @keyup.enter="handleRegister">
              <template #prefix><span class="input-icon">👤</span></template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-input v-model="form.password" type="password" placeholder="请输入密码（至少6位）" size="large" show-password @keyup.enter="handleRegister">
              <template #prefix><span class="input-icon">🔒</span></template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-input v-model="form.confirmPassword" type="password" placeholder="请确认密码" size="large" show-password @keyup.enter="handleRegister">
              <template #prefix><span class="input-icon">🔒</span></template>
            </el-input>
          </el-form-item>

          <el-button type="primary" size="large" class="auth-btn" :loading="loading" @click="handleRegister">注 册</el-button>
        </el-form>

        <div class="auth-footer">
          <span>已有账号？</span>
          <span class="auth-link" @click="goLogin">去登录</span>
        </div>

        <div class="auth-notice">
          <p>注册账号默认为普通用户</p>
          <p>管理员账号由系统后台统一分配</p>
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
  max-width: 340px;
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
.avatar-upload {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px;
  border: 1px dashed #d1d5db;
  border-radius: 6px;
  cursor: pointer;
}
.avatar-upload:hover { border-color: #3b82f6; }
.avatar-upload img {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
}
.avatar-upload span { font-size: 28px; }
.avatar-tip { font-size: 12px; color: #9ca3af; }
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
.auth-notice {
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
