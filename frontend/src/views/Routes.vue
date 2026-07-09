<template>
  <div class="routes-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>航线管理</span>
          <el-button type="primary" @click="showAddDialog = true">新增航线</el-button>
        </div>
      </template>

      <el-table :data="routes" border>
        <el-table-column prop="routeCode" label="航线编码" />
        <el-table-column prop="routeName" label="航线名称" />
        <el-table-column prop="startPort" label="起点码头" />
        <el-table-column prop="endPort" label="终点码头" />
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ scope.row.status === 'ACTIVE' ? '运营中' : '已停运' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作">
          <template #default="scope">
            <el-button size="small" @click="editRoute(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteRoute(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAddDialog" :title="isEdit ? '编辑航线' : '新增航线'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="航线编码" required>
          <el-input v-model="form.routeCode" />
        </el-form-item>
        <el-form-item label="航线名称" required>
          <el-input v-model="form.routeName" />
        </el-form-item>
        <el-form-item label="起点码头" required>
          <el-input v-model="form.startPort" />
        </el-form-item>
        <el-form-item label="终点码头" required>
          <el-input v-model="form.endPort" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="运营中" value="ACTIVE" />
            <el-option label="已停运" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRoute">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { routeApi, type Route } from '@/api'

const routes = ref<Route[]>([])
const showAddDialog = ref(false)
const isEdit = ref(false)

const form = reactive({
  routeCode: '',
  routeName: '',
  startPort: '',
  endPort: '',
  status: 'ACTIVE',
  remark: ''
})

let editingId: number | null = null

const loadRoutes = async () => {
  try {
    routes.value = await routeApi.getAll()
  } catch (error) {
    console.error('加载航线失败:', error)
    ElMessage.error('加载航线失败')
  }
}

const editRoute = (route: Route) => {
  isEdit.value = true
  editingId = route.id
  form.routeCode = route.routeCode
  form.routeName = route.routeName
  form.startPort = route.startPort
  form.endPort = route.endPort
  form.status = route.status
  form.remark = route.remark
  showAddDialog.value = true
}

const saveRoute = async () => {
  try {
    if (!form.routeCode || !form.routeName || !form.startPort || !form.endPort) {
      ElMessage.warning('请填写必填字段')
      return
    }

    if (isEdit.value && editingId) {
      await routeApi.update(editingId, form)
      ElMessage.success('更新成功')
    } else {
      await routeApi.create(form)
      ElMessage.success('创建成功')
    }

    showAddDialog.value = false
    await loadRoutes()
  } catch (error: any) {
    console.error('保存失败:', error)
    ElMessage.error(error.message || '保存失败')
  }
}

const deleteRoute = async (route: Route) => {
  try {
    await routeApi.delete(route.id)
    ElMessage.success('删除成功')
    await loadRoutes()
  } catch (error: any) {
    console.error('删除失败:', error)
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => {
  loadRoutes()
})
</script>

<style scoped>
.routes-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>