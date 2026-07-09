<template>
  <div class="records-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>变更台账</span>
        </div>
      </template>

      <el-table :data="records" border>
        <el-table-column prop="seatCode" label="座椅编号" />
        <el-table-column prop="changeType" label="变更类型">
          <template #default="scope">
            <el-tag :type="scope.row.changeType === 'BIND' ? 'success' : 'warning'">
              {{ scope.row.changeType === 'BIND' ? '绑定' : '解绑' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原航线">
          <template #default="scope">
            <span>{{ scope.row.oldRouteName || '无' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="新航线">
          <template #default="scope">
            <span>{{ scope.row.newRouteName || '无' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="changeReason" label="变更原因" />
        <el-table-column prop="operator" label="操作人" />
        <el-table-column label="操作">
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

const loadRecords = async () => {
  try {
    records.value = await recordApi.getAll()
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
</style>