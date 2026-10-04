# CS2 Dust2 安卓道具教学沙盘

现有实现基线 **v0.6.0-test2**：完整沙二地图、自由观察跑图、可见区域加载优化。正式教学课程、人物站位展示、动态烟火及 HE 炸烟仍待实现，详见 [进度](docs/STATUS.md) 和 [首版要求 C010](docs/requirements/C010.md)。

本仓库用于 APP、资源和项目经理三边协作。开工先读 [AGENTS.md](AGENTS.md) 和 [协作约定](docs/COLLABORATION.md)。

## 克隆后构建

全部移动资源已随仓库保存，不需要另找地图下载。先还原分卷：

```bash
python3 tools/restore_assets.py
python3 tools/validate_assets.py
chmod +x gradlew
./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon
```

需要 Python 3.10+、JDK 17、Android SDK 34 / Build Tools 34.0.0，以及可下载 Gradle 8.7 和依赖的网络。APK 输出 `app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 采用同样流程。

资源分卷在 `asset-packs/`，每卷、合并包及还原文件都有 SHA-256 校验；恢复后与导入的基线逐文件字节一致。JSON 元数据、Shader 和 Kotlin 代码可直接在仓库审查。资源更新后先完整还原，再执行 `python3 tools/package_assets.py --version <资源版本>` 并提交分卷、清单和元数据。

## 分工

| 参与侧 | 分支 |
| --- | --- |
| APP／功能 | app/* |
| 资源 | assets/* |
| 项目经理整合 | pm/* |

使用独立任务分支和 PR 汇合到 main。其他两个会话须分别验证私有仓库访问。

[基线原始说明](docs/BASELINE-v0.6.0-test2.md)包含已完成行为、既有测试、手机操作及已知限制。项目经理负责人工审查；[Codex 停止通知](docs/dispatch/C013-codex-review.md)已撤销旧接入与任务安排。
