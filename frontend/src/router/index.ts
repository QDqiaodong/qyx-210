import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue')
  },
  {
    path: '/routes',
    name: 'Routes',
    component: () => import('../views/Routes.vue')
  },
  {
    path: '/jacket-counts',
    name: 'JacketCounts',
    component: () => import('../views/JacketCounts.vue')
  },
  {
    path: '/voyages',
    name: 'Voyages',
    component: () => import('../views/Voyages.vue')
  },
  {
    path: '/seats',
    name: 'Seats',
    component: () => import('../views/Seats.vue')
  },
  {
    path: '/records',
    name: 'Records',
    component: () => import('../views/Records.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router