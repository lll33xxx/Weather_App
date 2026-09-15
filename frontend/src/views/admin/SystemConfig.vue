<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getModuleList, createModule, updateModule, deleteModule } from '@/api/admin'

const loading = ref(false)
const tableData = ref([])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  moduleKey: '',
  title: '',
  description: '',
  icon: '',
  path: '',
  sortOrder: 0
})

const rules = {
  moduleKey: [{ required: true, message: '请输入模块标识', trigger: 'blur' }],
  title: [{ required: true, message: '请输入模块标题', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    tableData.value = await getModuleList()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.id = null
  form.moduleKey = ''
  form.title = ''
  form.description = ''
  form.icon = ''
  form.path = ''
  form.sortOrder = 0
}

const handleAdd = () => {
  resetForm()
  isEdit.value = false
  dialogTitle.value = '新增模块'
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, row)
  isEdit.value = true
  dialogTitle.value = '编辑模块'
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    if (isEdit.value) {
      await updateModule(form.id, {
        title: form.title,
        description: form.description,
        icon: form.icon,
        path: form.path,
        sortOrder: form.sortOrder
      })
      ElMessage.success('修改成功')
    } else {
      await createModule({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    if (e !== false) {
      ElMessage.error(e.message || '操作失败')
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除模块「${row.title}」吗？此操作不可恢复。`, '确认删除', { type: 'error' })
    await deleteModule(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

// ========== SVG 图标上传 ==========
const isSvg = (val) => typeof val === 'string' && val.trim().startsWith('<svg')

const handleSvgUpload = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    let svgText = e.target.result
    // 移除 XML 声明、DOCTYPE、注释，只保留 <svg>...</svg>
    svgText = svgText
      .replace(/<\?xml[^?]*\?>/g, '')
      .replace(/<!DOCTYPE[^>]*>/gi, '')
      .replace(/<!--[\s\S]*?-->/g, '')
      .replace(/(<svg[^>]*?)\s+width="[^"]*"/gi, '$1')
      .replace(/(<svg[^>]*?)\s+height="[^"]*"/gi, '$1')
      .trim()
    form.icon = svgText
    ElMessage.success('SVG 图标已加载')
  }
  reader.onerror = () => {
    ElMessage.error('读取文件失败')
  }
  reader.readAsText(file.raw || file)
  return false
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">系统配置</h2>
      <p class="page-desc">管理首页展示的功能模块，可新增、编辑或删除模块</p>
    </div>

    <div class="action-bar">
      <el-button type="primary" @click="handleAdd">+ 新增模块</el-button>
      <el-button @click="loadData">刷新</el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" class="data-table">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="moduleKey" label="模块标识" width="120" />
      <el-table-column label="图标" width="70">
        <template #default="{ row }">
          <span v-if="isSvg(row.icon)" class="svg-icon-preview" v-html="row.icon"></span>
          <span v-else style="font-size: 20px">{{ row.icon }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="140" />
      <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
      <el-table-column prop="path" label="路由路径" width="120" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="warning" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="模块标识" prop="moduleKey">
          <el-input v-model="form.moduleKey" :disabled="isEdit" placeholder="如 weather、geo" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="模块显示名称" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="emoji 图标，如 🌤️">
            <template #append>
              <el-upload
                :show-file-list="false"
                accept=".svg"
                :before-upload="handleSvgUpload"
                action="#"
              >
                <el-button>上传SVG</el-button>
              </el-upload>
            </template>
          </el-input>
          <div v-if="isSvg(form.icon)" class="svg-preview-box" v-html="form.icon"></div>
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.path" :disabled="isEdit" placeholder="如 /weather" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="模块描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
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

.action-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.data-table {
  background: #ffffff;
  border-radius: 4px;
}

.svg-icon-preview :deep(svg) {
  width: 22px;
  height: 22px;
  fill: currentColor;
  display: block;
}

.svg-preview-box {
  margin-top: 8px;
  padding: 8px;
  background: #f9fafb;
  border: 1px dashed #d1d5db;
  border-radius: 6px;
  display: inline-flex;
}

.svg-preview-box :deep(svg) {
  width: 32px;
  height: 32px;
  fill: #3b82f6;
}
</style>
