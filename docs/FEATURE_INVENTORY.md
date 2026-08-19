# Feature inventory

| Area | Implemented behavior | Evidence target |
|---|---|---|
| Import | PDF/images, multi-source document, share intents, SAF, bounded validation and process recovery | Instrumentation repository tests |
| Scanner | Multi-page capture, preview, retake, reorder, rotate, remove, conservative enhancement | Emulator UI + physical camera pending |
| Viewer | Internal bounded PDF/image renderer, page controls, zoom/pan | Renderer instrumentation + UI inspection |
| OCR | Pinned offline Greek/English Tesseract models, page/document text, queued/failed/retry state | Model checksum + instrumentation OCR fixture |
| Metadata | Title/category/issuer/dates/protocol, confidence, source JSON, manual ownership | Unit extraction/application tests |
| Search | Room FTS with category/case/state/expiry filters | Unit/schema + emulator smoke |
| Cases | Full statuses, notes, next action, documents, checklist, timeline, deadlines | Repository/UI tests |
| Intelligence | Exact fingerprints, protocol/version hints, deterministic grounded Q&A, profile discrepancies | Pure unit tests |
| Security | Keystore AES-GCM files/profile, secure window, app lock, generic notifications | Crypto/instrumentation + physical biometric pending |
| Portability | Encrypted validated backup/restore; plaintext ZIP/unified PDF export | Round-trip/corruption instrumentation |

Semantic embeddings, local LLM synthesis, automatic legal advice, and unreliable automatic four-corner perspective claims are intentionally excluded from the core. FTS, structured retrieval, user confirmation, and manual scanner controls remain functional fallbacks.

