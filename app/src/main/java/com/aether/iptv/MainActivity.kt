package com.aether.iptv

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextClock
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val gold = c(0xFFD4AF37)
    private val white = Color.WHITE
    private val dim = c(0x99FFFFFF)
    private val defaultUA = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    private val h = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var player: ExoPlayer
    private val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10000)
        .setReadTimeoutMs(15000)
    private lateinit var prefs: SharedPreferences

    private var all: List<Channel> = emptyList()
    private var groups: List<String> = listOf("All")
    private var counts: Map<String, Int> = emptyMap()
    private var groupSel = 0
    private var shown: List<Channel> = emptyList()
    private var cur: Channel? = null
    private var menuVisible = false
    private var typed = ""

    private lateinit var menu: FrameLayout
    private lateinit var banner: LinearLayout
    private lateinit var bLogo: ImageView
    private lateinit var bNum: TextView
    private lateinit var bName: TextView
    private lateinit var bNow: TextView
    private lateinit var bNext: TextView
    private lateinit var bar: ProgressBar
    private lateinit var statusTv: TextView
    private lateinit var numTv: TextView
    private lateinit var countTv: TextView
    private lateinit var groupsRv: RecyclerView
    private lateinit var chRv: RecyclerView
    private val groupAdapter = GroupAdapter()
    private val chAdapter = ChAdapter()

    private var groupRun = Runnable {}
    private val playRun = Runnable { cur?.let { play(it) } }
    private val hideBannerRun = Runnable {
        banner.animate().alpha(0f).setDuration(250).withEndAction { banner.visibility = View.GONE }.start()
    }
    private val jumpRun = Runnable {
        val n = typed.toIntOrNull()
        typed = ""
        numTv.visibility = View.GONE
        if (n != null) all.getOrNull(n - 1)?.let { tune(it, true) }
    }

    // ---------- helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun lp(w: Int, hh: Int, wt: Float = 0f) = LinearLayout.LayoutParams(w, hh, wt)

    private fun tv(size: Float, color: Int = white, bold: Boolean = false) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun glass(radius: Int, top: Int, bot: Int, stroke: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bot)).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun rowBg() = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_focused),
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c(0x66D4AF37), c(0x26D4AF37))).apply {
                cornerRadius = dp(14).toFloat(); setStroke(dp(2), gold)
            }
        )
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }

    // ---------- lifecycle ----------
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("aether", MODE_PRIVATE)
        buildPlayer()
        buildUi()
        loadData()
    }

    override fun onStart() {
        super.onStart()
        cur?.let { if (player.playbackState == Player.STATE_IDLE) play(it) }
    }

    override fun onStop() {
        super.onStop()
        player.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        h.removeCallbacksAndMessages(null)
        player.release()
    }

    // ---------- player ----------
    private fun buildPlayer() {
        val lc = DefaultLoadControl.Builder().setBufferDurationsMs(12000, 30000, 1500, 3000).build()
        val rf = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, rf)
            .setLoadControl(lc)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> status("Loading…")
                    Player.STATE_READY -> status(null)
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare()
                } else {
                    status("Channel unavailable  •  press ▲ ▼ to switch")
                    showBanner()
                }
            }
        })
    }

    private fun play(ch: Channel) {
        http.setUserAgent(ch.ua ?: defaultUA)
        http.setDefaultRequestProperties(ch.referrer?.let { mapOf("Referer" to it) } ?: emptyMap())
        player.setMediaItem(MediaItem.fromUri(ch.url))
        player.prepare()
        player.playWhenReady = true
    }

    private fun tune(ch: Channel, instant: Boolean = false) {
        cur = ch
        prefs.edit().putString("last", ch.url).apply()
        status(null)
        showBanner()
        h.removeCallbacks(playRun)
        if (instant) play(ch) else h.postDelayed(playRun, 350) // debounce fast zapping
    }

    private fun step(dir: Int) {
        if (shown.isEmpty()) return
        val i = cur?.let { shown.indexOf(it) } ?: -1
        val n = if (i < 0) (if (dir > 0) 0 else shown.size - 1) else (i + dir + shown.size) % shown.size
        tune(shown[n])
    }

    // ---------- data ----------
    private fun loadData() {
        status("Loading channels…")
        Thread {
            val pf = File(filesDir, "playlist.m3u")
            Net.fetch(Config.PLAYLIST, pf, Config.REFRESH_MS)
            if (!pf.exists()) {
                runOnUiThread { status("Can't load playlist. Check internet and restart.") }
                return@Thread
            }
            val list = try { M3U.parse(pf) } catch (e: Exception) { emptyList() }
            runOnUiThread { setChannels(list) }
            if (Config.EPG_URL.isNotEmpty() && list.isNotEmpty()) {
                try {
                    val ef = File(filesDir, "guide.xml")
                    Net.fetch(Config.EPG_URL, ef, Config.REFRESH_MS)
                    if (ef.exists()) {
                        val wanted = list.map { Epg.key(it.tvgId) }.filter { it.isNotEmpty() }.toSet()
                        Epg.load(ef, wanted)
                        runOnUiThread { chAdapter.notifyDataSetChanged(); if (banner.visibility == View.VISIBLE) refreshBanner() }
                    }
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun setChannels(list: List<Channel>) {
        all = list
        counts = list.groupingBy { it.group }.eachCount()
        groups = listOf("All") + counts.keys.sorted()
        groupAdapter.notifyDataSetChanged()
        applyGroup(0)
        val last = prefs.getString("last", null)
        val start = all.firstOrNull { it.url == last } ?: all.firstOrNull()
        if (start == null) { status("Playlist is empty"); return }
        tune(start, true)
    }

    private fun applyGroup(i: Int) {
        val old = groupSel
        groupSel = i
        shown = if (i == 0) all else all.filter { it.group == groups[i] }
        chAdapter.items = shown
        chAdapter.notifyDataSetChanged()
        groupAdapter.notifyItemChanged(old)
        groupAdapter.notifyItemChanged(i)
        countTv.text = "${groups[i]}  •  ${shown.size} channels"
    }

    // ---------- UI ----------
    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val pv = PlayerView(this)
        pv.useController = false
        pv.isFocusable = false
        pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        pv.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        pv.player = player
        root.addView(pv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        buildMenu()
        root.addView(menu, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        buildBanner()
        root.addView(banner, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(56), 0, dp(56), dp(40))
        })

        statusTv = tv(16f, white, true).apply {
            setPadding(dp(22), dp(10), dp(22), dp(10))
            background = glass(30, c(0xCC0B1020), c(0xAA101830), c(0x55D4AF37))
            visibility = View.GONE
        }
        root.addView(statusTv, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(32)
        })

        numTv = tv(48f, gold, true).apply {
            setPadding(dp(26), dp(12), dp(26), dp(12))
            background = glass(24, c(0xCC0B1020), c(0xAA101830), c(0x66D4AF37))
            visibility = View.GONE
        }
        root.addView(numTv, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(32), dp(48), 0)
        })
        setContentView(root)
    }

    private fun buildMenu() {
        menu = FrameLayout(this).apply {
            visibility = View.GONE
            alpha = 0f
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(c(0xF2050810), c(0xD0050810), c(0x00050810))
            )
        }
        // warm gold "light" in the top-left corner
        val glow = View(this).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(c(0x55D4AF37), c(0x00D4AF37))
                gradientRadius = dp(720).toFloat()
                setGradientCenter(0.1f, 0f)
            }
        }
        menu.addView(glow, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(24), dp(32))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(tv(26f, gold, true).apply { text = "AETHER"; letterSpacing = 0.35f })
        countTv = tv(13f, dim).apply { setPadding(0, dp(4), 0, 0) }
        titles.addView(countTv)
        head.addView(titles, lp(0, WRAP_CONTENT, 1f))
        head.addView(TextClock(this).apply {
            format12Hour = "hh:mm a"; format24Hour = "HH:mm"; textSize = 20f; setTextColor(white)
        })
        col.addView(head, lp(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(18) })

        val lists = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        groupsRv = rv(groupAdapter)
        chRv = rv(chAdapter)
        lists.addView(groupsRv, lp(dp(230), MATCH_PARENT).apply { rightMargin = dp(16) })
        lists.addView(chRv, lp(0, MATCH_PARENT, 1f))
        col.addView(lists, lp(MATCH_PARENT, 0, 1f))

        menu.addView(col, FrameLayout.LayoutParams(dp(960), MATCH_PARENT))
    }

    private fun rv(a: RecyclerView.Adapter<*>) = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(this@MainActivity)
        adapter = a
        itemAnimator = null
        setHasFixedSize(true)
        clipToPadding = false
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = glass(22, c(0x30FFFFFF), c(0x10FFFFFF), c(0x30FFFFFF))
    }

    private fun buildBanner() {
        banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(20), dp(28), dp(20))
            background = glass(28, c(0xD00B1020), c(0xA0101830), c(0x66D4AF37))
            visibility = View.GONE
            alpha = 0f
        }
        bLogo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = glass(18, c(0x28FFFFFF), c(0x10FFFFFF), c(0x22FFFFFF))
        }
        banner.addView(bLogo, lp(dp(92), dp(92)).apply { rightMargin = dp(22) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bNum = tv(26f, gold, true)
        bName = tv(26f, white, true).apply { setPadding(dp(16), 0, 0, 0) }
        line.addView(bNum); line.addView(bName, lp(0, WRAP_CONTENT, 1f))
        col.addView(line)
        bNow = tv(16f, white).apply { setPadding(0, dp(8), 0, 0) }
        bNext = tv(14f, dim).apply { setPadding(0, dp(4), 0, 0) }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(gold)
            progressBackgroundTintList = ColorStateList.valueOf(c(0x33FFFFFF))
        }
        col.addView(bNow); col.addView(bNext)
        col.addView(bar, lp(MATCH_PARENT, dp(4)).apply { topMargin = dp(10) })
        banner.addView(col, lp(0, WRAP_CONTENT, 1f))
    }

    private fun status(msg: String?) {
        if (msg == null) statusTv.visibility = View.GONE
        else { statusTv.text = msg; statusTv.visibility = View.VISIBLE }
    }

    private fun fmt(t: Long) = clock.format(Date(t))

    private fun refreshBanner() {
        val ch = cur ?: return
        Img.load(ch.logo, bLogo)
        bNum.text = ch.num.toString()
        bName.text = ch.name
        val n = Epg.now(ch.tvgId)
        if (n != null) {
            bNow.text = "NOW   ${n.title}    ${fmt(n.start)} – ${fmt(n.stop)}"
            val span = (n.stop - n.start).coerceAtLeast(1)
            bar.progress = (((System.currentTimeMillis() - n.start) * 100) / span).toInt().coerceIn(0, 100)
            bar.visibility = View.VISIBLE
            val x = Epg.next(ch.tvgId)
            bNext.text = if (x != null) "NEXT   ${x.title}    ${fmt(x.start)}" else ""
        } else {
            bNow.text = "${ch.group}  •  Live"
            bNext.text = ""
            bar.visibility = View.GONE
        }
    }

    private fun showBanner() {
        refreshBanner()
        banner.animate().cancel()
        banner.visibility = View.VISIBLE
        banner.animate().alpha(1f).setDuration(120).start()
        h.removeCallbacks(hideBannerRun)
        h.postDelayed(hideBannerRun, 5000)
    }

    private fun showMenu() {
        menuVisible = true
        h.removeCallbacks(hideBannerRun); hideBannerRun.run()
        menu.animate().cancel()
        menu.visibility = View.VISIBLE
        menu.animate().alpha(1f).setDuration(160).start()
        chAdapter.notifyDataSetChanged()
        val i = cur?.let { shown.indexOf(it) } ?: -1
        val pos = if (i < 0) 0 else i
        (chRv.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, dp(120))
        chRv.postDelayed({
            (chRv.findViewHolderForAdapterPosition(pos)?.itemView ?: chRv).requestFocus()
        }, 120)
    }

    private fun hideMenu() {
        menuVisible = false
        h.removeCallbacks(groupRun)
        menu.animate().cancel()
        menu.animate().alpha(0f).setDuration(140).withEndAction { menu.visibility = View.GONE }.start()
    }

    // ---------- remote control ----------
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e)
        val k = e.keyCode
        if (menuVisible) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU) { hideMenu(); return true }
            return super.dispatchKeyEvent(e)
        }
        when (k) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { step(+1); return true }     // next
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); return true } // previous
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU -> { showMenu(); return true }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                if (typed.length < 4) typed += (k - KeyEvent.KEYCODE_0).toString()
                numTv.text = typed
                numTv.visibility = View.VISIBLE
                h.removeCallbacks(jumpRun)
                h.postDelayed(jumpRun, 1500)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // ---------- adapters ----------
    inner class GroupVH(v: View, val name: TextView, val cnt: TextView) : RecyclerView.ViewHolder(v)
    inner class ChVH(v: View, val num: TextView, val logo: ImageView, val name: TextView, val now: TextView) : RecyclerView.ViewHolder(v)

    inner class GroupAdapter : RecyclerView.Adapter<GroupVH>() {
        override fun getItemCount() = groups.size
        override fun onCreateViewHolder(p: ViewGroup, t: Int): GroupVH {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, dp(50))
                background = rowBg()
                isFocusable = true
                isClickable = true
            }
            val name = tv(15f)
            val cnt = tv(12f, c(0x88FFFFFF))
            row.addView(name, lp(0, WRAP_CONTENT, 1f))
            row.addView(cnt, lp(WRAP_CONTENT, WRAP_CONTENT))
            return GroupVH(row, name, cnt)
        }

        override fun onBindViewHolder(hd: GroupVH, i: Int) {
            hd.name.text = groups[i]
            hd.cnt.text = (if (i == 0) all.size else counts[groups[i]] ?: 0).toString()
            hd.name.setTextColor(if (i == groupSel) gold else white)
            hd.itemView.setOnClickListener { applyGroup(i) }
            hd.itemView.setOnFocusChangeListener { _, f ->
                if (f) {
                    h.removeCallbacks(groupRun)
                    groupRun = Runnable { if (i != groupSel) applyGroup(i) }
                    h.postDelayed(groupRun, 250)
                }
            }
        }
    }

    inner class ChAdapter : RecyclerView.Adapter<ChVH>() {
        var items: List<Channel> = emptyList()
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(p: ViewGroup, t: Int): ChVH {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(6), dp(14), dp(6))
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, dp(66))
                background = rowBg()
                isFocusable = true
                isClickable = true
            }
            val num = tv(14f, dim)
            val logo = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val name = tv(17f, white, true)
            val now = tv(12f, dim).apply { setPadding(0, dp(3), 0, 0) }
            col.addView(name); col.addView(now)
            row.addView(num, lp(dp(54), WRAP_CONTENT))
            row.addView(logo, lp(dp(46), dp(46)).apply { rightMargin = dp(14) })
            row.addView(col, lp(0, WRAP_CONTENT, 1f))
            return ChVH(row, num, logo, name, now)
        }

        override fun onBindViewHolder(hd: ChVH, i: Int) {
            val ch = items[i]
            hd.num.text = ch.num.toString()
            hd.name.text = ch.name
            hd.name.setTextColor(if (ch === cur) gold else white)
            Img.load(ch.logo, hd.logo)
            hd.now.text = Epg.now(ch.tvgId)?.title ?: ch.group
            hd.itemView.setOnClickListener { tune(ch, true); hideMenu() }
        }
    }
}
