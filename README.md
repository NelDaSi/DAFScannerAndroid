# DAF Scanner

A robust Data Matrix Code (dmc) Scanning and Verification application built with modern Android technologies. DAF Scanner is designed for industrial workflows where identifying, parsing, and verifying part serial numbers is critical.

## 🚀 Features

- **High-Performance Scanning**: Powered by Google's ML Kit Barcode Scanning for fast and reliable detection.
- **Specialized Part Parsing**: Automatically extracts Type Code, Supplier Code, Serial Number, and Batch Number from industrial barcode formats (27 or 29 character strings).
- **Smart Data Conversion**: Real-time conversion of Hexadecimal serial numbers to Decimal format.
- **Verification Mode**:
  - Import a list of expected serial numbers via CSV.
  - Scan items to verify their presence in the list.
  - Track progress and see which items are missing.
  - Export verification reports (Summary or CSV).
- **Comprehensive History**:
  - View all scanned parts with timestamps and parsed details.
  - Search and filter through your scanning history.
  - Multi-select and bulk delete functionality.
- **Rich Data**: Attach photos to scans for better traceability.
- **Modern UI**: Clean and responsive Material 3 interface built entirely with Jetpack Compose.

## 🛠 Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with StateFlow and ViewModel
- **Camera**: CameraX
- **Barcode Engine**: ML Kit Barcode Scanning
- **Database**: Room (for persistent storage of scans)
- **Navigation**: Compose Navigation
- **Image Loading**: Coil
- **Concurrency**: Kotlin Coroutines
- **Dependency Injection**: Manual / ViewModelProvider

## 📦 Getting Started

### Prerequisites
- Android Studio Ladybug or newer.
- Android SDK 24 (Nougat) or higher.

### Installation
1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run on your device.

## 📊 Verification CSV Format
To use the verification mode, import a CSV file (comma or semicolon delimited). The app searches for a column named **"Product ID"**.
- Each Product ID should be at least 13 characters long.
- The first 7 characters are treated as the **Type Code**.
- The last 6 characters are treated as the **Hex Serial Number**.
- Example Product ID: `215000188429123456` -> Type: `2150001`, Serial (Hex): `123456`.
