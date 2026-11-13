package com.greenart7c3.nostrsigner.desktop.core

import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Windows toast notifications with buttons, straight through the WinRT
 * `Windows.UI.Notifications` API (JNA, no helper process): the AWT tray
 * notification can neither carry Approve / Reject nor be taken down again.
 *
 * Amber is not a packaged (MSIX) app, so it posts under an AppUserModelID it
 * registers for the current user ([register]), which is what names the toast
 * "Amber" and gives it the icon. Button and body clicks come back through the
 * toast's `Activated` event, which Windows raises in this process for as long
 * as Amber runs: there is no COM activator and no URL scheme, so nothing
 * outside the process can answer a request through a toast.
 *
 * Every WinRT call runs on one thread ([worker], a multithreaded apartment);
 * the event arrives on a Windows thread pool thread and is handed to
 * [callbacks] so the handler never blocks Windows.
 */
internal object WindowsToasts {
    private const val IID_MANAGER_STATICS = "{50AC103F-D235-4598-BBEF-98FE4D1A3AD4}"
    private const val IID_TOAST_FACTORY = "{04124B20-82C6-4229-B109-FD9ED4662B53}"
    private const val IID_XML_DOCUMENT = "{F7F3A506-1E87-42D6-BCFB-B8C809FA5494}"
    private const val IID_XML_DOCUMENT_IO = "{6CD0E74E-EE65-4489-9EBF-CA43E87BA637}"
    private const val IID_ACTIVATED_ARGS = "{E3BF92F3-C197-436F-8265-0625824F8DAC}"

    /** `ITypedEventHandler<ToastNotification, IInspectable>`. */
    private const val IID_ACTIVATED_HANDLER = "{AB54DE2D-97D9-5528-B6AD-105AFE156530}"
    private const val IID_UNKNOWN = "{00000000-0000-0000-C000-000000000046}"

    /** Marks the handler as callable from any thread, so WinRT does not try to marshal it. */
    private const val IID_AGILE_OBJECT = "{94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90}"

    private const val S_OK = 0
    private const val E_NOINTERFACE = 0x80004002.toInt()
    private const val RO_INIT_MULTITHREADED = 1

    private const val CLOSE_TIMEOUT_SECONDS = 2L

    /** `NotificationSetting.Enabled`. */
    private const val SETTING_ENABLED = 0

    /** The body click's argument, like the freedesktop "default" action. */
    const val DEFAULT_ACTION = "default"

    // IInspectable takes the first six vtable slots; these follow them.
    private const val MANAGER_CREATE_NOTIFIER_WITH_ID = 7
    private const val NOTIFIER_SHOW = 6
    private const val NOTIFIER_HIDE = 7
    private const val NOTIFIER_GET_SETTING = 8
    private const val FACTORY_CREATE_TOAST = 6
    private const val XML_IO_LOAD_XML = 6
    private const val TOAST_ADD_ACTIVATED = 11
    private const val TOAST_REMOVE_ACTIVATED = 12
    private const val ARGS_GET_ARGUMENTS = 6

    @Suppress("FunctionName")
    private interface Combase : StdCallLibrary {
        fun RoInitialize(type: Int): Int
        fun WindowsCreateString(source: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer?): Int
        fun WindowsGetStringRawBuffer(string: Pointer?, length: IntByReference?): Pointer?
        fun RoGetActivationFactory(classId: Pointer?, iid: Guid.REFIID, factory: PointerByReference): Int
        fun RoActivateInstance(classId: Pointer?, instance: PointerByReference): Int
    }

    private val combase: Combase by lazy { Native.load("combase", Combase::class.java) }

    /** A COM object, called by vtable slot. */
    private class Com(pointer: Pointer) : Unknown(pointer) {
        fun call(slot: Int, vararg args: Any?): Int = _invokeNativeInt(slot, arrayOf(pointer, *args))

        fun query(iid: String): Com {
            val out = PointerByReference()
            check(QueryInterface(Guid.REFIID(Guid.IID(iid)), out).toInt(), "QueryInterface $iid")
            return Com(out.value)
        }
    }

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "amber-toast").apply { isDaemon = true }
    }
    private val callbacks = Executors.newSingleThreadExecutor { r ->
        Thread(r, "amber-toast-action").apply { isDaemon = true }
    }

    /** Only touched on [worker]. */
    private var notifier: Com? = null
    private var toastFactory: Com? = null

    val appUserModelId: String = "com.greenart7c3.nostrsigner${BuildVariant.suffix}"

    private fun check(hresult: Int, what: String) {
        if (hresult < 0) throw IllegalStateException("$what failed: 0x${Integer.toHexString(hresult)}")
    }

    private fun <T> withHString(text: String, block: (Pointer?) -> T): T {
        val out = PointerByReference()
        check(combase.WindowsCreateString(WString(text), text.length, out), "WindowsCreateString")
        return try {
            block(out.value)
        } finally {
            combase.WindowsDeleteString(out.value)
        }
    }

    private fun activationFactory(className: String, iid: String): Com = withHString(className) { name ->
        val out = PointerByReference()
        check(combase.RoGetActivationFactory(name, Guid.REFIID(Guid.IID(iid)), out), "RoGetActivationFactory $className")
        Com(out.value)
    }

    /**
     * Name the toasts: an unpackaged app has no identity of its own, and a
     * toast posted under an unknown id is dropped. Per user, no admin rights.
     */
    private fun register() {
        val key = "Software\\Classes\\AppUserModelId\\$appUserModelId"
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, "DisplayName", BuildVariant.appName)
        iconFile()?.let { Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, "IconUri", it.absolutePath) }
    }

    /** The app icon as a file Windows can read (the resource lives in the jar). */
    private fun iconFile(): File? = try {
        val file = File(AppDirs.dataDir, "notification-icon.png")
        if (!file.isFile) {
            WindowsToasts::class.java.getResourceAsStream("/icon.png")?.use { icon -> file.outputStream().use { icon.copyTo(it) } }
        }
        file.takeIf { it.isFile }
    } catch (e: Exception) {
        AmberLogger.d("WindowsToasts", "notification icon unavailable: ${e.message}")
        null
    }

    private fun notifier(): Com = notifier ?: run {
        // S_FALSE / RPC_E_CHANGED_MODE: the thread already has an apartment, which is fine.
        combase.RoInitialize(RO_INIT_MULTITHREADED)
        register()
        val manager = activationFactory("Windows.UI.Notifications.ToastNotificationManager", IID_MANAGER_STATICS)
        val created = withHString(appUserModelId) { id ->
            val out = PointerByReference()
            check(manager.call(MANAGER_CREATE_NOTIFIER_WITH_ID, id, out), "CreateToastNotifierWithId")
            Com(out.value)
        }
        manager.Release()
        toastFactory = activationFactory("Windows.UI.Notifications.ToastNotification", IID_TOAST_FACTORY)
        notifier = created
        created
    }

    /**
     * Handlers Windows may still call, so neither they nor their callbacks are
     * collected while a toast is up. Only touched on [worker].
     */
    private val live = mutableSetOf<ActivatedHandler>()

    /** Hide [toast] and stop listening to it. Runs on [worker], once per toast. */
    private fun close(toast: Com, handler: ActivatedHandler, token: Long) {
        if (!live.remove(handler)) return
        try {
            toast.call(TOAST_REMOVE_ACTIVATED, token)
            notifier?.call(NOTIFIER_HIDE, toast.pointer)
        } catch (e: Throwable) {
            AmberLogger.d("WindowsToasts", "closing toast failed: ${e.message}")
        } finally {
            toast.Release()
            handler.dispose()
        }
    }

    /**
     * Post a toast with [actions] as (argument, label) buttons and run
     * [onAction] with the argument the user picks ([DEFAULT_ACTION] for a click
     * on the body). Returns what takes the toast down again, from the screen
     * and the notification center; calling it twice, or after a click, is
     * harmless. Throws when WinRT is unavailable or the user turned Amber's
     * notifications off, so the caller can show the request some other way.
     */
    fun show(title: String, message: String, actions: List<Pair<String, String>>, onAction: (String) -> Unit): () -> Unit = try {
        worker.submit(
            Callable {
                val notifier = notifier()
                val setting = IntByReference()
                // Fails with "element not found" until the id has posted its
                // first toast, so only a setting Windows does report counts.
                if (notifier.call(NOTIFIER_GET_SETTING, setting) >= 0 && setting.value != SETTING_ENABLED) {
                    throw IllegalStateException("notifications are off (setting ${setting.value})")
                }

                val document = loadXml(toastXml(title, message, actions))
                val toast = try {
                    val out = PointerByReference()
                    check(toastFactory!!.call(FACTORY_CREATE_TOAST, document.pointer, out), "CreateToastNotification")
                    Com(out.value)
                } finally {
                    document.Release()
                }
                val handler = ActivatedHandler { argument -> callbacks.execute { onAction(argument) } }
                val token = LongByReference()
                try {
                    check(toast.call(TOAST_ADD_ACTIVATED, handler.pointer, token), "add_Activated")
                    check(notifier.call(NOTIFIER_SHOW, toast.pointer), "Show")
                } catch (e: Throwable) {
                    toast.Release()
                    handler.dispose()
                    throw e
                }
                live += handler
                val registration = token.value
                // Waits for the toast to be gone, so one closed while Amber
                // quits is really taken down before the process ends.
                val closer: () -> Unit = {
                    try {
                        worker.submit { close(toast, handler, registration) }.get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    } catch (e: Exception) {
                        AmberLogger.d("WindowsToasts", "closing toast did not finish: ${e.message}")
                    }
                }
                closer
            },
        ).get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

    private fun loadXml(xml: String): Com {
        val instance = withHString("Windows.Data.Xml.Dom.XmlDocument") { name ->
            val out = PointerByReference()
            check(combase.RoActivateInstance(name, out), "RoActivateInstance XmlDocument")
            Com(out.value)
        }
        try {
            val io = instance.query(IID_XML_DOCUMENT_IO)
            try {
                withHString(xml) { check(io.call(XML_IO_LOAD_XML, it), "LoadXml") }
            } finally {
                io.Release()
            }
            return instance.query(IID_XML_DOCUMENT)
        } finally {
            instance.Release()
        }
    }

    /**
     * The toast's XML: the title, up to two more lines of [message] (a generic
     * toast shows three text lines), and one button per action. `duration`
     * long keeps it on screen for about as long as the Linux notification.
     */
    internal fun toastXml(title: String, message: String, actions: List<Pair<String, String>>): String = buildString {
        append("<toast launch=\"").append(DEFAULT_ACTION).append("\" duration=\"long\">")
        append("<visual><binding template=\"ToastGeneric\">")
        append("<text>").append(xml(title)).append("</text>")
        message.lines().filter { it.isNotBlank() }.take(2).forEach { append("<text>").append(xml(it)).append("</text>") }
        append("</binding></visual>")
        if (actions.isNotEmpty()) {
            append("<actions>")
            actions.forEach { (argument, label) ->
                append("<action content=\"").append(xml(label)).append("\" arguments=\"").append(xml(argument)).append("\"/>")
            }
            append("</actions>")
        }
        append("</toast>")
    }

    /** Escaped for XML text and attributes; characters XML 1.0 cannot carry are dropped. */
    private fun xml(text: String): String = buildString {
        text.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\'' -> append("&apos;")
                c < ' ' && c != '\t' -> Unit
                c == '￾' || c == '￿' -> Unit
                else -> append(c)
            }
        }
    }

    /**
     * The COM object Windows calls when the toast is clicked: a hand-built
     * vtable (QueryInterface, AddRef, Release, Invoke) over JNA callbacks.
     * Its memory must outlive the registration, so the [Toast] keeps it until
     * the handler is removed again; reference counting is therefore a no-op.
     */
    private class ActivatedHandler(private val onActivated: (String) -> Unit) {
        private fun interface QueryInterface : StdCallLibrary.StdCallCallback {
            fun invoke(self: Pointer?, iid: Pointer?, out: Pointer?): Int
        }

        private fun interface RefCount : StdCallLibrary.StdCallCallback {
            fun invoke(self: Pointer?): Int
        }

        private fun interface Invoke : StdCallLibrary.StdCallCallback {
            fun invoke(self: Pointer?, sender: Pointer?, args: Pointer?): Int
        }

        private val accepted = setOf(IID_UNKNOWN, IID_AGILE_OBJECT, IID_ACTIVATED_HANDLER)
        private val self = Memory(Native.POINTER_SIZE.toLong())
        private val vtable = Memory(4L * Native.POINTER_SIZE)

        private val queryInterface = QueryInterface { _, iid, out ->
            val asked = iid?.let { Guid.GUID(it).toGuidString().uppercase() }
            if (asked in accepted) {
                out?.setPointer(0, self)
                S_OK
            } else {
                out?.setPointer(0, Pointer.NULL)
                E_NOINTERFACE
            }
        }
        private val refCount = RefCount { 1 }
        private val invoke = Invoke { _, _, args ->
            try {
                args?.let { argumentOf(it) }?.let(onActivated)
            } catch (e: Throwable) {
                AmberLogger.d("WindowsToasts", "toast activation failed: ${e.message}")
            }
            S_OK
        }

        val pointer: Pointer get() = self

        init {
            vtable.setPointer(0L, CallbackReference.getFunctionPointer(queryInterface))
            vtable.setPointer(1L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(refCount))
            vtable.setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(refCount))
            vtable.setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(invoke))
            self.setPointer(0, vtable)
        }

        /** `ToastActivatedEventArgs.Arguments`: the clicked button's argument, or the toast's `launch`. */
        private fun argumentOf(args: Pointer): String {
            val activated = Com(args).query(IID_ACTIVATED_ARGS)
            try {
                val out = PointerByReference()
                check(activated.call(ARGS_GET_ARGUMENTS, out), "get_Arguments")
                try {
                    return combase.WindowsGetStringRawBuffer(out.value, null)?.getWideString(0).orEmpty()
                } finally {
                    combase.WindowsDeleteString(out.value)
                }
            } finally {
                activated.Release()
            }
        }

        /** Let the native memory go, once Windows no longer holds the handler. */
        fun dispose() {
            self.close()
            vtable.close()
        }
    }
}
