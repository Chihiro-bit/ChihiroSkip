# ChihiroSkip

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="120" alt="ChihiroSkip Logo"/>
</p>

<p align="center">
  <strong>Android 广告自动跳过工具</strong><br>
  基于无障碍服务，智能识别并跳过 App 内广告
</p>

<p align="center">
  <a href="https://github.com/Chihiro-bit/ChihiroSkip/releases/latest">
    <img src="https://img.shields.io/github/v/release/Chihiro-bit/ChihiroSkip?include_prereleases&label=下载最新版&style=for-the-badge&color=6C63FF" alt="Latest Release"/>
  </a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-green?style=for-the-badge&logo=android" alt="Android 7.0+"/>
  <img src="https://img.shields.io/badge/语言-Kotlin-blueviolet?style=for-the-badge&logo=kotlin" alt="Kotlin"/>
</p>

---

## 下载

前往 [Releases 页面](https://github.com/Chihiro-bit/ChihiroSkip/releases/latest) 下载最新版 APK，无需编译，直接安装。

> **注意**：安装时需要允许「安装未知来源应用」。

---

## 功能特性

- **自动跳过广告**：监听屏幕内容，识别广告按钮并自动点击跳过
- **可视化规则管理**：通过界面添加、编辑、导入/导出跳过规则
- **录制助手**：切换到目标 App，一键扫描并录制广告跳过规则，无需手写
- **多种匹配方式**：支持 viewId、文本、contentDescription、坐标点击
- **安全节流**：内置点击冷却与频率限制，防止误触
- **跳过日志**：记录每次成功跳过的时间、App 包名、触发规则
- **规则导入导出**：通过 SAF 分享规则文件，无需存储权限
- **深色主题**：精心设计的深色 UI，视觉舒适

---

## 截图

| 主界面 | 设置面板 | 规则列表 |
|:---:|:---:|:---:|
| *(Power 按钮启动/停止服务)* | *(下滑展开设置)* | *(规则增删改查)* |

---

## 使用方法

### 1. 安装与授权

1. 下载并安装 APK
2. 打开 App，进入 **设置 → 无障碍** 找到「ChihiroSkip」并开启
3. 如需使用「录制助手」，还需授予 **悬浮窗权限**（设置 → 显示在其他应用上层）

### 2. 启动服务

- 点击主界面中央的 **电源按钮** 开启/关闭广告拦截
- 按钮亮起（橙色光晕）表示服务运行中

### 3. 添加规则

**方式一：录制助手（推荐）**

1. 点击主界面「录制」按钮，检查并授予悬浮窗权限
2. 切换到需要跳过广告的 App
3. 在悬浮窗中点击「扫描」，获取候选节点列表
4. 选择广告跳过按钮对应的节点
5. 在「规则预览」界面确认并保存

**方式二：手动添加**

1. 进入「规则列表」→「添加规则」
2. 填写目标 App 包名和匹配条件
3. 保存后立即生效

**方式三：导入规则文件**

- 在「规则列表」页面点击「导入」，选择 `.json` 规则文件

---

## 规则文件格式

规则以 JSON 格式存储，可手动编辑或通过导出功能获取：

```json
{
  "version": 2,
  "rules": [
    {
      "id": "rule_001",
      "name": "某视频App开屏广告",
      "packageName": "com.example.videoapp",
      "enabled": true,
      "conditions": [
        {
          "matchType": "text",
          "value": "跳过广告"
        }
      ],
      "action": {
        "type": "click"
      }
    }
  ]
}
```

---

## 权限说明

| 权限 | 用途 | 必须 |
|------|------|:----:|
| 无障碍服务 | 读取屏幕内容、模拟点击 | ✅ |
| 悬浮窗 (SYSTEM_ALERT_WINDOW) | 录制助手悬浮窗 | 仅录制时需要 |

本应用**不申请**网络、存储、定位等任何隐私敏感权限。

---

## 构建

```bash
# 克隆仓库
git clone https://github.com/Chihiro-bit/ChihiroSkip.git
cd ChihiroSkip

# 构建 Debug APK（Windows）
.\gradlew assembleDebug

# 构建 Release APK
.\gradlew assembleRelease

# 输出路径
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release-unsigned.apk
```

**环境要求**

- Android Studio Flamingo 或更高版本
- JDK 17+
- Android SDK API 33 (compileSdk)，minSdk 24 (Android 7.0)

---

## 项目结构

```
app/src/main/java/com/chihiro/skip/
├── accessibility/          # 无障碍服务基础层
│   ├── FastAccessibilityService.kt  # 4线程池基础服务
│   └── FastAccessibilityApi.kt
├── engine/                 # 核心引擎
│   ├── RuleEngine.kt       # 规则匹配与执行调度
│   ├── NodeMatcher.kt      # 节点匹配（viewId/text/contentDesc/坐标）
│   ├── ClickExecutor.kt    # 手势执行（含坐标比例化点击）
│   ├── SafetyGuard.kt      # 节流与安全保护
│   ├── RuleRecorderManager.kt  # 录制助手单例桥
│   ├── CandidateNodeScanner.kt # 候选节点扫描与评分
│   └── RuleGenerator.kt    # 节点 → AdSkipRule 转换
├── model/                  # 数据模型
│   ├── AdSkipRule.kt
│   ├── MatchCondition.kt
│   ├── RuleAction.kt
│   ├── AppConfig.kt
│   ├── SkipLog.kt
│   ├── CandidateNode.kt
│   └── RecordingSession.kt
├── repository/             # 数据存取
│   ├── RuleRepository.kt   # 规则 JSON 持久化（v2 格式）
│   ├── SettingsRepository.kt
│   └── SkipLogRepository.kt
├── service/
│   ├── MyAccessibilityService.kt   # 主无障碍服务
│   └── FloatingRecorderService.kt  # 录制助手悬浮窗服务
├── ui/
│   ├── RuleListActivity.kt
│   ├── SkipLogActivity.kt
│   └── RulePreviewActivity.kt
├── utils/
│   └── ImportExportManager.kt  # SAF 导入导出
└── MainActivity.kt
```

---

## 免责声明

本工具仅供个人学习与研究使用。使用本工具跳过广告可能违反部分应用的用户协议，请自行评估风险。作者不对因使用本工具产生的任何后果负责。

---

## License

[MIT License](LICENSE)
