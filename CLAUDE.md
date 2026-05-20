# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目状态

空白项目，待重新设计。当前仅有 Hello World 的 MainActivity，所有依赖已配置完毕。

## 技术栈

- **Android 原生开发**：Kotlin + Jetpack Compose
- **最低支持**：Android 8.0（API 26）
- **JDK**：11
- **依赖注入**：Hilt
- **本地数据库**：Room + KSP
- **图片加载**：Coil
- **导航**：Navigation Compose

## 构建与运行

- **真机调试**：Android Studio 连接设备，运行 `:app` 模块
- **Sync**：File → Sync Project with Gradle Files（或 Ctrl+Shift+O）

## 目录结构

```
app/src/main/java/com/mistakenotes/
└── MainActivity.kt          # 当前仅有 Hello World

app/build.gradle.kts         # 依赖配置完整
```

## 开发说明

- 项目刚重置，等待重新设计
- 依赖版本锁定在 build.gradle.kts 中
