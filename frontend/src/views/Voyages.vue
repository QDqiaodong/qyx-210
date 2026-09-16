<template>
  <div class="voyage-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>班次排班</span>
          <div>
            <el-select
              v-model="routeFilter"
              placeholder="全部航线"
              clearable
              style="width: 220px; margin-right: 12px"
              @change="loadVoyages"
            >
              <el-option v-for="r in routes" :key="r.id" :label="r.routeName" :value="r.id" />
            </el-select>
            <el-button @click="reload">刷新</el-button>
            <el-button type="primary" @click="openAddDialog">新排班次</el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="救生衣短缺约束"
        description="某航线实点少于额定时，该航线不能再新排还没开航的班次；已排好但开航时刻未到的班次自动标为“缺衣待补”，已开航的班次不改。补齐后新班可排，未开航的缺衣班次恢复正常；已经带着缺衣待补开过航的班次保留标记。"
        style="margin-bottom: 16px"
      />

      <el-table :data="voyages" border v-loading="loading">
        <el-table-column prop="voyageCode" label="班次编号" width="230" />
        <el-table-column label="航线">
          <template #default="scope">
            {{ scope.row.routeCode }} / {{ scope.row.routeName }}
          </template>
        </el-table-column>
        <el-table-column prop="vesselName" label="船名" width="120" />
        <el-table-column label="开航时刻" width="170">
          <template #default="scope">{{ formatTime(scope.row.departureTime) }}</template>
        </el-table-column>
        <el-table-column label="是否开航" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.departed ? 'info' : 'primary'" size="small">
              {{ scope.row.departed ? '已开航' : '未开航' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标记" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'JACKET_SHORT'" type="danger">缺衣待补</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button
              size="small"
              type="danger"
              :disabled="scope.row.departed"
              @click="removeVoyage(scope.row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新排班次：短缺航线在此被真正拦住，而不是只写个数字还能排上去 -->
    <el-dialog v-model="showAddDialog" title="新排班次" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="航线" required>
          <el-select v-model="form.routeId" placeholder="请选择航线" style="width: 100%">
            <el-option
              v-for="r in routeOptions"
              :key="r.id"
              :label="optionLabel(r)"
              :value="r.id"
              :disabled="r.shortage || r.status !== 'ACTIVE'"
            />
          </el-select>
          <div v-if="selectedShortage" class="block-tip">
            该航线救生衣短缺（额定 {{ selectedShortage.requiredCount }} 件、实点
            {{ selectedShortage.actualCount }} 件），缺衣补齐前不能新排班次。
          </div>
        </el-form-item>
        <el-form-item label="开航时刻" required>
          <el-date-picker
            v-model="form.departureTime"
            type="datetime"
            placeholder="选择开航时刻（必须晚于当前时刻）"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="船名">
          <el-input v-model="form.vesselName" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定排班</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  routeApi,
  voyageApi,
  jacketCountApi,
  type Route,
  type Voyage,
  type JacketCount
} from '@/api'

const routes = ref<Route[]>([])
const counts = ref<JacketCount[]>([])
const voyages = ref<Voyage[]>([])
const routeFilter = ref<number | null>(null)
const loading = ref(false)
const showAddDialog = ref(false)
const saving = ref(false)

const form = reactive({
  routeId: null as number | null,
  departureTime: '',
  vesselName: '',
  remark: ''
})

const countByRoute = computed(() => {
  const map = new Map<number, JacketCount>()
  for (const c of counts.value) map.set(c.routeId, c)
  return map
})

/** 航线下拉项附带短缺状态，短缺航线禁选 */
const routeOptions = computed(() =>
  routes.value.map((r) => ({
    ...r,
    shortage: countByRoute.value.get(r.id)?.shortage ?? false,
    status: r.status
  }))
)

const selectedShortage = computed(() =>
  form.routeId == null ? null : countByRoute.value.get(form.routeId) ?? null
)

const optionLabel = (r: Route & { shortage?: boolean }) => {
  const c = countByRoute.value.get(r.id)
  if (c?.shortage) return `${r.routeName}（救生衣短缺 ${c.shortageCount} 件，禁排）`
  if (r.status !== 'ACTIVE') return `${r.routeName}（已停运）`
  return r.routeName
}

const loadRoutesAndCounts = async () => {
  const [rs, cs] = await Promise.all([routeApi.getAll(), jacketCountApi.getAll()])
  routes.value = rs
  counts.value = cs
}

const loadVoyages = async () => {
  loading.value = true
  try {
    voyages.value = await voyageApi.getAll(routeFilter.value ?? undefined)
  } catch (error: any) {
    console.error('加载班次失败:', error)
    ElMessage.error(error.message || '加载班次失败')
  } finally {
    loading.value = false
  }
}

const reload = async () => {
  await loadRoutesAndCounts()
  await loadVoyages()
}

const openAddDialog = () => {
  form.routeId = null
  form.departureTime = ''
  form.vesselName = ''
  form.remark = ''
  showAddDialog.value = true
}

const save = async () => {
  if (form.routeId == null) {
    ElMessage.warning('请选择航线')
    return
  }
  if (!form.departureTime) {
    ElMessage.warning('请选择开航时刻')
    return
  }
  saving.value = true
  try {
    // 后端是最终闸口：即使前端状态过期，短缺航线的新班也会被 409 拒绝
    await voyageApi.create({
      routeId: form.routeId,
      departureTime: form.departureTime,
      vesselName: form.vesselName || undefined,
      remark: form.remark || undefined
    })
    ElMessage.success('排班成功')
    showAddDialog.value = false
    await reload()
  } catch (error: any) {
    // 409 = 救生衣短缺禁排：刷新清点结果，让短缺数字/禁选项立即生效
    if (error.code === 409) {
      ElMessage.error(error.message || '该航线救生衣短缺，不能新排班次')
      showAddDialog.value = false
      await reload()
    } else {
      ElMessage.error(error.message || '排班失败')
    }
  } finally {
    saving.value = false
  }
}

const removeVoyage = async (voyage: Voyage) => {
  try {
    await ElMessageBox.confirm(`确认删除班次 ${voyage.voyageCode}？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await voyageApi.delete(voyage.id)
    ElMessage.success('删除成功')
    await loadVoyages()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  }
}

const formatTime = (t?: string | null) => {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 19)
}

onMounted(reload)
</script>

<style scoped>
.voyage-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.block-tip {
  margin-top: 6px;
  color: #f56c6c;
  font-size: 12px;
  line-height: 1.5;
}
</style>
