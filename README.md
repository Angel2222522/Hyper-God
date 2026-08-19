# Hyper God

**Offline-first προσωπικό λειτουργικό σύστημα για έγγραφα, διοικητικές υποθέσεις, προθεσμίες και μακροχρόνια προσωπική μνήμη.**

Το Hyper God είναι πραγματική Android εφαρμογή χωρίς λογαριασμό, backend, analytics, διαφημίσεις ή `INTERNET` permission. Τα έγγραφα μένουν στη συσκευή, το ελληνικό/αγγλικό OCR εκτελείται τοπικά και η εφαρμογή παραμένει χρήσιμη χωρίς server.

## Κύριες δυνατότητες

- εισαγωγή PDF/εικόνων, πολλαπλών αρχείων, Android share intent και πολυσέλιδη φωτογράφιση,
- εσωτερικός viewer με σελιδοποίηση, zoom/pan και bounded rendering,
- bundled Tesseract OCR για ελληνικά και αγγλικά,
- διορθώσιμα metadata με confidence, provenance και επιβεβαιωμένες/προτεινόμενες τιμές,
- ισχυρή τοπική αναζήτηση Room FTS με φίλτρα,
- υποθέσεις, καταστάσεις, συνδεδεμένα έγγραφα, checklist, timeline, notes και deadlines,
- deterministic ανίχνευση ακριβών διπλοτύπων, πρωτοκόλλων και πιθανών εκδόσεων χωρίς αυτόματη συγχώνευση,
- grounded ερωτήσεις πάνω σε πραγματικά αποθηκευμένα στοιχεία με clickable πηγές,
- κρυπτογραφημένο προσωπικό μητρώο και προτεινόμενες ασυμφωνίες ΑΦΜ/ΑΜΚΑ,
- τοπικές υπενθυμίσεις με generic ιδιωτικές ειδοποιήσεις,
- AES-GCM document storage με Android Keystore, biometric/device-credential lock και `FLAG_SECURE`,
- password-encrypted portable backup/restore με validation, όρια και recovery journal,
- εξαγωγή ZIP και ενιαίου PDF μέσω Storage Access Framework.

## Τεχνικά

- Kotlin, Jetpack Compose, Room, WorkManager
- `applicationId`: `com.angel.hypergod`
- `minSdk 26`, `targetSdk 36`, Java 17
- version `1.0.0` / `versionCode 1`

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleDebugAndroidTest
./gradlew assembleRelease
```

Τα pinned OCR/font assets κατεβαίνουν μόνο κατά το build από αμετάβλητα Git commits και επαληθεύονται με SHA-256. Το τελικό APK περιλαμβάνει τα assets και δεν χρειάζεται Internet στη λειτουργία του.

## Απόρρητο και όρια

Τα document bytes και το προσωπικό μητρώο είναι κρυπτογραφημένα. Η Room database/FTS είναι app-private αλλά όχι SQLCipher-encrypted· OCR και metadata θεωρούνται ευαίσθητα και δεν εξάγονται ή αποστέλλονται αυτόματα. Τα ZIP/PDF exports είναι σκόπιμα plaintext και επισημαίνονται ως τέτοια.

Δες [Capability matrix](docs/CAPABILITY_MATRIX.md), [Architecture](docs/ARCHITECTURE.md), [Security](docs/SECURITY_PRIVACY.md), [πλήρη security scan](docs/security-scan/report.md), [Features](docs/FEATURE_INVENTORY.md) και [Testing](docs/TESTING_REPORT.md).
