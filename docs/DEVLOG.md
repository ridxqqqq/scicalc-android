# 科学计算器 APK — 开发过程记录

> 本文整理自完整的开发会话：从零手写工具链，到编译、签名、验证出可安装的 APK。
> 项目路径 `D:\android_1`，全程不使用 Gradle / Android Studio。

---

## 0. 需求

在 `D:\android_1` 里做出一个**科学计算器 APK**，要求：

- 不依赖 Gradle，用**手动工具链**直接调用 build-tools 完成编译打包；
- 有完整的工程结构（清单、资源、图标、Java 源码、构建脚本）；
- 产物是一个真正能装到手机上的签名 APK。

---

## 1. 环境与工具链

| 组件 | 路径 / 版本 | 说明 |
| --- | --- | --- |
| build-tools | `C:\android-build\android-14` | 34.0.0，提供 aapt2 / d8 / zipalign / apksigner |
| platform | `C:\android-build\android-34\android.jar` | 编译期 classpath |
| JDK | `C:\Program Files\BellSoft\LibericaJDK-17` | javac / d8 / keytool 依赖 |
| Python | `python`（在 PATH 中） | 仅用**标准库**：`zlib` 生成 PNG、`zipfile` 塞 dex |
| Git | 2.47.1.windows.2 | 版本管理 |

设计取向：**零第三方依赖**。图标生成器不用 Pillow（避免装包），只用 `zlib` + `struct` 手写 PNG 编码；
打包阶段不用 `zip`/`7z` 命令行，直接用 Python `zipfile`。

---

## 2. 目录结构

```
D:\android_1\
├─ AndroidManifest.xml
├─ build.ps1                        一键构建脚本（9 步）
├─ release.keystore                 签名密钥（已 gitignore，缺失时自动生成）
├─ ScienceCalculator-v1.0.apk       成品 APK
├─ java\com\scicalc\calculator\
│   ├─ Calc.java                    表达式求值器（递归下降解析）
│   └─ MainActivity.java            界面 + 交互逻辑
├─ res\
│   ├─ values\strings.xml           应用名：科学计算器
│   ├─ values\styles.xml            深色主题
│   └─ mipmap-{m,h,xh,xxh,xxxh}dpi\ic_launcher.png
├─ tools\
│   ├─ gen_icon.py                  纯标准库 PNG 图标生成器
│   └─ CalcTest.java                Calc 的独立单元测试（纯 Java，可脱离 Android 运行）
└─ build\                           中间产物（已 gitignore）
```

---

## 3. 手动构建链（build.ps1 的 9 步）

```
1/9  aapt2 compile --dir res -o build\res.zip
2/9  aapt2 link -o build\base.apk ... --java build\gen      → 同时产出 R.java
3/9  javac -source 1.8 -target 1.8 -classpath android.jar   → build\classes\*.class
4/9  d8 --min-api 21 --lib android.jar --output build       → classes.dex
5/9  python: 用 zipfile 把 classes.dex 追加进 base.apk
6/9  zipalign -f 4 base.apk aligned.apk
7/9  keytool -genkeypair（已存在则复用，不覆盖签名）
8/9  apksigner sign --ks release.keystore ...
9/9  apksigner verify --print-certs
```

几个关键点：

- **为什么要第 5 步**：`aapt2 link` 只负责资源，dex 不在它的职责内；没有 Gradle 就没人帮你合并，
  所以用 Python 把 `classes.dex` 以 `ZIP_STORED` 追加进 APK 再对齐。
- **为什么用 `d8` 而不是 `dx`**：34.0.0 的 build-tools 已移除 `dx`，`d8` 是唯一选择。
- **min-api 21**：与清单里的 `minSdkVersion 21` 保持一致。
- **`keytool` 幂等**：脚本先判断 `release.keystore` 是否存在，存在就跳过，避免每次构建换签名导致无法覆盖安装。

---

## 4. 关键设计

### 4.1 求值器（`Calc.java`）

递归下降，文法分层：

```
expr   := term ((-|+) term)*
term   := unary ((*|/|×|÷|mod) unary)*
unary  := (+|-|±) unary | postfix
postfix:= primary (! | % | ²)*          ← 后缀运算符，可叠加如 5!%
primary:= number | constant | func '(' expr ')' | '(' expr ')'
```

- `number` 支持小数点与 `E` 指数（如 `3.5E2`）。
- `^` 通过 `power()` 单独处理，实现**右结合**：`2^3^2` = 2⁹ = 512 而非 64。
- DEG/RAD 由构造参数 `deg` 控制，`apply()` 里在三角函数处做 `Math.toRadians` / `Math.toDegrees` 换算。
- 错误通过 `throw new IllegalArgumentException("中文提示")` 抛出，UI 直接展示，用户体验友好。

### 4.2 界面（`MainActivity.java`）

- **布局完全用代码构建**，不写 XML layout：省掉 `aapt2 link` 对 layout 资源的处理，也让补丁式编辑更安全。
- 网格用 `GridLayout`，5 列 × 7 行，`weight` 均分。
- 深色主题：`styles.xml` 里定义，配色以常量形式放在 Java 里。
- **实时预览**：`TextWatcher` + `afterTextChanged` → `refresh()`，每次输入都重算一遍并显示在结果区；抛异常时不显示，不弹提示。
- **智能退格**：`DEL` 不是简单 `substring(0, len-1)`，而是识别末尾多字符词元（`sin(`、`log(`、`mod`、`π`、`√(`…）整体删除。
- **结果续算**：`justEvaluated` 标志控制 —— 按 `=` 后输入数字则清空重开，输入运算符则接着结果继续算。

### 4.3 数字格式化

- `fmt()`：结果展示用，整数不带小数点，最多 10 位小数，过大/过小转科学计数法。
- `fmtRaw()`：把结果回填到表达式时用，保证**回填后的字符串一定能被解析器再次解析**（不会出现 `1.0E10` 被解析失败之类的问题）。
- 两者分开是刻意设计：显示好看 ≠ 可再解析。

### 4.4 图标（`tools/gen_icon.py`）

- 只用 `zlib` + `struct` 手写 PNG，无 Pillow 依赖。
- 内部以 RGBA 像素缓冲绘制：圆角矩形背景 + 渐变 + 运算符号，故自带 **alpha 透明通道**。
- 每个尺寸用 `make_sampler(size, ss)` 做超采样抗锯齿，`ss` 越大越平滑但越慢。
- 输出 5 个 mipmap 密度；另出 `tools/icon-256.png` 作为商店预览图。

---

## 5. 踩坑与修复（真实记录）

### 5.1 `gen_icon.py` 512px 超采样卡住

**现象**：早期版本用 `ss=4` 渲染 512×512（即 2048×2048 = 419 万像素）纯 Python 循环，
耗时不可接受，一度以为脚本死掉。

**修复**：把采样倍率参数化（`make_sampler(size, ss)`），并把商店预览图从 512px 降为 256px、
倍率降为 `ss=3`。5 个 mipmap（最大 192px）保持高质量不变。

### 5.2 PowerShell 5.1 把 keytool 的 stderr 当成致命错误 ★

**现象**：第一次跑 `build.ps1`，前 6 步（aapt2 / javac / d8 / 打包 / 对齐）全部成功，
到第 7 步 keytool **中断退出，退出码 1**：

```
keytool.exe : 正在为以下对象生成 2,048 位RSA密钥对和自签名证书 ...
    + CategoryInfo : NotSpecified: (...) [], RemoteException
    + FullyQualifiedErrorId : NativeCommandError
```

**原因**：`$ErrorActionPreference = "Stop"` 时，PowerShell 5.1 会把原生程序写到 **stderr**
的内容包装成 `ErrorRecord`（`NativeCommandError`），并当成终止性错误抛出。
但 keytool 那句「正在生成密钥对…」只是**正常提示**，它其实工作得好好的。

**修复**：把脚本的 `$ErrorActionPreference` 改为 `"Continue"`，
并**在每个原生调用之后显式检查 `$LASTEXITCODE`，非 0 就 `throw`**。
这样既不会被 stderr 误伤，又能真正在失败时立即停下。
（脚本里留了注释说明原因，避免以后有人「顺手」改回 Stop。）

### 5.3 `build.ps1` 必须保持纯 ASCII

`PowerShell 5.1` 读取**没有 BOM** 的 `.ps1` 时按 **ANSI（GBK）** 解码。
如果脚本里写了中文注释，在某些保存方式下会变成乱码甚至破坏语法。
本工程采用最保险的做法：**脚本内容全部 ASCII，中文只出现在 README 和文档里**。

（顺带一提，我一开始往脚本里加了中文注释，随即意识到这点并改回英文。）

### 5.4 `Calc.matchWord` 误拒数字，导致 `10mod3` 解析失败 ★

**现象**：单元测试报 `10mod3 → ERR(无法识别的字符: m)`。

**原因**：`matchWord()` 原本为了避免把 `sin` 误匹配进 `sine`，检查「关键词后面是否跟着字母或数字」：

```java
if (Character.isLetter(n) || Character.isDigit(n) || n == '_') return false;
```

但 `mod` 是**中缀**运算符，`10mod3` 里 `mod` 后面正好跟着数字 `3`，于是被误判为不匹配。

**修复**：只排除字母与下划线，**不再排除数字**：

```java
if (Character.isLetter(n) || n == '_') return false;
```

数字的冲突风险由函数名之后的「必须跟 `(`」检查兜住（如 `sin2` 会得到「sin 需要括号」的报错）。

### 5.5 测试用例自身的哨兵值写错

`chk("10%3", false, -1)` —— 我想表达「这里应该报错」，但断言里用的「期望报错」哨兵是 `-999`，
写成 `-1` 就变成了「期望结果是 -1」。解析器其实行为正确（`%` 是后缀百分号，`10%` 后面再跟 `3` 属于非法）。
**教训**：测试断言里的魔法值一定要用具名常量，别用裸数字。

### 5.6 `aapt2 dump badging` 输出中文乱码

`application-label:'绉戝璁＄畻鍣?'` —— 这是**控制台代码页（GBK）显示 UTF-8 字节**的假象，
APK 里的字符串本身是对的。用 Python 以 `utf-8` 解码同一命令的 stdout 即可确认：
`application-label:'科学计算器'`。
**不要被终端乱码骗着去「修」一个本来就正确的东西。**

---

## 6. 验证结果

### 6.1 单元测试：25 / 25 通过

`tools/CalcTest.java` 是**纯 Java** 的，不需要 Android 运行时：

```powershell
javac -encoding UTF-8 -d build\testout -sourcepath java tools\CalcTest.java
java -cp build\testout com.scicalc.calculator.CalcTest
```

覆盖范围：

| 类别 | 用例 |
| --- | --- |
| 基本运算 | `1+2*3`=7、`(1+2)*3`=9、`-3+5`=2 |
| 幂（右结合） | `2^3^2`=512、`2^-1`=0.5 |
| 取模 / 百分号 / 阶乘 | `10mod3`=1、`50%`=0.5、`5!`=120、`0!`=1 |
| 三角（DEG/RAD） | `sin(30)`=0.5(DEG) / −0.988(RAD)、`cos(60)`=0.5、`atan(1)`=45 |
| 对数与根号 | `ln(e)`=1、`log(1000)`=3、`sqrt(16)`=4、`√(81)`=9 |
| 常量 / 科学计数法 | `π`、`3.5E2`=350 |
| 错误分支 | `2+`→表达式不完整、`(1+2`→缺少右括号、`10%3`→非法字符 |

### 6.2 APK 内容

```
AndroidManifest.xml                          2052
res/mipmap-mdpi-v4/ic_launcher.png           1234
res/mipmap-hdpi-v4/ic_launcher.png           1816
res/mipmap-xhdpi-v4/ic_launcher.png          2082
res/mipmap-xxhdpi-v4/ic_launcher.png         2976
res/mipmap-xxxhdpi-v4/ic_launcher.png        4193
resources.arsc                               1576
classes.dex                                 15968
META-INF/...                                 签名文件
```

### 6.3 清单校验（`aapt2 dump badging`）

正确读出：包名 `com.scicalc.calculator`、versionCode 1 / versionName 1.0、
`minSdk 21` / `targetSdk 34`、启动项 `com.scicalc.calculator.MainActivity`、5 个密度图标、应用名 `科学计算器`。

### 6.4 签名校验（`apksigner verify --verbose`）

```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
```

### 6.5 最终产物

| 项 | 值 |
| --- | --- |
| 文件 | `build\ScienceCalculator-v1.0.apk`（根目录另存一份同名副本） |
| 大小 | 33,575 字节 |
| MD5 | `0cbc6e2ffe2a97c06e4db3747335c89b` |
| 证书 | `CN=Science Calculator, O=Local, C=CN` |
| 有效期 | 10,950 天 |

---

## 7. 已知限制

- 计算基于 `double` 双精度，显示最多保留 10 位小数，`sin(30)` 这类值实际是
  `0.49999999999999994`，回填时按 12 位有效数字四舍五入为 `0.5`。
- 未实现双曲函数（`sinh` / `asinh` …）。如需扩展：在 `Calc.FUNCS` 登记函数名 +
  在 `Calc.apply` 加分支即可，UI 上再补按钮。
- 只做了竖屏单手布局，横屏/平板未做适配（清单里锁定 portrait）。
- 无历史记录、无内存变量（M+/MR）功能。

---

## 8. 复现步骤

```powershell
git clone https://github.com/ridxqqqq/scicalc-android.git D:\android_1
cd D:\android_1

# 首次构建会自动生成一份新的 release.keystore
Set-ExecutionPolicy -Scope Process Bypass -Force
.\build.ps1

# 运行单元测试
javac -encoding UTF-8 -d build\testout -sourcepath java tools\CalcTest.java
java -cp build\testout com.scicalc.calculator.CalcTest

# 安装到设备
adb install -r .\build\ScienceCalculator-v1.0.apk
```

> `release.keystore` 被 `.gitignore` 排除（含私钥，不应入库）。
> 因为每次克隆都会生成**不同的**签名密钥，如果你要发布更新版本、覆盖安装，
> 请自行备份首次生成的 keystore。

