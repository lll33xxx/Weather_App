<template>
  <div class="profile-page">
    <div class="profile-card">
      <div class="card-title">个人中心</div>

      <!-- 头像 -->
      <div class="avatar-section">
        <div class="avatar-preview" @click="triggerUpload">
          <img v-if="previewUrl" :src="previewUrl" alt="头像" />
          <span v-else class="avatar-fallback">👤</span>
          <div class="avatar-mask">点击上传</div>
        </div>
        <input ref="fileInput" type="file" accept="image/*" hidden @change="onFileChange" />
      </div>
      <div v-if="pendingFile" class="avatar-actions">
        <button class="btn-primary" @click="saveAvatar">保存头像</button>
        <button class="btn-plain" @click="cancelAvatar">取消</button>
      </div>

      <div class="divider"></div>

      <!-- 基本信息 -->
      <div class="section">
        <div class="section-title">基本信息</div>
        <div class="form-row">
          <label>用户名</label>
          <div class="form-input-wrap">
            <input v-model="newUsername" type="text" placeholder="输入新用户名" />
            <button class="btn-primary" @click="saveUsername">保存用户名</button>
          </div>
        </div>
      </div>

      <div class="divider"></div>

      <!-- 修改密码 -->
      <div class="section">
        <div class="section-title">修改密码</div>
        <div class="form-row">
          <label>原密码</label>
          <input v-model="oldPassword" type="password" placeholder="输入原密码" />
        </div>
        <div class="form-row">
          <label>新密码</label>
          <input v-model="newPassword" type="password" placeholder="至少6位" />
        </div>
        <div class="form-row">
          <label>确认新密码</label>
          <input v-model="confirmPassword" type="password" placeholder="再次输入新密码" />
        </div>
        <div class="form-row">
          <label></label>
          <button class="btn-primary" :disabled="!canSavePassword" @click="savePassword">保存密码</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { updateUsername, changePassword, uploadAvatar } from '@/api/auth'

const authStore = useAuthStore()
const router = useRouter()

const newUsername = ref(authStore.username)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const fileInput = ref(null)

const avatarUrl = ref(authStore.user?.avatar || '')
const previewUrl = ref(authStore.user?.avatar || '')
const pendingFile = ref(null)

const canSavePassword = computed(() =>
  oldPassword.value && newPassword.value.length >= 6 && newPassword.value === confirmPassword.value
)

const triggerUpload = () => fileInput.value?.click()

const onFileChange = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 2MB')
    return
  }
  pendingFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  e.target.value = ''
}

const saveAvatar = async () => {
  if (!pendingFile.value) return
  try {
    const updated = await uploadAvatar(pendingFile.value)
    authStore.user = updated
    localStorage.setItem('nl_auth_user', JSON.stringify(updated))
    avatarUrl.value = updated.avatar
    previewUrl.value = updated.avatar
    pendingFile.value = null
    ElMessage.success('头像已更新')
  } catch (err) {
    ElMessage.error(err.message || '上传失败')
  }
}

const cancelAvatar = () => {
  pendingFile.value = null
  previewUrl.value = avatarUrl.value
}

const saveUsername = async () => {
  if (!newUsername.value.trim()) {
    ElMessage.warning('用户名不能为空')
    return
  }
  if (newUsername.value.trim() === authStore.username) {
    ElMessage.warning('新用户名与原用户名相同')
    return
  }
  try {
    const updated = await updateUsername(newUsername.value)
    authStore.user = updated
    localStorage.setItem('nl_auth_user', JSON.stringify(updated))
    ElMessage.success('用户名已更新')
  } catch (e) {
    ElMessage.error(e.message || '修改失败')
  }
}

const savePassword = async () => {
  if (newPassword.value === oldPassword.value) {
    ElMessage.warning('新密码与原密码相同')
    return
  }
  try {
    await changePassword(oldPassword.value, newPassword.value)
    ElMessage.success('密码已修改，请重新登录')
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    setTimeout(async () => {
      await authStore.logout()
      router.push('/login')
    }, 1500)
  } catch (e) {
    ElMessage.error(e.message || '修改失败')
  }
}
</script>

<style scoped>
.profile-page {
  padding: 24px;
  background: #f8fafc;
  min-height: calc(100vh - 60px);
}
.profile-card {
  max-width: 560px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 24px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 20px;
}
.avatar-section {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
}
.avatar-preview {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #f3f4f6;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-fallback {
  font-size: 36px;
}
.avatar-mask {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.4);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}
.avatar-preview:hover .avatar-mask { opacity: 1; }
.avatar-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 10px;
}
.btn-plain {
  padding: 8px 16px;
  background: #fff;
  color: #374151;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}
.section { margin-bottom: 8px; }
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 14px;
}
.form-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.form-row label {
  width: 80px;
  font-size: 13px;
  color: #6b7280;
  flex-shrink: 0;
}
.form-row input {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
}
.form-row input:focus { border-color: #3b82f6; }
.form-input-wrap {
  flex: 1;
  display: flex;
  gap: 8px;
}
.form-input-wrap input { flex: 1; }
.btn-primary {
  padding: 8px 16px;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.btn-primary:hover { background: #2563eb; }
.btn-primary:disabled { background: #93c5fd; cursor: not-allowed; }
.divider {
  height: 1px;
  background: #e5e7eb;
  margin: 20px 0;
}
</style>
