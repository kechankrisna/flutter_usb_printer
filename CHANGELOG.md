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