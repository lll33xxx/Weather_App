<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, updateUserRole, deleteUser } from '@/api/admin'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const keyword = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const res = await getUserList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined
    })
    tableData.value = res.list
    total.value = res.total
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pageNum.value = 1
  loadData()
}

const handleRoleChange = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定将用户「${row.username}」的角色修改为「${row.role === 'admin' ? '管理员' : '普通用户'}」吗？`,
      '确认修改',
      { type: 'warning' }
    )
    await updateUserRole(row.id, row.role)
    ElMessage.success('角色修改成功')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '修改失败')
      loadData()
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？此操作不可恢复。`,
      '确认删除',
      { type: 'error' }
    )
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    if (tableData.value.length === 1 && pageNum.value > 1) {
      pageNum.value--
    }
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

const roleTagType = (role) => role === 'admin' ? 'danger' : 'primary'

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <p class="page-desc">管理系统所有用户，可修改角色权限或删除用户</p>
    </div>

    <div class="search-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>🔍</template>
      </el-input>
      <el-button type="primary" @click="handleSearch">搜索</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" class="data-table">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" min-width="160" />
      <el-table-column label="角色" width="160">
        <template #default="{ row }">
          <el-select
            v-model="row.role"
            size="small"
            :style="{ width: '120px' }"
            @change="handleRoleChange(row)"
          >
            <el-option label="普通用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="角色标签" width="120">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.role)" size="small">
            {{ row.role === 'admin' ? '管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  color: #1f2937;
  margin: 0 0 6px;
}

.page-desc {
  color: #6b7280;
  font-size: 13px;
  margin: 0;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.data-table {
  background: #ffffff;
  border-radius: 4px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
