package com.angel.hypergod

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.angel.hypergod.data.ExportService
import com.angel.hypergod.data.DataOperationCoordinator
import com.angel.hypergod.data.ReminderScheduler
import com.angel.hypergod.processing.ScannerImageProcessor
import com.angel.hypergod.security.IncomingSharePolicy
import com.angel.hypergod.security.PendingActivityStateStore
import com.angel.hypergod.ui.HyperGodApp
import com.angel.hypergod.ui.HyperGodViewModel
import com.angel.hypergod.ui.HyperGodTheme
import com.angel.hypergod.ui.IncomingShareDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {
    private val viewModel by lazy { androidx.lifecycle.ViewModelProvider(this)[HyperGodViewModel::class.java] }
    private val settings by lazy { getSharedPreferences("hyper_god_settings", MODE_PRIVATE) }
    private var cameraFile: File? = null
    private var cameraUri: Uri? = null
    private var sessionUnlocked by mutableStateOf(false)
    private var lockEnabled by mutableStateOf(false)
    private var lockPromptVisible = false
    private var pendingBackupPassword: String? = null
    private var pendingExportDocumentIds: List<String> = emptyList()
    private var pendingPdfDocumentIds: List<String> = emptyList()
    private var scannerFiles by mutableStateOf<List<File>>(emptyList())
    private var scannerOpen by mutableStateOf(false)
    private var lastIncomingIntentKey: String? = null
    private var pendingPickerUris: List<Uri> = emptyList()
    private var pendingExternalShareUris by mutableStateOf<List<Uri>>(emptyList())
    private val biometricExecutor: Executor by lazy { ContextCompat.getMainExecutor(this) }
    private val exportService by lazy { ExportService(this) }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        if (!sensitiveSessionReady()) {
            val validated = uris.distinct().take(MAX_PENDING_PICKER_URIS)
            val granted = validated.filter { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    true
                }.getOrDefault(false)
            }
            pendingPickerUris = (pendingPickerUris + granted).distinct().take(MAX_PENDING_PICKER_URIS)
            val stored = PendingActivityStateStore.saveList(this, STATE_PICKER_URIS, pendingPickerUris.map(Uri::toString))
            if (!stored) {
                pendingPickerUris.forEach(::releasePersistedReadGrant)
                pendingPickerUris = emptyList()
                showAuthMessage("Η προσωρινή κατάσταση της επιλογής δεν ήταν έγκυρη. Επίλεξε ξανά τα αρχεία.")
            } else {
                showAuthMessage("Η εισαγωγή θα συνεχιστεί μετά το ξεκλείδωμα της συνεδρίας.")
            }
        } else {
            val selected = uris.distinct().take(MAX_PENDING_PICKER_URIS)
            val persisted = selected.filter { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    true
                }.getOrDefault(false)
            }
            viewModel.importUris(selected) {
                persisted.forEach(::releasePersistedReadGrant)
            }
        }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (sensitiveSessionReady()) {
            lifecycleScope.launch { runCatching { ReminderScheduler.rescheduleAll(this@MainActivity) } }
        }
    }

    private val cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = cameraFile
        if (success && file != null && sensitiveSessionReady()) {
            lifecycleScope.launch {
                val validation = runCatching {
                    withContext(Dispatchers.IO) { ScannerImageProcessor.validateCapture(file) }
                }
                if (validation.isSuccess && sensitiveSessionReady()) {
                    scannerFiles = scannerFiles + file
                    scannerOpen = true
                } else {
                    file.delete()
                    validation.exceptionOrNull()?.let {
                        showAuthMessage(it.message ?: "Η φωτογραφία της κάμερας απορρίφθηκε.")
                    }
                }
            }
        }
        else file?.delete()
        cameraFile = null
        cameraUri = null
    }

    private val backupCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingBackupPassword ?: PendingActivityStateStore.consumePassword(this)
        PendingActivityStateStore.clear(this)
        pendingBackupPassword = null
        if (uri != null && password != null && sensitiveSessionReady()) viewModel.createBackup(uri, password)
    }

    private val backupPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingBackupPassword ?: PendingActivityStateStore.consumePassword(this)
        PendingActivityStateStore.clear(this)
        pendingBackupPassword = null
        if (uri != null && password != null && sensitiveSessionReady()) viewModel.restoreBackup(uri, password)
    }

    private val exportCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val documentIds = pendingExportDocumentIds.ifEmpty { PendingActivityStateStore.consumeList(this, STATE_EXPORT_IDS) }
        PendingActivityStateStore.clearList(this, STATE_EXPORT_IDS)
        pendingExportDocumentIds = emptyList()
        if (uri != null && documentIds.isNotEmpty() && sensitiveSessionReady()) viewModel.exportDocuments(uri, documentIds)
    }

    private val pdfExportCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val documentIds = pendingPdfDocumentIds.ifEmpty { PendingActivityStateStore.consumeList(this, STATE_PDF_IDS) }
        PendingActivityStateStore.clearList(this, STATE_PDF_IDS)
        pendingPdfDocumentIds = emptyList()
        if (uri != null && documentIds.isNotEmpty() && sensitiveSessionReady()) viewModel.exportPdf(uri, documentIds)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        restorePendingState(savedInstanceState)
        if (savedInstanceState == null) {
            pendingExportDocumentIds = PendingActivityStateStore.peekList(this, STATE_EXPORT_IDS)
            pendingPdfDocumentIds = PendingActivityStateStore.peekList(this, STATE_PDF_IDS)
            pendingPickerUris = PendingActivityStateStore.peekList(this, STATE_PICKER_URIS)
                .mapNotNull { encoded -> runCatching { Uri.parse(encoded) }.getOrNull() }
        }
        releaseUnneededPersistedReadGrants(pendingPickerUris.toSet())
        lockEnabled = settings.getBoolean(KEY_LOCK, false)
        DataOperationCoordinator.setUserSessionState(lockEnabled, sessionUnlocked)
        if (lockEnabled && !canAuthenticate()) {
            // An already-enabled lock must fail closed. Do not silently weaken the
            // persisted policy when a credential is temporarily unavailable.
            Toast.makeText(this, "Το κλείδωμα παραμένει ενεργό, αλλά δεν υπάρχει διαθέσιμη ασφαλής ταυτοποίηση στη συσκευή.", Toast.LENGTH_LONG).show()
        }
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent {
            HyperGodTheme {
                HyperGodApp(
                    viewModel = viewModel,
                    onImport = { if (sensitiveSessionReady()) documentPicker.launch(arrayOf("application/pdf", "image/*")) },
                    onCamera = { if (sensitiveSessionReady()) takePhoto() },
                    onOpenDocument = ::openDocument,
                    onShareDocument = ::shareDocument,
                    onEnableLock = { authenticate(
                        onSuccess = { settings.edit().putBoolean(KEY_LOCK, true).apply(); lockEnabled = true; sessionUnlocked = true; updateSessionState() },
                        onFailure = ::showAuthMessage
                    ) },
                    onDisableLock = { authenticate(
                        onSuccess = { settings.edit().putBoolean(KEY_LOCK, false).apply(); lockEnabled = false; sessionUnlocked = true; updateSessionState() },
                        onFailure = ::showAuthMessage
                    ) },
                    onCreateBackup = { password -> if (sensitiveSessionReady()) { pendingBackupPassword = password; PendingActivityStateStore.savePassword(this, password); backupCreator.launch("hyper-god-backup.hgb") } },
                    onRestoreBackup = { password -> if (sensitiveSessionReady()) { pendingBackupPassword = password; PendingActivityStateStore.savePassword(this, password); backupPicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) } },
                    onRequestNotifications = ::requestNotificationPermission,
                    onExportDocuments = { documentIds -> if (sensitiveSessionReady()) { pendingExportDocumentIds = documentIds; PendingActivityStateStore.saveList(this, STATE_EXPORT_IDS, documentIds); exportCreator.launch("hyper-god-export.zip") } },
                    onExportPdf = { documentIds -> if (sensitiveSessionReady()) { pendingPdfDocumentIds = documentIds; PendingActivityStateStore.saveList(this, STATE_PDF_IDS, documentIds); pdfExportCreator.launch("hyper-god-export.pdf") } },
                    scannerOpen = scannerOpen,
                    scannerPageUris = scannerFiles.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) },
                    onScannerAddPage = { if (sensitiveSessionReady()) launchCamera() },
                    onScannerRetryLast = ::retryLastScanPage,
                    onScannerMovePage = ::moveScanPage,
                    onScannerDeletePage = ::deleteScanPage,
                    onScannerRotatePage = ::rotateScanPage,
                    onScannerFinish = ::finishScanner,
                    onScannerCancel = ::cancelScanner,
                    lockEnabled = lockEnabled,
                    locked = lockEnabled && !sessionUnlocked
                )
                if (pendingExternalShareUris.isNotEmpty() && (!lockEnabled || sessionUnlocked)) {
                    IncomingShareDialog(
                        uris = pendingExternalShareUris,
                        onAccept = ::acceptExternalShare,
                        onReject = ::rejectExternalShare
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!lockEnabled || sessionUnlocked) {
            lifecycleScope.launch { runCatching { ReminderScheduler.rescheduleAll(this@MainActivity) } }
        }
        if (lockEnabled && !canAuthenticate()) {
            sessionUnlocked = false
            updateSessionState()
            showAuthMessage("Το κλείδωμα παραμένει ενεργό. Ενεργοποίησε ξανά μια ασφαλή συσκευή ταυτοποίησης για να ξεκλειδώσεις.")
        } else if (lockEnabled && !sessionUnlocked && !lockPromptVisible) {
            authenticate(onSuccess = { sessionUnlocked = true; updateSessionState(); flushPendingPicker() }, onFailure = ::showAuthMessage)
        }
    }

    override fun onStop() {
        super.onStop()
        sessionUnlocked = false
        updateSessionState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(KEY_PENDING_EXPORT_IDS, ArrayList(pendingExportDocumentIds))
        outState.putStringArrayList(KEY_PENDING_PDF_IDS, ArrayList(pendingPdfDocumentIds))
        outState.putParcelableArrayList(KEY_PENDING_PICKER_URIS, ArrayList(pendingPickerUris))
        outState.putParcelableArrayList(KEY_PENDING_EXTERNAL_SHARE_URIS, ArrayList(pendingExternalShareUris))
        outState.putString(KEY_LAST_INCOMING_KEY, lastIncomingIntentKey)
        outState.putStringArrayList(KEY_SCANNER_FILES, ArrayList(scannerFiles.map(File::getAbsolutePath)))
        outState.putBoolean(KEY_SCANNER_OPEN, scannerOpen)
        super.onSaveInstanceState(outState)
    }

    private fun takePhoto() {
        if (!sensitiveSessionReady()) return
        scannerOpen = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun launchCamera() {
        if (!sensitiveSessionReady()) return
        val file = File(cacheDir, "camera/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        cameraFile = file
        cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraCapture.launch(cameraUri!!)
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val intent = incoming ?: return
        if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        val uris = runCatching { sharedUris(intent).distinct() }
            .getOrElse {
                showAuthMessage("Η άλλη εφαρμογή έστειλε μη έγκυρα στοιχεία εισαγωγής.")
                return
            }
        val rejection = IncomingSharePolicy.rejectionReason(
            uris.map { uri -> IncomingSharePolicy.Candidate(uri.scheme, uri.toString().length) }
        )
        if (rejection != null) {
            showAuthMessage(rejection)
            return
        }
        if (pendingExternalShareUris.isNotEmpty()) {
            showAuthMessage("Υπάρχει ήδη εισαγωγή από άλλη εφαρμογή σε αναμονή. Αποδέξου ή απόρριψέ την πρώτα.")
            return
        }
        val key = intent.action.orEmpty() + ":" + uris.joinToString("|")
        if (uris.isNotEmpty() && key != lastIncomingIntentKey) {
            lastIncomingIntentKey = key
            pendingExternalShareUris = uris
            if (lockEnabled && !sessionUnlocked) {
                showAuthMessage("Ξεκλείδωσε τη συνεδρία και έπειτα επίλεξε αν αποδέχεσαι τα εισερχόμενα αρχεία.")
            }
        }
    }

    private fun flushPendingPicker() {
        if (!sensitiveSessionReady()) return
        val queued = pendingPickerUris.ifEmpty {
            PendingActivityStateStore.consumeList(this, STATE_PICKER_URIS)
                .mapNotNull { encoded -> runCatching { Uri.parse(encoded) }.getOrNull() }
        }
        PendingActivityStateStore.clearList(this, STATE_PICKER_URIS)
        pendingPickerUris = emptyList()
        if (queued.isNotEmpty()) viewModel.importUris(queued) {
            queued.forEach(::releasePersistedReadGrant)
        }
    }

    private fun acceptExternalShare() {
        if (!sensitiveSessionReady()) return
        val accepted = pendingExternalShareUris
        pendingExternalShareUris = emptyList()
        if (accepted.isNotEmpty()) viewModel.importUris(accepted)
    }

    private fun rejectExternalShare() {
        pendingExternalShareUris = emptyList()
    }

    private fun sharedUris(incoming: Intent): List<Uri> = when (incoming.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            } ?: incoming.data
        )
        Intent.ACTION_SEND_MULTIPLE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            incoming.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            incoming.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
        else -> emptyList()
    }

    private fun releasePersistedReadGrant(uri: Uri) {
        runCatching { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun releaseUnneededPersistedReadGrants(keep: Set<Uri>) {
        contentResolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission && it.uri !in keep }
            .map { it.uri }
            .forEach(::releasePersistedReadGrant)
    }

    private fun openDocument(documentId: String) {
        lifecycleScope.launch {
            var shareFile: File? = null
            runCatching {
                check(!lockEnabled || sessionUnlocked) { "Η συνεδρία κλειδώθηκε. Ταυτοποιήσου ξανά." }
                val document = viewModel.getDocument(documentId) ?: error("Το έγγραφο δεν βρέθηκε.")
                val file = exportService.createSharePdf(document.id)
                shareFile = file
                check(!lockEnabled || sessionUnlocked) { "Η συνεδρία κλειδώθηκε πριν ολοκληρωθεί το άνοιγμα." }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.open_original)))
                shareFile = null
            }.onFailure { showAuthMessage(it.message ?: "Δεν ήταν δυνατό το άνοιγμα του εγγράφου.") }
            shareFile?.delete()
        }
    }

    private fun shareDocument(documentId: String) {
        lifecycleScope.launch {
            var shareFile: File? = null
            runCatching {
                check(!lockEnabled || sessionUnlocked) { "Η συνεδρία κλειδώθηκε. Ταυτοποιήσου ξανά." }
                val document = viewModel.getDocument(documentId) ?: error("Το έγγραφο δεν βρέθηκε.")
                val file = exportService.createSharePdf(document.id)
                shareFile = file
                check(!lockEnabled || sessionUnlocked) { "Η συνεδρία κλειδώθηκε πριν ολοκληρωθεί η κοινοποίηση." }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_document)))
                shareFile = null
            }.onFailure { showAuthMessage(it.message ?: "Δεν ήταν δυνατή η κοινοποίηση.") }
            shareFile?.delete()
        }
    }

    private fun authenticate(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (lockPromptVisible) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onFailure("Δεν υπάρχει διαθέσιμη ασφαλής ταυτοποίηση στη συσκευή. Το κλείδωμα δεν ενεργοποιήθηκε.")
            return
        }
        lockPromptVisible = true
        val prompt = BiometricPrompt(this, biometricExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lockPromptVisible = false
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                lockPromptVisible = false
                onFailure(errString.toString())
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle(getString(R.string.biometric_lock))
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }

    private fun canAuthenticate(): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showAuthMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun retryLastScanPage() {
        if (!sensitiveSessionReady()) return
        scannerFiles.lastOrNull()?.delete()
        scannerFiles = scannerFiles.dropLast(1)
        launchCamera()
    }

    private fun moveScanPage(index: Int, direction: Int) {
        val target = index + direction
        if (index !in scannerFiles.indices || target !in scannerFiles.indices) return
        scannerFiles = scannerFiles.toMutableList().apply {
            val page = removeAt(index)
            add(target, page)
        }
    }

    private fun deleteScanPage(index: Int) {
        if (index !in scannerFiles.indices) return
        val removed = scannerFiles[index]
        scannerFiles = scannerFiles.filterIndexed { pageIndex, _ -> pageIndex != index }
        removed.delete()
    }

    private fun rotateScanPage(index: Int) {
        if (index !in scannerFiles.indices || !sensitiveSessionReady()) return
        val source = scannerFiles[index]
        lifecycleScope.launch(Dispatchers.IO) {
            val output = File(cacheDir, "camera/rotated_${System.nanoTime()}.jpg")
            runCatching { ScannerImageProcessor.rotateQuarterTurn(source, output) }
                .onSuccess { rotated ->
                    withContext(Dispatchers.Main) {
                        if (index in scannerFiles.indices && scannerFiles[index] == source) {
                            scannerFiles = scannerFiles.toMutableList().apply { this[index] = rotated }
                            source.delete()
                        } else {
                            rotated.delete()
                        }
                    }
                }
                .onFailure { error ->
                    output.delete()
                    withContext(Dispatchers.Main) {
                        showAuthMessage(error.message ?: "Δεν ήταν δυνατή η περιστροφή της σελίδας.")
                    }
                }
        }
    }

    private fun cancelScanner() {
        scannerFiles.forEach(File::delete)
        scannerFiles = emptyList()
        scannerOpen = false
    }

    private fun finishScanner() {
        if (!sensitiveSessionReady()) return
        val rawFiles = scannerFiles.toList()
        if (rawFiles.isEmpty()) {
            scannerOpen = false
            return
        }
        scannerOpen = false
        lifecycleScope.launch(Dispatchers.IO) {
            val processed = mutableListOf<File>()
            try {
                rawFiles.forEachIndexed { index, raw ->
                    val output = File(cacheDir, "scanner/processed_${System.nanoTime()}_$index.jpg")
                    processed += ScannerImageProcessor.enhance(raw, output)
                }
                val uris = processed.map { FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", it) }
                withContext(Dispatchers.Main) {
                    viewModel.importUris(uris) { succeeded ->
                        processed.forEach(File::delete)
                        if (succeeded) {
                            rawFiles.forEach(File::delete)
                            scannerFiles = emptyList()
                        } else {
                            scannerFiles = rawFiles
                            scannerOpen = true
                        }
                    }
                }
            } catch (error: Throwable) {
                processed.forEach(File::delete)
                withContext(Dispatchers.Main) {
                    showAuthMessage(error.message ?: "Δεν ήταν δυνατή η επεξεργασία της σάρωσης.")
                    scannerFiles = rawFiles
                    scannerOpen = true
                }
            }
        }
    }

    private fun updateSessionState() {
        DataOperationCoordinator.setUserSessionState(lockEnabled, sessionUnlocked)
    }

    private fun sensitiveSessionReady(): Boolean = runCatching {
        DataOperationCoordinator.requireRecoverySafe()
        DataOperationCoordinator.requireUserSessionUnlocked()
    }.onFailure { showAuthMessage(it.message ?: "Η λειτουργία δεν είναι διαθέσιμη.") }.isSuccess

    companion object {
        private const val KEY_LOCK = "biometric_lock"
        private const val MAX_PENDING_PICKER_URIS = 100
        private const val KEY_PENDING_EXPORT_IDS = "pending_export_ids"
        private const val KEY_PENDING_PDF_IDS = "pending_pdf_ids"
        private const val KEY_PENDING_PICKER_URIS = "pending_picker_uris"
        private const val KEY_PENDING_EXTERNAL_SHARE_URIS = "pending_external_share_uris"
        private const val KEY_LAST_INCOMING_KEY = "last_incoming_key"
        private const val KEY_SCANNER_FILES = "scanner_files"
        private const val KEY_SCANNER_OPEN = "scanner_open"
        private const val STATE_EXPORT_IDS = "export_ids"
        private const val STATE_PDF_IDS = "pdf_ids"
        private const val STATE_PICKER_URIS = "picker_uris"
    }

    private fun restorePendingState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        pendingExportDocumentIds = savedInstanceState.getStringArrayList(KEY_PENDING_EXPORT_IDS).orEmpty()
        pendingPdfDocumentIds = savedInstanceState.getStringArrayList(KEY_PENDING_PDF_IDS).orEmpty()
        pendingPickerUris = savedInstanceState.parcelableUriList(KEY_PENDING_PICKER_URIS)
        pendingExternalShareUris = savedInstanceState.parcelableUriList(KEY_PENDING_EXTERNAL_SHARE_URIS)
        lastIncomingIntentKey = savedInstanceState.getString(KEY_LAST_INCOMING_KEY)
        scannerFiles = savedInstanceState.getStringArrayList(KEY_SCANNER_FILES).orEmpty().map(::File)
        scannerOpen = savedInstanceState.getBoolean(KEY_SCANNER_OPEN, false)
    }

    private fun Bundle.parcelableUriList(key: String): List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayList(key, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayList<Uri>(key).orEmpty()
    }
}
