<template>
  <div class="routes-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>航线管理</span>
          <div>
            <el-button @click="openBatchesDialog">封航清单查询</el-button>
            <el-button type="primary" @click="openAddDialog">新增航线</el-button>
          </div>
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
        <el-table-column label="操作" width="280">
          <template #default="scope">
            <el-button size="small" @click="editRoute(scope.row)">编辑</el-button>
            <el-button
              v-if="scope.row.status === 'ACTIVE'"
              size="small"
              type="warning"
              @click="openSuspendDialog(scope.row)"
            >封航停运</el-button>
            <el-button size="small" type="danger" @click="deleteRoute(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑航线：运营状态不允许直接编辑，停运只能走“封航停运” -->
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
        <el-form-item v-if="isEdit" label="运营状态">
          <el-tag :type="form.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ form.status === 'ACTIVE' ? '运营中' : '已停运' }}
          </el-tag>
          <span class="status-tip">（停运请使用“封航停运”，系统会同步拆绑座椅并留清单）</span>
        </el-form-item>
        <el-form-item v-else label="状态">
          <el-select v-model="form.status">
            <el-option label="运营中" value="ACTIVE" />
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

    <!-- 封航停运确认 -->
    <el-dialog v-model="showSuspendDialog" title="汛期临时封航停运" width="520px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="封航将一次性完成两件事"
        description="航线上挂载的座椅全部拆下（资产看板配套数立即归零）；同时按本次封航生成逐椅挂载清单台账，供复航核对。任一步骤失败整次封航自动撤销，航线保持运营中、椅子全部回到原航线。"
        style="margin-bottom: 16px"
      />
      <el-descriptions :column="1" border v-if="suspendTarget">
        <el-descriptions-item label="航线">{{ suspendTarget.routeCode }} / {{ suspendTarget.routeName }}</el-descriptions-item>
        <el-descriptions-item label="航段">{{ suspendTarget.startPort }} → {{ suspendTarget.endPort }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="suspendForm" label-width="100px" style="margin-top: 16px">
        <el-form-item label="操作人">
          <el-input v-model="suspendForm.operator" placeholder="调度员工号/姓名" />
        </el-form-item>
        <el-form-item label="封航原因">
          <el-input v-model="suspendForm.reason" type="textarea" placeholder="如：汛期临时封航" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSuspendDialog = false">取消</el-button>
        <el-button type="danger" :loading="suspendLoading" @click="confirmSuspend">确认封航停运</el-button>
      </template>
    </el-dialog>

    <!-- 历次封航批次 -->
    <el-dialog v-model="showBatchesDialog" title="封航清单查询（复航逐把核对）" width="820px">
      <el-table :data="batches" border v-loading="batchesLoading">
        <el-table-column prop="batchNo" label="封航批次号" width="260" />
        <el-table-column label="停运航线">
          <template #default="scope">
            {{ scope.row.oldRouteCode }} / {{ scope.row.oldRouteName }}
          </template>
        </el-table-column>
        <el-table-column prop="seatCount" label="拆下椅数" width="90" />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column label="封航时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.changeTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="scope">
            <el-button size="small" @click="openManifest(scope.row.batchNo)">挂载清单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showBatchesDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 某次封航的逐椅挂载清单 -->
    <el-dialog v-model="showManifestDialog" :title="`封航挂载清单（批次 ${manifestBatchNo}）`" width="760px">
      <el-alert
        type="info"
        :closable="false"
        title="以下为停运当时每把椅子的原挂载记录，复航时逐把核对后重新挂回"
        style="margin-bottom: 12px"
      />
      <el-table :data="manifest" border v-loading="manifestLoading">
        <el-table-column prop="seatCode" label="座椅编号" width="140" />
        <el-table-column label="原挂航线">
          <template #default="scope">
            {{ scope.row.oldRouteCode }} / {{ scope.row.oldRouteName }}
          </template>
        </el-table-column>
        <el-table-column prop="changeReason" label="封航原因" width="140" />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column label="封航时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.changeTime) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showManifestDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  routeApi,
  type Route,
  type SuspensionBatch,
  type ChangeRecord
} from '@/api'

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

const openAddDialog = () => {
  isEdit.value = false
  editingId = null
  form.routeCode = ''
  form.routeName = ''
  form.startPort = ''
  form.endPort = ''
  form.status = 'ACTIVE'
  form.remark = ''
  showAddDialog.value = true
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
    await ElMessageBox.confirm(
      `确认删除航线 ${route.routeCode} / ${route.routeName}？`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await routeApi.delete(route.id)
    ElMessage.success('删除成功')
    await loadRoutes()
  } catch (error: any) {
    console.error('删除失败:', error)
    ElMessage.error(error.message || '删除失败')
  }
}

// ---- 封航停运 ----

const showSuspendDialog = ref(false)
const suspendLoading = ref(false)
const suspendTarget = ref<Route | null>(null)
const suspendForm = reactive({ operator: '', reason: '汛期临时封航' })

const openSuspendDialog = (route: Route) => {
  suspendTarget.value = route
  suspendForm.operator = ''
  suspendForm.reason = '汛期临时封航'
  showSuspendDialog.value = true
}

const confirmSuspend = async () => {
  if (!suspendTarget.value) return
  suspendLoading.value = true
  try {
    const result = await routeApi.suspend(suspendTarget.value.id, {
      operator: suspendForm.operator || undefined,
      reason: suspendForm.reason || undefined
    })
    showSuspendDialog.value = false
    ElMessageBox.alert(
      `航线 ${result.routeCode} 已停运；本次封航拆下座椅 ${result.seatCount} 把，` +
      `配套看板已归零，挂载清单批次号：${result.batchNo}。复航时凭批次号在“封航清单查询”中逐把核对。`,
      '封航生效',
      { type: 'success', confirmButtonText: '知道了' }
    )
    await loadRoutes()
  } catch (error: any) {
    // 后端事务回滚时返回明确错误：航线仍运营中，已拆椅子全部回挂
    console.error('封航失败:', error)
    ElMessage.error((error?.response?.data?.message) || error.message || '封航失败，整次操作已撤销')
    await loadRoutes()
  } finally {
    suspendLoading.value = false
  }
}

// ---- 封航清单查询 ----

const showBatchesDialog = ref(false)
const batchesLoading = ref(false)
const batches = ref<SuspensionBatch[]>([])

const openBatchesDialog = async () => {
  showBatchesDialog.value = true
  batchesLoading.value = true
  try {
    batches.value = await routeApi.suspensionBatches()
  } catch (error: any) {
    console.error('加载封航批次失败:', error)
    ElMessage.error(error.message || '加载封航批次失败')
  } finally {
    batchesLoading.value = false
  }
}

const showManifestDialog = ref(false)
const manifestLoading = ref(false)
const manifest = ref<ChangeRecord[]>([])
const manifestBatchNo = ref('')

const openManifest = async (batchNo: string) => {
  manifestBatchNo.value = batchNo
  manifest.value = []
  showManifestDialog.value = true
  manifestLoading.value = true
  try {
    manifest.value = await routeApi.suspensionManifest(batchNo)
  } catch (error: any) {
    console.error('加载挂载清单失败:', error)
    ElMessage.error(error.message || '加载挂载清单失败')
  } finally {
    manifestLoading.value = false
  }
}

const formatTime = (t?: string | null) => {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 19)
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

.status-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
