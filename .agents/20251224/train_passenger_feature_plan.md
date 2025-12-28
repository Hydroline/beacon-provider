# MTR Train Passenger Info Feature Plan

## 1. 背景与目标 (Background & Goals)
当前 `hydroline-beacon-provider` 已支持查询线路上的列车基础信息（位置、ID、进度）。用户希望进一步获取**列车上的乘客信息**，具体包括：
1.  **列车位置**：车辆所在的 Rail ID 和进度（已支持）。
2.  **列车 ID**：车辆 UUID 和 Train ID（已支持）。
3.  **车上玩家**：当前乘坐该列车的玩家列表（新增需求）。

目标是增强现有的数据模型和映射逻辑，使得调用 `mtr:get_route_trains` (或相关接口) 时，能够返回车上玩家的详细信息。

## 2. 需求详情 (Requirements)

### 2.1 功能需求
*   **数据源获取**：通过反射访问 MTR `mtr.data.Train` 类中的 `ridingEntities` 字段 (类型 `Set<UUID>`)。
*   **玩家信息解析**：将获取到的实体 UUID 解析为玩家名称（如果玩家在线）。
*   **API 响应增强**：在 `TrainStatus` 模型中增加 `passengers` 字段。
*   **接口涉及**：主要影响 `mtr:get_route_trains` 和 `mtr:get_depot_trains`，以及任何返回 `TrainStatus` 的地方。

### 2.2 数据结构变更
在 `TrainStatus` DTO 中新增字段：
```json
{
  "trainId": "...",
  "...",
  "passengers": [
    {
      "uuid": "c056...",
      "name": "PlayerName"
    }
  ]
}
```

## 3. 技术实现方案 (Technical Implementation)

1.  **反射字段定位**：
    *   在 `MtrDataMapper` 中添加对 `mtr.data.Train.ridingEntities` 的反射引用。
    *   注意混淆映射（目前反编译显示为 `ridingEntities`，需确保运行时可访问）。

2.  **玩家名称解析**：
    *   `MtrDataMapper` 是静态工具类，需要从调用方（如 `ForgeMtrQueryGateway`）传入 `MinecraftServer` 或 `PlayerList` 的上下文，或者传入一个 `Function<UUID, String> nameResolver`。
    *   在 Forge/Fabric 端实现具体的解析逻辑（通过 `ServerPlayer` 查找）。

3.  **模型更新**：
    *   修改 `MtrModels.java` 中的 `TrainStatus` 类。
    *   新增 `Passenger` 简单类 (包含 uuid 和 name)。

## 4. 待办事项 (Todo List)

- [x] **Step 1: 基础设施准备**
    - [x] 在 `MtrModels.java` 中定义 `Passenger` 类 (UUID, Name)。
    - [x] 在 `TrainStatus` 类中添加 `List<Passenger> passengers` 字段。

- [x] **Step 2: 反射逻辑实现**
    - [x] 在 `MtrDataMapper.java` 中添加 `ridingEntities` 的 `Field` 定义和初始化。
    - [x] 实现 `resolvePassengers(Object train, Function<UUID, String> nameResolver)` 辅助方法。

- [x] **Step 3: 逻辑层接入**
    - [x] 修改 `MtrDataMapper` 的 `buildRouteTrains` 和 `buildDepotTrains` 方法签名，增加 `nameResolver` 参数。
    - [x] 修改 `MtrQueryGateway` 接口，允许传递上下文或在实现类中处理。
    - [x] 在 `ForgeMtrQueryGateway` (及 Fabric 版本) 中调用 Mapper 时，传入基于 `MinecraftServer` 的玩家名称解析逻辑。

- [x] **Step 4: 验证与测试**
    - [x] 编译项目，确保无编译错误。
