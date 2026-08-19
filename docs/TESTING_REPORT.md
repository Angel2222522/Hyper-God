# Verification report

## Automated gates

CI is configured to run:

1. JVM/domain unit tests,
2. Android lint,
3. instrumentation-test compilation,
4. debug APK assembly,
5. emulator instrumentation on API 28,
6. light/dark runtime screenshots,
7. unsigned minified release compilation,
8. APK, diagnostics and Room-schema artifacts.

Test suites cover metadata extraction/ownership, FTS repair, import typing, reminder dates, storage coordination, cleanup, duplicate identity, relationship hints, grounded answers, profile discrepancies, Room migrations, encrypted backup round trips, corrupt/wrong-password restore, hostile non-image backup rejection without live-generation mutation, generation-reader/maintenance exclusion, repository import/delete/export, PDF rendering/original preservation, OCR fixtures, pending encrypted activity state, and Greek navigation smoke paths.

The sealed security review covers 74 source/configuration/test files and records six high-confidence findings, all marked `remediated` in the canonical findings artifact. Static scan limitations are recorded in [the report](security-scan/report.md).

## Evidence labels

- `source`: source/security review only.
- `unit`: JVM/domain test execution.
- `emulator`: Android runtime on the CI emulator.
- `physical pending`: camera optics, biometric hardware, OEM battery policy, vendor SAF providers, and real-world OCR quality.

The exact successful workflow run, artifact SHA-256, APK package/version/signature and screenshot inspection findings are recorded after the final CI pass. No runtime claim is made before that evidence exists.
