<template>
  <div class="records-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>变更台账</span>
          <el-radio-group v-model="typeFilter" size="small" @change="loadRecords">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="BIND">绑定</el-radio-button>
            <el-radio-button label="UNBIND">解绑</el-radio-button>
            <el-radio-button label="SUSPEND">封航清单</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table :data="records" border>
        <el-table-column prop="seatCode" label="座椅编号" width="130" />
        <el-table-column prop="changeType" label="变更类型" width="100">
          <template #default="scope">
            <el-tag :type="typeTag(scope.row.changeType)" effect="plain">
              {{ typeLabel(scope.row.changeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="suspendBatchNo" label="封航批次号" width="250">
          <template #default="scope">
            <span v-if="scope.row.suspendBatchNo" class="batch-no">{{ scope.row.suspendBatchNo }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="原航线" width="180">
          <template #default="scope">
            <span>{{ scope.row.oldRouteName || '无' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="新航线" width="180">
          <template #default="scope">
            <span>{{ scope.row.newRouteName || '无' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="changeReason" label="变更原因" min-width="140" />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column label="时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.changeTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="scope">
            <el-button size="small" type="danger" @click="deleteRecord(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { recordApi, type ChangeRecord } from '@/api'

const records = ref<ChangeRecord[]>([])
const typeFilter = ref<'ALL' | 'BIND' | 'UNBIND' | 'SUSPEND'>('ALL')

const typeLabel = (t: string) => {
  if (t === 'BIND') return '绑定'
  if (t === 'UNBIND') return '解绑'
  if (t === 'SUSPEND') return '封航拆绑'
  return t
}

const typeTag = (t: string) => {
  if (t === 'BIND') return 'success'
  if (t === 'UNBIND') return 'warning'
  if (t === 'SUSPEND') return 'danger'
  return 'info'
}

const formatTime = (t?: string | null) => {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 19)
}

const loadRecords = async () => {
  try {
    if (typeFilter.value === 'ALL') {
      records.value = await recordApi.getAll()
    } else {
      records.value = await recordApi.getByType(typeFilter.value)
    }
  } catch (error) {
    console.error('加载变更记录失败:', error)
    ElMessage.error('加载变更记录失败')
  }
}

const deleteRecord = async (record: ChangeRecord) => {
  try {
    await recordApi.delete(record.id)
    ElMessage.success('删除成功')
    await loadRecords()
  } catch (error: any) {
    console.error('删除失败:', error)
    ElMessage.error(error.message || '删除失败')
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<style scoped>
.records-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.batch-no {
  font-family: monospace;
  color: #e6a23c;
}

.muted {
  color: #c0c4cc;
}
</style>
