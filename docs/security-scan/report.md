# Security Review: Angel2222522/Hyper-God

## Scope

Πλήρης έλεγχος Android εφαρμογής, εξωτερικών intents/SAF, κρυπτογράφησης, backup/restore, Room, OCR, FileProvider, WorkManager, UI lock και CI/release επιφανειών.

- Scan mode: repository
- Target kind: git_revision
- Target ID: b02ea7d7c402b329fd29302e869c4b8b6f4d2919
- Revision: b02ea7d7c402b329fd29302e869c4b8b6f4d2919
- Inventory strategy: repository
- Included paths: app/, docs/, scripts/, .github/workflows/
- Excluded paths: .git/, app/build/, build/
- Runtime or test status: Στατικός έλεγχος με στοχευμένες δοκιμές παλινδρόμησης· το τελικό CI/emulator καταγράφεται χωριστά.
- Artifacts reviewed: 74 αρχεία παραγωγής, δοκιμών, ρυθμίσεων και τεκμηρίωσης

Limitations and exclusions:
- Το TAC advisory εργαλείο δεν ήταν διαθέσιμο στη συνεδρία, επομένως δεν έγινε συσχέτιση με ιδιωτικές TAC συμβουλές.
- Η φυσική βιομετρική συσκευή και πραγματικό camera hardware δεν περιλαμβάνονταν στο στατικό σκέλος.
- Excluded app/build/\*\*: Παράγωγα build, όχι πηγαίος κώδικας.
- Excluded .git/\*\*: Εσωτερικά αντικείμενα Git, εκτός runtime επιφάνειας.

### Scan Summary

| Field | Value |
| --- | --- |
| Reportable findings | 6 |
| Severity mix | high: 2, medium: 4 |
| Confidence mix | high: 6 |
| Coverage | complete |
| Validation mode | evidence-backed source review with remediation validation |

Canonical artifacts: `scan-manifest.json`, `findings.json`, and `coverage.json`. This report is a deterministic projection of those files.

## Threat Model

Κακόβουλη εγκατεστημένη εφαρμογή/ContentProvider ή πρόσωπο που δίνει στον ξεκλειδωμένο χρήστη ειδικά κατασκευασμένο share, εικόνα, PDF ή κρυπτογραφημένο backup.

### Assets

- Έγγραφα και OCR
- Μεταδεδομένα/υποθέσεις/υπενθυμίσεις
- Κλειδιά Android Keystore
- Ακεραιότητα ζωντανής γενιάς και αντιγράφων

### Trust Boundaries

- exported ACTION_SEND activity
- Storage Access Framework και ContentProvider streams
- camera FileProvider grant
- password-authenticated HGB/ZIP
- WorkManager OCR προς Room/filesystem

### Attacker Capabilities

- Παροχή content URI με ελεγχόμενα bytes/metadata/καθυστέρηση
- Δημιουργία δικού του HGB και γνωστοποίηση κωδικού
- Χρονισμός πολλαπλών intents ή ενεργειών γύρω από restore

### Security Objectives

- Ρητή συγκατάθεση πριν από μόνιμη εισαγωγή
- Φραγμένη χρήση χρόνου/χώρου/μνήμης
- Καμία μετάλλαξη πριν από πλήρη επαλήθευση restore
- Μία συνεπής γενιά filesystem και Room
- Ανθεκτική offline προστασία backup

### Assumptions

- Το Android application sandbox και το Keystore δεν έχουν παραβιαστεί
- Δεν υπάρχει root ή φυσική εξαγωγή ιδιωτικού app storage

## Findings

| Finding | Severity | Confidence | Detailed write-up |
| --- | --- | --- | --- |
| [Overlapping restore/backup/OCR operations could corrupt or silently replace a data generation](#finding-1) | high | high | inline below |
| [Restore could commit undecryptable legacy envelopes or arbitrary non-PDF page bytes](#finding-2) | high | high | inline below |
| [Backup decryption could consume unbounded provider time and new backups used an under-costed length-only password policy](#finding-3) | medium | high | inline below |
| [Exported share intents could crash or persist attacker-selected documents without request-specific consent](#finding-4) | medium | high | inline below |
| [Backup restore allowed mobile-unsafe ZIP/JSON amplification and duplicate expansion](#finding-5) | medium | high | inline below |
| [Untrusted provider imports, camera results and OCR lacked aggregate concurrency and duration budgets](#finding-6) | medium | high | inline below |

### Confidence Scale

| Label | Meaning |
| --- | --- |
| high | Direct evidence supports the finding with no material unresolved blocker. |
| medium | Evidence supports a plausible issue, but material runtime or reachability proof remains. |
| low | Evidence is incomplete and the item is retained only for explicit follow-up. |

<a id="finding-1"></a>

### [1] Overlapping restore/backup/OCR operations could corrupt or silently replace a data generation

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | Multiple concrete interleavings were traced through BackupService, WorkManager, OcrWorker and the coordinator. |
| Category | concurrency-data-integrity |
| CWE | CWE-362 |
| Affected lines | app/src/main/java/com/angel/hypergod/data/BackupService.kt:332-445, app/src/main/java/com/angel/hypergod/data/DataOperationCoordinator.kt:72-105, app/src/main/java/com/angel/hypergod/workers/OcrWorker.kt:13-35 |

#### Summary

The fixed global restore journal was written outside the short mutex, cancellation was not awaited, queued OCR could start after an idle observation, and cleanup occurred after ownership was released.

#### Validation

Multiple concrete interleavings were traced through BackupService, WorkManager, OcrWorker and the coordinator. Validation details were not recorded separately.

Evidence:
- DataOperationCoordinator.withGenerationRead
- DataOperationCoordinator.withMaintenance
- operationId-bound journal clearing

#### Dataflow

The canonical finding records the affected path at app/src/main/java/com/angel/hypergod/data/BackupService.kt:332-445, app/src/main/java/com/angel/hypergod/data/DataOperationCoordinator.kt:72-105, app/src/main/java/com/angel/hypergod/workers/OcrWorker.kt:13-35, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — A process interruption during overlap could remove recovery evidence and leave mixed Room/filesystem generations.

Additional runtime or deployment evidence could raise or lower this severity.

#### Remediation

Add a re-entrant generation-reader lease and single maintenance owner that drains readers and holds the global mutex across validation, journaling, swap, Room commit, rollback and cleanup; cancel queued OCR only after candidate validation and await completion.

Tests:
- DataOperationCoordinatorTest.maintenanceWaitsForExistingGenerationReader
- DataOperationCoordinatorTest.nestedGenerationReadIsReentrant
- BackupRoundTripTest.corruptedPortableBackupDoesNotChangeDatabase

Preventive controls:
- Restore-wide maintenance mutex
- Atomic reader drain
- Journal operation UUID
- Disabled backup/restore controls while busy

<a id="finding-2"></a>

### [2] Restore could commit undecryptable legacy envelopes or arbitrary non-PDF page bytes

| Field | Value |
| --- | --- |
| Severity | high |
| Confidence | high |
| Confidence rationale | The non-PDF fast path and destructive post-validation swap were explicit in source. |
| Category | integrity-input-validation |
| CWE | CWE-20, CWE-354 |
| Affected lines | app/src/main/java/com/angel/hypergod/data/BackupService.kt:529-575, app/src/main/java/com/angel/hypergod/data/BackupService.kt:369-401, app/src/main/java/com/angel/hypergod/data/DocumentRenderService.kt:102-159 |

#### Summary

Only PDF-labelled staged pages were opened before commit; non-PDF bytes were counted as one page, MIME decisions differed across restore/viewer/OCR, and v1 non-PDF device-bound envelopes were never authenticated.

#### Validation

The non-PDF fast path and destructive post-validation swap were explicit in source. Validation details were not recorded separately.

Evidence:
- ImportTypePolicy.resolvePageMime
- normalizeLegacyEnvelope
- validateStagedPageCounts

#### Dataflow

The canonical finding records the affected path at app/src/main/java/com/angel/hypergod/data/BackupService.kt:529-575, app/src/main/java/com/angel/hypergod/data/BackupService.kt:369-401, app/src/main/java/com/angel/hypergod/data/DocumentRenderService.kt:102-159, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**High** — A crafted authenticated backup could replace the entire readable library with an unreadable generation and delete the prior root.

Additional runtime or deployment evidence could raise or lower this severity.

#### Remediation

Use one effective MIME policy everywhere; authenticate and re-encrypt every v1 envelope; decrypt every staged page and run bounded PdfRenderer or BitmapFactory bounds plus sampled-decode validation before writing the journal.

Tests:
- BackupRoundTripTest.invalidImageBackupIsRejectedBeforeLiveGenerationChanges
- BackupRoundTripTest.portableBackupRestoreRoundTripPreservesPageAndOcr

Preventive controls:
- Actual-byte validation
- Shared MIME resolver
- Legacy GCM authentication
- Precommit image/PDF parser budgets

<a id="finding-3"></a>

### [3] Backup decryption could consume unbounded provider time and new backups used an under-costed length-only password policy

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | Direct review of BackupCrypto header/KDF/read loops and PasswordDialog keyboard configuration. |
| Category | cryptography-resource-exhaustion |
| CWE | CWE-400, CWE-916, CWE-522 |
| Affected lines | app/src/main/java/com/angel/hypergod/security/BackupCrypto.kt:20-101, app/src/main/java/com/angel/hypergod/ui/HyperGodApp.kt:1410-1431 |

#### Summary

Raw ciphertext had no independent counter/deadline/cancellable descriptor and authentication completed only at EOF; PBKDF2 used a fixed 120,000 iterations and the IME fields were masked visually but configured as ordinary text.

#### Validation

Direct review of BackupCrypto header/KDF/read loops and PasswordDialog keyboard configuration. Validation details were not recorded separately.

Evidence:
- PFBK2 with encoded 600,000-iteration parameter
- BackupPasswordPolicy
- BoundedInputStream
- KeyboardType.Password

#### Dataflow

The canonical finding records the affected path at app/src/main/java/com/angel/hypergod/security/BackupCrypto.kt:20-101, app/src/main/java/com/angel/hypergod/ui/HyperGodApp.kt:1410-1431, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — Malicious providers could stall restore, while captured exported backups with predictable passwords were exposed to cheaper offline guessing.

Additional runtime or deployment evidence could raise or lower this severity.

#### Remediation

Version the header, raise new-backup PBKDF2 cost while retaining v1 compatibility, enforce a local strength/passphrase policy and password IME flags, and decrypt through a size-checked AssetFileDescriptor closed by cancellation with a ten-minute deadline and free-space reserve.

Tests:
- BackupPasswordPolicyTest
- BackupRoundTripTest.backupCryptoRoundTripAndWrongPasswordAreRejected

Preventive controls:
- Versioned KDF parameters
- AES-256-GCM
- Raw and plaintext byte ceilings
- Cancellable provider descriptor
- Startup plaintext cache purge

<a id="finding-4"></a>

### [4] Exported share intents could crash or persist attacker-selected documents without request-specific consent

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | Direct source-to-sink trace from exported intent parsing to state persistence and repository import. |
| Category | authorization-input-validation |
| CWE | CWE-862, CWE-248, CWE-772 |
| Affected lines | app/src/main/AndroidManifest.xml:17-35, app/src/main/java/com/angel/hypergod/MainActivity.kt:231-263, app/src/main/java/com/angel/hypergod/security/PendingActivityStateStore.kt:73-82 |

#### Summary

ACTION_SEND values crossed an exported activity directly into durable import/OCR; oversized URI state could throw and persistable grants were not released on every discard path.

#### Validation

Direct source-to-sink trace from exported intent parsing to state persistence and repository import. Validation details were not recorded separately.

Evidence:
- IncomingSharePolicy
- IncomingShareDialog
- PendingActivityStateStoreTest

#### Dataflow

The canonical finding records the affected path at app/src/main/AndroidManifest.xml:17-35, app/src/main/java/com/angel/hypergod/MainActivity.kt:231-263, app/src/main/java/com/angel/hypergod/security/PendingActivityStateStore.kt:73-82, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — A foreground installed app could inject documents, exhaust grants, or repeatedly crash the locked activity.

Additional runtime or deployment evidence could raise or lower this severity.

#### Remediation

Validate typed content URIs/count/length before state mutation, present an explicit post-unlock Accept/Reject dialog, avoid persistable grants for external shares, and make temporary state rejection non-throwing.

Tests:
- IncomingSharePolicyTest
- PendingActivityStateStoreTest.overlongExternalStateIsRejectedWithoutThrowingOrPersisting

Preventive controls:
- Explicit user consent
- Typed Parcelable extraction
- Transient external URI grants
- Startup persisted-grant reconciliation

<a id="finding-5"></a>

### [5] Backup restore allowed mobile-unsafe ZIP/JSON amplification and duplicate expansion

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | Limits and both ZipInputStream passes were explicit and reachable after password authentication. |
| Category | archive-resource-exhaustion |
| CWE | CWE-409, CWE-400 |
| Affected lines | app/src/main/java/com/angel/hypergod/data/BackupSizePolicy.kt:5-9, app/src/main/java/com/angel/hypergod/data/BackupService.kt:260-280, app/src/main/java/com/angel/hypergod/data/BackupService.kt:673-693 |

#### Summary

The original policy allowed 100,000 entries, 2 GiB expanded payload, 64 MiB recursive JSON and two full decompression passes including unreferenced members and quadratic descriptor searches.

#### Validation

Limits and both ZipInputStream passes were explicit and reachable after password authentication. Validation details were not recorded separately.

Evidence:
- JsonStructurePolicy
- BackupSizePolicy.requireCompressionRatio
- exact archive.names comparison
- groupBy/associateBy indexing

#### Dataflow

The canonical finding records the affected path at app/src/main/java/com/angel/hypergod/data/BackupSizePolicy.kt:5-9, app/src/main/java/com/angel/hypergod/data/BackupService.kt:260-280, app/src/main/java/com/angel/hypergod/data/BackupService.kt:673-693, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — A compact attacker-created HGB could exhaust cache, heap, CPU and battery before live mutation.

Additional runtime or deployment evidence could raise or lower this severity.

#### Remediation

Use ZipFile central-directory sizes, require exact manifest-derived membership, extract each expected page once, enforce ratio/entry/payload budgets, preflight JSON depth/tokens/string sizes and index descriptors by entry/document.

Tests:
- JsonStructurePolicyTest
- BackupSizePolicyTest
- Hostile restore instrumentation tests

Preventive controls:
- 10,001-entry semantic ceiling
- 512 MiB aggregate ceiling
- 16 MiB manifest ceiling
- 32-level JSON depth
- Exact ZIP membership

<a id="finding-6"></a>

### [6] Untrusted provider imports, camera results and OCR lacked aggregate concurrency and duration budgets

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | Byte accounting and worker scheduling were directly observable in repository, FileCrypto and OcrWorker. |
| Category | resource-exhaustion |
| CWE | CWE-400 |
| Affected lines | app/src/main/java/com/angel/hypergod/data/HyperGodRepository.kt:54-108, app/src/main/java/com/angel/hypergod/security/FileCrypto.kt:46-93, app/src/main/java/com/angel/hypergod/workers/OcrWorker.kt:13-35, app/src/main/java/com/angel/hypergod/processing/ScannerImageProcessor.kt:58-67 |

#### Summary

Concurrent staging could exceed the document budget, provider reads could stall, camera bytes were decoded without compressed-size validation, and per-document WorkManager jobs could run native OCR concurrently.

#### Validation

Byte accounting and worker scheduling were directly observable in repository, FileCrypto and OcrWorker. Validation details were not recorded separately.

Evidence:
- importSemaphore
- encryptUriWithDigestCancellable
- OcrWorker.processSemaphore
- ScannerImageProcessor.validateCapture

#### Dataflow

The canonical finding records the affected path at app/src/main/java/com/angel/hypergod/data/HyperGodRepository.kt:54-108, app/src/main/java/com/angel/hypergod/security/FileCrypto.kt:46-93, app/src/main/java/com/angel/hypergod/workers/OcrWorker.kt:13-35, app/src/main/java/com/angel/hypergod/processing/ScannerImageProcessor.kt:58-67, but no expanded source-to-sink narrative was recorded.

#### Reachability

Reachability was not recorded beyond the canonical finding summary and affected locations.

#### Severity

**Medium** — User-visible storage, CPU and availability impact through attacker-controlled providers or camera handlers.

Additional runtime or deployment evidence could raise or lower this severity.

#### Remediation

Serialize import and OCR admission, pass the remaining aggregate byte budget, preflight free space, tie provider descriptors to cancellation/deadlines, and validate camera byte/dimension/pixel bounds before UI decoding.

Tests:
- Import byte-budget regression coverage
- CI instrumentation camera/scanner smoke path

Preventive controls:
- Single import lane
- Single OCR lane
- Five-minute provider deadline
- Thirty-minute OCR deadline
- 256 MiB document and 512 MiB library budgets

## Reviewed Surfaces

| Surface | Risk Area | Outcome | Notes |
| --- | --- | --- | --- |
| Exported share and SAF intake | authorization and resource consumption | Reported | No additional canonical notes were recorded. |
| Encrypted backup and restore | generation integrity, parsing and cryptography | Reported | No additional canonical notes were recorded. |
| Camera and FileProvider | untrusted external bytes | Reported | No additional canonical notes were recorded. |
| Room, FTS and CRUD | not recorded | No issue found | Bound Room parameters; no dynamic SQL injection path or exported database provider. |
| Runtime network and exfiltration | not recorded | No issue found | No INTERNET permission or runtime network client; build-time assets are pinned by immutable revision and SHA-256. |
| Archive paths, exports and sharing | not recorded | No issue found | Canonical containment and narrow non-exported FileProvider roots; no traversal found. |
| CI, dependencies and release secrets | not recorded | No issue found | Pinned Actions and assets; no production credential or private signing key committed. |
