package com.greenart7c3.nostrsigner.testclient

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Base64
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject

class MainActivity : Activity() {
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private var availableSigners: List<String> = emptyList()
    private var selectedSigner: String? = null
    private var pubkeyHex: String? = null
    private var selectedSizeBytes: Int = SIZE_3_MB

    private var lastSignedExpectedContent: String? = null
    private var lastEncryptedRawPlaintext: ByteArray? = null
    private var lastEncryptedWirePlaintext: String? = null
    private var lastEncryptedCiphertext: String? = null
    private var pendingIntentStartMs: Long = 0L

    private lateinit var signerStatusText: TextView
    private lateinit var capabilityText: TextView
    private lateinit var pubkeyText: TextView
    private lateinit var sizeStatusText: TextView
    private lateinit var logTextView: TextView
    private lateinit var mainScrollView: ScrollView

    private val sizeButtons = LinkedHashMap<Int, Button>()
    private val logBuffer = ArrayDeque<String>(64)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pubkeyHex = prefs.getString(KEY_PUBKEY_HEX, null)
        selectedSigner = prefs.getString(KEY_SIGNER_PACKAGE, null)
        selectedSizeBytes = prefs.getInt(KEY_SIZE_BYTES, SIZE_3_MB)
        refreshSigners()

        setContentView(buildUi())
        updateStatusUi()
        appendLog("[INFO] NIP-55 Test Client ready (package=$packageName, uid=${android.os.Process.myUid()})")
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshSigners() {
        availableSigners = Nip55PayloadClient.discoverSigners(this)
        val current = selectedSigner
        selectedSigner = if (current != null && current in availableSigners) {
            current
        } else {
            availableSigners.firstOrNull()
        }
        selectedSigner?.let { prefs.edit().putString(KEY_SIGNER_PACKAGE, it).apply() }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(18))
        }

        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            val top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = insets.systemWindowInsetBottom
            v.setPadding(dp(14), top + dp(12), dp(14), bottom + dp(18))
            insets
        }

        val title = TextView(this).apply {
            text = "NIP-55 Large Payload Test Client"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(title)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#18212D"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#35475E"))
            }
        }
        signerStatusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        capabilityText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        pubkeyText = TextView(this).apply {
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
        }
        sizeStatusText = TextView(this).apply {
            setTextColor(Color.parseColor("#93C5FD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, 0)
        }
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val switchBtn = actionButton("Switch Signer") {
            refreshSigners()
            if (availableSigners.isNotEmpty()) {
                val idx = availableSigners.indexOf(selectedSigner)
                selectedSigner = availableSigners[(idx + 1) % availableSigners.size]
                prefs.edit().putString(KEY_SIGNER_PACKAGE, selectedSigner).apply()
            }
            updateStatusUi()
            appendLog("[INFO] Active signer: ${selectedSigner ?: "none"} (discovered: $availableSigners)")
        }
        switchRow.addView(switchBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val recheckBtn = actionButton("Refresh Signer") {
            refreshSigners()
            updateStatusUi()
            val signer = selectedSigner
            val cap = signer?.let { Nip55PayloadClient.supportsLargePayloads(this, it) } ?: false
            appendLog("[INFO] Signer=${signer ?: "none"} | .PAYLOADS=${if (cap) "SUPPORTED" else "NOT SUPPORTED"}")
        }
        switchRow.addView(
            recheckBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) },
        )

        statusCard.addView(signerStatusText)
        statusCard.addView(capabilityText)
        statusCard.addView(pubkeyText)
        statusCard.addView(sizeStatusText)
        statusCard.addView(switchRow)
        root.addView(statusCard)

        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(4))
        }
        SIZE_OPTIONS.forEachIndexed { idx, (label, bytes) ->
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setOnClickListener {
                    selectedSizeBytes = bytes
                    prefs.edit().putInt(KEY_SIZE_BYTES, bytes).apply()
                    updateStatusUi()
                    appendLog("[INFO] Payload size set to $label ($bytes bytes)")
                }
            }
            sizeButtons[bytes] = btn
            sizeRow.addView(
                btn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx > 0) leftMargin = dp(4)
                },
            )
        }
        root.addView(sizeRow)

        val actions = listOf(
            "1. Login (get_public_key Intent)" to { launchLoginIntent() },
            "2. Intent: sign_event (stream -> event_uri)" to { launchSignEventIntent(returnType = null, useStreaming = true) },
            "3. Intent: sign_event (returnType=signature)" to { launchSignEventIntent(returnType = "signature", useStreaming = true) },
            "4. Intent: nip44v3_encrypt (stream -> result_uri)" to { launchNip44v3EncryptIntent() },
            "5. Intent: nip44v3_decrypt (verify round-trip)" to { launchNip44v3DecryptIntent() },
            "6. Resolver: SIGN_EVENT (stream -> event_uri)" to { runResolverSignEvent(returnType = null) },
            "7. Resolver: SIGN_EVENT (return_type=signature)" to { runResolverSignEvent(returnType = "signature") },
            "8. Resolver: NIP44_V3 Encrypt + Decrypt" to { runResolverNip44v3() },
            "9. Run All ContentResolver Tests" to { runAllResolverTests() },
            "10. Legacy Inline Intent (Expect Fail >1MB)" to { launchSignEventIntent(returnType = null, useStreaming = false) },
            "Clear Log" to {
                logBuffer.clear()
                logTextView.text = ""
            },
        )

        actions.forEach { (label, handler) ->
            root.addView(
                actionButton(label, handler),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                },
            )
        }

        val logHeader = TextView(this).apply {
            text = "Live Output Log"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(logHeader)

        logTextView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.parseColor("#334155"))
            }
            minHeight = dp(180)
        }
        root.addView(logTextView)

        mainScrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        return mainScrollView
    }

    private fun actionButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setOnClickListener { onClick() }
        }

    private fun updateStatusUi() {
        val signer = selectedSigner
        val supported = signer != null && Nip55PayloadClient.supportsLargePayloads(this, signer)
        signerStatusText.text = "Signer: ${signer ?: "NONE (install Amber)"}"
        capabilityText.text = ".PAYLOADS capability: ${if (supported) "SUPPORTED" else "NOT SUPPORTED"}"
        capabilityText.setTextColor(if (supported) Color.parseColor("#4ADE80") else Color.parseColor("#F87171"))
        val pk = pubkeyHex
        pubkeyText.text = if (pk != null) "User pubkey: ${pk.take(16)}...${pk.takeLast(8)}" else "User pubkey: Not logged in (tap button 1)"
        val label = SIZE_OPTIONS.firstOrNull { it.second == selectedSizeBytes }?.first ?: "$selectedSizeBytes B"
        sizeStatusText.text = "Payload size: $label ($selectedSizeBytes bytes)"
        sizeButtons.forEach { (bytes, btn) ->
            btn.setTypeface(null, if (bytes == selectedSizeBytes) Typeface.BOLD else Typeface.NORMAL)
            btn.alpha = if (bytes == selectedSizeBytes) 1.0f else 0.72f
        }
    }

    private fun launchLoginIntent() {
        val signer = requireSigner() ?: return
        pendingIntentStartMs = System.currentTimeMillis()
        appendLog("[INFO] Launching get_public_key Intent -> $signer")
        @Suppress("DEPRECATION")
        startActivityForResult(Nip55PayloadClient.buildGetPublicKeyIntent(signer, "login-1"), REQ_LOGIN)
    }

    private fun launchSignEventIntent(returnType: String?, useStreaming: Boolean) {
        val signer = requireSigner() ?: return
        val pubkey = requirePubkey() ?: return
        val sizeBytes = selectedSizeBytes
        appendLog("[INFO] Preparing Intent sign_event (size=$sizeBytes, stream=$useStreaming, returnType=${returnType ?: "default"})...")
        ioExecutor.execute {
            try {
                val (content, unsignedJson) = Nip55PayloadClient.buildUnsignedEventJson(pubkey, sizeBytes)
                lastSignedExpectedContent = content
                val payloadArg = if (useStreaming) {
                    val uri = Nip55PayloadClient.stagePayload(contentResolver, signer, unsignedJson.toByteArray(Charsets.UTF_8))
                    uri.toString()
                } else {
                    unsignedJson
                }
                val intent = Nip55PayloadClient.buildSignEventIntent(
                    signerPackage = signer,
                    id = if (returnType == "signature") "intent-sig-only" else if (useStreaming) "intent-sign-stream" else "intent-legacy",
                    currentUserHex = pubkey,
                    payloadOrUri = payloadArg,
                    useStreaming = useStreaming,
                    returnType = returnType,
                )
                val reqCode = when {
                    !useStreaming -> REQ_LEGACY_INLINE
                    returnType == "signature" -> REQ_SIGN_SIG_ONLY
                    else -> REQ_SIGN_STREAM
                }
                runOnUiThread {
                    try {
                        pendingIntentStartMs = System.currentTimeMillis()
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, reqCode)
                    } catch (t: Throwable) {
                        val rootCause = generateSequence(t) { it.cause }.lastOrNull() ?: t
                        if (!useStreaming && sizeBytes > 512 * 1024) {
                            appendLog("[PASS] Legacy inline Intent failed as expected on $sizeBytes B: ${rootCause.javaClass.simpleName}: ${rootCause.message}")
                        } else {
                            appendLog("[FAIL] Intent launch threw ${rootCause.javaClass.simpleName}: ${rootCause.message}")
                        }
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread { appendLog("[FAIL] Stage/prepare failed: ${t.javaClass.simpleName}: ${t.message}") }
            }
        }
    }

    private fun launchNip44v3EncryptIntent() {
        val signer = requireSigner() ?: return
        val pubkey = requirePubkey() ?: return
        val sizeBytes = selectedSizeBytes
        appendLog("[INFO] Staging $sizeBytes B raw binary for Intent nip44v3_encrypt...")
        ioExecutor.execute {
            try {
                val (raw, wireBase64) = Nip55PayloadClient.buildRawBinaryBase64(sizeBytes)
                lastEncryptedRawPlaintext = raw
                lastEncryptedWirePlaintext = wireBase64
                val uri = Nip55PayloadClient.stagePayload(contentResolver, signer, wireBase64.toByteArray(Charsets.UTF_8))
                val intent = Nip55PayloadClient.buildNip44v3Intent(
                    signerPackage = signer,
                    type = "nip44v3_encrypt",
                    id = "intent-v3-enc",
                    currentUserHex = pubkey,
                    peerHex = pubkey,
                    kind = Nip55PayloadClient.NIP44_V3_KIND,
                    scope = Nip55PayloadClient.NIP44_V3_SCOPE,
                    payloadUri = uri.toString(),
                )
                runOnUiThread {
                    pendingIntentStartMs = System.currentTimeMillis()
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_V3_ENCRYPT)
                }
            } catch (t: Throwable) {
                runOnUiThread { appendLog("[FAIL] Intent nip44v3_encrypt stage failed: ${t.javaClass.simpleName}: ${t.message}") }
            }
        }
    }

    private fun launchNip44v3DecryptIntent() {
        val signer = requireSigner() ?: return
        val pubkey = requirePubkey() ?: return
        val ciphertext = lastEncryptedCiphertext
        if (ciphertext.isNullOrBlank()) {
            appendLog("[FAIL] Run button 4 (Intent: nip44v3_encrypt) first so there is ciphertext to decrypt.")
            return
        }
        appendLog("[INFO] Staging ${ciphertext.length} B ciphertext for Intent nip44v3_decrypt...")
        ioExecutor.execute {
            try {
                val uri = Nip55PayloadClient.stagePayload(contentResolver, signer, ciphertext.toByteArray(Charsets.UTF_8))
                val intent = Nip55PayloadClient.buildNip44v3Intent(
                    signerPackage = signer,
                    type = "nip44v3_decrypt",
                    id = "intent-v3-dec",
                    currentUserHex = pubkey,
                    peerHex = pubkey,
                    kind = Nip55PayloadClient.NIP44_V3_KIND,
                    scope = Nip55PayloadClient.NIP44_V3_SCOPE,
                    payloadUri = uri.toString(),
                )
                runOnUiThread {
                    pendingIntentStartMs = System.currentTimeMillis()
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_V3_DECRYPT)
                }
            } catch (t: Throwable) {
                runOnUiThread { appendLog("[FAIL] Intent nip44v3_decrypt stage failed: ${t.javaClass.simpleName}: ${t.message}") }
            }
        }
    }

    private fun runResolverSignEvent(returnType: String?) {
        val signer = requireSigner() ?: return
        val pubkey = requirePubkey() ?: return
        val sizeBytes = selectedSizeBytes
        ioExecutor.execute {
            executeResolverSignEvent(signer, pubkey, sizeBytes, returnType)
        }
    }

    private fun runResolverNip44v3() {
        val signer = requireSigner() ?: return
        val pubkey = requirePubkey() ?: return
        val sizeBytes = selectedSizeBytes
        ioExecutor.execute {
            executeResolverNip44v3(signer, pubkey, sizeBytes)
        }
    }

    private fun runAllResolverTests() {
        val signer = requireSigner() ?: return
        val pubkey = requirePubkey() ?: return
        val sizeBytes = selectedSizeBytes
        appendLog("[INFO] Running all 3 ContentResolver tests (size=$sizeBytes B)...")
        ioExecutor.execute {
            executeResolverSignEvent(signer, pubkey, sizeBytes, returnType = null)
            executeResolverSignEvent(signer, pubkey, sizeBytes, returnType = "signature")
            executeResolverNip44v3(signer, pubkey, sizeBytes)
        }
    }

    private fun executeResolverSignEvent(signer: String, pubkey: String, sizeBytes: Int, returnType: String?) {
        try {
            val res = Nip55PayloadClient.querySignEvent(contentResolver, signer, pubkey, sizeBytes, returnType)
            val msg = if (returnType == "signature") {
                "[PASS] Resolver SIGN_EVENT (return_type=signature) | cols=${res.columns} | sig=${res.signatureHex.take(12)}.. (${res.elapsedMs}ms)"
            } else {
                "[PASS] Resolver SIGN_EVENT (${res.eventTransport}) | ${res.eventJsonBytes} B | content=${res.verifiedContentLength} | singleUseDel=${res.singleUseDeleted} (${res.elapsedMs}ms)"
            }
            runOnUiThread { appendLog(msg) }
        } catch (t: Throwable) {
            runOnUiThread { appendLog("[FAIL] Resolver SIGN_EVENT(${returnType ?: "default"}): ${t.message}") }
        }
    }

    private fun executeResolverNip44v3(signer: String, pubkey: String, sizeBytes: Int) {
        try {
            val res = Nip55PayloadClient.queryNip44v3RoundTrip(contentResolver, signer, pubkey, sizeBytes)
            val msg = "[PASS] Resolver NIP44_V3 round-trip | raw=${res.rawPlaintextBytes} B -> cipher=${res.ciphertextBytes} B (${res.encryptTransport}, del=${res.encryptSingleUseDeleted}) -> (${res.decryptTransport}, del=${res.decryptSingleUseDeleted}) | match=${res.byteForByteMatch} (${res.elapsedMs}ms)"
            runOnUiThread { appendLog(msg) }
        } catch (t: Throwable) {
            runOnUiThread { appendLog("[FAIL] Resolver NIP44_V3: ${t.message}") }
        }
    }

    @Deprecated("Uses standard Activity.onActivityResult to match NIP-55 native client apps")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        val elapsedMs = System.currentTimeMillis() - pendingIntentStartMs
        if (requestCode == REQ_LEGACY_INLINE && resultCode != RESULT_OK) {
            appendLog("[PASS] Legacy inline Intent did not return RESULT_OK (code=$resultCode, ${elapsedMs}ms)")
            return
        }
        if (resultCode != RESULT_OK || data == null) {
            appendLog("[FAIL] Intent req=$requestCode returned resultCode=$resultCode, data=${data != null} (${elapsedMs}ms)")
            return
        }
        if (data.getBooleanExtra("rejected", false)) {
            appendLog("[FAIL] Signer rejected Intent req=$requestCode (${elapsedMs}ms)")
            return
        }

        val keys = data.extras?.keySet()?.sorted().orEmpty()
        when (requestCode) {
            REQ_LOGIN -> handleLoginResult(data, keys, elapsedMs)
            REQ_SIGN_STREAM -> handleSignStreamResult(data, keys, elapsedMs)
            REQ_SIGN_SIG_ONLY -> handleSignSigOnlyResult(data, keys, elapsedMs)
            REQ_V3_ENCRYPT -> handleV3EncryptResult(data, keys, elapsedMs)
            REQ_V3_DECRYPT -> handleV3DecryptResult(data, keys, elapsedMs)
            REQ_LEGACY_INLINE -> appendLog("[INFO] Legacy inline Intent returned OK (cols=$keys, ${elapsedMs}ms)")
        }
    }

    private fun handleLoginResult(data: Intent, keys: List<String>, elapsedMs: Long) {
        val returnedKey = (data.getStringExtra("result") ?: data.getStringExtra("signature"))?.trim()
        val returnedPkg = data.getStringExtra("package")?.trim()
        if (returnedKey.isNullOrBlank()) {
            appendLog("[FAIL] Login returned blank key (extras=$keys)")
            return
        }
        pubkeyHex = returnedKey
        prefs.edit().putString(KEY_PUBKEY_HEX, returnedKey).apply()
        if (!returnedPkg.isNullOrBlank()) {
            selectedSigner = returnedPkg
            prefs.edit().putString(KEY_SIGNER_PACKAGE, returnedPkg).apply()
        }
        updateStatusUi()
        appendLog("[PASS] Login Intent | signer=${selectedSigner} | pubkey=${returnedKey.take(16)}.. | extras=$keys (${elapsedMs}ms)")
    }

    private fun handleSignStreamResult(data: Intent, keys: List<String>, elapsedMs: Long) {
        val sig = data.getStringExtra("signature")
        val res = data.getStringExtra("result")
        val inlineEvent = data.getStringExtra("event")
        val eventUri = data.getStringExtra(Nip55PayloadClient.EXTRA_EVENT_URI)
        val expectedContent = lastSignedExpectedContent

        if (sig.isNullOrBlank() || sig.length != 128 || sig != res) {
            appendLog("[FAIL] Intent sign_event invalid sig/result (len=${sig?.length}, same=${sig == res}, extras=$keys)")
            return
        }
        if (!eventUri.isNullOrBlank()) {
            if (inlineEvent != null) {
                appendLog("[FAIL] Intent sign_event unexpectedly included inline event alongside event_uri")
                return
            }
            ioExecutor.execute {
                try {
                    val signedJson = Nip55PayloadClient.readStreamUtf8(contentResolver, eventUri)
                    val deleted = Nip55PayloadClient.verifySingleUseConsumed(contentResolver, eventUri)
                    val jsonObj = JSONObject(signedJson)
                    val verifiedSig = jsonObj.getString("sig")
                    val verifiedContent = jsonObj.getString("content")
                    val ok = verifiedSig == sig && verifiedContent == expectedContent
                    runOnUiThread {
                        if (ok) {
                            appendLog("[PASS] Intent sign_event (streamed event_uri) | ${signedJson.length} B | content=${verifiedContent.length} | sig=${sig.take(12)}.. | singleUseDel=$deleted (${elapsedMs}ms)")
                        } else {
                            appendLog("[FAIL] Intent sign_event streamed JSON verification failed")
                        }
                    }
                } catch (t: Throwable) {
                    runOnUiThread { appendLog("[FAIL] Reading event_uri failed: ${t.message}") }
                }
            }
        } else if (!inlineEvent.isNullOrBlank()) {
            val jsonObj = JSONObject(inlineEvent)
            val verifiedContent = jsonObj.getString("content")
            val ok = jsonObj.getString("sig") == sig && verifiedContent == expectedContent
            appendLog("${if (ok) "[PASS]" else "[FAIL]"} Intent sign_event (inline event) | ${inlineEvent.length} B | content=${verifiedContent.length} (${elapsedMs}ms)")
        } else {
            appendLog("[FAIL] Intent sign_event returned neither event nor event_uri (extras=$keys)")
        }
    }

    private fun handleSignSigOnlyResult(data: Intent, keys: List<String>, elapsedMs: Long) {
        val sig = data.getStringExtra("signature")
        val res = data.getStringExtra("result")
        val event = data.getStringExtra("event")
        val eventUri = data.getStringExtra(Nip55PayloadClient.EXTRA_EVENT_URI)
        val ok = sig != null && sig.length == 128 && sig == res && event == null && eventUri == null
        appendLog("${if (ok) "[PASS]" else "[FAIL]"} Intent sign_event (returnType=signature) | extras=$keys | sig=${sig?.take(12)}.. (${elapsedMs}ms)")
    }

    private fun handleV3EncryptResult(data: Intent, keys: List<String>, elapsedMs: Long) {
        val resultUri = data.getStringExtra(Nip55PayloadClient.EXTRA_RESULT_URI)
        val inlineResult = data.getStringExtra("result")
        val inlineSig = data.getStringExtra("signature")
        val inlineEvent = data.getStringExtra("event")
        if (!resultUri.isNullOrBlank()) {
            if (inlineResult != null || inlineSig != null || inlineEvent != null) {
                appendLog("[FAIL] Intent nip44v3_encrypt leaked inline extras alongside result_uri: $keys")
                return
            }
            ioExecutor.execute {
                try {
                    val cipher = Nip55PayloadClient.readStreamUtf8(contentResolver, resultUri)
                    val deleted = Nip55PayloadClient.verifySingleUseConsumed(contentResolver, resultUri)
                    lastEncryptedCiphertext = cipher
                    runOnUiThread {
                        appendLog("[PASS] Intent nip44v3_encrypt (streamed result_uri) | cipher=${cipher.length} B | singleUseDel=$deleted (${elapsedMs}ms)")
                    }
                } catch (t: Throwable) {
                    runOnUiThread { appendLog("[FAIL] Reading result_uri failed: ${t.message}") }
                }
            }
        } else if (!inlineResult.isNullOrBlank()) {
            lastEncryptedCiphertext = inlineResult
            appendLog("[PASS] Intent nip44v3_encrypt (inline result) | cipher=${inlineResult.length} B (${elapsedMs}ms)")
        } else {
            appendLog("[FAIL] Intent nip44v3_encrypt returned neither result_uri nor result ($keys)")
        }
    }

    private fun handleV3DecryptResult(data: Intent, keys: List<String>, elapsedMs: Long) {
        val resultUri = data.getStringExtra(Nip55PayloadClient.EXTRA_RESULT_URI)
        val inlineResult = data.getStringExtra("result")
        val expectedWire = lastEncryptedWirePlaintext
        val expectedRaw = lastEncryptedRawPlaintext

        if (!resultUri.isNullOrBlank()) {
            ioExecutor.execute {
                try {
                    val decryptedWire = Nip55PayloadClient.readStreamUtf8(contentResolver, resultUri)
                    val deleted = Nip55PayloadClient.verifySingleUseConsumed(contentResolver, resultUri)
                    val decoded = Base64.decode(decryptedWire, Base64.DEFAULT)
                    val match = decryptedWire == expectedWire && expectedRaw != null && expectedRaw.contentEquals(decoded)
                    runOnUiThread {
                        appendLog("${if (match) "[PASS]" else "[FAIL]"} Intent nip44v3_decrypt (streamed result_uri) | ${decoded.size} B | match=$match | singleUseDel=$deleted (${elapsedMs}ms)")
                    }
                } catch (t: Throwable) {
                    runOnUiThread { appendLog("[FAIL] Reading decrypt result_uri failed: ${t.message}") }
                }
            }
        } else if (!inlineResult.isNullOrBlank()) {
            val decoded = Base64.decode(inlineResult, Base64.DEFAULT)
            val match = inlineResult == expectedWire && expectedRaw != null && expectedRaw.contentEquals(decoded)
            appendLog("${if (match) "[PASS]" else "[FAIL]"} Intent nip44v3_decrypt (inline result) | ${decoded.size} B | match=$match (${elapsedMs}ms)")
        } else {
            appendLog("[FAIL] Intent nip44v3_decrypt returned neither result_uri nor result ($keys)")
        }
    }

    private fun requireSigner(): String? {
        val signer = selectedSigner
        if (signer == null) {
            appendLog("[FAIL] No NIP-55 signer installed on device.")
        }
        return signer
    }

    private fun requirePubkey(): String? {
        val pk = pubkeyHex
        if (pk.isNullOrBlank()) {
            appendLog("[FAIL] Not logged in yet. Tap button 1 first.")
        }
        return pk
    }

    private fun appendLog(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        if (logBuffer.size >= 40) {
            logBuffer.removeFirst()
        }
        logBuffer.addLast("[$ts] $line")
        logTextView.text = logBuffer.joinToString("\n\n")
        mainScrollView.post {
            mainScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val PREFS_NAME = "nip55_test_client"
        const val KEY_PUBKEY_HEX = "pubkey_hex"
        const val KEY_SIGNER_PACKAGE = "signer_package"
        const val KEY_SIZE_BYTES = "size_bytes"

        const val REQ_LOGIN = 1001
        const val REQ_SIGN_STREAM = 1002
        const val REQ_SIGN_SIG_ONLY = 1003
        const val REQ_V3_ENCRYPT = 1004
        const val REQ_V3_DECRYPT = 1005
        const val REQ_LEGACY_INLINE = 1006

        const val SIZE_64_KB = 64 * 1024
        const val SIZE_2_MB = 2 * 1024 * 1024
        const val SIZE_3_MB = 3 * 1024 * 1024
        const val SIZE_8_MB = 8 * 1024 * 1024

        val SIZE_OPTIONS = listOf(
            "64 KB" to SIZE_64_KB,
            "2 MB" to SIZE_2_MB,
            "3 MB" to SIZE_3_MB,
            "8 MB" to SIZE_8_MB,
        )
    }
}
