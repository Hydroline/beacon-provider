# Beacon routefinder status & proposed actions (2025-11-19)

> 备注：Beacon Provider 只负责把 MTR Mod 数据通过 Beacon Bukkit 插件转发给后端，Provider 本身并不直接与后端通信，所有 action 要先通过 Bukkit 发送。

## 当前状况

- [ ] 依赖 `transportation_railway_rails` 导出的节点拓扑 + `routes.platform_ids`，后端只能重建一个“碍于静态边”的图。MTR 正在运行时还会结合 `RailwayDataRouteFinderModule` 里的 `platformConnections`/`ConnectionDetails`、`blacklist`、`durationInfo` 等状态，以及 `Rail` 的双段曲线参数（`h/k/r/t/y` + `reverse/isStraight`），因此复刻出来的路径经常缺段、偏离真实曲线、部分 `rails` 被视为“次要/不可走”而错过。
- [ ] 持久化的 `world/mtr` 数据确实提供了 `rails`/`platforms`/`routes`，但那些运行时惩罚、平台节点映射、`RouteFinderData` 序列、连接密度缓存、采样策略，后端目前都拿不到。只有 Beacon provider 把这些额外信息以 action 的形式同步出来，才能让后端 1:1 构造 same-as-MTR 的路线。

## 需要 Beacon 承担的动作（actions）

1. **`mtr:get_routefinder_snapshot`**  
   - [ ] 目的：把 `RailwayDataRouteFinderModule.findRoute` 的输出（`RouteFinderData` 列表）原封不动地下发给后端。  
   - [ ] 返回字段建议：`dimension`、`requestId`、`start_pos`、`end_pos`、`route_id`、`station_ids`、`pos`（`BlockPos.asLong()`）、`duration`、`waiting_time`，以及用于调试的 `penalty_tags`。  
   - [ ] 有了这个骨架，后端只要按顺序连接 `pos` 就能和 MTR 选路一致，避免 no-path/岔道歧义。

2. **`mtr:get_connection_profile`**  
   - [ ] 目的：获取 `DataCache.platformConnections` 里每个 `ConnectionDetails` 的完整字段，并说明各字段在代价计算里的作用。  
   - [ ] 必需字段：`platform_start_pos`、`platform_end_pos`、`duration_info`（routeId→duration ticks）、`is_secondary_dir`、`preferred_curve`/`reverse_curve`、`connection_density`。  
   - [ ] 说明：需要明确 key 是平台位置还是 platformId，`platformStart` 是哪个端点，`durationInfo` 单位（tick？）。这些字段是 routefinder 的“输入状态”，后端没有它们就无法复刻惩罚。

3. **`mtr:get_platform_position_map`**  
   - [ ] 目的：确认 routefinder 里用的“平台节点”是 pos_1/pos_2/中心/吸附点，避免后端在 graph 里选错起终点。  
   - [ ] 返回内容：每个 platformId 对应的 raw positions（`pos_1`/`pos_2`）、`mid_pos`、`platformStart`、`platformEnd` 的选取逻辑（例如 start 取平台靠近前方的 rail node）。

4. **`mtr:get_rail_curve_segments`**  
   - [ ] 目的：把 `Rail` 的双段曲线参数（两组 `h/k/r/t/y`、`reverse` + `isStraight`、`yStart/yEnd`、`vertical_curve_radius`）以及采样策略同步过来，让后端按真实公式采样 `points[]`。  
   - [ ] 说明：需确认 `tStart/tEnd` 的单位（0..1）、采样步长（固定/按曲率）、两个段在拼接点是否要去重、`transport_mode` 对连接合法性的影响。

5. **`mtr:get_routefinder_version`**  
   - [ ] 目的：明确 Beacon 端运行的 MTR 版本（build + git commit/patch）以及 `RailwayDataRouteFinderModule` 的实现对应哪次更新。后端将以此判断惩罚逻辑是否对齐。

6. **`mtr:get_routefinder_data`**  
   - [ ] 目的：把 `RailwayDataRouteFinderModule.data` 和 `tempData` 里已经缓存的 `RouteFinderData` 序列导出（不再主动调用 `findRoute`），包含 `pos`, `routeId`, `duration`, `waitingTime`, `stationIds`。  
   - [ ] 有了这个列表，后端只需按 `pos` 顺序拼接 rail 模块里的 `RouteFinderData`，就能还原列车真实选路的 node 流，避免 no-path/岔道歧义依赖猜测。

7. **`mtr:get_routefinder_state`**  
   - [ ] 目的：把 `RailwayDataRouteFinderModule` 的上下文状态（`startPos`/`endPos`/`tickStage`/`totalTime`/`count`/`startMillis`、`globalBlacklist`/`localBlacklist`）也吐出来。  
   - [ ] 说明：后端需要知道哪些连接被黑名单屏蔽、当前 tickStage 是哪步、`startPos/endPos` 是否还能连通，这些信息无法从 static `rails` 导出。

8. **`mtr:get_routefinder_edges`**  
    - [ ] 目的：从 `RouteFinderData` 结果中按顺序拼出每段 `fromPos` → `toPos` 边（同一 routeId 且 source 不变时续接），并附上 `connectionDensity`，方便直接映射 `mtr_rail_curve_segments` 输出。  
    - [ ] 说明：后端可以直接用这些 `fromPos/toPos` 路径查对应的 Rail 曲线参数和采样点，从而 1:1 绘制 MTR 的实际轨道。

## 预期结果

- [ ] Beacon provider 先搭好这些 actions，后端结合它们重建图、代价函数和曲线采样，就能在响应里直接输出 `routes → segments(points[])`，前端只需画线不必再猜。  
- [ ] 当前任务就是先把这份清单传给 Beacon 开发，对齐哪些字段有、哪些字段要新增、以及传输格式。后续再实现 action + 后端处理逻辑。  

我会把这份清单保存到 `.agents/20251219/routefinder-actions.md` 并同步给你，等待你确认我们要跟 Beacon 讨论的具体字段和流程。
