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
