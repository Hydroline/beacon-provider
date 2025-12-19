# 2025-12-20 实施摘要（RouteFinder Cache）

## 已完成
- [x] 引入 `routefinder-cache.sqlite` 缓存（位于 `config/beacon-provider/`），支持 `upsert/read/list`、版本不一致清理与过期清理。
- [x] 启动后台调度器：服务器启动后异步逐条 `findRoute` 计算并写入缓存，每小时刷新一次，避免阻塞主线程。
- [x] 修复 `RouteFinderModuleState` 的 fastutil 读取异常，确保 `globalBlacklist/localBlacklist` 能正常输出。
- [x] 新增 action `mtr:get_routefinder_cache`：直接读取 SQLite，返回单条缓存或摘要列表。
- [x] 更新 `docs/Beacon Actions.md` 与 `tests/test-actions.js`。

## 待办
- [ ] 补齐 polyline 采样点或完整 cost/penalty breakdown，让缓存输出达到 1:1 曲线渲染标准。
- [ ] 为缓存缺失场景加入回退/告警策略（可降级读 snapshot）。
- [ ] 增加统计/监控日志（每次刷新耗时、成功/失败条数）。
