package com.teneasy.qldemo

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.teneasy.qldemo.databinding.FragmentMainBinding
import com.teneasy.chatuisdk.ChatSDKConfig
import com.teneasy.chatuisdk.TeneasyChatUISDK
import com.teneasy.chatuisdk.ui.base.AppChatTheme
import com.teneasy.chatuisdk.ui.base.Constants
import com.teneasy.chatuisdk.ui.base.GlobalMessageDelegate
import com.teneasy.chatuisdk.ui.base.GlobalMessageManager
import com.teneasy.chatuisdk.ui.base.Utils
import com.teneasy.chatuisdk.ui.main.KeFuActivity
import com.teneasy.chatuisdk.ui.main.KeFuFragment

class MainFragment : Fragment(), GlobalMessageDelegate {

    companion object {
        fun newInstance() = MainFragment()
    }

    var binding: FragmentMainBinding? = null

    private var selectedThemeIndex = 0
    private val selectedTheme get() = AppChatTheme.presets[selectedThemeIndex]
    private val swatches = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils().readConfig()
        // 模拟宿主"打开 App 即初始化"：用设置页存的参数启动全局聊天，
        // 首页期间即可实时收消息/未读数；参数未配置时 init 内部会安全跳过
        TeneasyChatUISDK.init(
            requireContext(),
            ChatSDKConfig(
                cert = Constants.cert,
                userId = Constants.userId,
                merchantId = Constants.merchantId,
                lines = Constants.lines,
                baseUrlImage = Constants.baseUrlImage,
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMainBinding.inflate(inflater, container, false)

        binding?.apply {
            buildThemeSwatches()

//            btnSend.setOnClickListener {
//                // 把首页选中的主题透传给聊天页，使两端主题一致
//                startActivity(
//                    Intent(requireContext(), KeFuActivity::class.java)
//                        .putExtra(KeFuFragment.EXTRA_THEME_INDEX, selectedThemeIndex)
//                )
//            }

            btnSend.setOnClickListener {
                // 把首页选中的主题透传给聊天页，使两端主题一致；
                // 指定 consultId 跳过咨询类型入口页直达聊天页（测试值 1）
                startActivity(
                    Intent(requireContext(), KeFuActivity::class.java)
                        .putExtra(KeFuFragment.EXTRA_THEME_INDEX, selectedThemeIndex)
                        .putExtra(KeFuActivity.EXTRA_DIRECT_CONSULT_ID, 1L)
                )
            }

            btnBackup.setOnClickListener { openBackupCustomerService() }

            ivSettings.setOnClickListener {
                findNavController().navigate(R.id.frg_settings)
            }

            tvVersionNumber.text = "Version: ${getAppVersion(requireContext()).first}"
        }

        updateThemeUI()
        return binding?.root
    }

    override fun onResume() {
        super.onResume()
        // 注册全局消息委托：进入聊天相关页面时本页已 onPause 让出槽位，时序错开不冲突
        Constants.globalMessageDelegate = this
        updateUnreadBadge()
    }

    override fun onPause() {
        super.onPause()
        if (Constants.globalMessageDelegate == this) {
            Constants.globalMessageDelegate = null
        }
    }

    /** 未读数变化回调（新消息、聊天页读完清零都会触发） */
    override fun onMessageReceived(consultId: Long) {
        activity?.runOnUiThread { updateUnreadBadge() }
    }

    // 把所有会话的未读总数画到“联系客服”按钮角标，0 时隐藏
    private fun updateUnreadBadge() {
        val badge = binding?.tvUnreadBadge ?: return
        val total = GlobalMessageManager.instance.getTotalUnReadCount()
        if (total > 0) {
            badge.text = if (total > 99) "99+" else total.toString()
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 避免视图绑定在返回栈期间持有已销毁的视图层级
        swatches.clear()
        binding = null
    }

    // 构建主题色卡（每个预设一个圆形 swatch）
    private fun buildThemeSwatches() {
        val container = binding?.themeContainer ?: return
        container.removeAllViews()
        swatches.clear()

        val swatchSize = dp(60)
        val cellSize = dp(70)

        AppChatTheme.presets.forEachIndexed { index, theme ->
            val cell = FrameLayout(requireContext())
            val cellLp = ViewGroup.MarginLayoutParams(cellSize, cellSize)
            cellLp.marginEnd = dp(15)
            cell.layoutParams = cellLp

            val swatch = View(requireContext())
            val swatchLp = FrameLayout.LayoutParams(swatchSize, swatchSize)
            swatchLp.gravity = Gravity.CENTER
            swatch.layoutParams = swatchLp
            swatch.background = circleDrawable(theme.gradientEndColor, selected = false)
            swatch.setOnClickListener { selectTheme(index) }

            cell.addView(swatch)
            container.addView(cell)
            swatches.add(swatch)
        }
    }

    // 点击色卡：切换主题（对齐 iOS themeSelected）
    private fun selectTheme(index: Int) {
        selectedThemeIndex = index
        updateThemeUI()
        binding?.tvTitle?.setBackgroundColor(selectedTheme.leftBubbleColor)
        binding?.tvTitle?.setTextColor(selectedTheme.leftBubbleTextColor)
        binding?.ivSettings?.setColorFilter(selectedTheme.tintColor)
    }

    // 刷新主屏外观（对齐 iOS updateThemeUI）
    private fun updateThemeUI() {
        val theme = selectedTheme
        applyStatusBarColor(theme)
        binding?.apply {
            main.background = GradientDrawable(
                theme.gradientOrientation,
                intArrayOf(theme.gradientStartColor, theme.gradientEndColor)
            )

            val supportBg = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(theme.tintColor)
            }
            btnSend.background = supportBg
            btnSend.backgroundTintList = null
            btnSend.setTextColor(Color.WHITE)

            val backupBg = GradientDrawable().apply {
                cornerRadius = dp(25).toFloat()
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), theme.tintColor)
            }
            btnBackup.background = backupBg
            btnBackup.backgroundTintList = null
            btnBackup.setTextColor(theme.tintColor)

            ivSettings.setColorFilter(theme.gradientEndColor)

            swatches.forEachIndexed { i, v ->
                v.background = circleDrawable(
                    AppChatTheme.presets[i].gradientEndColor,
                    selected = i == selectedThemeIndex
                )
                val scale = if (i == selectedThemeIndex) 1.15f else 1f
                v.scaleX = scale
                v.scaleY = scale
            }
        }
    }

    // 状态栏颜色跟随主题（对齐屏幕顶部渐变色），并按亮度切换图标明暗
    private fun applyStatusBarColor(theme: AppChatTheme) {
        val window = activity?.window ?: return
        val color = theme.statusBarColor
        window.statusBarColor = color
        // 浅色背景用深色图标，深色背景用浅色图标
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = ColorUtils.calculateLuminance(color) > 0.5
    }

    private fun circleDrawable(fill: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (selected) setStroke(dp(3), Color.WHITE)
        }
    }

    // 备用客服：juhekefu:// 深链，失败回退网页（对齐 iOS backupClick）
    private fun openBackupCustomerService() {
        val params = linkedMapOf(
            "cert" to Constants.cert,
            "userId" to Constants.userId.toString(),
            "merchantId" to Constants.merchantId.toString(),
            "userName" to Constants.userName,
            "platformName" to Constants.platformName,
            "userType" to Constants.userType.toString(),
            "themeIndex" to selectedThemeIndex.toString(),
        )
        if (Constants.xToken.isNotEmpty()) {
            params["xToken"] = Constants.xToken
        }

        val deepLink = Uri.Builder()
            .scheme("juhekefu")
            .authority("open")
            .apply { params.forEach { (k, v) -> appendQueryParameter(k, v) } }
            .build()

        try {
            startActivity(Intent(Intent.ACTION_VIEW, deepLink))
        } catch (e: ActivityNotFoundException) {
            openBackupWebUrl(params)
        }
    }

    private fun openBackupWebUrl(params: Map<String, String>) {
        val webUrl = Constants.backupWebUrl.trim()
        if (webUrl.isEmpty()) {
            Toast.makeText(requireContext(), "未安装客服中心 App，且未配置备用网页", Toast.LENGTH_SHORT).show()
            return
        }
        val builder = Uri.parse(webUrl).buildUpon()
        params.forEach { (k, v) -> builder.appendQueryParameter(k, v) }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, builder.build()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "打开备用网页失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun getAppVersion(context: Context): Pair<String, Int> {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = packageInfo.versionCode
            return Pair(versionName, versionCode)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return Pair("Unknown", -1)
        }
    }
}
