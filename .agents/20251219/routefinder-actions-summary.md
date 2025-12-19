## 2025-12-19 路线 Finder 支持完成

- 新增了 8 个 Beacon action（路由节点、routefinder 数据、routefinder 状态、routefinder 边、连接配置、站台位置、轨道曲线、版本信息），每个 action 都从 `MtrSnapshotCache` 的快照数据中提取，不触发 `RailwayDataRouteFinderModule.findRoute` 之类的运行时逻辑。
- 在 `MtrModels/MtrDataMapper/MtrJsonWriter` 里增加了对应的 DTO、构造函数和 JSON 序列化逻辑；`MtrQueryGateway` 提供默认实现供 Loader 复用。
- 编写了对应的 action handler、更新 `BeaconServiceFactory`、补充 action文档（`docs/Beacon Actions.md`）与测试脚本（`tests/test-actions.js`，新增步骤会依次调用新 action 并保存 JSON）。
- 目前运行 `./gradlew build` 时仍在 Gradle 启动阶段因为 “Could not determine a usable wildcard IP for this machine” 失败（Gradle 步骤还没走到 Java 编译），需要等网络/环境允许再跑一次。
