# 科学计算器 (Science Calculator) — Android APK

一个纯手写工具链构建的 Android 科学计算器，**不依赖 Gradle / Android Studio**。
使用本机 `C:\android-build` 里的 build-tools 34.0.0 + android-34 平台直接编译打包。

## 成品

| 项目 | 值 |
| --- | --- |
| APK | `build\ScienceCalculator-v1.0.apk`（根目录另有一份同名副本） |
| 包名 | `com.scicalc.calculator` |
| 版本 | versionCode 1 / versionName 1.0 |
| SDK | minSdk 21（Android 5.0） / targetSdk 34 |
| 签名 | v1 + v2 + v3，密钥库 `release.keystore`（口令 `scicalc2026`，别名 `scicalc`） |
| 图标 | 5 个密度 mipmap（由 `tools/gen_icon.py` 生成） |

## 一键构建

```powershell
cd D:\android_1
Set-ExecutionPolicy -Scope Process Bypass -Force
.\build.ps1
```

## 构建流程（9 步，全部手工）

1. `aapt2 compile --dir res -o build\res.zip`
2. `aapt2 link` → `build\base.apk` + 生成 `R.java`
3. `javac -source 1.8 -target 1.8 -classpath android.jar`
4. `d8 --min-api 21` → `classes.dex`
5. Python `zipfile` 把 `classes.dex` 追加进 `base.apk`
6. `zipalign -f 4`
7. `keytool -genkeypair`（生成签名密钥，已存在则复用）
8. `apksigner sign`
9. `apksigner verify --print-certs`

工具链路径（在 `build.ps1` 顶部可改）：

```
build-tools : C:\android-build\android-14       (34.0.0)
android.jar : C:\android-build\android-34\android.jar
JDK         : C:\Program Files\BellSoft\LibericaJDK-17
Python      : python (在 PATH 中)
```

## 目录结构

```
D:\android_1\
├─ AndroidManifest.xml
├─ build.ps1                        一键构建脚本
├─ release.keystore                 签名密钥（勿删，删了签名会变）
├─ java\com\scicalc\calculator\
│   ├─ Calc.java                    表达式求值器（递归下降解析）
│   └─ MainActivity.java            界面 + 交互（布局全部代码构建）
├─ res\
│   ├─ values\strings.xml           应用名：科学计算器
│   ├─ values\styles.xml            深色主题
│   └─ mipmap-*dpi\ic_launcher.png  图标
├─ tools\
│   ├─ gen_icon.py                  纯标准库 PNG 图标生成器
│   └─ CalcTest.java                Calc 的独立单元测试
└─ build\                           构建中间产物 + 最终 APK
```

## 功能

**运算符**：`+ - × ÷ ^ mod %`（`%` 为后缀百分号，如 `50%` = 0.5）
**函数**：`sin cos tan asin acos atan ln log √ exp abs`
**后缀**：`!`（0~170 整数的阶乘）
**常量**：`π`、`e`
**其他**：括号、`±` 正负号、`1/x`、`x²`、`x^y`、`DEG/RAD` 角度弧度切换

交互特性：

- **实时预览**：输入过程中下方大号字体实时显示计算结果，出错不显示。
- **智能退格 DEL**：`sin(`、`log(`、`mod`、`π` 等多字符词元整体删除，不会删成半截。
- **结果续算**：按 `=` 后，输入数字会重新开始；输入运算符/`!`/`^` 则接着结果继续算。
- **数字格式**：整数不带小数点；过大或过小自动转科学计数法；回填表达式时用普通记数法，保证可再次解析。

## 测试

`tools/CalcTest.java` 是 Calc 的独立验证程序（纯 Java，不依赖 Android）：

```powershell
cd D:\android_1
javac -encoding UTF-8 -d build\testout -sourcepath java tools\CalcTest.java
java -cp build\testout com.scicalc.calculator.CalcTest
```

当前结果：**25 项全部通过**（含右结合幂运算 `2^3^2=512`、DEG/RAD 三角函数、
阶乘、百分号、`mod`、`√`、`ln e`、科学计数法，以及 4 类非法表达式的报错）。

## 安装

```powershell
adb install -r D:\android_1\build\ScienceCalculator-v1.0.apk
```

或直接把 APK 传到手机点击安装（需允许安装未知来源应用）。

## 备注 / 已知限制

- 表达式解析为双层 double 精度，显示最多保留 10 位小数；`sin(30)` 等值为 0.49999999999999994，
  回填时按 12 位有效数字四舍五入为 `0.5`。
- `asinh/acosh/atanh/sinh` 等双曲函数未做按钮（解析器也未实现），如需可自行扩展
  `Calc.FUNCS` 与 `Calc.apply`。
- `build.ps1` 内的注释与提示刻意保持纯 ASCII：PowerShell 5.1 读取无 BOM 的 .ps1 时按 ANSI 解码。
- `$ErrorActionPreference` 设为 `Continue`，因为 PS 5.1 会把 keytool/apksigner 写到 stderr 的
  正常提示当成 `NativeCommandError`；脚本改为在每个原生调用后显式检查 `$LASTEXITCODE` 并 `throw`。

## 仓库

<https://github.com/ridxqqqq/scicalc-android>（私有）

## 开发过程记录

完整的过程记录 —— 文法设计、9 步构建链细节、**6 个踩坑与修复**、验证结果、
已知限制与复现步骤 —— 见 **[docs/DEVLOG.md](docs/DEVLOG.md)**。

## 许可

仅用于学习与个人使用。

