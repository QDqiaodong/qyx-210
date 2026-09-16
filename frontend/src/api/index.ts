import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'

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
      // 业务失败（如救生衣短缺禁止排班 409、清点版本冲突 409、保存失败 500）：
      // 带上后端 code 与 message，调用处据此区分“并发冲突请刷新”等提示。
      const err = new Error(res.message || '请求失败') as Error & { code?: number }
      err.code = res.code
      throw err
    }
    return res.data
  },
  (error) => {
    return Promise.reject(error)
  }
)

const api = instance as Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete'> & {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
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
  suspendBatchNo?: string | null
  oldRouteCode: string | null
  oldRouteName: string | null
  newRouteCode: string | null
  newRouteName: string | null
  changeReason: string | null
  operator: string
  changeTime?: string | null
  remark: string | null
}

export interface SuspensionBatch {
  batchNo: string
  oldRouteCode: string | null
  oldRouteName: string | null
  operator: string
  changeReason: string | null
  changeTime: string
  seatCount: number
}

export interface RouteSuspension {
  routeId: number
  routeCode: string
  routeName: string
  status: string
  batchNo: string
  seatCount: number
  operator: string
  reason: string
  suspendedAt: string
  items: ChangeRecord[]
}

export interface Summary {
  totalSeats: number
  totalRoutes: number
}

/** 按航线的救生衣清点结果（未清点航线额定/实点/清点人为 null） */
export interface JacketCount {
  id: number | null
  routeId: number
  routeCode: string
  routeName: string
  requiredCount: number | null
  actualCount: number | null
  counter: string | null
  shortage: boolean
  shortageCount: number
  /** 乐观锁版本，再次提交时原样带回；首次清点为 null */
  version: number | null
  countTime?: string | null
  remark?: string | null
}

export interface JacketCountSubmit {
  requiredCount: number
  actualCount: number
  counter: string
  expectedVersion: number | null
  remark?: string
}

/** 班次（排班） */
export interface Voyage {
  id: number
  voyageCode: string
  routeId: number
  routeCode: string
  routeName: string
  departureTime: string
  vesselName?: string | null
  /** NORMAL 正常 / JACKET_SHORT 缺衣待补 */
  status: string
  remark?: string | null
  createdAt?: string | null
  /** 请求时点是否已开航（开航时刻已过） */
  departed: boolean
}

export interface VoyageCreate {
  voyageCode?: string
  routeId: number
  departureTime: string
  vesselName?: string
  remark?: string
}

export const routeApi = {
  getAll: () => api.get<Route[]>('/routes'),
  getById: (id: number) => api.get<Route>(`/routes/${id}`),
  create: (data: Omit<Route, 'id'>) => api.post<Route>('/routes', data),
  update: (id: number, data: Partial<Route>) => api.put<Route>(`/routes/${id}`, data),
  delete: (id: number) => api.delete<void>(`/routes/${id}`),
  /** 封航停运：原子拆下全部挂载座椅并生成本次封航挂载清单 */
  suspend: (id: number, data: { operator?: string; reason?: string }) =>
    api.post<RouteSuspension>(`/routes/${id}/suspend`, data),
  /** 历次封航批次一览 */
  suspensionBatches: () => api.get<SuspensionBatch[]>('/routes/suspensions/batches'),
  /** 按批次号查停运当时的挂载清单（复航逐把核对用） */
  suspensionManifest: (batchNo: string) =>
    api.get<ChangeRecord[]>(`/routes/suspensions/${encodeURIComponent(batchNo)}/manifest`)
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

/** 救生衣按航线清点 */
export const jacketCountApi = {
  /** 清点一览：每条航线的额定件数、实点件数、清点人（未清点航线也返回） */
  getAll: () => api.get<JacketCount[]>('/jacket-counts'),
  getByRoute: (routeId: number) => api.get<JacketCount | null>(`/jacket-counts/route/${routeId}`),
  /**
   * 提交清点（首次 version 传 null）。保存与班次标记同一事务，
   * 并发只留先写完者，后端返回 code=409 时需提示刷新后重试。
   */
  submit: (routeId: number, data: JacketCountSubmit) =>
    api.put<JacketCount>(`/jacket-counts/route/${routeId}`, data)
}

/** 排班（班次） */
export const voyageApi = {
  /** 排班页：全部班次按开航时刻升序；可按航线过滤 */
  getAll: (routeId?: number) =>
    api.get<Voyage[]>('/voyages', routeId == null ? undefined : { params: { routeId } }),
  /** 新排班次；航线救生衣短缺时后端拒绝（code=409），不是只写个短缺数字还放行 */
  create: (data: VoyageCreate) => api.post<Voyage>('/voyages', data),
  delete: (id: number) => api.delete<void>(`/voyages/${id}`)
}