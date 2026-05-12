# Budget

个人记账 Android 应用，基于 Kotlin + Jetpack Compose。

## 功能

- **记账**：记录收入/支出，支持预定义及自定义分类（主食、饮料、零食、房租、交通、购物等 20+ 类），可添加备注
- **日薪自动入账**：设置月收入后自动折算为日薪，每日 12:00 自动录入（支持后台触发）
- **智能分类推测**：根据历史交易中同数额的记录自动推断分类
- **多维度统计**：支持日 / 周 / 月 / 年维度，按分类查看每日开销分布
- **浮动窗口快速记账**：通过悬浮窗在其他应用之上快速记录支出
- **CSV 导入导出**：支持从 CSV 文件批量导入历史账目，导出当前全部记录
- **主题切换**：白色 / 黑色 / 纯黑（OLED 友好）三套主题

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin 1.9.24 |
| UI 框架 | Jetpack Compose + Material 3 |
| 构建 | Gradle 8.11.1, AGP 8.7.3 |
| 最低 SDK | Android 8.0 (API 26) |
| 目标 SDK | Android 14 (API 34) |
| 持久化 | JSON 文件存储 |
| 架构 | MVVM（ViewModel + Repository） |

## 项目结构

```
app/src/main/java/ai/findnextstep/budget/
├── MainActivity.kt              # 唯一 Activity，入口
├── logic/                       # 逻辑层（与 UI 无关）
│   ├── model/
│   │   ├── Transaction.kt       # 交易记录数据类
│   │   ├── Category.kt          # 分类定义（20+ 预置类型）
│   │   ├── Statistics.kt        # 统计数据模型
│   │   └── Period.kt            # 时间周期枚举
│   ├── repository/
│   │   └── TransactionRepository.kt  # 数据仓库接口
│   └── service/
│       ├── StatisticsService.kt      # 统计计算服务
│       ├── SalaryService.kt          # 日薪自动入账服务
│       ├── CategoryPredictor.kt      # 基于历史数据的分类推测
│       └── CsvService.kt             # CSV 导入导出服务
├── data/
│   └── JsonTransactionRepository.kt  # JSON 文件持久化实现
└── ui/                          # UI 层
    ├── screen/
    │   ├── MainScreen.kt             # 主界面
    │   ├── AddTransactionScreen.kt   # 记账面板（含数字输入器）
    │   ├── StatisticsScreen.kt       # 统计页面
    │   └── SettingsScreen.kt         # 设置页面
    ├── component/
    │   ├── NumberPad.kt              # 数字键盘组件
    │   ├── CategorySelector.kt       # 分类选择器
    │   └── PeriodSelector.kt         # 时间周期选择器
    ├── service/
    │   └── FloatingExpenseService.kt # 悬浮窗快速记账 Service
    ├── viewmodel/
    │   └── BudgetViewModel.kt        # 全局 ViewModel
    └── theme/
        ├── Color.kt                  # 颜色定义
        ├── Theme.kt                  # 主题（白色/黑色/纯黑）
        └── Type.kt                   # 字体定义
```

## CSV 格式

导入导出的 CSV 文件包含以下列：

| 列名 | 说明 |
|------|------|
| date | 日期，格式 `YYYY-MM-DD` |
| time | 时间，格式 `HH:MM` |
| amount | 金额，正数为收入、负数为支出 |
| original category | 原始分类 key |
| category | 最终分类 key |

参考 `example.csv` 了解具体格式。

## 构建

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/`。

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗快速记账 |
| `FOREGROUND_SERVICE` | 前台服务保活（日薪自动入账） |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 前台服务类型声明 |
| `POST_NOTIFICATIONS` | Android 13+ 通知权限 |

## License

MIT
