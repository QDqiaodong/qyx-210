import axios, { type AxiosInstance } from 'axios'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const instance = axios.create({
  baseURL: '/api',
  timeout: 10000
})

instance.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse<any>
    if (res.code !== 200) {
      throw new Error(res.message || '请求失败')
    }
    return res.data
  },
  (error) => {
    return Promise.reject(error)
  }
)

const api = instance as Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete'> & {
  get<T>(url: string): Promise<T>
  post<T>(url: string, data?: any): Promise<T>
  put<T>(url: string, data?: any): Promise<T>
  delete<T>(url: string): Promise<T>
}

export interface Route {
  id: number
  routeCode: string
  routeName: string
  startPort: string
  endPort: string
  status: string
  remark: string
}

export interface Seat {
  id: number
  seatCode: string
  material: string
  waitingArea: string
  sizeSpec: string
  routeId: number | null
  routeCode?: string | null
  routeName?: string | null
  status: string
  remark: string
}

export interface SeatStat {
  routeId: number
  routeCode: string
  routeName: string
  startPort: string
  endPort: string
  seatCount: number
  status: string
}

export interface ChangeRecord {
  id: number
  seatId: number
  seatCode: string
  changeType: string
  oldRouteCode: string | null
  oldRouteName: string | null
  newRouteCode: string | null
  newRouteName: string | null
  changeReason: string | null
  operator: string
  remark: string | null
}

export interface Summary {
  totalSeats: number
  totalRoutes: number
}

export const routeApi = {
  getAll: () => api.get<Route[]>('/routes'),
  getById: (id: number) => api.get<Route>(`/routes/${id}`),
  create: (data: Omit<Route, 'id'>) => api.post<Route>('/routes', data),
  update: (id: number, data: Partial<Route>) => api.put<Route>(`/routes/${id}`, data),
  delete: (id: number) => api.delete<void>(`/routes/${id}`)
}

export const seatApi = {
  getAll: () => api.get<Seat[]>('/seats'),
  getById: (id: number) => api.get<Seat>(`/seats/${id}`),
  getByRoute: (routeId: number) => api.get<Seat[]>(`/seats/route/${routeId}`),
  getSizeSpec: (seatCode: string) => api.get<string>(`/seats/size/${seatCode}`),
  create: (data: Omit<Seat, 'id'>) => api.post<Seat>('/seats', data),
  update: (id: number, data: Partial<Seat>) => api.put<Seat>(`/seats/${id}`, data),
  bindRoute: (id: number, routeId: number, operator?: string, reason?: string) =>
    api.post<Seat>(`/seats/${id}/bind`, { routeId, operator, reason }),
  unbindRoute: (id: number, operator?: string, reason?: string) =>
    api.post<Seat>(`/seats/${id}/unbind`, { operator, reason }),
  delete: (id: number) => api.delete<void>(`/seats/${id}`)
}

export const statsApi = {
  getRouteStats: () => api.get<SeatStat[]>('/stats/routes'),
  getRouteStat: (routeId: number) => api.get<SeatStat>(`/stats/routes/${routeId}`),
  getSummary: () => api.get<Summary>('/stats/summary')
}

export const recordApi = {
  getAll: () => api.get<ChangeRecord[]>('/records'),
  getById: (id: number) => api.get<ChangeRecord>(`/records/${id}`),
  getBySeat: (seatId: number) => api.get<ChangeRecord[]>(`/records/seat/${seatId}`),
  getByType: (changeType: string) => api.get<ChangeRecord[]>(`/records/type/${changeType}`),
  delete: (id: number) => api.delete<void>(`/records/${id}`)
}