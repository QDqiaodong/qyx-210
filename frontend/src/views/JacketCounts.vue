<template>
  <div class="jacket-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>救生衣按航线清点</span>
          <el-button @click="load">刷新</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="按航线登记额定件数、实点件数和清点人"
        description="实点少于额定时，该航线上已排好但还没开航的班次会在排班页标成“缺衣待补”，且不能再新排班次；实点补到不少于额定后恢复。两人同时交同一条航线的清点时，只留下先写完的那份。"
        style="margin-bottom: 16px"
      />

      <el-table :data="rows" border v-loading="loading">
        <el-table-column prop="routeCode" label="航线编码" width="160" />
        <el-table-column prop="routeName" label="航线名称" />
        <el-table-column label="额定件数" width="100" align="center">
          <template #default="scope">
            <span v-if="scope.row.requiredCount != null">{{ scope.row.requiredCount }}</span>
            <span v-else class="muted">未登记</span>
          </template>
        </el-table-column>
        <el-table-column label="实点件数" width="100" align="center">
          <template #default="scope">
            <span v-if="scope.row.actualCount != null">{{ scope.row.actualCount }}</span>
            <span v-else class="muted">未清点</span>
          </template>
        </el-table-column>
        <el-table-column prop="counter" label="清点人" width="120">
          <template #default="scope">
            <span v-if="scope.row.counter">{{ scope.row.counter }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="清点结果" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.actualCount == null" type="info">未清点</el-tag>
            <el-tag v-else-if="scope.row.shortage" type="danger">
              短缺 {{ scope.row.shortageCount }} 件
            </el-tag>
            <el-tag v-else type="success">足额</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="清点时间" width="170">
          <template #default="scope">{{ formatTime(scope.row.countTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openDialog(scope.row)">
              {{ scope.row.actualCount == null ? '提交清点' : '重新清点' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 清点登记：额定件数、实点件数、清点人 -->
    <el-dialog v-model="showDialog" :title="`救生衣清点 · ${form.routeName}`" width="480px">
      <el-alert
        v-if="form.shortageHint"
        type="warning"
        :closable="false"
        show-icon
        :title="form.shortageHint"
        style="margin-bottom: 16px"
      />
      <el-form :model="form" label-width="100px">
        <el-form-item label="航线">
          <span>{{ form.routeCode }} / {{ form.routeName }}</span>
        </el-form-item>
        <el-form-item label="额定件数" required>
          <el-input-number v-model="form.requiredCount" :min="0" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="实点件数" required>
          <el-input-number v-model="form.actualCount" :min="0" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="清点人" required>
          <el-input v-model="form.counter" placeholder="清点员工号/姓名" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存清点</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { jacketCountApi, type JacketCount } from '@/api'

const rows = ref<JacketCount[]>([])
const loading = ref(false)
const showDialog = ref(false)
const saving = ref(false)

const form = reactive({
  routeId: 0 as number,
  routeCode: '',
  routeName: '',
  requiredCount: 0,
  actualCount: 0,
  counter: '',
  remark: '',
  // 打开表单时看到的版本；保存时原样带回，两人同时交时后写者会被后端拒绝
  expectedVersion: null as number | null,
  shortageHint: ''
})

const load = async () => {
  loading.value = true
  try {
    rows.value = await jacketCountApi.getAll()
  } catch (error: any) {
    console.error('加载救生衣清点失败:', error)
    ElMessage.error(error.message || '加载救生衣清点失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (row: JacketCount) => {
  form.routeId = row.routeId
  form.routeCode = row.routeCode
  form.routeName = row.routeName
  form.requiredCount = row.requiredCount ?? 0
  form.actualCount = row.actualCount ?? 0
  form.counter = row.counter ?? ''
  form.remark = row.remark ?? ''
  form.expectedVersion = row.version
  form.shortageHint = row.shortage
    ? `当前实点 ${row.actualCount} 件、额定 ${row.requiredCount} 件，短缺 ${row.shortageCount} 件，未开航班次已在排班页标为缺衣待补`
    : ''
  showDialog.value = true
}

const save = async () => {
  if (form.counter.trim() === '') {
    ElMessage.warning('请填写清点人')
    return
  }
  saving.value = true
  try {
    const saved = await jacketCountApi.submit(form.routeId, {
      requiredCount: form.requiredCount,
      actualCount: form.actualCount,
      counter: form.counter.trim(),
      expectedVersion: form.expectedVersion,
      remark: form.remark || undefined
    })
    showDialog.value = false
    if (saved.shortage) {
      ElMessage.warning(`清点已保存：短缺 ${saved.shortageCount} 件，未开航班次已标缺衣待补，补齐前不能新排班次`)
    } else {
      ElMessage.success('清点已保存：救生衣足额，可继续排新班')
    }
    await load()
  } catch (error: any) {
    // 409：同一航线已有他人先写完的清点，本次未保存，需刷新看到先写完的实点后重新填写
    if (error.code === 409) {
      ElMessage.error(error.message || '该航线已有他人先完成的清点，请刷新后重新清点')
      showDialog.value = false
      await load()
    } else {
      ElMessage.error(error.message || '清点保存失败，班次标记保持保存前的样子')
    }
  } finally {
    saving.value = false
  }
}

const formatTime = (t?: string | null) => {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 19)
}

onMounted(load)
</script>

<style scoped>
.jacket-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.muted {
  color: #909399;
}
</style>
