<template>
  <div class="dashboard">
    <div class="stats-cards">
      <el-card class="stat-card">
        <div class="stat-icon seats-icon">
          <el-icon><Grid /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ summary.totalSeats }}</div>
          <div class="stat-label">座椅总数</div>
        </div>
      </el-card>
      <el-card class="stat-card">
        <div class="stat-icon routes-icon">
          <el-icon><Ship /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ summary.totalRoutes }}</div>
          <div class="stat-label">航线总数</div>
        </div>
      </el-card>
    </div>

    <el-card class="chart-card">
      <template #header>
        <div class="card-header">
          <span>航线配套座椅统计</span>
          <el-button type="primary" size="small" @click="refreshData">刷新数据</el-button>
        </div>
      </template>
      <div ref="chartRef" class="chart"></div>
    </el-card>

    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>航线座椅明细统计</span>
        </div>
      </template>
      <el-table :data="stats" border>
        <el-table-column prop="routeCode" label="航线编码" />
        <el-table-column prop="routeName" label="航线名称" />
        <el-table-column prop="startPort" label="起点码头" />
        <el-table-column prop="endPort" label="终点码头" />
        <el-table-column prop="seatCount" label="配套座椅数">
          <template #default="scope">
            <el-tag :type="scope.row.seatCount > 0 ? 'success' : 'warning'">
              {{ scope.row.seatCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ scope.row.status === 'ACTIVE' ? '运营中' : '已停运' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Grid, Ship } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { statsApi, type SeatStat, type Summary } from '@/api'

const summary = ref<Summary>({ totalSeats: 0, totalRoutes: 0 })
const stats = ref<SeatStat[]>([])
const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!chartInstance) return
  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: stats.value.map(s => s.routeName),
      axisLabel: {
        rotate: 30,
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      name: '座椅数量'
    },
    series: [
      {
        name: '配套座椅数',
        type: 'bar',
        data: stats.value.map(s => s.seatCount),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#67c23a' },
            { offset: 1, color: '#85ce61' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }
  chartInstance.setOption(option)
}

const handleResize = () => {
  chartInstance?.resize()
}

const refreshData = async () => {
  await loadData()
}

const loadData = async () => {
  try {
    summary.value = await statsApi.getSummary()
    stats.value = await statsApi.getRouteStats()
    updateChart()
  } catch (error) {
    console.error('加载数据失败:', error)
  }
}

onMounted(() => {
  loadData()
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stats-cards {
  display: flex;
  gap: 20px;
}

.stat-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.seats-icon {
  background: linear-gradient(135deg, #67c23a, #85ce61);
  color: #fff;
}

.routes-icon {
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-card {
  flex: 1;
}

.chart {
  height: 400px;
}

.table-card {
  flex: 1;
}
</style>