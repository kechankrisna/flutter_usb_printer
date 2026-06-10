## 0.3.1

### New Features

* **`setChunkDelay(int ms)`**: New method to insert a configurable delay (in milliseconds) between USB bulk-transfer chunks. Increase to 20–50 ms if large payloads print intermittently on slower printers

### Bug Fixes

* **User-facing errors**: USB error messages (`uninitialized device`, `uninitialized manager`, permission errors) are now shown as Toasts instead of silent logcat entries
* **Toast on uninitialized**: Toasts are now shown when `USB device is not initialized` or `USB Manager is not initialized` rather than failing silently

## 0.3.0+1

### Breaking Changes

* `minSdk` raised from 16 to 24

### New Features

* USB permission dialog is now shown automatically when a device is plugged in (via `USB_DEVICE_ATTACHED` intent-filter and `device_filter.xml`)

### New Features

* **`isConnected()`**: New method that returns `true` only when a device is selected, the USB connection is open, and the bulk-out endpoint is ready

### Bug Fixes

* **Large print jobs**: `write()`, `printText()`, and `printRawText()` now send data in 16 KB chunks instead of a single bulk transfer, eliminating JNI critical lock warnings on large payloads
* **Transfer retry**: Bulk transfers are retried up to 3 times with increasing backoff (100ms/200ms/300ms) before failing
* **Endpoint stall**: A USB `CLEAR_FEATURE / ENDPOINT_HALT` control transfer is sent after `claimInterface` to clear any stale stall condition on the bulk-out endpoint, fixing `-1` transfer failures on first write after fresh connection
* **Power-cycle reconnect**: `mUsbDevice` is now cleared on `USB_DEVICE_DETACHED` and when `openDevice()` fails, so power-cycling the printer no longer causes a `RemoteException` — the next write triggers a proper re-select and permission request with the fresh device reference
* **Permission flow**: `connect()` now waits for the user to respond to the USB permission dialog before resolving — previously it returned `true` immediately before the user even saw the dialog
* **Permission check**: `hasPermission()` is checked before calling `requestPermission()` — no redundant dialog when permission is already granted
* **Connection reliability**: `openConnection()` now scans all USB interfaces (not just index 0) to find the BULK OUT endpoint, fixing printers that expose the print interface at index 1+
* **Silent failure fixed**: `openConnection()` previously returned `true` even when no endpoint was found, causing a silent NPE on `bulkTransfer`; it now returns `false` with a clear log message
* **Transfer result**: `write()`, `printText()`, and `printRawText()` now return the actual transfer result (`b >= 0`) instead of always returning `true`
* **Null crash**: Fixed `NullPointerException` in the USB permission broadcast receiver when `EXTRA_DEVICE` is absent on permission denial
* **UI null crash**: Fixed crash in device list when `manufacturer` or `productName` fields are null

### Maintenance

* Migrated Android build to AGP 8.11.1, Gradle 8.14, Kotlin 2.2.20
* Migrated to Kotlin DSL (`build.gradle.kts`) and new Flutter Gradle plugin DSL
* Fixed deprecated `getParcelableExtra` API (Android 13+)
* Fixed deprecated `buildDir` setter/getter (replaced with `layout.buildDirectory`)
* Replaced raw `Thread{}` with Kotlin Coroutines for proper error propagation
* Migrated test mocking to `TestDefaultBinaryMessengerBinding` API
* Added `flutter_lints` and `analysis_options.yaml`
* Added `android:exported="true"` to `MainActivity` (required for Android 12+)

## 0.2.0

* Upgraded to Flutter 3.41.9 / Dart 3.9.2+
* Migrated Android build to AGP 8.11.1, Gradle 8.14, Kotlin 2.2.20
* Migrated to Kotlin DSL (build.gradle.kts) and new Flutter Gradle plugin DSL
* Raised minSdk from 16 to 24 (breaking change)
* Fixed deprecated getParcelableExtra API (Android 13+)
* Replaced raw Thread{} with Kotlin Coroutines for proper error propagation
* Fixed printText/printRawText always returning true regardless of actual result
* Fixed non-true singleton pattern in USBPrinterAdapter
* Migrated test mocking to TestDefaultBinaryMessengerBinding API
* Added flutter_lints and analysis_options.yaml

## 0.1.0+1

* fixed Future<dynamic>

## 0.1.0

* null safety

## 0.0.1

* first release