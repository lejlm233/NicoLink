# 签名密钥配置（CI Secrets）

签名密钥 `signing/nicolink-test.jks` **不入库**（已被 `.gitignore` 排除：`signing/`、`*.jks`、`*.keystore`）。
公开仓库（GitHub）与私有仓库（cnb）都不含任何密钥文件，构建时通过环境变量 / CI Secret 注入，避免密钥泄露。

## 本地开发（无需密钥）

直接构建即可，未注入密钥时自动使用 Android `debug` 默认签名，`adb install -r` 正常：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :androidApp:assembleDebug --no-daemon
```

## 发布构建（注入密钥）

设置以下环境变量后，`debug` 与 `release` 都会使用注入的正式签名：

| 环境变量 | 说明 | 必填 |
|---|---|---|
| `SIGNING_KEYSTORE_BASE64` | keystore 文件的 Base64 编码（CI Secret 推荐） | 二选一 |
| `SIGNING_KEYSTORE_PATH` | keystore 文件路径（本地/CI 挂载，与 base64 二选一） | 二选一 |
| `SIGNING_STORE_PASSWORD` | keystore 密码 | 是 |
| `SIGNING_KEY_PASSWORD` | key 密码 | 是 |
| `SIGNING_KEY_ALIAS` | key 别名 | 是 |

> `SIGNING_KEYSTORE_BASE64` 优先于 `SIGNING_KEYSTORE_PATH`。base64 会在构建目录
> `androidApp/build/keystore/release.jks` 临时落盘（该目录已被 `.gitignore` 排除），构建后随 build 目录清理。

### 生成 Base64

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('signing/nicolink-test.jks'))
```

### CI 配置示例

在 GitHub Actions / cnb CI 的 Secrets 中新增上述 5 项，构建步骤前注入为环境变量即可。
密钥只在 CI 运行时存在，不进入代码仓库、不进入构建产物仓库。

## 安全说明

- 任何情况下都不要把 `.jks` / `.keystore` 提交到 git，也不要贴到日志、issue、聊天里。
- 若怀疑密钥泄露，立即更换 keystore 与所有密码，并重新分发 App。
- 测试签名与发布签名建议分开：测试用 debug 默认签名即可，发布用 CI 注入的正式签名。
