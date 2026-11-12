package com.greenart7c3.nostrsigner.desktop.core

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * macOS notifications with buttons, through `UNUserNotificationCenter` (JNA
 * over the Objective-C runtime, no helper binary): the AWT tray notification
 * can neither carry Approve / Reject nor be taken down again.
 *
 * It only works inside the packaged app: the notification center identifies
 * the sender by its bundle, and throws for a bare `java` process such as
 * `./gradlew :desktop:run` ([usable] is false there). macOS asks the user once
 * to allow Amber's notifications; until they do, and whenever they turn them
 * off, [authorized] is false and the caller shows the request some other way.
 *
 * Buttons are notification *categories* (a fixed set of actions registered
 * ahead of time), so one is registered per combination of button labels in
 * use. Clicks come back through the center's delegate, an Objective-C class
 * built at runtime whose methods are JNA callbacks.
 *
 * Every call into Objective-C runs on [worker] inside an autorelease pool;
 * macOS calls the delegate on a thread of its own and the click is handed to
 * [callbacks] so the delegate returns at once.
 */
internal object MacNotifications {
    /** A click on the notification itself, like the freedesktop "default" action. */
    const val DEFAULT_ACTION = "default"

    private const val APPLE_DEFAULT_ACTION = "com.apple.UNNotificationDefaultActionIdentifier"

    /** `UNAuthorizationOptionAlert`. */
    private const val AUTHORIZATION_ALERT = 4L

    /** `UNNotificationPresentationOptionList | …Banner` (macOS 11+), else the older `…Alert`. */
    private const val PRESENT_LIST_AND_BANNER = 24L
    private const val PRESENT_ALERT = 4L

    private const val CLOSE_TIMEOUT_SECONDS = 2L

    /** `BLOCK_IS_GLOBAL`: a block that is never copied or freed. */
    private const val BLOCK_IS_GLOBAL = 1 shl 28

    /** Offset of the `invoke` function pointer in a block: isa, flags, reserved. */
    private const val BLOCK_INVOKE_OFFSET = 16L

    @Suppress("FunctionName")
    private interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer
        fun objc_allocateClassPair(superclass: Pointer?, name: String, extraBytes: Long): Pointer?
        fun objc_registerClassPair(cls: Pointer)
        fun class_addMethod(cls: Pointer, selector: Pointer, implementation: Callback, types: String): Boolean
        fun objc_autoreleasePoolPush(): Pointer?
        fun objc_autoreleasePoolPop(pool: Pointer?)
    }

    private fun interface AuthorizationBlock : Callback {
        fun invoke(block: Pointer?, granted: Byte, error: Pointer?)
    }

    /** Both delegate methods: (self, _cmd, center, notification or response, completion block). */
    private fun interface DelegateMethod : Callback {
        fun invoke(self: Pointer?, selector: Pointer?, center: Pointer?, subject: Pointer?, completion: Pointer?)
    }

    private val objc: ObjC by lazy { Native.load("objc", ObjC::class.java) }
    private val msgSend: Function by lazy { Function.getFunction("objc", "objc_msgSend") }

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "amber-mac-notify").apply { isDaemon = true }
    }
    private val callbacks = Executors.newSingleThreadExecutor { r ->
        Thread(r, "amber-mac-notify-action").apply { isDaemon = true }
    }

    /** What to run for a click, per notification identifier. */
    private val handlers = ConcurrentHashMap<String, (String) -> Unit>()

    /** Registered categories: identifier → its (action, label) buttons. Only touched on [worker]. */
    private val categories = LinkedHashMap<String, List<Pair<String, String>>>()

    /** The notification center, or null when it cannot be used. Only touched on [worker]. */
    private var center: Pointer? = null
    private var setUp = false

    @Volatile
    private var granted = false

    @Volatile
    private var authorizationAnswered = CountDownLatch(0)

    // Callbacks and native memory macOS keeps pointers to: never collected.
    private val authorizationBlockInvoke = AuthorizationBlock { _, isGranted, _ ->
        granted = isGranted.toInt() != 0
        authorizationAnswered.countDown()
    }
    private val didReceive = DelegateMethod { _, _, _, response, completion ->
        try {
            response?.let { clicked(it) }
        } catch (e: Throwable) {
            AmberLogger.d("MacNotifications", "notification click failed: ${e.message}")
        }
        completion?.let { invokeBlock(it) }
    }
    private val willPresent = DelegateMethod { _, _, _, _, completion ->
        // Show it even while Amber is the frontmost app (its tray window, say).
        completion?.let { invokeBlock(it, if (macOsMajor() >= 11) PRESENT_LIST_AND_BANNER else PRESENT_ALERT) }
    }
    private var authorizationBlock: Memory? = null

    /** Native memory the block above points at. */
    private val pinned = mutableListOf<Memory>()

    private fun macOsMajor(): Int = System.getProperty("os.version").orEmpty().substringBefore('.').toIntOrNull() ?: 0

    private fun cls(name: String): Pointer = objc.objc_getClass(name) ?: throw IllegalStateException("no Objective-C class $name")

    private fun send(receiver: Pointer?, selector: String, vararg args: Any?): Pointer? = msgSend.invokePointer(arrayOf(receiver, objc.sel_registerName(selector), *args))

    private fun sendVoid(receiver: Pointer?, selector: String, vararg args: Any?) {
        msgSend.invoke(Void.TYPE, arrayOf(receiver, objc.sel_registerName(selector), *args))
    }

    /** An autoreleased NSString; the bytes are passed NUL-terminated, never as a platform-encoded string. */
    private fun nsString(text: String): Pointer? = send(cls("NSString"), "stringWithUTF8String:", text.replace("\u0000", "").toByteArray(Charsets.UTF_8) + 0)

    private fun string(nsString: Pointer?): String = nsString?.let { send(it, "UTF8String") }?.getString(0, "UTF-8").orEmpty()

    private fun nsArray(items: List<Pointer?>): Pointer? {
        if (items.isEmpty()) return send(cls("NSArray"), "array")
        val buffer = Memory(items.size.toLong() * Native.POINTER_SIZE)
        items.forEachIndexed { i, item -> buffer.setPointer(i.toLong() * Native.POINTER_SIZE, item) }
        return send(cls("NSArray"), "arrayWithObjects:count:", buffer, items.size.toLong())
    }

    private fun invokeBlock(block: Pointer, vararg args: Any?) {
        Function.getFunction(block.getPointer(BLOCK_INVOKE_OFFSET)).invoke(Void.TYPE, arrayOf(block, *args))
    }

    private fun <T> onWorker(block: () -> T): T = try {
        worker.submit(
            Callable {
                val pool = objc.objc_autoreleasePoolPush()
                try {
                    block()
                } finally {
                    objc.objc_autoreleasePoolPop(pool)
                }
            },
        ).get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

    /**
     * The notification center with Amber's delegate installed, or null outside
     * an app bundle, where asking for it raises an Objective-C exception (which
     * would take the JVM down).
     */
    private fun center(): Pointer? {
        if (setUp) return center
        setUp = true
        val bundleId = send(send(cls("NSBundle"), "mainBundle"), "bundleIdentifier")
        if (bundleId == null) {
            AmberLogger.d("MacNotifications", "not running from an app bundle: no native notifications")
            return null
        }
        NativeLibrary.getInstance("/System/Library/Frameworks/UserNotifications.framework/UserNotifications")
        val shared = send(cls("UNUserNotificationCenter"), "currentNotificationCenter") ?: return null

        val delegateClass = objc.objc_allocateClassPair(cls("NSObject"), "AmberNotificationDelegate", 0)
            ?: throw IllegalStateException("could not create the notification delegate class")
        // v@:@@@? — void, self, _cmd, two objects and a block.
        objc.class_addMethod(delegateClass, objc.sel_registerName("userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:"), didReceive, "v@:@@@?")
        objc.class_addMethod(delegateClass, objc.sel_registerName("userNotificationCenter:willPresentNotification:withCompletionHandler:"), willPresent, "v@:@@@?")
        objc.objc_registerClassPair(delegateClass)
        // The center does not retain its delegate; this instance is never released.
        sendVoid(shared, "setDelegate:", send(send(delegateClass, "alloc"), "init"))
        // Requests do not outlive the process: what an earlier run left in
        // Notification Center (it was killed, or crashed) answers nothing now.
        sendVoid(shared, "removeAllDeliveredNotifications")

        // A global block (isa, flags, reserved, invoke, descriptor{reserved, size}).
        val descriptor = Memory(16).apply {
            setLong(0, 0)
            setLong(8, 32)
        }
        val block = Memory(32).apply {
            setPointer(0, NativeLibrary.getInstance("System").getGlobalVariableAddress("_NSConcreteGlobalBlock"))
            setInt(8, BLOCK_IS_GLOBAL)
            setInt(12, 0)
            setPointer(BLOCK_INVOKE_OFFSET, CallbackReference.getFunctionPointer(authorizationBlockInvoke))
            setPointer(24, descriptor)
        }
        pinned += descriptor
        authorizationBlock = block
        center = shared
        return shared
    }

    /** Whether this process can use the notification center at all (the packaged app). */
    fun usable(): Boolean = try {
        onWorker { center() != null }
    } catch (e: Throwable) {
        AmberLogger.d("MacNotifications", "notification center unavailable: ${e.message}")
        false
    }

    /**
     * Whether the user allows Amber's notifications. The first call makes
     * macOS ask; while that prompt is open (and whenever the answer is no)
     * this is false. Asking again later only reports the current setting.
     * Blocks for at most [waitMs].
     */
    fun authorized(waitMs: Long = 500): Boolean = try {
        val answered = CountDownLatch(1)
        val asked = onWorker {
            val shared = center() ?: return@onWorker false
            authorizationAnswered = answered
            sendVoid(shared, "requestAuthorizationWithOptions:completionHandler:", AUTHORIZATION_ALERT, authorizationBlock)
            true
        }
        asked && answered.await(waitMs, TimeUnit.MILLISECONDS) && granted
    } catch (e: Throwable) {
        AmberLogger.d("MacNotifications", "notification authorization failed: ${e.message}")
        false
    }

    /**
     * Post a notification with [actions] as (key, label) buttons and run
     * [onAction] with the key the user picks ([DEFAULT_ACTION] for a click on
     * the notification). Returns what takes it down again, from the screen and
     * Notification Center. Throws when the notification center is unusable.
     */
    fun show(title: String, message: String, actions: List<Pair<String, String>>, onAction: (String) -> Unit): () -> Unit {
        val id = UUID.randomUUID().toString()
        onWorker {
            val shared = center() ?: throw IllegalStateException("no notification center")
            val content = send(send(cls("UNMutableNotificationContent"), "alloc"), "init")
            try {
                sendVoid(content, "setTitle:", nsString(title))
                sendVoid(content, "setBody:", nsString(message))
                if (actions.isNotEmpty()) sendVoid(content, "setCategoryIdentifier:", nsString(category(shared, actions)))
                val request = send(cls("UNNotificationRequest"), "requestWithIdentifier:content:trigger:", nsString(id), content, null)
                handlers[id] = onAction
                sendVoid(shared, "addNotificationRequest:withCompletionHandler:", request, null)
            } finally {
                sendVoid(content, "release")
            }
        }
        return {
            handlers.remove(id)
            // Waits for the removal, so a notification closed while Amber
            // quits is really gone before the process ends.
            try {
                worker.submit {
                    val pool = objc.objc_autoreleasePoolPush()
                    try {
                        val ids = nsArray(listOf(nsString(id)))
                        sendVoid(center, "removePendingNotificationRequestsWithIdentifiers:", ids)
                        sendVoid(center, "removeDeliveredNotificationsWithIdentifiers:", ids)
                    } finally {
                        objc.objc_autoreleasePoolPop(pool)
                    }
                }.get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                AmberLogger.d("MacNotifications", "closing notification failed: ${e.message}")
            }
        }
    }

    /**
     * The category whose buttons are [actions], registering it first when it
     * is new. Setting the categories replaces the whole set, so every one in
     * use is passed again.
     */
    private fun category(shared: Pointer, actions: List<Pair<String, String>>): String {
        categories.entries.firstOrNull { it.value == actions }?.let { return it.key }
        val id = categoryId(actions)
        categories[id] = actions
        val all = categories.map { (categoryId, buttons) ->
            val unActions = buttons.map { (key, label) ->
                send(cls("UNNotificationAction"), "actionWithIdentifier:title:options:", nsString(key), nsString(label), 0L)
            }
            send(cls("UNNotificationCategory"), "categoryWithIdentifier:actions:intentIdentifiers:options:", nsString(categoryId), nsArray(unActions), nsArray(emptyList()), 0L)
        }
        sendVoid(shared, "setNotificationCategories:", send(cls("NSSet"), "setWithArray:", nsArray(all)))
        return id
    }

    /** Stable per set of buttons and their labels, so a language change gets its own category. */
    internal fun categoryId(actions: List<Pair<String, String>>): String = "amber.request." + actions.joinToString("|") { "${it.first}=${it.second}" }.hashCode().toUInt().toString(16)

    /** `UNNotificationResponse` → the handler of the notification it answers. */
    private fun clicked(response: Pointer) {
        val action = string(send(response, "actionIdentifier"))
        val id = string(send(send(send(response, "notification"), "request"), "identifier"))
        val handler = handlers.remove(id) ?: return
        callbacks.execute { handler(if (action == APPLE_DEFAULT_ACTION) DEFAULT_ACTION else action) }
    }
}
