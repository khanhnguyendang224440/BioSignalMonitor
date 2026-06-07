# BioSignalMonitor

[![Android](https://img.shields.io/badge/Nền_tảng-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Ngôn_ngữ-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Giao_diện-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Trạng thái](https://img.shields.io/badge/Trạng_thái-Đang_phát_triển-orange)](#trạng-thái-dự-án)

**BioSignalMonitor** là ứng dụng Android dùng để tiếp nhận, phân tích, lưu đệm và hiển thị theo thời gian thực ba loại tín hiệu y sinh đồng bộ:

- **ECG** — Điện tâm đồ
- **PPG** — Quang thể tích ký
- **PCG** — Âm tâm đồ

Ứng dụng là một phần của hệ thống giám sát tín hiệu y sinh, trong đó thiết bị nhúng sử dụng STM32 để thu thập đồng bộ ECG, PPG và PCG, sau đó truyền dữ liệu đến điện thoại Android qua kết nối không dây.

> Repository hiện tập trung vào phía Android, bao gồm xử lý dữ liệu đầu vào, ghép packet, phân tích packet, lưu mẫu bằng ring buffer và hiển thị waveform. Phần kết nối BLE với thiết bị thật đang được tiếp tục phát triển.

---

## Mục lục

- [Giới thiệu](#giới-thiệu)
- [Trạng thái dự án](#trạng-thái-dự-án)
- [Bối cảnh hệ thống](#bối-cảnh-hệ-thống)
- [Kiến trúc ứng dụng](#kiến-trúc-ứng-dụng)
- [Luồng dữ liệu](#luồng-dữ-liệu)
- [Chức năng đã thực hiện](#chức-năng-đã-thực-hiện)
- [Chức năng dự kiến](#chức-năng-dự-kiến)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Công nghệ sử dụng](#công-nghệ-sử-dụng)
- [Cài đặt dự án](#cài-đặt-dự-án)
- [Build và chạy ứng dụng](#build-và-chạy-ứng-dụng)
- [Kiểm thử](#kiểm-thử)
- [Xử lý packet](#xử-lý-packet)
- [Các vấn đề kỹ thuật quan trọng](#các-vấn-đề-kỹ-thuật-quan-trọng)
- [Quy trình Git](#quy-trình-git)
- [Bảo mật và quyền riêng tư](#bảo-mật-và-quyền-riêng-tư)
- [Hạn chế hiện tại](#hạn-chế-hiện-tại)
- [Lộ trình phát triển](#lộ-trình-phát-triển)
- [Đóng góp](#đóng-góp)
- [Tác giả](#tác-giả)
- [Giấy phép](#giấy-phép)

---

## Giới thiệu

Mục tiêu của BioSignalMonitor là xây dựng một ứng dụng Android có khả năng giám sát tín hiệu y sinh theo thời gian thực một cách ổn định, dễ mở rộng và dễ kiểm thử.

Ứng dụng được thiết kế để thực hiện các nhiệm vụ chính:

1. Nhận dữ liệu thô từ nguồn truyền thông.
2. Ghép các mảnh dữ liệu thành packet hoàn chỉnh.
3. Kiểm tra và phân tích nội dung packet.
4. Lưu mẫu tín hiệu vào bộ đệm có giới hạn.
5. Cập nhật trạng thái ứng dụng thông qua ViewModel.
6. Hiển thị waveform ECG, PPG và PCG.
7. Phát hiện packet lỗi, mất block hoặc gián đoạn sequence.

Việc tách riêng tầng truyền dữ liệu, tầng giao thức, tầng lưu trữ và tầng giao diện giúp hệ thống dễ bảo trì, dễ kiểm thử và dễ thay thế nguồn dữ liệu giả bằng BLE thật.

---

## Trạng thái dự án

**Giai đoạn hiện tại:** Xây dựng nguyên mẫu luồng xử lý dữ liệu Android.

### Đã có

- Project Android sử dụng Kotlin.
- Giao diện Jetpack Compose.
- Mô hình dữ liệu `BioPacket`.
- Bộ ghép packet `PacketAssembler`.
- Bộ phân tích packet `PacketParser`.
- Ring buffer để lưu tín hiệu.
- Nguồn dữ liệu giả phục vụ kiểm thử.
- Thành phần vẽ waveform tùy chỉnh.
- Tầng quản lý trạng thái ban đầu.

### Đang thực hiện

- Quét thiết bị BLE.
- Kết nối GATT.
- Khám phá service và characteristic.
- Nhận notification từ BLE.
- Tự động kết nối lại khi mất kết nối.
- Hiển thị liên tục ba tín hiệu ECG, PPG và PCG.
- Phát hiện mất packet và gián đoạn sequence.

### Chưa hoàn thành

- Tích hợp BLE hoàn chỉnh với thiết bị nhúng thật.
- Kiểm thử chạy dài hạn.
- Ghi và xuất dữ liệu CSV.
- Xác thực tín hiệu theo tiêu chuẩn y tế.
- Cấu hình bản phát hành production.

---

## Bối cảnh hệ thống

BioSignalMonitor được thiết kế để làm việc với hệ thống thu thập tín hiệu gồm:

- **STM32F401**
- **AD8232** dùng cho ECG
- **MAX30102** dùng cho PPG
- **INMP441** dùng cho PCG
- **FreeRTOS / CMSIS-OS**
- Thu thập dữ liệu bằng DMA
- Đồng bộ bằng Timer
- Truyền dữ liệu theo block

Thiết bị nhúng gom mẫu thành các block đồng bộ. Mỗi block dự kiến chứa dữ liệu ECG, PPG và PCG có cùng mã sequence và timestamp.

```mermaid
flowchart LR
    ECG[AD8232\nECG] --> STM32[STM32F401\nFreeRTOS]
    PPG[MAX30102\nPPG] --> STM32
    PCG[INMP441\nPCG] --> STM32

    STM32 --> SYNC[Đồng bộ block\nID + timestamp + samples]
    SYNC --> LINK[Kết nối không dây\nBLE]
    LINK --> APP[BioSignalMonitor\nAndroid]
    APP --> PARSER[Ghép và phân tích packet]
    PARSER --> BUFFER[Ring buffer tín hiệu]
    BUFFER --> UI[Hiển thị waveform]
```

---

## Kiến trúc ứng dụng

Ứng dụng được tổ chức theo các tầng độc lập:

```mermaid
flowchart TD
    SOURCE[Nguồn dữ liệu\nFake source / BLE source]
    ASSEMBLER[PacketAssembler]
    PARSER[PacketParser]
    MODEL[BioPacket]
    BUFFER[RingBuffer]
    VM[SignalViewModel]
    UI[Giao diện Jetpack Compose]
    CANVAS[WaveformCanvas]

    SOURCE --> ASSEMBLER
    ASSEMBLER --> PARSER
    PARSER --> MODEL
    MODEL --> BUFFER
    BUFFER --> VM
    VM --> UI
    UI --> CANVAS
```

### Vai trò của từng tầng

| Tầng | Chức năng |
|---|---|
| Nguồn dữ liệu | Tạo hoặc tiếp nhận luồng byte từ dữ liệu giả hoặc BLE |
| Ghép packet | Ghép nhiều đoạn byte thành một frame hoàn chỉnh |
| Phân tích packet | Kiểm tra và chuyển frame thành dữ liệu tín hiệu |
| Lưu tín hiệu | Lưu lịch sử mẫu trong ring buffer có giới hạn |
| Quản lý trạng thái | Cung cấp dữ liệu và trạng thái cho giao diện |
| Hiển thị | Vẽ waveform và hiển thị trạng thái kết nối hoặc packet |

Kiến trúc này giúp giảm phụ thuộc giữa các thành phần và cho phép phát triển BLE thật mà không phải sửa toàn bộ phần giao diện.

---

## Luồng dữ liệu

Một block dữ liệu đi qua các bước sau:

```text
Cảm biến y sinh
    ↓
STM32 tạo block đồng bộ
    ↓
BLE packet hoặc mảnh packet
    ↓
PacketAssembler
    ↓
PacketParser
    ↓
BioPacket
    ↓
Ring buffer ECG / PPG / PCG
    ↓
SignalViewModel
    ↓
WaveformCanvas
```

Các kiểm tra dự kiến ở tầng packet:

- Packet đã đủ dữ liệu hay chưa
- Kích thước packet có đúng hay không
- Loại packet có được hỗ trợ hay không
- Số lượng mẫu có hợp lệ hay không
- Sequence có liên tục hay không
- Timestamp có tăng hợp lệ hay không
- Dữ liệu có bị thiếu hoặc hỏng hay không

---

## Chức năng đã thực hiện

### Xử lý dữ liệu và giao thức

- Mô hình dữ liệu `BioPacket`
- Ghép packet từ nhiều mảnh byte
- Phân tích packet thành các mảng tín hiệu
- Tách riêng tầng truyền thông và tầng giao thức
- Tạo dữ liệu kiểm thử thông qua `FakeBleSource`

### Hạ tầng xử lý tín hiệu

- Ring buffer có dung lượng cố định
- Chèn mẫu liên tục
- Kiểm soát bộ nhớ
- Chuẩn bị nền tảng lưu ba kênh tín hiệu đồng bộ

### Giao diện

- Sử dụng Jetpack Compose
- Vẽ waveform bằng `WaveformCanvas`
- Hỗ trợ theme
- Chuẩn bị kiến trúc cho cập nhật thời gian thực

### Kiểm thử

- Có thư mục unit test
- Có thư mục instrumented test
- Có nguồn dữ liệu giả để kiểm thử giao thức độc lập với phần cứng

---

## Chức năng dự kiến

- Xử lý quyền Bluetooth theo từng phiên bản Android
- Quét thiết bị BLE
- Lọc thiết bị theo tên hoặc UUID
- Kết nối GATT
- Khám phá service và characteristic
- Đăng ký nhận notification
- Thương lượng MTU
- Ghép packet bị chia nhỏ qua BLE
- Phát hiện mất sequence
- Thống kê tỷ lệ mất packet
- Tự động kết nối lại với retry và timeout
- Điều khiển hiển thị từng tín hiệu
- Tạm dừng, tiếp tục và xóa biểu đồ
- Điều chỉnh tỉ lệ waveform
- Quản lý phiên ghi dữ liệu
- Xuất CSV
- Lưu metadata của phiên đo
- Kiểm thử bộ nhớ và hiệu năng dài hạn
- Xử lý vòng đời ứng dụng
- Ghi log lỗi và chẩn đoán

---

## Cấu trúc thư mục

```text
BioSignalMonitor/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── androidTest/
│       │   └── java/com/example/biosignalmonitor/
│       │       └── ExampleInstrumentedTest.kt
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/biosignalmonitor/
│       │   │   ├── MainActivity.kt
│       │   │   ├── WaveformCanvas.kt
│       │   │   ├── fake/
│       │   │   │   └── FakeBleSource.kt
│       │   │   ├── protocol/
│       │   │   │   ├── BioPacket.kt
│       │   │   │   ├── PacketAssembler.kt
│       │   │   │   └── PacketParser.kt
│       │   │   ├── signal/
│       │   │   │   ├── RingBuffer.kt
│       │   │   │   └── SignalViewModel.kt
│       │   │   └── ui/theme/
│       │   │       ├── Color.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   └── res/
│       └── test/
│           └── java/com/example/biosignalmonitor/
│               └── ExampleUnitTest.kt
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```

---

## Công nghệ sử dụng

| Hạng mục | Công nghệ |
|---|---|
| Ngôn ngữ | Kotlin |
| Nền tảng | Android |
| Giao diện | Jetpack Compose |
| Build system | Gradle với Kotlin DSL |
| Quản lý trạng thái | Android ViewModel |
| Vẽ tín hiệu | Compose Canvas |
| Truyền dữ liệu | Dự kiến BLE, hiện có nguồn giả |
| Quản lý mã nguồn | Git và GitHub |
| Kiểm thử | JUnit và Android Instrumented Test |

---

## Cài đặt dự án

### Yêu cầu

Cần cài đặt:

- Android Studio
- Android SDK
- Git
- JDK tương thích với Android Gradle Plugin
- Thiết bị Android hoặc emulator

Để kiểm thử BLE thật, thiết bị Android cần hỗ trợ Bluetooth Low Energy.

### Clone repository

Bằng SSH:

```bash
git clone git@github.com:khanhnguyendang224440/BioSignalMonitor.git
```

Hoặc bằng HTTPS:

```bash
git clone https://github.com/khanhnguyendang224440/BioSignalMonitor.git
```

Sau đó mở thư mục project bằng Android Studio.

### Đồng bộ Gradle

Sau khi mở project:

1. Chờ Android Studio hoàn tất Gradle Sync.
2. Cài các thành phần SDK được yêu cầu.
3. Chọn emulator hoặc thiết bị Android.
4. Build và chạy ứng dụng.

---

## Build và chạy ứng dụng

### Windows PowerShell

```powershell
.\gradlew.bat assembleDebug
```

### macOS hoặc Linux

```bash
./gradlew assembleDebug
```

### Cài bản debug lên thiết bị

```powershell
.\gradlew.bat installDebug
```

### Chạy từ Android Studio

1. Mở project.
2. Chọn cấu hình chạy `app`.
3. Chọn thiết bị.
4. Nhấn **Run**.

---

## Kiểm thử

### Chạy unit test

Windows:

```powershell
.\gradlew.bat test
```

macOS hoặc Linux:

```bash
./gradlew test
```

### Chạy instrumented test

Windows:

```powershell
.\gradlew.bat connectedAndroidTest
```

macOS hoặc Linux:

```bash
./gradlew connectedAndroidTest
```

### Các trường hợp nên kiểm thử cho packet

- Một packet hoàn chỉnh
- Một packet bị chia thành nhiều mảnh
- Nhiều packet trong cùng một mảng byte
- Header sai
- Kích thước packet sai
- Packet chưa đủ
- Số mẫu không đúng
- Sequence bị lặp
- Sequence bị mất
- Sequence bị tràn
- Timestamp không liên tục
- Có byte rác trước packet hợp lệ
- Nhận dữ liệu liên tục trong thời gian dài

---

## Xử lý packet

Tầng giao thức hiện có:

- `BioPacket.kt` — mô tả dữ liệu của một block tín hiệu
- `PacketAssembler.kt` — ghép packet từ các đoạn dữ liệu nhận được
- `PacketParser.kt` — kiểm tra và phân tích packet hoàn chỉnh

Một packet sau khi phân tích dự kiến chứa:

- Sequence
- Timestamp
- Số lượng mẫu
- Mảng ECG
- Mảng PPG
- Mảng PCG

Định dạng byte của packet Android phải luôn đồng bộ với firmware.

Khi thay đổi định dạng packet, cần sửa đồng thời:

1. Phần đóng gói packet phía STM32 hoặc ESP32
2. `PacketParser` phía Android
3. Kiểm tra kích thước packet
4. Unit test
5. Tài liệu giao thức

### Mẫu tài liệu giao thức

| Trường | Kích thước | Kiểu dữ liệu | Ý nghĩa |
|---|---:|---|---|
| Header | Theo thiết kế | Unsigned | Đánh dấu bắt đầu frame |
| Sequence | Theo thiết kế | Unsigned | ID block tăng dần |
| Timestamp | Theo thiết kế | Unsigned | Thời điểm thu mẫu |
| Sample count | Theo thiết kế | Unsigned | Số mẫu trong block |
| ECG payload | Theo thiết kế | Signed samples | Dữ liệu ECG |
| PPG payload | Theo thiết kế | Unsigned hoặc signed | Dữ liệu PPG |
| PCG payload | Theo thiết kế | Signed samples | Dữ liệu PCG |
| CRC/checksum | Tùy chọn | Unsigned | Kiểm tra toàn vẹn dữ liệu |

> Kích thước cụ thể cần được cập nhật sau khi định dạng packet cuối cùng giữa firmware và Android được chốt.

---

## Các vấn đề kỹ thuật quan trọng

### Quản lý bộ nhớ

Ứng dụng giám sát y sinh có thể chạy liên tục trong thời gian dài. Nếu dùng danh sách không giới hạn, dữ liệu sẽ tăng dần và có thể làm ứng dụng hết bộ nhớ.

Ring buffer giúp:

- Bộ nhớ luôn có giới hạn
- Tự động ghi đè mẫu cũ
- Hiển thị một cửa sổ thời gian cố định
- Tăng độ ổn định khi chạy lâu

### Packet fragmentation

BLE notification không đảm bảo một notification tương ứng với đúng một packet ứng dụng.

Một packet có thể:

- Nằm trọn trong một notification
- Bị chia thành nhiều notification
- Chung notification với một phần của packet tiếp theo

Vì vậy cần tách riêng `PacketAssembler` và `PacketParser`.

### Hiệu năng giao diện

Waveform thời gian thực cần hạn chế cấp phát bộ nhớ và recomposition không cần thiết.

Một số nguyên tắc:

- Dùng buffer có giới hạn
- Không copy mảng lớn ở mỗi sample
- Cập nhật UI theo batch
- Tách tần số lấy mẫu và tần số vẽ
- Xử lý packet trên coroutine nền
- Callback BLE phải ngắn và không blocking

### Đồng bộ tín hiệu

Ứng dụng không nên mặc định ba tín hiệu đồng bộ chỉ vì chúng xuất hiện trong cùng packet.

Cần kiểm tra:

- Cùng sequence
- Cùng timestamp
- Đủ số mẫu
- Đúng thứ tự
- Không mất block

### Xử lý lỗi

Hệ thống thực tế cần xử lý:

- Bluetooth bị tắt
- Thiếu quyền truy cập
- Thiết bị ngoài phạm vi
- Kết nối GATT thất bại
- Khám phá service thất bại
- Bật notification thất bại
- Packet sai
- Packet timeout
- Mất sequence
- Mất kết nối bất ngờ
- Kết nối lại thất bại nhiều lần

---

## Quy trình Git

Nhánh `main` được dùng làm nhánh ổn định.

Mỗi chức năng mới nên được phát triển trên branch riêng.

### Quy tắc đặt tên branch

```text
feature/ble-scanning
feature/gatt-connection
feature/realtime-waveform
feature/csv-export
fix/packet-fragmentation
fix/sequence-gap
refactor/protocol-layer
test/packet-parser
docs/update-readme
```

### Quy trình làm việc

```bash
git switch main
git pull origin main
git switch -c feature/ble-scanning

# Thực hiện thay đổi

git status
git add .
git commit -m "feat: implement BLE device scanning"
git push -u origin feature/ble-scanning
```

Sau đó tạo Pull Request từ branch chức năng vào `main`, kiểm tra code và chỉ merge khi project build thành công.

### Quy tắc commit

Dự án sử dụng cách đặt commit gần với Conventional Commits:

```text
feat: thêm chức năng mới
fix: sửa lỗi
refactor: tái cấu trúc code nhưng không đổi chức năng
test: thêm hoặc sửa kiểm thử
docs: cập nhật tài liệu
chore: thay đổi cấu hình hoặc công cụ
perf: cải thiện hiệu năng
```

Ví dụ:

```text
feat: add BLE packet notification handler
fix: preserve incomplete bytes between notifications
test: add fragmented packet parser tests
refactor: separate protocol parsing from BLE transport
docs: document synchronized signal packet format
```

---

## Bảo mật và quyền riêng tư

Tín hiệu y sinh có thể là dữ liệu cá nhân nhạy cảm.

Trước khi dùng ứng dụng với người thật:

- Không commit API key, mật khẩu, certificate hoặc signing key
- Không lưu dữ liệu sức khỏe nếu chưa có sự đồng ý
- Không ghi dữ liệu y sinh thô vào log công khai
- Sử dụng truyền dữ liệu an toàn khi cần
- Xác định chính sách lưu trữ và xóa dữ liệu
- Bảo vệ file xuất ra
- Kiểm tra cơ chế Android Backup
- Chỉ yêu cầu quyền cần thiết
- Tuân thủ quy định về quyền riêng tư và dữ liệu y tế

Các file sau không được commit:

```text
local.properties
*.jks
*.keystore
*.p12
*.pem
.env
secrets.properties
```

Dự án phục vụ học tập, nghiên cứu và phát triển kỹ thuật. Đây **không phải thiết bị y tế đã được chứng nhận** và không được sử dụng làm căn cứ duy nhất cho chẩn đoán hoặc điều trị.

---

## Hạn chế hiện tại

- BLE với phần cứng thật chưa hoàn thành
- Hiện vẫn dùng một phần nguồn dữ liệu giả
- Định dạng packet chưa được chốt chính thức
- Chưa kiểm thử waveform trong thời gian dài
- Chưa có chức năng ghi dữ liệu hoàn chỉnh
- Chưa có xác thực lâm sàng
- Chưa có quy trình phát hành ứng dụng

---

## Lộ trình phát triển

### Giai đoạn 1 — Luồng xử lý giao thức

- [x] Tạo mô hình dữ liệu packet
- [x] Tạo PacketAssembler
- [x] Tạo PacketParser
- [x] Tạo ring buffer
- [x] Tạo nguồn dữ liệu giả
- [x] Tạo thành phần waveform ban đầu

### Giai đoạn 2 — Tích hợp BLE

- [ ] Thêm quyền Bluetooth
- [ ] Quét thiết bị
- [ ] Lọc thiết bị theo tên hoặc UUID
- [ ] Kết nối GATT
- [ ] Khám phá service và characteristic
- [ ] Bật notification
- [ ] Nhận packet bị phân mảnh
- [ ] Tự động kết nối lại

### Giai đoạn 3 — Giám sát thời gian thực

- [ ] Hiển thị ECG
- [ ] Hiển thị PPG
- [ ] Hiển thị PCG
- [ ] Hiển thị trạng thái kết nối
- [ ] Hiển thị tốc độ packet
- [ ] Hiển thị số sequence bị mất
- [ ] Thêm nút pause và clear

### Giai đoạn 4 — Ghi và phân tích dữ liệu

- [ ] Bắt đầu và dừng phiên ghi
- [ ] Xuất CSV
- [ ] Lưu metadata
- [ ] Thêm bộ lọc cơ bản
- [ ] Hiển thị chất lượng tín hiệu
- [ ] Kiểm tra tính liên tục của mẫu

### Giai đoạn 5 — Độ ổn định

- [ ] Kiểm thử chạy dài hạn
- [ ] Kiểm tra bộ nhớ
- [ ] Kiểm tra CPU
- [ ] Stress test kết nối lại
- [ ] Kiểm thử packet lỗi
- [ ] Kiểm thử vòng đời ứng dụng
- [ ] Cấu hình release build

---

## Đóng góp

Repository hiện được duy trì phục vụ đồ án và phát triển kỹ thuật.

Khi đóng góp thay đổi:

1. Tạo branch riêng.
2. Mỗi commit chỉ nên chứa một nhóm thay đổi rõ ràng.
3. Thêm test khi thay đổi giao thức.
4. Cập nhật tài liệu khi thay đổi hành vi.
5. Kiểm tra build trước khi tạo Pull Request.
6. Không commit file cấu hình cá nhân hoặc dữ liệu nhạy cảm.

---

## Tác giả

**Nguyễn Đăng Khánh**

- GitHub: [@khanhnguyendang224440](https://github.com/khanhnguyendang224440)
- Lĩnh vực quan tâm: Embedded Systems, IoT, Android, BLE và thu thập tín hiệu y sinh

---

## Giấy phép

Dự án hiện chưa chọn giấy phép mã nguồn.

Khi chưa có license, mã nguồn vẫn thuộc quyền sở hữu mặc định của tác giả. Người khác có thể xem repository nhưng không mặc nhiên được phép sao chép, sửa đổi hoặc phân phối lại.

Có thể bổ sung MIT, Apache-2.0 hoặc giấy phép phù hợp khác sau khi xác định mục đích sử dụng của dự án.
