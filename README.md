# 内河航运码头候船座椅航线绑定统计系统

## 项目简介

本系统用于内河码头后勤管理候船座椅，核心功能是按航运航线自动统计各航线配套座椅总量。座椅可绑定对应通航航线，当航线班次调整时可更换座椅归属航线，系统自动计算每条航线配套座椅数量，并生成航线配套资产统计看板。

## 技术栈

### 后端
- **框架**: Spring Boot 3.3
- **语言**: JDK 17
- **数据库**: MySQL 8.0
- **缓存**: Redis 7
- **构建工具**: Maven

### 前端
- **框架**: Vue 3 + Vite
- **语言**: TypeScript
- **UI组件**: Element Plus
- **图表**: ECharts
- **路由**: Vue Router

### 容器化
- **Docker**: 20.10+
- **Docker Compose**: 3.8

## 功能模块

1. **候船座椅基础建档**: 编号、材质、摆放码头候船区、尺寸规格
2. **通航航线初始归属绑定**: 分配座椅对应航运航线
3. **航线归属调整登记**: 留存变更台账
4. **自动统计每条航线配套座椅总量**
5. **航线资产统计看板**: 可视化图表展示
6. **救生衣按航线清点与排班联动**（见下节）

## 救生衣清点与排班联动

调度按航线清点救生衣，系统按航线登记**额定件数、实点件数、清点人**，并在清点保存的同一事务内联动排班页班次标记。规则如下：

- **短缺联动**：实点少于额定时，该航线上**已排好、开航时刻还没到**的班次全部标成`缺衣待补`；**已经开航的班次一律不改**。
- **禁止新班**：短缺期间，不能再给这条航线新排还没开航的班次（后端直接拒绝，不是只在航线旁写一个短缺数字还放行）。
- **补够恢复**：实点补到不少于额定后，新班可以再排；此前被标成`缺衣待补`且开航时刻仍没到的班次自动恢复正常。但**已经标成`缺衣待补`、且开航时刻已经过了的班次不会自动改回正常**（它带着缺衣事实开过了航，标记作为历史留存）。
- **并发只留先写完者**：两人同时给同一航线交清点时，后写完者持过期版本提交会被拒绝（409），只留下先写完的那份实点；必须刷新看到先写完者的结果后再决定。
- **保存失败回滚**：清点记录与班次标记在同一数据库事务内提交，任一步失败整体回滚——清点记录不留痕，排班页各班次标记仍是保存前的样子。

### 救生衣清点 / 排班接口

- `GET /api/jacket-counts` - 按航线清点一览（每条航线的额定件数、实点件数、清点人；未清点航线也列出）
- `GET /api/jacket-counts/route/{routeId}` - 查单条航线清点结果
- `PUT /api/jacket-counts/route/{routeId}` - 提交/更新清点（同一事务联动班次标记；`expectedVersion` 为页面看到的版本，首次清点为 `null`，并发冲突返回 409）
- `GET /api/voyages?routeId=` - 排班页班次（按开航时刻升序，含正常/缺衣待补标记与是否已开航）
- `POST /api/voyages` - 新排班次（航线救生衣短缺时返回 409 拒绝）
- `DELETE /api/voyages/{id}` - 删除班次（已开航班次前端禁删）

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8131 |
| 后端API | http://localhost:8140 |
| MySQL | localhost:3356 |
| Redis | localhost:6429 |

## 环境变量

端口配置文件: `.env`

```env
SERVER_PORT=8140
FRONTEND_PORT=8131
DB_HOST=mysql
DB_PORT=3306
DB_NAME=seat_stats
DB_USERNAME=admin
DB_PASSWORD=password
REDIS_HOST=redis
REDIS_PORT=6379
LOCAL_DB_PORT=3356
LOCAL_REDIS_PORT=6429
```

## 启动方式

### 方式一：Docker Compose（推荐）

```bash
# 启动服务
docker compose up -d --build

# 或使用启动脚本
./start.sh

# 停止服务
docker compose down

# 或使用停止脚本
./stop.sh
```

### 方式二：本地开发

**后端**:
```bash
cd backend
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
mvn spring-boot:run
```

**前端**:
```bash
cd frontend
npm ci
npm run dev
```

## 项目结构

```
qyx-210/
├── backend/                    # 后端项目
│   ├── src/main/java/com/example/seatstats/
│   │   ├── controller/         # REST API控制器
│   │   ├── service/            # 业务逻辑层
│   │   ├── repository/         # 数据访问层
│   │   ├── entity/             # 实体类
│   │   ├── dto/                # 数据传输对象
│   │   ├── config/             # 配置类
│   │   └── exception/          # 异常处理
│   ├── src/main/resources/
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                   # 前端项目
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   ├── api/                # API接口封装
│   │   └── router/             # 路由配置
│   ├── index.html
│   ├── vite.config.ts
│   ├── package.json
│   └── Dockerfile
├── docker-compose.yml          # Docker Compose配置
├── .env                        # 环境变量
├── .gitignore
├── .dockerignore
├── start.sh                    # 启动脚本
├── stop.sh                     # 停止脚本
└── README.md
```

## API接口

### 航线管理
- GET `/api/routes` - 获取所有航线
- POST `/api/routes` - 创建航线
- PUT `/api/routes/{id}` - 更新航线（运营状态不可通过编辑直接修改）
- DELETE `/api/routes/{id}` - 删除航线
- POST `/api/routes/{id}/suspend` - 汛期临时封航停运（原子操作）
- GET `/api/routes/suspensions/batches` - 历次封航批次一览
- GET `/api/routes/suspensions/{batchNo}/manifest` - 按批次号查封航时的逐椅挂载清单

#### 封航停运的原子性保证

封航在**一个数据库事务**内同时完成两件事，任一失败整体回滚：

1. 当时还挂在这条航线上的座椅全部拆下（`seat.route_id` 置空），资产看板中该航线的配套件数立即归零；
2. 为本次封航生成一份逐椅挂载清单：每把椅子一条 `SUSPEND` 变更台账（共享同一封航批次号 `suspendBatchNo`），记录停运当时每把椅子原先挂在哪条航线，供复航逐把核对。台账只追加，不删除、不改写成"从未挂载"。

失败回滚保证（例如某把椅子写不出台账）：

- 航线仍保持 `ACTIVE` 运营中；
- 已经拆下的椅子全部回到原航线，看板配套数恢复原值；
- 不会留下半份封航台账；
- 不会出现"航线已停运却还挂着椅子"或"航线仍运营但配套已先清零"两种中间态。

并发封航通过航线行级悲观锁串行化：第二次封航在拿到锁后会发现航线已停运并被拒绝。
停运航线不会被看板隐藏，只是配套数显示为 0、状态显示"已停运"；座椅也不能再被手动挂到停运航线上（需先复航）。

请求体示例：

```json
POST /api/routes/1/suspend
{ "operator": "调度员甲", "reason": "汛期临时封航" }
```

### 座椅管理
- GET `/api/seats` - 获取所有座椅
- POST `/api/seats` - 创建座椅
- PUT `/api/seats/{id}` - 更新座椅
- POST `/api/seats/{id}/bind` - 绑定航线
- POST `/api/seats/{id}/unbind` - 解绑航线

### 统计接口
- GET `/api/stats/routes` - 获取航线座椅统计
- GET `/api/stats/summary` - 获取统计摘要

### 变更台账
- GET `/api/records` - 获取变更记录

## 缓存策略

- Redis缓存座椅尺寸规格，设置7天过期策略
- 航线统计结果使用Redis缓存，提高查询性能

## 数据库索引优化

- 座椅表: route_id索引，支持航线维度查询
- 变更台账表: seat_id、change_type索引，支持快速查询