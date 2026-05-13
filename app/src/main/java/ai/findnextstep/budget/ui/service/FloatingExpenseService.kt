package ai.findnextstep.budget.ui.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.findnextstep.budget.MainActivity
import ai.findnextstep.budget.data.JsonTransactionRepository
import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.logic.model.Transaction
import ai.findnextstep.budget.logic.service.CategoryPredictor
import ai.findnextstep.budget.ui.theme.CategoryColorMap
import ai.findnextstep.budget.ui.theme.ExpenseRed

/**
 * 系统级悬浮窗快速记账 Service。
 * 在其他应用之上显示一个可拖动的悬浮气泡，点击后展开为记账面板。
 */
class FloatingExpenseService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: FrameLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val repository = JsonTransactionRepository()
    private val categoryPredictor by lazy { CategoryPredictor(repository) }

    // 当前气泡位置（屏幕像素坐标）
    private var bubbleX = 0
    private var bubbleY = 300

    companion object {
        private const val CHANNEL_ID = "budget_floating"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP = "ai.findnextstep.budget.STOP_FLOATING"
        const val PREF_FLOATING_ENABLED = "floating_window_enabled"
        const val PREF_FLOATING_X = "floating_bubble_x"
        const val PREF_FLOATING_Y = "floating_bubble_y"
        const val PREF_HIDE_HINT = "floating_hide_hint"

        val hideHintState = mutableStateOf(false)

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences("budget_prefs", 0)
                .getBoolean(PREF_FLOATING_ENABLED, false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("budget_prefs", 0)
                .edit().putBoolean(PREF_FLOATING_ENABLED, enabled).apply()
        }

        fun start(context: Context) {
            setEnabled(context, true)
            context.startForegroundService(Intent(context, FloatingExpenseService::class.java))
        }

        fun stop(context: Context) {
            setEnabled(context, false)
            context.stopService(Intent(context, FloatingExpenseService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // 加载持久化数据
        val dataPath = filesDir.resolve("transactions.json").absolutePath
        repository.load(dataPath)

        // 恢复气泡位置
        val prefs = getSharedPreferences("budget_prefs", 0)
        bubbleX = prefs.getInt(PREF_FLOATING_X, 0)
        bubbleY = prefs.getInt(PREF_FLOATING_Y, 300)
        hideHintState.value = prefs.getBoolean(PREF_HIDE_HINT, false)

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        showOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        savedStateRegistryController.performSave(Bundle())
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        hideOverlay()
        super.onDestroy()
    }

    // ────────────────────── 通知 ──────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮记账",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗快捷记账服务"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stopIntent = Intent(this, FloatingExpenseService::class.java).apply {
            action = ACTION_STOP
        }.let {
            PendingIntent.getService(this, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮记账已开启")
                .setContentText("点击气泡快速记录支出")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(openIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "关闭悬浮窗",
                    stopIntent
                )
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("悬浮记账已开启")
                .setContentText("点击气泡快速记录支出")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        }
    }

    // ────────────────────── 悬浮窗 ──────────────────────

    private fun showOverlay() {
        if (overlayView != null) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }

        val composeView = ComposeView(this).apply {
            setContent {
                FloatingWindowContent(
                    onUpdatePosition = { dx, dy ->
                        val lp = overlayParams
                        if (lp != null) {
                            lp.x += dx.toInt()
                            lp.y += dy.toInt()
                            bubbleX = lp.x
                            bubbleY = lp.y
                            try {
                                windowManager.updateViewLayout(overlayView, lp)
                            } catch (_: Exception) {}
                            // 持久化位置
                            getSharedPreferences("budget_prefs", 0).edit()
                                .putInt(PREF_FLOATING_X, bubbleX)
                                .putInt(PREF_FLOATING_Y, bubbleY)
                                .apply()
                        }
                    },
                    onRequestFocus = { focused ->
                        val lp = overlayParams
                        if (lp != null) {
                            if (focused) {
                                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
                            } else {
                                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            }
                            try {
                                windowManager.updateViewLayout(overlayView, lp)
                            } catch (_: Exception) {}
                        }
                    },
                    onClose = { stopSelf() },
                    onAddTransaction = { category, amount ->
                        val txn = Transaction.create(
                            category = category,
                            amount = -amount,
                            note = "悬浮窗记账"
                        )
                        repository.add(txn)
                    },
                    categoryPredictor = categoryPredictor
                )
            }
        }

        overlayView = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@FloatingExpenseService)
            setSavedStateOwnerReflective(this, this@FloatingExpenseService)
            addView(composeView)
        }

        try {
            windowManager.addView(overlayView, overlayParams)
        } catch (e: Exception) {
            overlayView = null
            stopSelf()
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        overlayParams = null
    }

    /**
     * 通过反射设置 ViewTreeSavedStateRegistryOwner。
     * 绕过编译期依赖问题，运行时 savedstate 库必然在 classpath 中。
     */
    private fun setSavedStateOwnerReflective(view: android.view.View, owner: SavedStateRegistryOwner) {
        try {
            val clazz = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            val method = clazz.getMethod("set", android.view.View::class.java, SavedStateRegistryOwner::class.java)
            method.invoke(null, view, owner)
        } catch (_: Exception) {
            // Compose 检测到缺失时仍会崩溃，但新版 savedstate 应正常工作
        }
    }
}

// ────────────────────── Compose 悬浮窗 UI ──────────────────────

@Composable
private fun FloatingWindowContent(
    onUpdatePosition: (Float, Float) -> Unit,
    onRequestFocus: (Boolean) -> Unit,
    onClose: () -> Unit,
    onAddTransaction: (Category, Double) -> Unit,
    categoryPredictor: CategoryPredictor
) {
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }

    if (expanded) {
        ExpandedPanel(
            amount = amount,
            onAmountChange = { newVal ->
                amount = newVal
                val amt = newVal.toDoubleOrNull()
                if (amt != null && amt > 0) {
                    selectedCategory = categoryPredictor.predictOrDefault(-amt)
                }
            },
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            onConfirm = {
                val amt = amount.toDoubleOrNull()
                if (amt != null && amt > 0) {
                    val cat = selectedCategory ?: Category.OTHER
                    onAddTransaction(cat, amt)
                }
                amount = ""
                selectedCategory = null
                expanded = false
                onRequestFocus(false)
            },
            onCollapse = {
                expanded = false
                onRequestFocus(false)
            }
        )
    } else {
        CollapsedBubble(
            onTap = {
                expanded = true
                onRequestFocus(true)
            },
            onDrag = { dx, dy -> onUpdatePosition(dx, dy) },
            onClose = onClose
        )
    }
}

@Composable
private fun CollapsedBubble(
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit
) {
    var showClose by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { showClose = false },
                    onDragCancel = { showClose = false }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        FloatingActionButton(
            onClick = {
                if (showClose) onClose() else onTap()
            },
            shape = CircleShape,
            containerColor = ExpenseRed.copy(alpha = 0.75f),
            contentColor = Color.White,
            modifier = Modifier
                .size(44.dp)
                .shadow(8.dp, CircleShape),
            elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            if (showClose) {
                Text("✕", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("¥", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 长按显示关闭
        LaunchedEffect(Unit) {
            // 使用简单的长按检测：点击后如果再次点击间隔很短
        }
    }

    // 底部提示
    if (!FloatingExpenseService.hideHintState.value) {
    Box(
        modifier = Modifier
            .padding(top = 48.dp)
            .background(
                Color.Black.copy(alpha = 0.6f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "点击记账 · 长按关闭",
            color = Color.White,
            fontSize = 10.sp
        )
    }
    }
}

@Composable
private fun ExpandedPanel(
    amount: String,
    onAmountChange: (String) -> Unit,
    selectedCategory: Category?,
    onCategorySelected: (Category) -> Unit,
    onConfirm: () -> Unit,
    onCollapse: () -> Unit
) {
    Surface(
        modifier = Modifier.width(280.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2C2C2E),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("快速记账", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("−", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)
                    }
                    IconButton(
                        onClick = {
                            onAmountChange("")
                            onCollapse()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("✕", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 金额显示
            Text(
                text = if (amount.isEmpty()) "¥ 0" else "¥ $amount",
                color = ExpenseRed,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedCategory != null && amount.isNotEmpty()) {
                Text(
                    "推断: ${selectedCategory.displayName}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 数字键盘
            val buttons = listOf(
                listOf("1", "2", "3", "⌫"),
                listOf("4", "5", "6", "C"),
                listOf("7", "8", "9", "✓"),
                listOf(".", "0", "00", "")
            )

            buttons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { label ->
                        if (label.isEmpty()) {
                            Spacer(modifier = Modifier.size(56.dp))
                        } else {
                            MiniKey(
                                label = label,
                                onClick = {
                                    when (label) {
                                        "⌫" -> if (amount.isNotEmpty()) onAmountChange(amount.dropLast(1))
                                        "C" -> onAmountChange("")
                                        "✓" -> onConfirm()
                                        "." -> if (!amount.contains(".")) onAmountChange(amount + ".")
                                        else -> onAmountChange(amount + label)
                                    }
                                },
                                highlight = label == "✓"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 快捷类型选择
            val quickCategories = listOf(
                Category.FOOD, Category.TRAFFIC, Category.SHOPPING,
                Category.RESTAURANT, Category.PARTY, Category.OTHER
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                quickCategories.forEach { cat ->
                    val isSel = cat == selectedCategory
                    val baseColor = CategoryColorMap[cat.key] ?: ExpenseRed
                    // 悬浮面板始终为暗色底，前景提亮以保证可读性
                    val fgColor = lerp(baseColor, Color.White, 0.30f)
                    Surface(
                        modifier = Modifier.clickable { onCategorySelected(cat) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) baseColor else fgColor.copy(alpha = 0.22f)
                    ) {
                        Text(
                            cat.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (isSel) Color.White else fgColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniKey(
    label: String,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    val bg = when {
        highlight -> ExpenseRed
        label in listOf("⌫", "C") -> Color.White.copy(alpha = 0.10f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val fg = when {
        highlight -> Color.White
        label in listOf("⌫", "C") -> ExpenseRed.copy(alpha = 0.8f)
        else -> Color.White
    }

    Surface(
        modifier = Modifier
            .size(56.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = fg,
                fontSize = if (label.length > 1) 14.sp else 20.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}