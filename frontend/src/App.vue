<template>
  <el-container class="app-container">
    <el-aside width="200px" class="aside">
      <div class="logo">
        <h2>座椅航线统计系统</h2>
      </div>
      <el-menu :default-active="activeMenu" class="menu" @select="handleMenuSelect">
        <el-menu-item index="/dashboard">
          <el-icon><PieChart /></el-icon>
          <span>统计看板</span>
        </el-menu-item>
        <el-menu-item index="/routes">
          <el-icon><Ship /></el-icon>
          <span>航线管理</span>
        </el-menu-item>
        <el-menu-item index="/seats">
          <el-icon><Grid /></el-icon>
          <span>座椅管理</span>
        </el-menu-item>
        <el-menu-item index="/records">
          <el-icon><Document /></el-icon>
          <span>变更台账</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-title">{{ pageTitle }}</div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { PieChart, Ship, Grid, Document } from '@element-plus/icons-vue'

const route = useRoute()

const menuTitleMap: Record<string, string> = {
  '/dashboard': '航线资产统计看板',
  '/routes': '通航航线管理',
  '/seats': '候船座椅管理',
  '/records': '变更台账'
}

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => menuTitleMap[route.path] || '')

const handleMenuSelect = (index: string) => {
  window.location.href = index
}
</script>

<style scoped>
.app-container {
  height: 100vh;
}

.aside {
  background-color: #1a1a2e;
  color: #fff;
}

.logo {
  padding: 20px;
  border-bottom: 1px solid #2d2d44;
}

.logo h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.menu {
  border-right: none;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 20px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  line-height: 60px;
}

.main {
  padding: 20px;
  background-color: #f5f5f5;
}
</style>