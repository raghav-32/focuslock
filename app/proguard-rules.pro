# Room, WorkManager and Compose ship their own consumer rules.
# Keep the accessibility service and receivers referenced only from the manifest.
-keep class com.focuslock.app.system.FocusLockAccessibilityService { *; }
-keep class com.focuslock.app.system.SystemEventsReceiver { *; }
-keep class com.focuslock.app.system.UsageSnapshotWorker { *; }
