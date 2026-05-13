package ai.findnextstep.budget.ui.service

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * 系统快捷开关，用于快速开启/关闭悬浮记账。
 * 在通知栏快捷设置中长按编辑可添加此磁贴。
 */
class FloatingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        val enabled = FloatingExpenseService.isEnabled(this)
        if (enabled) {
            // 关闭悬浮窗
            FloatingExpenseService.stop(this)
            updateTile()
        } else {
            // 检查悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(this)
            ) {
                // 无权限，引导用户开启
                showToast("请先授予「显示在其他应用上层」权限")
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapse(intent)
            } else {
                // 有权限，开启悬浮窗，直接展开记账面板
                FloatingExpenseService.start(this, startExpanded = true)
                updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    private fun updateTile() {
        val enabled = FloatingExpenseService.isEnabled(this)
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(android.R.string.unknownName).let {
                // 使用应用名作为标签
                if (it == "unknown") "悬浮记账" else it
            }
            // 设置磁贴的副标题
            subtitle = if (enabled) "已开启" else "已关闭"
            updateTile()
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
