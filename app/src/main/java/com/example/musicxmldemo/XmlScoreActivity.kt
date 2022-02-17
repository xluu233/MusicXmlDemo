package com.example.musicxmldemo

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.musicxmldemo.databinding.ActivityXmlBinding
import com.example.musicxmldemo.util.AssertMangerUtil
import com.example.musicxmldemo.util.FilePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.dolphin_com.*
import uk.co.dolphin_com.SeeScoreView.TapNotification
import uk.co.dolphin_com.sscore.*
import uk.co.dolphin_com.sscore.RenderItem.Colour
import uk.co.dolphin_com.sscore.ex.ScoreException
import uk.co.dolphin_com.sscore.ex.XMLValidationException
import uk.co.dolphin_com.sscore.playdata.Note
import uk.co.dolphin_com.sscore.playdata.PlayData
import uk.co.dolphin_com.sscore.playdata.PlayData.PlayControls
import uk.co.dolphin_com.sscore.playdata.UserTempo
import java.io.*
import java.text.DecimalFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt


class XmlScoreActivity : FragmentActivity(), ScoreViewOptions {
    private val TAG = "XmlScoreActivity"

    private lateinit var binding: ActivityXmlBinding
    private lateinit var ssview: SeeScoreView
    private lateinit var currentScore: SScore

    @Volatile
    private var player: Player? = null

    //是否在执行播放
    private var isInPlay = false

    //当前行
    private var currentSystemIndex = 0

    //当前小节
    private var currentBar = 0

    //分段练习
    private var customLoopStart = -1
    private var customLoopEnd = -1

    //set to show a single part
    private var isShowingSinglePart = false
    private var singlePart = 0

    //速度seekbar百分比，通过tempoSliderPercentToBPM(tempoSliderValPercent)计算实际速度
    private var tempoSliderValPercent: Int = -1

    //xml文件
    private lateinit var xmlFile: File

    //midi音频文件
    private val midiFile: File by lazy {
        File(FilePath.getAppCachePath(this), "midiFile.mid")
    }

    /**
     * TODO 设置左右手
     * 左手：下面一行
     * 右手：上行
     */
    enum class LRHand {
        L, R, LR
    }

    private var lrHand: LRHand = LRHand.LR

    override fun getLayoutOptions(): LayoutOptions {
        val hidePartNames = false
        val hideBarNumbers = false
        val simplifyHarmonyEnharmonicSpelling = false
        val ignoreXmlPositions = true
        val useXMLxLayout = false
        val heedXMLSystemBreaks = false
        return LayoutOptions(
            hidePartNames,
            hideBarNumbers,
            simplifyHarmonyEnharmonicSpelling,
            ignoreXmlPositions,
            useXMLxLayout,
            heedXMLSystemBreaks
        )
    }

    private val cursorScrollView by lazy {
        binding.scrollViewCursor
    }
    private val scrollView1: ScrollView by lazy {
        binding.scrollView1
    }

    //加载so库
    init {
        System.loadLibrary("SeeScoreLib")
    }

    companion object {
        private const val PlayUsingMediaPlayer = true
        private const val UseNoteCursorIfPossible = true // else bar cursor
        private const val ColourPlayedNotes = true

        //Player速度
        private const val kMinTempoBPM = 30
        private const val kMaxTempoBPM = 240
        private const val kDefaultTempoBPM = 80

        private const val kMinTempoScaling = 0.5
        private const val kMaxTempoScaling = 2.0
        private const val kDefaultTempoScaling = 1.0

        //缩放倍率
        private const val kMinZoom = 0.4f
        private const val kMaxZoom = 2.0f

        //当前缩放倍率
        private var magnification = 0.8f

        //分段练习循环次数
        private const val kPlayLoopRepeats = 1

        //播放时是否滚动bar
        private const val needScrollBar = false

        private val Red = Colour(255f, 0f, 0f, 1f)
        private val Purple = Colour(160f, 32f, 240f, 1f)
        private val Blue = Colour(30f, 144f, 255f, 1f)
        private val Green = Colour(144f, 238f, 144f, 1f)
        private val Black = Colour(0f, 0f, 0f, 1f)

        //左右手設置
        @Volatile
        private var playingLeft = true

        @Volatile
        private var playingRight = true
    }

    val Int.sp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this.toFloat(),
            resources.displayMetrics
        )
    val Float.dp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this,
            resources.displayMetrics
        )
    val Int.dp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityXmlBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        initView()
        initData()
    }

    private fun initData() {
        copyFile()

        //文件加载完之后开始显示
        currentScore = loadFile(xmlFile)!!
        binding.zoom.zoomText.text = magnification.toString()

        //显示曲谱
        showScore(score = currentScore, zoom = magnification)
    }

    private fun initView() {

        val cursorView = CursorView(this@XmlScoreActivity) {
            scrollView1.scrollY.toFloat()
        }

        val zoomNotification = object : SeeScoreView.ZoomNotification {
            override fun zoom(scale: Float) {
                //showZoom(scale)
            }
        }
        val tapNotification = object : TapNotification {
            @SuppressLint("SyntheticAccessor")
            override fun tap(
                systemIndex: Int,
                partIndex: Int,
                barIndex: Int,
                components: Array<Component>
            ) {
                //在这里设置点击事件
                println("tap_system:$systemIndex bar:$barIndex")

                currentBar = barIndex
                currentSystemIndex = systemIndex

                val isPlaying = player?.state == Player.State.Started
                val isPaused = player?.state == Player.State.Paused

                ssview.setCursorAtBar(barIndex, SeeScoreView.CursorType.box, 200)
            }

            override fun longTap(
                systemIndex: Int,
                partIndex: Int,
                barIndex: Int,
                components: Array<Component>
            ) {
                stopPlay()
            }
        }
        ssview = SeeScoreView(this, this, cursorView, assets, zoomNotification, tapNotification)
        setMargins(ssview, 0, 55.dp.toInt(), 0, 0)
        setMargins(cursorView, 0, 55.dp.toInt(), 0, 0)

        scrollView1.addView(ssview)
        cursorScrollView.addView(cursorView)
        cursorScrollView.setOnTouchListener { v, event ->
            scrollView1.dispatchTouchEvent(event)
        }
        cursorScrollView.viewTreeObserver.addOnGlobalLayoutListener {
            cursorView.measure(
                cursorScrollView.width,
                cursorScrollView.height
            )
        }


        binding.playBtn.setOnClickListener {
            if (!checkPlayer()) {
                setupPlayer()
                return@setOnClickListener
            }
            //设置图标
            if (isInPlay) {
                binding.playBtn.background =
                    AppCompatResources.getDrawable(this, R.drawable.player_start)
                //it.setBackgroundResource(R.drawable.player_start)
            } else {
                binding.playBtn.background =
                    AppCompatResources.getDrawable(this, R.drawable.player_stop)
                //it.setBackgroundResource(R.drawable.player_stop)
            }
            //播放逻辑
            initPlay()
        }

    }

    private fun initPlay() = lifecycleScope.launch(Dispatchers.Main) {
        if (!checkPlayer()) return@launch
        when (player?.state) {
            Player.State.NotStarted -> {
                Log.d(TAG, "Player.State.NotStarted")
                clearAllColour()
                startPlay()
            }
            Player.State.Started -> {
                Log.d(TAG, "Player.State.Started")
                player?.pause()
                isInPlay = false
                currentBar = player!!.currentBar()
                //在这里仅暂停，不调用stopPlay()
            }
            Player.State.Paused -> {
                Log.d(TAG, "Player.State.Paused")
                //在loop模式下，resume并没有作用，currentBar不会改变，都会重新从customStartBar开始
                startPlay()
            }
            Player.State.Stopped, Player.State.Completed -> {
                Log.d(TAG, "Player.State.Stopped")
                player?.reset()
                clearAllColour()
                startPlay()

                if (currentBar == currentScore.numBars() - 1) {
                    currentBar = max(0, customLoopStart)
                }
            }
        }
    }

    //检查player是否初始化
    private fun checkPlayer(): Boolean {
        return player != null && midiFile.exists()
    }

    private suspend fun startPlay() = withContext(Dispatchers.Main) {
        isInPlay = true
        ssview.clearCursorAtBar(currentBar)
        ssview.scrollToBar(currentBar, 200)
        player?.startAt(currentBar, false)
    }

    private fun stopPlay() = lifecycleScope.launch(Dispatchers.Main) {
        isInPlay = false
        binding.playBtn.background =
            AppCompatResources.getDrawable(this@XmlScoreActivity, R.drawable.player_start)

        //消除所有渲染
        if (ColourPlayedNotes) {
            ssview.clearAllColouring()
        }
        ssview.clearCursorAtBar(currentBar, 200)
        currentBar = max(0, this@XmlScoreActivity.customLoopStart)
        player?.state = Player.State.NotStarted
    }


    /**
     * TODO 初始化播放器，终极递归
     * @param play 是否播放
     * @param evaluation 是否评测
     */
    private fun setupPlayer() {
        player?.reset()
        player = null

        val playControl = object : PlayControls {
            override fun getPartEnabled(partIndex: Int): Boolean {
                return if (isShowingSinglePart) partIndex == singlePart // play single part if showing single part
                else true
            }

            //左右手
            override fun getPartStaffEnabled(partIndex: Int, staffIndex: Int): Boolean {
                return if (staffIndex == 0) playingRight else playingLeft
            }

            override fun getPartMIDIInstrument(partIndex: Int): Int {
                return 0 // 0 = use default. Return eg 41 for violin (see MIDI spec for standard program change values)
            }

            //节拍器设置
            override fun getMetronomeEnabled(): Boolean {
                return false
            }

            override fun getMidiKeyForMetronome(): Int {
                // defines voice of metronome - see MIDI spec "Appendix 1.5 - General MIDI Percussion Key Map"
                return 0 // use default voice
            }

            override fun getPartVolume(partIndex: Int): Float {
                return if (metronomeEnabled) 0.5f else 1.0f // reduce volume of all parts if metronome is enabled
            }

            override fun getMetronomeVolume(): Float {
                return 1f
            }
        }
        val pl = Player(
            score = currentScore,
            userTempo = UserTempoImpl(),
            playNotes = PlayUsingMediaPlayer,
            playControls = playControl,
            startLoopBarIndex = customLoopStart,
            endLoopBarIndex = customLoopEnd,
            numRepeats = if (customLoopStart >= 0 && customLoopEnd >= 0) kPlayLoopRepeats else 0,
            midi = midiFile
        )

        val autoScrollAnimationTime = pl.bestScrollAnimationTime()
        pl.setBarStartHandler(object : Dispatcher.EventHandler {
            private var lastIndex = -1
            override fun event(index: Int, ci: Boolean) {
                // use bar cursor if bar time is short
                val useNoteCursor = UseNoteCursorIfPossible && !pl.needsFastCursor()
                if (!useNoteCursor || ColourPlayedNotes) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        // use bar cursor
                        if (!useNoteCursor) {
                            // pm not need cursor
                            //ssview.setCursorAtBar(index, SeeScoreView.CursorType.box, autoScrollAnimationTime)
                        }
                        scrollSysView(index)

                        if (ColourPlayedNotes) { // if this is a repeat section we clear the colouring from the previous repeat
                            //处理反复符号
                            val startRepeat = index < lastIndex
                            if (startRepeat) {
                                ssview.clearColouringForBarRange(
                                    index,
                                    currentScore.numBars() - index
                                )
                            }
                        }
                        lastIndex = index
                    }
                }
            }
        }, 0)
        // anticipate so cursor arrives on time
        if (UseNoteCursorIfPossible || ColourPlayedNotes) {
            pl.setNoteHandler(object : Dispatcher.NoteEventHandler {
                override fun startNotes(notes: MutableList<Note>?) {
                    if (notes.isNullOrEmpty()) return

                    // disable note cursor if bar time is short
                    val useNoteCursor = !pl.needsFastCursor()
                    if (useNoteCursor && needScrollBar) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            ssview.moveNoteCursor(notes, autoScrollAnimationTime)
                        }
                    }

                    //渲染note颜色
                    if (ColourPlayedNotes) {
                        for (note in notes) {
                            colorNote(note, Blue)
                        }
                    }
                }
            }, 0)
        }
        //播放完成
        pl.setEndHandler(object : Dispatcher.EventHandler {
            override fun event(index: Int, countIn: Boolean) {
                stopPlay()
            }
        }, 0)
        player = pl

    }


    private fun setMargins(v: View?, l: Int, t: Int, r: Int, b: Int) {
        val layoutParams = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutParams.setMargins(l, t, r, b) //4个参数按顺序分别是左上右下
        v?.layoutParams = layoutParams
    }


    /*--------------score UI相关------------*/

    //重置UI
    private suspend fun reSetUI() = withContext(Dispatchers.Main) {
        currentBar = 0
        ssview.clearCursorAtBar(currentBar, 200)
        clearLoop()
        clearAllColour()
    }

    //清除分段练习
    private fun clearLoop() {
        customLoopEnd = -1
        customLoopStart = customLoopEnd
        ssview.hideLoopGraphics()
    }

    //清除所有音符渲染
    private suspend fun clearAllColour() = withContext(Dispatchers.Main) {
        ssview.clearAllColouring()
    }


    /**
     * TODO 根据systemview index换行
     * @param index
     */
    @Synchronized
    private fun scrollSysView(index: Int) {
        if (index in 0 until ssview.views.size) {
            val startBar = ssview.views[index].system.barRange.startBarIndex
            ssview.scrollToBar(startBar, 200, -30.dp.toInt())
        }
    }

    /**
     * TODO 改变Note音符颜色
     * @param note
     * @param color
     */
    private fun colorNote(note: Note, color: Colour) = lifecycleScope.launch(Dispatchers.Main) {
        when (lrHand) {
            LRHand.LR -> {
                ssview.colourItem(note.partIndex, note.startBarIndex, note.item_h, color, false)
            }
            LRHand.L -> {
                if (note.staffindex == 1) {
                    ssview.colourItem(note.partIndex, note.startBarIndex, note.item_h, color, false)
                }
            }
            LRHand.R -> {
                if (note.staffindex == 0) {
                    ssview.colourItem(note.partIndex, note.startBarIndex, note.item_h, color, false)
                }
            }
        }
    }


    /*--------------曲谱速率相关------------*/

    private fun tempoSliderPercentToScaling(percent: Int): Double {
        return kMinTempoScaling + percent / 100.0 * (kMaxTempoScaling - kMinTempoScaling)
    }

    private fun tempoSliderPercentToBPM(percent: Int): Int {
        return kMinTempoBPM + (percent / 100.0 * (kMaxTempoBPM - kMinTempoBPM)).toInt()
    }

    private fun updateBmp(round: Boolean) {
        if (::currentScore.isInitialized) {
            if (currentScore.hasDefinedTempo()) {
                val scaling = tempoSliderPercentToScaling(tempoSliderValPercent)
                try {
                    val tempo = currentScore.tempoAtStart()
                    // 傻逼产品说这里速度差了1，所以手动给他加上去
                    if (round) {
                        setTempoText((scaling * tempo.bpm + 0.5).roundToInt())
                    } else {
                        setTempoText((scaling * tempo.bpm + 0.5).toInt())
                    }
                } catch (ex: ScoreException) {
                }
            } else {
                val bpm = tempoSliderPercentToBPM(tempoSliderValPercent)
                setTempoText(bpm)
            }
        }
    }

    private fun setTempoText(bmp: Int) {
        //设置速度TextView
        //binding.toolbar.speedSetting.text = "速度:${bmp}"
    }

    private fun setTempo(bpm: Int) {
        tempoSliderValPercent = bpmToTempoSliderPercent(bpm)
    }

    private fun bpmToTempoSliderPercent(bpm: Int): Int {
        return (100.0 * (bpm - kMinTempoBPM) / (kMaxTempoBPM - kMinTempoBPM).toDouble()).toInt()
    }

    private fun setTempoScaling(tempoScaling: Double, nominalBPM: Int) {
        tempoSliderValPercent = scalingToTempoSliderPercent(tempoScaling)
        //val bmp = scalingToBPM(tempoScaling, nominalBPM)
    }

    private fun scalingToTempoSliderPercent(scaling: Double): Int {
        return (0.5 + 100 * ((scaling - kMinTempoScaling) / (kMaxTempoScaling - kMinTempoScaling))).toInt()
    }

    private fun scalingToBPM(scaling: Double, nominalBPM: Int): Int {
        return (nominalBPM * scaling).toInt()
    }

    /**
     * an implementation of the UserTempo interface used by the [PlayData]
     * to get a current user-defined tempo, or scaling for the score embedded tempo values
     * These read the position of the tempo slider and convert that to a suitable tempo value
     */
    private inner class UserTempoImpl : UserTempo {
        /**
         * @return the user-defined tempo BPM (if not defined by the score)
         */
        override fun getUserTempo(): Int {
            return tempoSliderPercentToBPM(tempoSliderValPercent)
        }

        /**
         * @return the user-defined tempo scaling for score embedded tempo values (ie 1.0 => use standard tempo)
         */
        override fun getUserTempoScaling(): Float {
            var scaling = tempoSliderPercentToScaling(tempoSliderValPercent)
            if (abs(scaling - 1.0) < 0.05) scaling =
                1.0 // ensure we can get an exact scaling of 1 (despite rounding error with integer percent)
            return scaling.toFloat()
        }
    }

    private fun loadFile(file: File): SScore? {
        val sscore: SScore? = when {
            file.name.endsWith(".mxl") -> {
                loadMXLFile(file)
            }
            file.name.endsWith(".xml") -> {
                loadXMLFile(file)
            }
            else -> null
        }
        if (sscore == null) {
            //错误
            finishWithError()
        }
        return sscore
    }

    /**
     * TODO 发生错误退出
     */
    private fun finishWithError() {
        Log.d(TAG, "finishWithError")
        finish()
    }


    private fun copyFile() {
        xmlFile = AssertMangerUtil.loadAssetsToCache(this, "三复调乐曲-2赋格三声部《平均律钢琴曲集》第一册No.6(巴赫).xml")
        Log.d(TAG, "copyFile: ${xmlFile.absolutePath}")
    }

    private fun loadMXLFile(file: File): SScore? {
        if (!file.name.endsWith(".mxl")) return null
        val `is`: InputStream
        try {
            `is` = FileInputStream(file)
            var zis: ZipInputStream? = null
            try {
                zis = ZipInputStream(BufferedInputStream(`is`))
                var ze: ZipEntry
                while (zis.nextEntry.also { ze = it } != null) {
                    if (!ze.name.startsWith("META-INF") // ignore META-INF/ and container.xml
                        && ze.name !== "container.xml"
                    ) {
                        // read from Zip into buffer and copy into ByteArrayOutputStream which is converted to byte array of whole file
                        val os = ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        var count: Int
                        while (zis.read(buffer).also { count = it } != -1) { // load in 1K chunks
                            os.write(buffer, 0, count)
                        }
                        try {
                            val loadOptions = LoadOptions(LicenceKeyInstance.SeeScoreLibKey, true)
                            return SScore.loadXMLData(os.toByteArray(), loadOptions)
                        } catch (e: XMLValidationException) {
                            Log.w(
                                "sscore",
                                "loadfile <" + file + "> xml validation error: " + e.message
                            )
                        } catch (e: ScoreException) {
                            Log.w("sscore", "loadfile <$file> error:$e")
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                zis?.close()
            }
        } catch (e1: FileNotFoundException) {
            e1.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun loadXMLFile(file: File): SScore? {
        if (!file.name.endsWith(".xml")) return null
        try {
            val loadOptions = LoadOptions(LicenceKeyInstance.SeeScoreLibKey, true)
            return SScore.loadXMLFile(file, loadOptions)
        } catch (e: XMLValidationException) {
            Log.w("sscore", "loadfile <" + file + "> xml validation error: " + e.message)
        } catch (e: ScoreException) {
            Log.w("sscore", "loadfile <$file> error:$e")
        }
        return null
    }

    /**
     * TODO 初始化播放速度Tempo
     * @param score
     */
    private fun setupTempoUI(score: SScore) {
        Log.d(TAG, "setupTempoUI")
        when {
            //曲谱默认速度
            score.hasDefinedTempo() -> {
                try {
                    val tempo = score.tempoAtStart()
                    setTempoScaling(kDefaultTempoScaling, tempo.bpm)
                } catch (ex: ScoreException) {
                }
            }
            //如果xml中没有默认速度，则设定一个值
            else -> {
                setTempo(kDefaultTempoBPM)
            }
        }
    }

    /**
     * TODO 显示曲谱
     */
    private fun showScore(score: SScore?, zoom: Float) = lifecycleScope.launch(Dispatchers.Main) {
        if (score == null) return@launch

        val parts = mutableListOf<Boolean>()
        if (isShowingSinglePart) {
            for (i in 0 until score.numParts()) parts.add(i == singlePart)
        }
        ssview.setScore(score, parts, zoom)
        setupTempoUI(score)
        setupPlayer()

    }

    /**
     * TODO 设置缩放比例
     */
    fun setZoom(view: View) {
        when (view.id) {
            R.id.zoom_less -> {
                //缩小
                if (magnification <= kMinZoom) return
                magnification -= 0.1f
            }
            R.id.zoom_add -> {
                //放大
                if (magnification >= kMaxZoom) return
                magnification += 0.1f
            }
        }
        val decimalFormat = DecimalFormat("0.0")
        magnification = decimalFormat.format(magnification).toFloat()

        if (magnification in kMinZoom..kMaxZoom) {
            binding.zoom.zoomText.text = magnification.toString()
            ssview.zoom(magnification)
        }
    }

}