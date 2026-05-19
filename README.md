# Budget

个人记账 Android 应用，基于 Kotlin + Jetpack Compose。

## 功能

- **记账**：记录收入/支出，支持 20+ 种预定义分类，可添加备注
- **编辑交易**：点击历史记录可修改金额、分类和备注
- **日薪自动入账**：设置月收入后自动折算为日薪，每日自动录入
- **智能分类推测**：根据历史同数额记录自动推断分类
- **悬浮窗快速记账**：在其他应用之上通过悬浮气泡快速记录支出，支持拖拽、长按关闭
- **悬浮球环形进度**：气泡边缘显示今日支出占收入比例（绿色 < 60%，红色 ≥ 60%）
- **悬浮窗「其他」跳转**：输入金额后点击「其他」跳转到完整记账页选择细分类别
- **数字键盘震动反馈**：按键时数字键轻震、操作键强震
- **GitHub 风格热力图**：月视图每日格子、年视图每周格子，直观展示支出密度
- **前后周期导航**：统计页左右滑动切换时间周期，带平滑过渡动画
- **日详情钻取**：周/月/年视图点击某天展开当日交易列表，支持返回上层
- **快捷开关磁贴**：通知栏一键开关悬浮记账
- **CSV 导入导出**：批量导入历史账目，导出当前全部记录
- **深色/纯黑主题**：适配不同使用场景

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 构建 | Gradle, AGP |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |
| 持久化 | JSON 文件存储 |
| 架构 | MVVM（ViewModel + Repository） |

## 项目结构

```
app/src/main/java/ai/findnextstep/budget/
├── MainActivity.kt
├── logic/
│   ├── model/
│   │   ├── Transaction.kt
│   │   ├── Category.kt
│   │   ├── Statistics.kt
│   │   └── Period.kt
│   ├── repository/
│   │   └── TransactionRepository.kt
│   ├── service/
│   │   ├── StatisticsService.kt
│   │   ├── SalaryService.kt
│   │   ├── CategoryPredictor.kt
│   │   └── CsvService.kt
│   └── util/
│       └── DateUtils.kt
├── data/
│   └── JsonTransactionRepository.kt
└── ui/
    ├── screen/
    │   ├── MainScreen.kt
    │   ├── AddTransactionScreen.kt
    │   ├── StatisticsScreen.kt
    │   └── SettingsScreen.kt
    ├── component/
    │   ├── NumberPad.kt
    │   ├── CategorySelector.kt
    │   ├── PeriodSelector.kt
    │   └── ExpenseHeatmap.kt
    ├── service/
    │   ├── FloatingExpenseService.kt
    │   └── FloatingTileService.kt
    ├── viewmodel/
    │   └── BudgetViewModel.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

## 构建

```bash
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/`。

## 权限

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗快速记账 |
| `FOREGROUND_SERVICE` | 前台服务 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 前台服务类型 |
| `POST_NOTIFICATIONS` | Android 13+ 通知权限 |
| `BIND_QUICK_SETTINGS_TILE` | 快捷开关磁贴 |
