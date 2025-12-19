# RouteFinder 全量缓存计划（2025-12-20）

## 目标：让后端 1:1 恢复 MTR 选路

1. **必须**：对每个 `dimension + routeId` 主动调用一次 `RailwayDataRouteFinderModule.findRoute`（不要再依赖 `MtrSnapshotCache`）；
2. **持久化**：将 `findRoute` 的最终输出写进 `config/beacon-provider/routefinder-cache.sqlite`，字段包括：
   * `dimension`, `routeId`, `railwayDataVersion`, `routefinderVersion`（版本校验）；
   * `polylinePoints`（推荐格式：`[{x,y,z}]` + `step`/`formatVersion`），或者至少 `edges[]`（按 `RouteFinderData` 顺序列出 `fromPos→toPos`、`routingSource`、`index`、`density`）；
   * `nodes[]`（routefinder 的 node 序列）+ `cost`/`penalty` breakdown；
   * `state`（`startPos`/`endPos`/`tickStage`、`globalBlacklist`/`localBlacklist`）——请用 fastutil API 读取 `LongArrayList`/`Long2IntMap`，转成普通集合，避免 `wrapped is null`。
3. **调度**：启动时、每小时以及每次 MTR 版本变动时以异步任务（非主线程）逐条 route 执行 `findRoute`，分批限速以免阻塞主线程或触发 stack overflow；每次完工都刷新 SQLite 缓存的 timestamp/版本。
4. **Provider 职责**：不再实时调用 `findRoute`，只提供新的 action 从 `routefinder-cache.sqlite` 读取记录并序列化成 JSON（比如 `routefinder:polyline`/`routefinder:edges`）。  
5. **错误修复**：state-action 要用 fastutil 的 `LongList`/`Long2IntMap` API（`getLong(int)`、`long2IntEntrySet()`）而不是直接 cast，确保 `globalBlacklist`/`localBlacklist` 能输出；否则会抛 `wrapped is null`，payload 为空。

请把这份清单直接转给 Beacon 团队，让他们知道需要做哪些缓存/导出工作，Provider 端负责读取并输出 pre-computed SQLite 数据即可。

## 阶段性实施计划（Markdown Todo 清单）

### 阶段 1：SQLite 缓存基础
- [x] 设计 `config/beacon-provider/routefinder-cache.sqlite` 架构：表字段包括 dimension/routeId/railwayDataVersion/routefinderVersion/formatVersion/timestamp + JSON 字段 (`polylinePoints`, `edges`, `nodes`, `state`, `costBreakdown`)。
- [x] 提供 Java 工具类负责初始化数据库、执行 CRUD（支持 `upsert`），并用 JSON 字段序列化 polyline/edges/state（允许后续切到 Msgpack）。
- [x] 把 schema 设计、工具入口、版本字段写入合约文档，确保 Beacon 端知道 formatVersion 的含义及升级方式。

### 阶段 2：异步 RouteFinder 计算 + 缓存填充
- [x] 在 mod 里引入调度器（启动时/每小时/版本变化触发），后台线程逐个遍历所有 `RailwayData.routes`；每次调用 `RailwayDataRouteFinderModule.findRoute(startPos, endPos, ...)`，并从 `RouteFinderData` 结果中构建 `edges`/`nodes`。
- [x] 同步 `state`（`startPos`, `endPos`, `tickStage`, `globalBlacklist`, `localBlacklist`）以 fastutil 安全方式读取并写入缓存，附加基础 cost 信息；封装 `RouteFinderCacheEntry` 放入 SQLite。
- [x] 控制调用频次（单线程逐条 + 轻微延迟），避免 `findRoute` 触发堆栈深度；记录更新时间和格式版本，并清理版本不一致/缺失线路的数据。
- [ ] 后续补齐：polyline 采样（或完整 cost/penalty breakdown），让缓存达到 1:1 几何输出标准。

### 阶段 3：Provider 读取缓存 + action 输出
- [x] 新增 Provider action `mtr:get_routefinder_cache` 直接从 SQLite 读取对应 `routeId` 的记录，并用 JSON 结构返回（无 `routeId` 时返回摘要列表）。
- [x] 更新 docs/tests （`docs/Beacon Actions.md`, `tests/test-actions.js`）说明缓存 action 的 payload/更新频率，并在 `.agents` 记录接口说明。
- [ ] 保留回退策略：若缓存缺失，Provider 可降级读取 `RailwayData` 快照并告警，便于部署早期过渡。

### 阶段 4：监控与维护
- [ ] 在 cache 工具里记录每次 rollup 的 `timestamp`/`routefinderVersion`/`railwayDataVersion`，并提供一个 “force refresh” 命令（或 config flag）。
- [ ] 添加简易监控日志：输出每次任务计算了多少 route、用了多少时间、失败条数，方便排查 `no-path`。
- [x] 将上述状态写入 `.agents/20251220/routefinder-cache-plan.md` 的 summary，以便后续接手的同事能直接参考实施步骤。
