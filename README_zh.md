# beacon-provider

> [English](README.md)。本项目与 Beacon Plugin 高度耦合。

Beacon Provider 提供可在 Fabric 和 Forge 上分发的共享遥测（telemetry）逻辑，且不依赖旧的 Architectury shims。仓库按 Minecraft 版本组织，以便每个 loader 能独立随版本演进。

Beacon Provider 设计为与 Beacon Plugin 配合使用。它只向 Beacon Plugin 提供与 modpack 相关的数据，而 Beacon Plugin 负责通过 Socket.IO 初始化通信，将 Minecraft 服务器信息暴露给后端或其他消费者。

Beacon Provider 通过 Netty 与 Beacon Plugin 通信。Provider 会自动启动一个 Netty 服务器，Beacon Plugin 会扫描 Provider 在 `/config` 中的配置文件并尝试连接。请不要在此处报告与 Beacon Plugin 相关的问题。

## 布局

```
root
├── common/           # 共享逻辑，只编译一次（Java 8 目标）
├── fabric-1.16.5/    # 针对 MC 1.16.5 的 Fabric loader 入口
├── fabric-1.18.2/
├── fabric-1.20.1/
├── forge-1.16.5/     # 针对 MC 1.16.5 的 Forge loader 入口
├── forge-1.18.2/
└── forge-1.20.1/
```

## 构建

- 单模块构建：`./gradlew :fabric-1.20.1:build` 或 `./gradlew :forge-1.18.2:build`
- 构建指定 Minecraft 目标：`./gradlew buildTarget_1_18_2`
- 构建所有目标：`./gradlew buildAllTargets`

每个 loader 的 jar 会自动打包编译后的 `common` 类和资源，从而使产物自包含，便于部署。

## Actions

一个 Action 是 mod 的基本请求单元。详尽的定义与用法请参阅 `docs/Beacon Actions.md`。
