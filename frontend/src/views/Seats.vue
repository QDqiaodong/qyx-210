<template>
  <div class="seats-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>座椅管理</span>
          <el-button type="primary" @click="showAddDialog = true">新增座椅</el-button>
        </div>
      </template>

      <el-table :data="seats" border>
        <el-table-column prop="seatCode" label="座椅编号" />
        <el-table-column prop="material" label="材质" />
        <el-table-column prop="waitingArea" label="候船区" />
        <el-table-column prop="sizeSpec" label="尺寸规格" />
        <el-table-column prop="routeName" label="所属航线">
          <template #default="scope">
            <el-tag v-if="scope.row.routeName" type="success">{{ scope.row.routeName }}</el-tag>
            <el-tag v-else type="warning">未绑定</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'IN_USE' ? 'success' : 'danger'">
              {{ scope.row.status === 'IN_USE' ? '使用中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作">
          <template #default="scope">
            <el-button size="small" @click="editSeat(scope.row)">编辑</el-button>
            <el-button size="small" type="primary" @click="bindRoute(scope.row)">绑定航线</el-button>
            <el-button size="small" type="warning" @click="unbindRoute(scope.row)">解绑</el-button>
            <el-button size="small" type="danger" @click="deleteSeat(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAddDialog" :title="isEdit ? '编辑座椅' : '新增座椅'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="座椅编号" required>
          <el-input v-model="form.seatCode" />
        </el-form-item>
        <el-form-item label="材质" required>
          <el-input v-model="form.material" />
        </el-form-item>
        <el-form-item label="候船区" required>
          <el-input v-model="form.waitingArea" />
        </el-form-item>
        <el-form-item label="尺寸规格">
          <el-input v-model="form.sizeSpec" />
        </el-form-item>
        <el-form-item label="所属航线">
          <el-select v-model="form.routeId" placeholder="请选择航线">
            <el-option v-for="route in routes" :key="route.id" :label="route.routeName" :value="route.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="使用中" value="IN_USE" />
            <el-option label="已停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSeat">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showBindDialog" title="绑定航线" width="400px">
      <el-form :model="bindForm" label-width="100px">
        <el-form-item label="目标航线" required>
          <el-select v-model="bindForm.routeId" placeholder="请选择航线">
            <el-option v-for="route in routes" :key="route.id" :label="route.routeName" :value="route.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="bindForm.operator" placeholder="默认SYSTEM" />
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="bindForm.reason" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBindDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmBind">确定绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { seatApi, routeApi, type Seat, type Route } from '@/api'

const seats = ref<Seat[]>([])
const routes = ref<Route[]>([])
const showAddDialog = ref(false)
const showBindDialog = ref(false)
const isEdit = ref(false)

const form = reactive({
  seatCode: '',
  material: '',
  waitingArea: '',
  sizeSpec: '',
  routeId: null as number | null,
  status: 'IN_USE',
  remark: ''
})

const bindForm = reactive({
  routeId: null as number | null,
  operator: '',
  reason: ''
})

let editingId: number | null = null
let bindingSeatId: number | null = null

const loadSeats = async () => {
  try {
    seats.value = await seatApi.getAll()
  } catch (error) {
    console.error('加载座椅失败:', error)
    ElMessage.error('加载座椅失败')
  }
}

const loadRoutes = async () => {
  try {
    routes.value = await routeApi.getAll()
  } catch (error) {
    console.error('加载航线失败:', error)
  }
}

const editSeat = (seat: Seat) => {
  isEdit.value = true
  editingId = seat.id
  form.seatCode = seat.seatCode
  form.material = seat.material
  form.waitingArea = seat.waitingArea
  form.sizeSpec = seat.sizeSpec
  form.routeId = seat.routeId
  form.status = seat.status
  form.remark = seat.remark
  showAddDialog.value = true
}

const saveSeat = async () => {
  try {
    if (!form.seatCode || !form.material || !form.waitingArea) {
      ElMessage.warning('请填写必填字段')
      return
    }

    if (isEdit.value && editingId) {
      await seatApi.update(editingId, form)
      ElMessage.success('更新成功')
    } else {
      await seatApi.create(form)
      ElMessage.success('创建成功')
    }

    showAddDialog.value = false
    await loadSeats()
  } catch (error: any) {
    console.error('保存失败:', error)
    ElMessage.error(error.message || '保存失败')
  }
}

const bindRoute = (seat: Seat) => {
  bindingSeatId = seat.id
  bindForm.routeId = null
  bindForm.operator = ''
  bindForm.reason = ''
  showBindDialog.value = true
}

const confirmBind = async () => {
  try {
    if (!bindForm.routeId || !bindingSeatId) {
      ElMessage.warning('请选择目标航线')
      return
    }

    await seatApi.bindRoute(bindingSeatId, bindForm.routeId, bindForm.operator || undefined, bindForm.reason || undefined)
    ElMessage.success('绑定成功')
    showBindDialog.value = false
    await loadSeats()
  } catch (error: any) {
    console.error('绑定失败:', error)
    ElMessage.error(error.message || '绑定失败')
  }
}

const unbindRoute = async (seat: Seat) => {
  try {
    await seatApi.unbindRoute(seat.id, 'SYSTEM', '手动解绑')
    ElMessage.success('解绑成功')
    await loadSeats()
  } catch (error: any) {
    console.error('解绑失败:', error)
    ElMessage.error(error.message || '解绑失败')
  }
}

const deleteSeat = async (seat: Seat) => {
  try {
    await seatApi.delete(seat.id)
    ElMessage.success('删除成功')
    await loadSeats()
  } catch (error: any) {
    console.error('删除失败:', error)
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => {
  loadSeats()
  loadRoutes()
})
</script>

<style scoped>
.seats-page {
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