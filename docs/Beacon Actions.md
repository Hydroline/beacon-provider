# Beacon Actions

Beacon Provider 只保留最小的 `action` 集合，专注于提供当前 Minecraft 世界的 MTR 数据快照：静态结构数据已经由 Bukkit 端通过 `world/mtr` 直接采集并缓存，Provider 只需要返回动态的 `RailwayData` 状态，供前端/Beacon 进行进一步的处理。

## 1. 可用 Action 一览

| Action 名称 | 说明 | 请求 `payload` | 响应结构 |
|-------------|------|----------------|----------|
| `beacon:ping` | 验证 Gateway 通信，并测量往返延迟。 | 可选：`echo` (`string`) | `echo`（原样返回）、`receivedAt`（服务器时间，ms）、`latencyMs`（处理耗时） |
| `mtr:get_railway_snapshot` | 返回一个或多个维度当前的 `RailwayData` 快照（MessagePack 格式 + Base64 编码），仅支持读操作。 | 可选：`dimension`（如 `minecraft:overworld`），不传则返回所有缓存的维度。 | `format: "messagepack"`，`snapshots` （数组，每项包含 `dimension`, `timestamp`, `length`, `payload`（Base64）） |
| `mtr:get_routefinder_snapshot` | 提供每条线路的节点序列（复用 `Route.platformIds` 生成的 `RouteNode[]`），用于前端确认应该渲染哪些节点。 | 可选：`dimension` | `snapshots`（数组，每项含 `dimension`, `routeId`, `name`, `nodes`） |
| `mtr:get_routefinder_data` | 以未经 `findRoute` 重新计算的 `RouteFinderData` 序列还原当前运行时选路器的节点流（`pos`, `duration`, `waitingTime`, `routeId`, `stationIds`）。 | 可选：`dimension` | `data`（数组，每项含 `dimension`, `pos`, `routeId`, `duration`, `waitingTime`, `stationIds`, `source`） |
| `mtr:get_routefinder_state` | 暴露 `RailwayDataRouteFinderModule` 的上下文状态（起始点/终点、tickStage、黑名单、调度计数），用于对齐惩罚/过滤逻辑。 | 可选：`dimension` | `state`（对象含 `dimension`, `startPos`, `endPos`, `totalTime`, `count`, `startMillis`, `tickStage`, `globalBlacklist`, `localBlacklist`） |
| `mtr:get_routefinder_edges` | 按 `RouteFinderData` 的顺序拼出每段 `fromPos` → `toPos`，与 `RailCurveSegment` 的 `fromPos/toPos` 对应，供后端直接拿来画曲线。 | 可选：`dimension` | `edges`（数组，每项含 `dimension`, `routeId`, `source`, `index`, `fromPos`, `toPos`, `connectionDensity`） |
| `mtr:get_connection_profile` | 暴露 `RailwayDataRouteFinderModule` 正在使用的 `platformConnections` 细节（`ConnectionDetails` 元字段），用于输送惩罚/持续时间、连接密度等参数。 | 可选：`dimension` | `profiles`（数组，每项含 `fromPos`, `toPos`, `platformStart`, `shortestDuration`, `durationInfo`, `connectionDensity`） |
| `mtr:get_platform_position_map` | 导出所有站台在轨道图上的位置（`pos_1`, `pos_2`, `midPos`, `platformStart`, `platformEnd`），供后端渲染起终点。 | 可选：`dimension` | `positions`（数组，每项含 `platformId`, `pos1`, `pos2`, `midPos`, `platformStart`, `platformEnd`） |
| `mtr:get_rail_curve_segments` | 聚合 `Rail` 实例中的双段曲线参数（`h/k/r/t/y`, `reverse`, `isStraight`），让后端可按原始公式采样 `points[]`。 | 可选：`dimension` | `segments`（数组，每项含 `fromPos`, `toPos`, `railType`, `transportMode`, `segment1`, `segment2`） |
| `mtr:get_routefinder_version` | 返回当前 Dimension 对应的 MTR 版本信息（mod 包版本、`RailwayData.DATA_VERSION`）。 | 可选：`dimension` | `version`（结构含 `dimension`, `mtrVersion`, `railwayDataVersion`） |

## 2. 新 Action 的响应说明

- `snapshots`：数组，每个元素对应一个维度的 `RailwayData`。  
  - `dimension`: 维度标识（`ResourceLocation` 字符串）。  
  - `timestamp`: 服务端序列化时的毫秒时间戳。  
  - `length`: 解码后的原始 MessagePack 字节数。  
  - `payload`: 使用 `Base64` 编码的 MessagePack 数据，解码后可交由 Bukkit/前端复用 `RailwayData` 模块提供的逻辑进一步解析。
- `format`（根级）：目前固定为 `"messagepack"`，用于说明 `payload` 的编码格式。

调用方只需解析 Base64 并交给 MessagePack 解析器，即可得到与 `world/mtr` 存储结构等价的 `stations`、`platforms`、`routes`、`depots`、`rails`、`signalBlocks`、`sidings` 等集合，用作进一步的 Leaflet 可视化或数据对比。

## 3. 路径/几何补充 Actions

- 新增的 `mtr:get_routefinder_snapshot` / `mtr:get_connection_profile` / `mtr:get_platform_position_map` / `mtr:get_rail_curve_segments` / `mtr:get_routefinder_version` / `mtr:get_routefinder_data` / `mtr:get_routefinder_state` / `mtr:get_routefinder_edges` 都依赖 `MtrSnapshotCache` 的快照数据，不会直接调用 `RailwayDataRouteFinderModule` 的 `findRoute` 等昂贵方法；它们只是从缓存里读现有的节点/连接/曲线参数。  
- 每个 action 都支持可选的 `dimension` 字段，只返回对应维度的数据；不传则返回当前快照里所有维度的合集。  
- Action 的响应结构可直接交给后端复用 `RouteNode[]`、`ConnectionDetails` 等数据，避免后端再额外采样 MTR 运行时的数据。

## 4. 扩展与注意事项

- provider 仍保留 `PingAction` 用于连接检测，所有 MTR 逻辑通过 `MtrQueryGateway` 的快照缓存（`MtrSnapshotCache`）读取，防止在主线程上频繁重新构建 `RailwayData`。  
- 如果需要覆盖维度筛选或补充额外的 `payload` 字段，可以在 Bukkit 端负责格式化，Provider 只负责将 `RailwayData` 原封不动地序列化为 MessagePack 并返回。  
- 对于大文件/高频请求场景，建议在客户端缓存解码后的快照，并结合 `timestamp` 判断是否需要重新请求。  
