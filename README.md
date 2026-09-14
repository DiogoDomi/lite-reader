# Media Reader 📖

## 🎯 Objective
A manga and document reader (`.cbz`, `.zip`, `.pdf`) for Android, focused strictly on lightweight performance, extreme stability, and low memory footprint.

## 💡 Motivation
This project was born out of a personal need: to keep reading on my old **Motorola Xoom 2** tablet (Android 4.0.3). Modern reading apps are far too heavy for legacy hardware and constantly crash due to OutOfMemory (OOM) errors. Media Reader was built from scratch to solve this issue, utilizing aggressive RAM management and optimized rendering to breathe new life into older devices.

## ✨ Key Highlights
* **OOM Guard & Predictive Pre-cache:** Uses a strict single-thread background cache limited to one ahead/behind page (direction-aware) to prevent memory leaks.
* **3-Stage PDF Rendering:** Native C++ rendering that automatically scales down resolution (1600px -> 1280px -> 800px) only if the device runs out of memory.
* **Night Reading Filters:** Built-in dynamic opacity filters (Dim and Sepia) controllable via screen-edge vertical swipe gestures.
* **Legacy-Friendly File Explorer:** A custom, lightweight built-in directory manager with reading progress tracking, bypassing the heavy native Android file picker.
* **Hardware Integration:** Screen-rotation-aware physical volume key navigation and immersive mode support.

## 🛠️ Tech Stack
* **Language:** Java
* **Platform:** Android SDK (Min API 15 / Android 4.0.3)
* **PDF Engine:** `pdfium-android` (Native C++ rendering)
* **Concurrency:** `ExecutorService` (Single-thread task queues)
* **Storage:** `SharedPreferences` (Reading state and local progress persistence)
* **Architecture:** Native UI (XML, ViewHolders) modularized following S.O.L.I.D. principles (separated UI, TouchManager, and standalone BookEngines).
