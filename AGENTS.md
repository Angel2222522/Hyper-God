# Hyper God maintenance contract

- Preserve `applicationId = com.angel.hypergod` and increase `versionCode` monotonically.
- Use the same external release signing certificate for every installable update. Never commit the private key.
- Never use destructive Room migration, uninstall/reinstall, clearing app data, or changing encryption assumptions as a shortcut.
- Keep core operation offline; do not add accounts, trackers, analytics, ads, telemetry, or `INTERNET` without an explicit product decision.
- Treat document bytes, OCR, metadata, FTS, profile, case history, reminders, exports, staging files, and logs as sensitive surfaces.
- Keep input, archive, PDF, bitmap, OCR, and text work bounded. Preserve cancellation and recovery journals.
- Automatic extraction and relationship logic must retain confidence/provenance and must never silently overwrite confirmed user facts.
- Before delivery run unit, lint, debug/release build, instrumentation/emulator, privacy, visual, and regression gates. Label physical-device-only checks honestly.

