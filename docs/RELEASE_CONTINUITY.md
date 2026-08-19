# Release continuity

- Permanent application identity: `com.angel.hypergod`.
- Every release increments `versionCode` and preserves the existing database, files, OCR, profile, cases, timeline, reminders and backup compatibility.
- Every installable release update must use the same external signing key/certificate. The key and passwords never enter the public repository.
- Room changes require explicit migrations and representative old-data tests. `fallbackToDestructiveMigration`, clearing data and uninstall/reinstall are forbidden shortcuts.
- File/backup envelope versions are append-only contracts; new readers must authenticate and validate before exposing or mutating data.
- A debug APK is an internal test artifact, not proof of permanent release-signing continuity.

