# NicoLink 连接层协议笔记（M2 / AP + STA）

> 只记**事实**与我们的实现选择，不复制任何第三方代码。
> 事实来源：ISO 15740 / CIPA DC-X005、公开的 PTP 操作码表、OpenZCine 公开协议文档（<https://opencapture.org/openzcine/docs/protocol/>）、Nikon Z30 参考手册。时间参数一律以真机实测为准。

---

## 1. 组网：AP + STA 双模式

Z30 实测结论先行：**AP（相机开热点）与 STA（相机连手机热点）全链路都已打通**——
STA 锁标准对象命令但私有命令可直读，两条路径都能浏览/下载/取景/拍照（见 §5、§8）。
两种模式的组网方式：

AP（相机开热点，浏览推荐）：

```
相机: 网络菜单 → 连接至智能设备 → Wi-Fi 连接 → 建立连接（显示 NIKON_XXXXXX + 8 位密码）
  → 手机: 系统 Wi-Fi 连上该热点（无互联网 → 保持连接）
  → App: 直探 192.168.1.1:15740 → TCP 15740（socket 绑定热点网络）
```

STA（相机连手机热点，遥控/取景）：

```
手机开「个人热点」（系统热点，SSID/密码固定）
  → 相机: 网络菜单 → 连接至智能设备 → Wi-Fi 连接(STA mode) → 创建配置文件
         → 搜索 Wi-Fi 网络 → 选手机热点 SSID → 输入密码 → 自动获取 IP
  → 手机 App: 发现相机（mDNS + 子网扫描）→ TCP 15740
```

要点：

- Z30 的 STA 入口在「连接至智能设备 → Wi-Fi 连接(STA mode)」，固件较新才有；没有该菜单需先升级固件。
- 相机首次建档后记住 SSID/密码，之后开热点 + 开相机 Wi-Fi 即可直接连上。
- 一台相机同一时刻只接受一个 PTP/IP 发起方；测试前先退出 SnapBridge 等 App。
- Android 17（API 37）开始有本地网络访问权限 `ACCESS_LOCAL_NETWORK`，需要运行时授权。

---

## 2. PTP/IP 传输层（CIPA DC-X005）

TCP 端口 **15740**，两条连接：

| 连接 | 用途 |
|---|---|
| command | 所有操作事务 + 数据阶段 |
| event | 相机事件、`Probe_Request` 心跳 |

包头（小端）：

```
length:u32 | type:u32 | payload
```

`length` 含 8 字节包头（整包长度），< 8 为非法。

### 包类型

| 值 | 类型 | 通道 | 说明 |
|---|---|---|---|
| 0x01 | Init_Command_Request | command | GUID(16) + UTF-16LE 友好名(NUL 结尾) + protocolVersion u32(0x00010000) |
| 0x02 | Init_Command_Ack | command | connectionNumber u32 + 相机名 UTF-16LE(NUL 结尾，可能缺省) |
| 0x03 | Init_Event_Request | event | connectionNumber u32 |
| 0x04 | Init_Event_Ack | event | 仅包头 |
| 0x05 | Init_Fail | 任一 | 原因 u32：1 拒绝 / 2 忙 / 3 其他 |
| 0x06 | Operation_Request | command | dataPhase u32 + opcode u16 + transactionId u32 + params u32×n |
| 0x07 | Operation_Response | command | code u16 + transactionId u32 + params u32×n |
| 0x08 | Event | event | 同上形状 |
| 0x09 | Start_Data | command | transactionId u32 + totalLength u64 |
| 0x0A | Data | command | transactionId u32 + 原始数据（无内层长度头） |
| 0x0B | Cancel | command | transactionId u32 |
| 0x0C | End_Data | command | 同 Data，最后一个数据包 |
| 0x0D | Probe_Request | event | 仅包头 |
| 0x0E | Probe_Response | event | 仅包头（必须回，否则相机认为主机失联） |

`dataPhase`：`1` 无数据（或部分机型 data-in）、`2` data-out（主机→相机）、`3` data-in（相机→主机，规范值）。

`transactionId`：`OpenSession` 固定 **0**，之后从 1 递增。

---

## 3. 连接时序（Z30 = 第二代 Z 机身）

```
TCP connect :15740
→ Init_Command_Request / Ack          （拿 connectionNumber + 相机名）
→ 第二条 TCP + Init_Event_Request / Ack
→ OpenSession(0x1002, txId=0, sessionID=1)
→ GetDeviceInfo(0x1001)               解析机型/序列号/支持的操作码
→ ChangeApplicationMode(0x9435, p1=1) 进入 App 控制（失败不致命，记日志）
→ GetVendorPropCodes(0x90CA)          Gen1/Gen2 用这个；Gen3 用 0x9439
→ 以下按需：GetVendorStorageIDs / GetObjectHandles / GetObjectInfo / GetThumb
→ CloseSession(0x1003) 后再断开 socket
```

### 配对 / 首次注册（2026-11 修订）

> 旧结论「Z30 无配对码、STA 确认配置文件后即可直连」**已被推翻**：Z50II、
> ZR（libgphoto2 issue #1314 实测）都需要注册，Z8/Z30 按同代协议预期一致，以真机验证为准。

事实（来源：libgphoto2 #1314；真机待验）：

- 相机每个 Wi-Fi 配置文件按 **Init_Command_Request 里的 GUID** 绑定一个主机，主机名无关。
- 未绑定的配置文件：任何 GUID 都接受；已绑定其他主机：`Init_Fail reason 1`。
- 配对态下 GetDeviceInfo 返回受限操作表：`1001/1002/1003/935A/9439/9440/9441/9442/952B`，vendorExtID=0xFFFFFFFF。
- mDNS TXT 记录 `apps=PAIR` 表示该配置文件需要配对，`$DSC` 表示已配对（可用于 UI 判断，未实现）。

注册时序（连接成功后执行）：

```
OpenSession(1)
→ 0x9439 (param 9)    进配对模式（部分机身不需要，失败容忍）
→ 0x952B              读一次性配对码：u32 位数 + 每位一字节 0-9（非 ASCII）；
                      相机屏幕同步显示，主机侧比对。另一种返回带 16 字节头（u16 位数、偏移 20），解析做两形态兼容
→ 0x935A (param 0x2001) 确认 —— 相机屏幕等用户按 OK，响应阻塞到确认完成
→ 0x9439 (param 13)   退出配对模式（失败容忍）
→ 不发 CloseSession，直接断开两个 socket
→ 相机重启 Wi-Fi，等几秒后用同一 GUID 重连（可能 no route / refused，轮询重试）
```

- 配对确认后事件通道会收到 `0x4008 DeviceInfoChanged`。
- 取消注册：0x952B 之后直接断链，相机退出配对画面。
- App 侧实现：`NikonCamera.registerHost()` + `CameraSession.registerCamera()`；注册状态按序列号记在 `CameraStore`。

### 断线重连

相机只接受一个会话。断开顺序：`CloseSession` → 关事件 socket → 关命令 socket；重连前等待约 10s，避免相机把上一次会话当成未释放。

---

## 4. 用到的操作码

| Opcode | 名称 | 用途 |
|---|---|---|
| 0x1001 | GetDeviceInfo | 机型、序列号、支持的操作/属性 |
| 0x1002 / 0x1003 | OpenSession / CloseSession | 会话 |
| 0x9435 | ChangeApplicationMode | p1=1 进 App 控制 |
| 0x90CA | GetVendorPropCodes | 厂商属性清单 |
| 0x9209 | GetVendorStorageIDs | 真实存在的卡（推荐优先） |
| 0x1004 | GetStorageIDs | 标准存储列表（可能含占位槽） |
| 0x1005 | GetStorageInfo | 容量/剩余 |
| 0x1007 | GetObjectHandles | p1 存储 / p2 格式(0=全部) / p3 关联(0xFFFFFFFF=全部) |
| 0x1008 | GetObjectInfo | 文件名、大小、尺寸、拍摄时间 |
| 0x100A | GetThumb | 内嵌 JPEG 缩略图 |
| 0x101B | GetPartialObject | 32 位偏移分段读（默认 4 MiB/次） |
| 0x9431 | GetPartialObjectEx | 64 位偏移（大文件兜底） |
| 0x9421 | GetObjectSize | 64 位对象大小（兜底） |
| 0x9201 / 0x9202 | StartLiveView / EndLiveView | 实时取景 |
| 0x90C8 | DeviceReady | 启动取景后轮询就绪 |
| 0x9428 / 0x9203 | GetLiveViewImageEx / GetLiveViewImage | 取一帧（header + JPEG） |
| 0x9207 | InitiateCaptureRecInMedia | 首选释放快门 |
| 0x100E | InitiateCapture | 标准兜底 |
| 0x90C0 | InitiateCaptureRecInSdram | 拍进相机内存（备用） |

### 响应码

对照 libgphoto2 ptp.h 实表校正（0x200F 是 AccessDenied，0x2013 是 StoreNotAvailable）：

| 码 | 含义 |
|---|---|
| 0x2001 | OK |
| 0x2003 | Session Not Open |
| 0x2005 | Operation Not Supported |
| 0x2009 | Invalid Object Handle |
| 0x200F | Access Denied（App 控制态下读对象信息即此码） |
| 0x2013 | Store Not Available |
| 0x2019 | Device Busy |
| 0x201E | Session Already Open |

### 事件

| 码 | 含义 |
|---|---|
| 0x4002 | ObjectAdded（一张照片一个，RAW+JPG 计两个；params 为 (storage, handle, ofc)，语义不稳只当触发信号） |
| 0x400D | CaptureComplete |
| 0x4006 | DevicePropChanged |
| 0xC101 | 尼康 ObjectAddedInSDRAM（params[0]=新对象句柄；双拍场景可能不带有效句柄） |
| 0xC102 | 尼康 CaptureCompleteRecInSdram |

### 自动传输（2026-11-20 实现并真机验证）

相机「自动发送到智能设备/计算机」与图库「标记上传」在 PTP/IP 里的机制是
**事件通知 + 主机拉取**（拉协议，相机不主动发起 TCP）：

- 0xC101（带句柄）→ 直接入拉取队列；0x4002/0x400D/0xC102 → 1.5s 去抖后 0x9434 全卡
  元数据对账找新增媒体句柄；另有 12s 低频对账轮询兜底（覆盖不发事件的纯标记上传）。
- 拉取流程：0x9421 等写卡稳定（连续两次读数一致且 >0，30s 超时）→ 直读元数据构造
  CameraObject（占位名+0x9434 时间）→ 增量插入列表 → 下载落盘 → 角标。
- App 态（取景中）0x9434 依然实时（GetObjectHandles 不实时），对账在取景/拍摄模式都可用。
- 全局下载 Mutex：手动下载 / 遥控拍照 / 自动拉取三条路径串行，避免并发写同一文件。

M2 早期拍照等待策略（先记拍前 handle 集合 → 轮询找新增）仍保留在 captureAndWait 内，
但已升级为三层轮询（见 §5 拍摄小节）。

---

## 5. 实时取景

- 拉取式：`0x9435(1)`（取景时按需进入）→ `0x90C2(1)` 控制权交主机 → `0xD1A6=0` 取景选择器 → `0x9201` → 轮询 `DeviceReady` → 循环 `0x9428` 取帧，没有推流。
- `0x9428` 被拒（含 0xA00B）一律回落 `0x9203` 兜底；取帧指令两档都失败则清缓存重探测。
- 退出必须 `0x9202` + `0x90C2(0)` + `0x9435(0)`，否则相机留在 App 控制态、耗电发热且对象浏览被锁。
- **App 控制态（0x9435=1）会锁对象浏览**：`GetObjectInfo` 一律 `0x200F AccessDenied`。
  所以浏览/下载场景不能进 App 态；只有取景和遥控拍照按需进入，用完即退（Z30 固件 V1.20 实测）。
- **拍摄模式下标准对象命令同样被拒（Z30 实测，2026-11-19；2026-11-20 修正解读）**：
  GetObjectHandles 能列出句柄（含真实媒体句柄，混着随时间漂移的日期目录条目 0x11 段），
  但 0x1008/0x100A/0x101B 一律被拒——这是 STA 会话的标准命令锁（见下节「STA 直读通道」），
  私有命令 0x9434/0x9421/0x9431/0x9530 不受锁，**拍摄模式下也能完整浏览**（直读通道实测）。
- 一帧 = 显示器信息头 + JPEG。第三代头 1024 字节，第一代 512 字节；Z30 未知 → 实现按 `FF D8 FF` 扫描 JPEG 起点、按 `FF D9` 收尾，逐机型差异不影响出图。
- 头部字段（大端）：偏移 12 JPEG 长度、16/18 画面宽高、42 对焦结果、48 起对焦框……

### STA 直读通道（2026-11-20 修订，推翻「对象访问被固件锁死」的旧结论）

**STA（连接到电脑类会话）只锁标准对象命令，尼康私有读命令完全不受锁**（Z30 V1.20 实测，
注册与否无关）。此前把「目录句柄上 0x9431 失败」误判为整机会话锁——真实媒体句柄不受此限。

- 被锁（一律 0x200F AccessDenied）：`0x1008 GetObjectInfo`、`0x100A GetThumb`、
  `0x101B GetPartialObject`、`0x9805 GetObjectPropList`、`0x9803 GetObjectPropValue`。
- 不受锁（同会话实测全绿）：
  - `0x9434 GetObjectsMetaData(storage,0,0)`：一次取全卡元数据。载荷
    `u32 版本, u32 数量, 数量×{u32 handle, u32 size, u8 保留, u8 秒, u8 分, u8 时, u8 日, u8 月, u16 年}`。
    Z30 实测 size 字段全 0（需逐个 0x9421 补），拍摄时间有效。
  - `0x9421 GetObjectSize(h)`：64 位文件大小。
  - `0x9431 GetPartialObjectEx(h, offLo, offHi, lenLo, lenHi)`：任意区间读 → 缩略图/下载都走它。
  - `0x9530 GetPartialImage(h, type, offset, maxBytes)`：机身现生成的预览 JPEG，
    块大小实测 10240 可用，响应参数[1] = 预览总长；type 0 被拒再试 1。
    JPEG/NEF 都出图，用作 STA 锁态下的缩略图（0x100A 的替代）。
- **句柄编码（Z30 实测）**：高 8 位 = 对象类别
  （0x09/0x0A=RAW、0x29/0x2A=JPEG、0x59/0x5A/0x61/0x62/0x65/0x75=视频、0x11/0x12=目录）；
  **低 16 位 = 卡内文件号**（0001–9999）。文件名占位 `DSC_%04d.扩展名` 与卡内文件名一致；
  真名无法获取（0x9805/0x9803 被锁）。
- 探测规则：**只对媒体句柄（按高 8 位判型）探测**；目录句柄（0x11 段）上 0x9431 会回
  0x2009 InvalidObjectHandle，拿它当样本会误判整机会话不可用。
- App 实现：`NikonCamera.staDirectList / loadStaMeta / staPartialImage / staFormat`。
  列表 = 句柄按高 8 位过滤 → 0x9434 元数据（缺大小逐个 0x9421 补）→ 占位名；缩略图 = 0x9530；
  下载 = 0x101B 被拒（含 AccessDenied）自动回退 0x9431 分段。

### STA 拍摄（2026-11-20 实测）

- `0x9207 InitiateCaptureRecInMedia(0,0)` → **InvalidParameter**：STA 下部分固件把第一参数当
  存储 ID 解释，改发 `0x9207(-1,0)`（0xFFFFFFFF=全部存储）→ OK。快门序列：0x9207(0,0) →
  0x9207(-1,0) → 0x100E(0,0) → 0x90C0。
- **STA App 态（0x9435=1，取景中）GetObjectHandles 视图不实时**：拍后轮询拿不到新对象
  （照片确实已写卡）。改用 0x9434 全卡元数据轮询找新 handle，兜底「前缀最大编号 + 1」
  预测句柄 + 0x9421 验证。拍前基线取自最近一次非 App 态列表，避免把上一张误判成新照片。
- 拍后自动下载走直读通道，与手动下载同路。

---

## 6. 缩略图 / RAW

- `GetThumb` 返回相机内嵌 JPEG；RAW 也常有内嵌缩略图，但个别机型对 NEF 返回错误 → UI 用 RAW 徽标兜底。
- 下载走 `GetPartialObject`，一次 4 MiB，偏移累加；`GetObjectInfo.compressedSize` 作为总长度，为 0 时用 `GetObjectSize`。
- RAW 判定以扩展名为主（.nef/.nrw/.dng…），不依赖格式码。

---

## 7. 文件落盘

- JPEG：`MediaStore.Images` → `Pictures/NicoLink/`，`IS_PENDING` 控制可见时机，完成后出现在「本机」Tab。
- NEF：mime `image/x-nikon-nef`，同样落 `Pictures/NicoLink/`；MediaStore 拒绝时退回 App 外部目录 + `MediaScannerConnection.scanFile`。
- API < 29 无 `RELATIVE_PATH`：写 App 外部 `Pictures/NicoLink` 后扫描。

---

## 8. 真机验证结论（Z30 · 固件 V1.20 · 2026-11-19）

### AP 模式（相机开热点）——✅ 全链路打通

- 相机地址固定 `192.168.1.1`，手机侧 socket 必须绑定热点 Wi-Fi 网络
  （`NetworkBinder`：只认「Wi-Fi 且无 INTERNET 能力」的网络，并按目标网段精确匹配）。
- **禁止高并发全网段扫描**：48 并发会把相机嵌入式 Wi-Fi 服务打挂（相机报「无法连接」，
  需重新建立连接才能恢复）。AP 直探 `192.168.1.1` 即可，扫描并发整体降到 16。
- 浏览/下载/实时取景/遥控拍照全部实测通过。

### STA 模式（连接至智能设备）——✅ 直读通道全链路打通（2026-11-20）

- 连接 / 注册 / 取景 / 拍照 / **浏览 / 缩略图 / 下载**全部可用。
- 注册结论（真机实证）：注册后操作表恢复完整（96 个），但**标准对象命令仍被锁**
  ——注册解锁的是「会话合法性」，不解锁对象元数据；对象访问靠私有命令直读（见 §5 直读通道）。
- 直读实测数据：列表 12 张（JPEG+NEF 对）、0x9434 拍摄时间有效、0x9431 读 26MB NEF 头、
  0x9530 预览 JPEG/NEF 都出图、0x9431 分段下载 8MB JPG 字节数与 0x9421 完全一致、
  取景中遥控拍照（0x9207(-1,0)）→ 0x9434 轮询命中 → 自动下载落盘。
- 已知怪癖：0x9434 的 size 字段全 0；日期目录句柄（0x11 段）跨午夜漂移；
  0x9407/0x9408 队列指令不支持；App 态下 GetObjectHandles 视图不实时。
- 浏览体验：文件名是占位名（`DSC_0006.jpg`，卡内编号与真名一致）、尺寸显示 0；
  RAW 缩略图改为读文件头内嵌 JPEG（0x9431 单次请求，省去 0x9530 分块循环），已下载视频取本地首帧，
  相机列表缩略图按最大边 320px 采样解码；AP 的 0x1008 仍能给出真名/尺寸。
- AP 与 STA 都是完整可用通道：AP 有标准对象元数据；STA 靠私有直读，「不切热点也能用」。

### 传输层实证补充

- `Init_Command_Ack` 载荷：ConnectionID u32@0 + 相机 GUID 16B@4 + 相机名 UTF-16LE@20
  （此前从 @4 读名字得到乱码）。
- `OpenSession` 用随机会话号（1..0x7FFFFFFF）；遇 0x201E 显式 CloseSession 后换号重开。
- 注册流程需在 0x952B 后**等待约 8.5s**（用户在相机端按 OK），0x935A 失败重试 3 次。
- 心跳保活：每 5s 轻事务（0x1015 读电量），传输层异常连续 2 次判定断线。
- **进程冻结坑**：App 退后台被 Android 冻结后 socket 全死（ECONNABORTED），恢复前台时心跳会连续失败。
  已实现 `onAppResumed()` 触发 `autoReconnectWithRetry()`（最多 5 次递增退避）；
  异常断开 `markDisconnected()` 在前台也会自动重连。
  **用户主动断开除外**：本次进程内置 `userDisconnected` 标记，回相机页/回前台都不再重连；
  重启 App 后标记复位，仍按设置页「自动连接」执行。
- 日志落盘 `cacheDir/nicolink_debug.log`，`adb exec-out run-as com.filmlens.nicolink cat cache/nicolink_debug.log` 拉取。

### 验证清单

- [x] AP 模式：发现 / 注册 / 照片列表 / 缩略图 / 下载 / 实时取景 / 遥控拍照
- [x] STA 模式：连接 / 注册 / 照片列表 / 缩略图 / 下载 / 实时取景 / 遥控拍照（直读通道）
- [x] JPG / NEF 下载完整性（AP/STA 均验证落盘；速度实测用户确认无需再做）
- [x] 前台恢复自动重连 + 主动断开抑制重连（Z30 真机验证）
- [x] STA 直读缩略图提速（RAW 内嵌 JPEG / 本地首帧 / 320px 采样解码）
- [x] P2/P3 拍摄增强：录像、画面联动、触屏对焦、B 门开曝与收门参数（0x920C(0,0)）

调试日志开关在连接页；主页空态亦显示最近日志。

---

## 9. P2 拍摄增强（2026-11-22）

### 录像（0x920A / 0x920B）
- `0x920A StartMovieRecInCard` / `0x920B EndMovieRec`：DeviceInfo 声明支持，无参数。
- 链路：`0x9435(1)` 进入应用模式 → `0x920A`；`0xA00B NotLiveView` 表示机身要求先开取景，
  启动 `0x9201` 后重试一次；`DeviceBusy` 短暂重试；收门 `0x920B` 后退应用模式。
- **Z30 实测：拨盘在照片档时 `0x920A` 回 `0xA004 NikonInvalidStatus`**（机身状态不允许）。
  录像需把机身拨盘转到视频档；App 侧把该码翻译成明确提示，不冒充命令失败。
- 录像产物按视频句柄高 8 位识别（0x59/0x5A/0x61/0x62/0x65/0x75 → .mov，格式码 0xB003），
  复用拍照的三层新对象对账（句柄 → 0x9434 元数据 → 预测句柄）；落 App 专属目录，不进显影队列。
- 注意：0x920A/0x920B 与 0x90C2 无关；0x90C2 不是录像链路的一部分。

### 画面模式（0x90C2）
- 参数 1 = **遥控画面**：相机屏熄，显示「已连接到智能设备」；参数 0 = **联动画面**：相机屏与手机同显。
- 实时取景启动本身已发 `0x90C2(1)`；`EndLiveView` 收尾发 `0x90C2(0)`。用户主动切换后 UI 状态须与最后一次实际发送的参数一致。
- 联动画面下机身转入录像应用模式会停推 LV 帧（预期行为），不要据此判定监看断线。

### 厂商响应码（非标准段）
| 码 | 含义 |
|---|---|
| 0xA004 | NikonInvalidStatus：机身当前状态/档位不允许该操作 |
| 0xA00B | NikonNotLiveView：操作要求实时取景已开启 |
| 0xA200 | NikonBulbBusy：B 门收门时正在写卡，稍后重试 |

### 其他响应码
- 间隔/定时拍摄不新增 PTP 命令：定时=延迟后复用 `0x9207`；间隔=循环调用同一拍照链路，
  用串行闸门防止手动/间隔/自动下载重叠。

---

## 10. P3 对焦 / B 门（2026-11-22）

### 触屏点对焦
- `0x9205 ChangeAfArea(x, y)`：点是 live view 图像的像素空间坐标，与帧同宽高比。
  实测口径（厂商官方 App 与成熟第三方 App 报文独立收敛）：x 域 0..4000，
  y 域 0..(4000×帧高/帧宽)；帧尺寸未知时用 4000×3000 兜底。
- 下发 `0x9205` 后等 150ms 再发 `0x90C1 AfDrive`（无参数）：立即驱动可能仍按旧区域对焦。
- Z30 实测两命令均返回 OK（见 `nicolink_debug.log`），对焦框由 App 自绘（相机 AF 框不进 JPEG 流）。

### B 门长曝光
- 属性 `0x500D ExposureTime=B` 写入无效（实测只读）→ 必须机身拨盘切到 B 档。
- App 链路：`0x9207` 开曝 → App 计时（5/10/20/30s）→ `0x920C TerminateCapture` 收门。
- **收门 `0x920C` 必须带参数 `(0,0)`**：Z30 实测无参数回 `0x2006 ParameterNotSupported`
  （开曝 `0x9207(-1,0)` 本身 OK）。失败时降级 `0x1010 TerminateOpenCapture(0)`。
- 收门时 `0xA200 NikonBulbReleaseBusy`（正在写卡）或 `DeviceBusy` 需短暂重试，不应判失败。
- 两级都失败时保持曝光态并明确提示「相机可能仍在曝光，请手动按快门结束」，不静默判成功。
- 收门后用拍照同一套三层轮询找新照片（句柄 → 0x9434 元数据 → 预测句柄）。
