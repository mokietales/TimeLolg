# Time Log Android Demo

一个 Android v2 Demo（Kotlin + Jetpack Compose + Room），用于验证 Time Log 的核心交互：

- 开始一个行为（一次只允许一个进行中行为）
- 结束并保存行为
- 记录一句话笔记
- 首页计时卡片 + 今日投入摘要
- 在列表中回顾历史投入
- 历史筛选（全部 / 仅有笔记 / 今天 / 本周）
- 本地持久化（重启 App 后记录仍保留）

## 运行方式

1. 用 Android Studio 打开项目根目录 `TimeLolg`
2. 等待 Gradle 同步完成
3. 选择模拟器或真机，点击 Run

## Demo 范围

当前是单机本地原型，不含账号和云同步。主要用于快速验证产品方向与交互节奏。