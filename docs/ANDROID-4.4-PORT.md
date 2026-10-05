# Android 4.4.2 适配与验证记录

日期：2026-10-05。分支：`android-4.4`。**项目尚未完成：用户需要正常投屏成品，当前测试 APK 不满足该目标。** 无运行认证资产的 APK 已交付 Library，仅通过官方 Android4.4.2 模拟器安装、冷启动和界面检查，不能完成 CarPlay。

## 基线与设备

- 保留上游完整 Git 历史，基于 0.2.12 提交 `2fc876e578eba3905a5b873e3c2dbd74f498433a` 开发。
- 参考 Legacy 0.2.7 提交 `c8884adcc75bfda3c134db63877bd6c6f83beb74` 的兼容思路，没有用旧项目覆盖当前源码，也未把它当作验证成功的底座。
- 目标照片确认 Android 4.4.2、K2X、1 GB RAM、16 GB 存储。APPVER `K2201S_YT_S112201.20181217.18594129.N0026.01`；PlatformVER `K22-2018/12/14[10:08:45]-10.05-R2B`。CPU、ABI、USB 模式与编解码能力仍需设备报告确认。
- 用户确认 Legacy APK 能安装、启动，但无线连接没有成功。通用重连提示不足以确定失败阶段；手机手动加入热点不是正常无线流程的必要前提，Bluetooth iAP2 通常负责交接热点信息。
- 未找到适用的 AGENTS.md 或 .agents 指令；已阅读 README、BUILD、TESTING、VALIDATION 和 CI。

## 隔离构建工具链

最初原始基线命令在 Java 启动前失败：`JAVA_HOME is not set`，并非源代码编译错误。之后只读发现 D 盘已有 JDK17、Gradle8.9、SDK35、NDK27，均不满足固定上游工具链/API19 NDK 要求。获得用户下载授权后，官方工具安装于任务目录 `.tools/`，没有修改全局环境或既有工具。

| 工具 | 本次实际使用 |
| --- | --- |
| JDK | OpenJDK25.0.2，官方下载并核验 SHA-256 |
| Gradle / AGP | 仓库 wrapper9.5.0（核验固定 SHA-256）/ 上游9.3.0 |
| SDK / Build Tools | `platforms;android-37.0` revision2 / 36.0.0 |
| NDK | 25.2.9519653 / r25c，32位目标 API19 |
| Emulator | 37.2.12，官方 Android19 default x86 revision6 |

Windows 隔离入口为任务目录 `run-gradle.ps1`。通用环境设置 JAVA_HOME、ANDROID_HOME 和 GRADLE_USER_HOME 后可使用原 wrapper：

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
./gradlew :common:lintDebug :shared:lintDebug
./gradlew :automotive:assembleDebug :home:assembleDebug :maphost:assembleDebug
```

未在补齐工具后另建未修改的上游 checkout 做对照构建；下述正式结果针对本适配分支。

## 主要兼容改动

- mobile/common/shared 最低 API19；multidex、核心库 desugaring、明确 v1 签名；debug 版本名 `0.2.12-api19-test`，包名 `com.shihab.diplay.hudtest`。
- NDK25 在 Gradle、Application.mk 和 CI 一致；保留 ARM64/x86_64，补齐 ARMv7/x86。64位库遵循相应平台最低 API，不宣称 Android4.4 支持64位。
- 移除 common/mobile 的 Compose，诊断主页面使用普通 View。保留0.2.12设置、投屏、导航和媒体逻辑；Car App 类移动到 automotive，避免拖高 mobile 最低版本。
- Core1.13.1、Activity1.8.2、AppCompat1.6.1、Media1.7.0、Media3 1.4.1；没有 Manifest override 掩盖依赖最低版本。
- Holo/API21 分级主题，旧通知、媒体会话、音频焦点、悬浮窗、权限和系统栏接口；较新平台能力有明确版本边界。
- AudioTrack/AudioRecord 旧构造和读写、MediaCodec 缓冲区数组、旧版切换 Surface 重建；保留语音/通话/导航音频和恢复逻辑。视频每个相关队列8 MiB，音频每路最多192×64 KiB；这些不是应用总内存上限。
- API19 默认 H.264/30fps，首次分辨率按约1024×600预算等比缩放，保留用户已存设置；不把此策略当成1 GB车机性能保证。
- 设备报告包括 API/ABI/RAM/屏幕/系统编解码器；无线报告区分热点地址、蓝牙服务、Bonjour、iAP2和TCP阶段并保留最近失败。导出不包含热点明文密码、SSID、MAC或认证材料。
- USB 使用真实配置/alternate描述符，旧平台有界bulk读取与16 KiB分块写入，保持整次超时预算。API19已有Wi-Fi、手动热点和mDNS提供兼容实现，不自动改变系统默认路由。

## 已知有线与认证限制

上游有线链路是 USB NCM以太帧 → TUN → 内核TCP/IP → AirPlay socket。`VpnService.Builder.addAllowedApplication` 与 `setBlocking` 从API21才提供。本分支在API19的VPN连接边界明确报错，没有把VPN擅自扩大到所有应用。

这不是“Android4.4理论上无法有线”的结论。后续需要用户态IPv6/TCP/UDP/NDP栈与socket适配，或证据表明车机内核提供可用NCM网卡。当前用户态USB端点不能仅靠普通Socket.bind变成内核网络接口。

测试APK不含accessory identity，也不含第三方证书私钥。没有提取、复制、上传第三方认证私钥，没有修改认证绕过。它用于安装、启动、设置和诊断验证；完整CarPlay会话仍需合法授权的运行认证条件。

### 正常投屏具体缺什么

当前合法输入没有提供可被iPhone接受、且获准用于本项目的认证身份或认证提供者，因此现在不能交付正常投屏成品。Android APK签名与CarPlay认证是两回事；换热点、换APK签名或再次安装同一无认证包都不能补齐身份。

`DiPlayBootstrap.ensure` 默认LOCAL模式先查本应用私有 `no_backup/offline-mfi`。仅当目标目录不存在时，它才尝试从APK的 `assets/offline-mfi/identity.pk8` 和 `certificate.p7b` 初始化；已有目录会直接加载，不会因覆盖安装自动替换。当前测试APK没有这些asset。

构建入口接受显式 `DIPLAY_AUTH_ASSETS_DIR`，`assembleStandaloneDebug`会要求这两个输入存在；这只是技术接入机制，不提供身份申领或授权。`LocalMfiAuthenticationClient`校验密钥/证书匹配不等于iPhone信任。应用自动生成的AirPlay配对身份也不能替代MFi认证。

覆盖安装保留数据需要相同applicationId、兼容Android签名且未清除数据。本测试包是 `com.shihab.diplay.hudtest`，官方release是 `com.shihab.diplay`，两者不是覆盖更新关系；也未验证旧Legacy包的签名。不能把当前界面的“覆盖安装完整构建”当作已验证解决方案，更不能让用户删除旧数据尝试修复认证。

源码有USB/CH341真MFi芯片、I2C和远程客户端后端，但这不证明用户拥有可用硬件或服务，也未找到已授权可用的托管服务。新装默认LOCAL的界面还会在身份缺失时拦截连接/主机设置，不能声称插入芯片并切换选项即可。没有K2X硬件证据时，不建议据此购买配件。后续须先确认真实、获授权的认证提供者，再接入并进行真机完整会话测试；本次不提取第三方私钥、不伪造身份、不绕过认证。

## 验证结果

| 项目 | 实际结果 |
| --- | --- |
| 首次完整 `:mobile:assembleDebug` | **通过**，JNI、资源合并、Kotlin、DEX及APK打包完成 |
| 正式 shared 单元测试 | **687/687通过**，0失败、0跳过 |
| 正式 common 单元测试 | **550/550通过**，0失败、0跳过；原19失败为系统服务查找改动后测试夹具未同步，修正夹具并保留行为断言 |
| home 单元测试与样例lint | **4/4通过**；home/maphost lint通过 |
| 独立小范围回归 | 36项纯JVM、53项媒体、4项无线筛选测试通过；不与完整Gradle数量相加 |
| mobile / shared lint | **通过**，原API边界和权限检查错误已处理；仍有警告 |
| common 单独lint | **未通过**：60项错误全部为已有资源的 `MissingTranslation`；没有隐藏此检查，最终报告无NewApi错误，另有191项警告 |
| APK元数据 | minSdk **19**、targetSdk37；四种ABI；4个DEX均为 **035** |
| APK签名与ZIP | `apksigner --min-sdk-version 19` **v1=true、v2=true**，zipalign/ZIP完整性通过 |
| 运行认证资产 | 构建凭据检查通过，assets未含identity/私钥容器；APK未发现完整PEM私钥块 |
| 官方API19模拟器 | 冻结交付APK **pm install返回Success**；冷启动 `Status:ok`，约1765ms，本次日志无FATAL EXCEPTION / NoSuchMethodError / VerifyError |
| API19模拟器页面 | 主页、常规设置、连接准备和设备/解码器报告均成功显示；设备报告列出API19/x86/约1GB/800×480。连接按钮明确提示认证未加载，未建立CarPlay会话 |
| automotive / home / maphost构建 | **通过**；其中automotive因移动Car App类而增加构建覆盖 |
| K2X真机连接、音视频、重连和长时压力 | **未测试** |
| GitHub Actions | 源码提交 `f6710d5` 已触发 [Android checks](https://github.com/pandaligx/DiPlay/actions/runs/37270890800)，记录本次文档时仍在运行；不能称为通过 |

日志在 `build/api19-verification/`，不提交生成物。Robolectric4.17支持SDK23起，其通过不等于API19框架/厂商ROM通过。API19模拟器为独立AVD/端口，复用已有WHPX，不连接或扫描物理设备。

最终合并验证命令使用 `--continue`，退出码为1，唯一失败任务是 `:common:lintDebug` 的60项缺失翻译；这些字符串全部已存在于固定上游基线。其余本次任务均执行完成，没有把整个命令描述为通过。正式单元测试总计1241项，全部通过。

模拟器证据在任务目录 `.tools/logs/api19/`：`delivery-install.log`、`delivery-launch.log`、`delivery-launch-logcat.txt`、`delivery-home.png`、`delivery-device-report.png`、`delivery-connection.png`。验证完成后停止本任务模拟器，保留AVD与日志。

## 交付与后续

Library 文件：`DiPlay-0.2.12-api19-source-test.apk`，18,888,976字节。

SHA-256：`5272bd149a6e845f006451243f0f3f5579e36725586ef154dc5d07b0daf643cd`。

测试包使用debug签名，不能承诺覆盖其他作者签名的旧包；不要为覆盖安装盲目卸载而丢失旧配置。先在停车状态验证安装/启动/设置/设备报告，再按实际ABI、蓝牙与热点证据推进无线。未经真机验证，不宣称全面兼容或连接成功。

此前创建fork曾被自动审批阻止。用户随后自行创建仓库；现已把原始适配提交 `f6710d568a9439bcd2af487dbefbf38d7ce0292a` 非强制推送至 [pandaligx/DiPlay:android-4.4](https://github.com/pandaligx/DiPlay/tree/android-4.4)，并核验远端SHA一致。`main`仍为上游基线，没有改默认分支或创建Release。此代码推送不代表认证和正常投屏目标已完成。

## 参考

- [固定上游基线](https://github.com/shihabal3amri/DiPlay/tree/2fc876e578eba3905a5b873e3c2dbd74f498433a)
- [固定Legacy参考](https://github.com/programmerguohuajing/DiPlay-Legacy-Android/tree/c8884adcc75bfda3c134db63877bd6c6f83beb74)
- [NDK历史：r26移除KitKat](https://developer.android.com/ndk/downloads/revision_history)
- [VpnService.Builder API](https://developer.android.com/reference/android/net/VpnService.Builder)
