package com.roundesk.sdk.activity

import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.Gson
import com.roundesk.sdk.R
import com.roundesk_stee_sdk.util.LogUtil
import de.tavendo.autobahn.WebSocket
import io.antmedia.webrtcandroidframework.ConferenceManager
import io.antmedia.webrtcandroidframework.IDataChannelObserver
import io.antmedia.webrtcandroidframework.IWebRTCListener
import io.antmedia.webrtcandroidframework.StreamInfo
import io.antmedia.webrtcandroidframework.apprtc.CallActivity
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.SurfaceViewRenderer
import java.nio.charset.StandardCharsets
import java.util.*

class VideoCallActivityNew : AppCompatActivity(), View.OnClickListener, IWebRTCListener,    IDataChannelObserver  {

    companion object {
        val TAG: String = VideoCallActivityNew::class.java.simpleName
        private val SERVER_ADDRESS: String = "stee-dev.roundesk.io:5080"
        private val SERVER_URL = "ws://$SERVER_ADDRESS/WebRTCAppEE/websocket"
    }


    private var mCamera: Camera? = null
    private var mRoomId: Int = 0
    private var mMeetingId: Int = 0
    private var mStreamId: String? = null
    private var mReceiver_stream_id: String? = null
    private var activityName: String? = null

    private var conferenceManager: ConferenceManager? = null

    private lateinit var publishViewRenderer: SurfaceViewRenderer
    private var play_view_renderer1: SurfaceViewRenderer? = null
    private var imgCallEnd: ImageView? = null
    private var imgCamera: ImageView? = null
    private var imgVideo: ImageView? = null
    private var imgAudio: ImageView? = null
    private var imgArrowUp: ImageView? = null
    private var imgBack: ImageView? = null
    private var switchView: View? = null
    private var relLayToolbar: RelativeLayout? = null
    private var relLayoutMain: RelativeLayout? = null
    private var relLayoutSurfaceViews: RelativeLayout? = null
    private var chronometer: Chronometer? = null

    private lateinit var layoutBottomSheet: CardView
    lateinit var sheetBehavior: BottomSheetBehavior<View>

    private var enableVideo: Boolean = true
    private var enableAudio: Boolean = true
    private var isCallerSmall: Boolean = false
    private var isPictureInPictureMode: Boolean = false
    var counter = 0

    @RequiresApi(Build.VERSION_CODES.O)
    private var pictureInPictureParamsBuilder = PictureInPictureParams.Builder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_video_call_new)
        getIntentData()
        initView()
    }

    private fun getIntentData() {
        val extras = intent.extras
        if (extras != null) {
            activityName = extras.getString("activity")
            mRoomId = extras.getInt("room_id")
            mMeetingId = extras.getInt("meeting_id")
            mStreamId = extras.getString("stream_id")
            mReceiver_stream_id = extras.getString("receiver_stream_id")
        }
        LogUtil.e(
            TAG,
            "activity : $activityName"
                    + " room_id : $mRoomId"
                    + " meeting_id : $mMeetingId "
                    + "stream_id : $mStreamId"
                    + "receiver_stream_id : $mReceiver_stream_id"
        )
    }

    private fun initView() {
        publishViewRenderer = findViewById(R.id.publish_view_renderer)
        play_view_renderer1 = findViewById(R.id.play_view_renderer1)
        val playViewRenderers = ArrayList<SurfaceViewRenderer>()

        layoutBottomSheet = findViewById(R.id.bottomSheet)
        sheetBehavior = BottomSheetBehavior.from(layoutBottomSheet)
        imgCallEnd = findViewById(R.id.imgCallEnd)
        imgCamera = findViewById(R.id.imgCamera)
        imgVideo = findViewById(R.id.imgVideo)
        imgAudio = findViewById(R.id.imgAudio)
        imgArrowUp = findViewById(R.id.imgArrowUp)
        imgBack = findViewById(R.id.imgBack)
        switchView = findViewById(R.id.switchView)
        relLayToolbar = findViewById(R.id.relLayToolbar)
        relLayoutMain = findViewById(R.id.relLayoutMain)
        relLayoutSurfaceViews = findViewById(R.id.relLayoutSurfaceViews)
        chronometer = findViewById(R.id.chronometer)

        playViewRenderers.add(findViewById(R.id.play_view_renderer1))

        setListeners()

        sheetBehaviour()
        checkPermissions()

        conferenceDetails(publishViewRenderer, playViewRenderers)

        joinConference()
    }

    private fun conferenceDetails(
        publishViewRenderer: SurfaceViewRenderer,
        playViewRenderers: ArrayList<SurfaceViewRenderer>
    ) {
        this.intent.putExtra(CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, true)
//          this.getIntent().putExtra(CallActivity.EXTRA_VIDEO_CALL, false);
        conferenceManager = ConferenceManager(
            this,
            this,
            intent,
            SERVER_URL,
            mRoomId.toString(),
            publishViewRenderer,
            playViewRenderers,
            mStreamId,
//            mReceiver_stream_id,
            null
        )

        conferenceManager?.setPlayOnlyMode(false)
        conferenceManager?.setOpenFrontCamera(true)
    }

    private fun setListeners() {
        imgCallEnd?.setOnClickListener(this)
        imgCamera?.setOnClickListener(this)
        imgVideo?.setOnClickListener(this)
        imgAudio?.setOnClickListener(this)
        imgArrowUp?.setOnClickListener(this)
        imgBack?.setOnClickListener(this)
        switchView?.setOnClickListener(this)
    }

    private fun checkPermissions() {
        // Check for mandatory permissions.
        for (permission in CallActivity.MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission $permission is not granted", Toast.LENGTH_SHORT)
                    .show()
                return
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.imgCallEnd -> {
                conferenceManager?.leaveFromConference()
                finish()
            }

            R.id.imgCamera -> {
            }

            R.id.imgAudio -> {
                controlAudio()
            }

            R.id.imgVideo -> {
                controlVideo()
            }

            R.id.imgArrowUp -> {
                toggleBottomSheet()
            }

            R.id.imgBack -> {
                val aspectRatio = Rational(relLayoutMain!!.getWidth(), relLayoutMain!!.getHeight())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    pictureInPictureParamsBuilder.setAspectRatio(aspectRatio).build()
                    enterPictureInPictureMode(pictureInPictureParamsBuilder.build())
                }
//                enterPictureInPictureMode()
            }

            R.id.switchView -> {
                isCallerSmall = !isCallerSmall
                switchLayout(isCallerSmall)
            }
        }
    }

    private fun joinConference() {
        if (conferenceManager?.isJoined == false) {
            Log.w(javaClass.simpleName, "Joining Conference")
            conferenceManager?.joinTheConference()
        } else {
            conferenceManager?.leaveFromConference()
        }
    }

    private fun controlAudio() {
        if (conferenceManager!!.isPublisherAudioOn) {
            if (conferenceManager != null) {
                runOnUiThread {
                    conferenceManager!!.disableAudio()
                }
            }
            imgAudio?.setImageResource(R.drawable.ic_audio_mute)
        } else {
            if (conferenceManager != null) {
                runOnUiThread {
                    conferenceManager!!.enableAudio()
                }
            }
            imgAudio?.setImageResource(R.drawable.ic_audio)
        }
    }

    private fun controlVideo() {
        if (conferenceManager!!.isPublisherVideoOn) {
            if (conferenceManager != null) {
                runOnUiThread {
                    conferenceManager!!.disableVideo()
                }
            }
            imgVideo?.setImageResource(R.drawable.ic_video_mute)
        } else {
            if (conferenceManager != null) {
                runOnUiThread {
                    conferenceManager!!.enableVideo()
                }
            }
            imgVideo?.setImageResource(R.drawable.ic_video)
        }
    }

    private fun sheetBehaviour() {
        sheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(@NonNull bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        imgArrowUp?.rotation = 180F
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        imgArrowUp?.rotation = 0F
                    }
                    BottomSheetBehavior.STATE_DRAGGING -> {
                    }
                    BottomSheetBehavior.STATE_SETTLING -> {
                    }
                }
            }

            override fun onSlide(@NonNull bottomSheet: View, slideOffset: Float) {
            }
        })
    }

    private fun toggleBottomSheet() {
        if (sheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
            sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
            imgArrowUp?.rotation = 180F
        } else {
            sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
            imgArrowUp?.rotation = 0F
        }
    }

    override fun onDisconnected(streamId: String?) {
        LogUtil.e(TAG, "onDisconnected streamId: $streamId")
    }

    override fun onPublishFinished(streamId: String?) {
        LogUtil.e(TAG, "onPublishFinished streamId: $streamId")
    }

    override fun onPlayFinished(streamId: String?) {
        LogUtil.e(TAG, "onPlayFinished streamId: $streamId")
    }

    override fun onPublishStarted(streamId: String?) {
        LogUtil.e(TAG, "onPublishStarted streamId: $streamId")
        startCallDurationTimer()
    }

    override fun onPlayStarted(streamId: String?) {
        LogUtil.e(TAG, "onPlayStarted streamId: $streamId")
    }

    override fun noStreamExistsToPlay(streamId: String?) {
        LogUtil.e(TAG, "noStreamExistsToPlay streamId: $streamId")
    }

    override fun onError(description: String?, streamId: String?) {
        LogUtil.e(TAG, "onError streamId: $streamId description : $description")
    }

    override fun onSignalChannelClosed(
        code: WebSocket.WebSocketConnectionObserver.WebSocketCloseNotification?,
        streamId: String?
    ) {
        LogUtil.e(TAG, "onSignalChannelClosed streamId: $streamId code : ${Gson().toJson(code)}")
    }

    override fun streamIdInUse(streamId: String?) {
        LogUtil.e(TAG, "streamIdInUse streamId: $streamId")
    }

    override fun onIceConnected(streamId: String?) {
        LogUtil.e(TAG, "onIceConnected streamId: $streamId")
    }

    override fun onIceDisconnected(streamId: String?) {
        LogUtil.e(TAG, "onIceDisconnected streamId: $streamId")
    }

    override fun onTrackList(tracks: Array<out String>?) {
        LogUtil.e(TAG, "onTrackList tracks: ${Gson().toJson(tracks)}")
    }

    override fun onBitrateMeasurement(
        streamId: String?,
        targetBitrate: Int,
        videoBitrate: Int,
        audioBitrate: Int
    ) {
        LogUtil.e(
            TAG, "streamId : $streamId"
                    + "targetBitrate $targetBitrate"
                    + "videoBitrate $videoBitrate"
                    + "audioBitrate $audioBitrate"
        )
    }

    override fun onStreamInfoList(streamId: String?, streamInfoList: ArrayList<StreamInfo>?) {
        LogUtil.e(
            TAG,
            "onStreamInfoList streamId: $streamId streamInfoList: ${Gson().toJson(streamInfoList)}"
        )
    }

    override fun onBufferedAmountChange(previousAmount: Long, dataChannelLabel: String?) {
    }

    override fun onStateChange(state: DataChannel.State?, dataChannelLabel: String?) {
    }

    override fun onMessage(buffer: DataChannel.Buffer?, dataChannelLabel: String?) {
        val data = buffer!!.data
        val strDataJson = String(data.array(), StandardCharsets.UTF_8)

        try {
            val json = JSONObject(strDataJson)
            val eventType = json.getString("eventType")
            val streamId = json.getString("streamId")
            Toast.makeText(this, "$eventType : $streamId", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(javaClass.simpleName, e.message!!)
        }
    }

    override fun onMessageSent(buffer: DataChannel.Buffer?, successful: Boolean) {
        val data = buffer!!.data
        val strDataJson = String(data.array(), StandardCharsets.UTF_8)

        Log.e(javaClass.simpleName, "SentEvent: $strDataJson")
    }

    private fun startTimeCounter() {
        /* val countTime: TextView = findViewById(R.id.txtTimer)
         object : CountDownTimer(50000, 1000) {
             override fun onTick(millisUntilFinished: Long) {
                 countTime.text = counter.toString()
                 counter++
             }
             override fun onFinish() {
                 countTime.text = "Finished"
             }
         }.start()*/

        val duration: Long = 81200000 //6 hours

        object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                var millisUntilFinished = millisUntilFinished
                val secondsInMilli: Long = 1000
                val minutesInMilli = secondsInMilli * 60
                val hoursInMilli = minutesInMilli * 60
                val elapsedHours = millisUntilFinished / hoursInMilli
                millisUntilFinished = millisUntilFinished % hoursInMilli
                val elapsedMinutes = millisUntilFinished / minutesInMilli
                millisUntilFinished = millisUntilFinished % minutesInMilli
                val elapsedSeconds = millisUntilFinished / secondsInMilli
                val yy =
                    String.format("%02d:%02d", /*elapsedHours,*/ elapsedMinutes, elapsedSeconds)
            }

            override fun onFinish() {
            }
        }.start()
    }

    private fun updateDisplay() {
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                val c = Calendar.getInstance()

                var mHour = c[Calendar.HOUR_OF_DAY]
                var mMinute = c[Calendar.MINUTE]
                var mSecond = c[Calendar.SECOND]

                /*runOnUiThread {
                    txtTimer?.setText(
                        StringBuilder()
                            .append(mHour).append(":")
                            .append(mMinute).append(":").append(mSecond)
                    )
                }*/

            }
        }, 0, 1000) //Update text every second
    }

    private fun switchLayout(isCallerSmall: Boolean) {
        if (isCallerSmall) {
            val paramsReceiver: RelativeLayout.LayoutParams =
                play_view_renderer1?.getLayoutParams() as RelativeLayout.LayoutParams
            paramsReceiver.height = FrameLayout.LayoutParams.MATCH_PARENT
            paramsReceiver.width = FrameLayout.LayoutParams.MATCH_PARENT
            paramsReceiver.marginEnd = 0
            paramsReceiver.topMargin = 0
            play_view_renderer1?.layoutParams = paramsReceiver

            val paramsCaller: RelativeLayout.LayoutParams =
                publishViewRenderer.getLayoutParams() as RelativeLayout.LayoutParams
            paramsCaller.height = 510
            paramsCaller.width = 432
            paramsCaller.marginEnd = 48
            paramsCaller.topMargin = 48
            paramsCaller.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

//            paramsCaller.gravity = Gravity.TOP or Gravity.END
            publishViewRenderer.layoutParams = paramsCaller
            publishViewRenderer.elevation = 2F

        } else {
            val paramsCaller: RelativeLayout.LayoutParams =
                publishViewRenderer.getLayoutParams() as RelativeLayout.LayoutParams
            paramsCaller.height = FrameLayout.LayoutParams.MATCH_PARENT
            paramsCaller.width = FrameLayout.LayoutParams.MATCH_PARENT
            paramsCaller.marginEnd = 0
            paramsCaller.topMargin = 0
            publishViewRenderer.layoutParams = paramsCaller


            val paramsReceiver: RelativeLayout.LayoutParams =
                play_view_renderer1?.getLayoutParams() as RelativeLayout.LayoutParams
            paramsReceiver.height = 510
            paramsReceiver.width = 432
            paramsReceiver.marginEnd = 48
            paramsReceiver.topMargin = 48
            paramsReceiver.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
//            paramsReceiver.gravity = Gravity.TOP or Gravity.END
            play_view_renderer1?.layoutParams = paramsReceiver
            play_view_renderer1?.elevation = 2F

        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration?
    ) {
        if (isInPictureInPictureMode) {
            isPictureInPictureMode = true
            layoutBottomSheet.visibility = View.GONE
            relLayToolbar?.visibility = View.GONE
            val paramsReceiver: RelativeLayout.LayoutParams =
                play_view_renderer1?.getLayoutParams() as RelativeLayout.LayoutParams
            paramsReceiver.height = 210
            paramsReceiver.width = 165
            paramsReceiver.marginEnd = 10
            paramsReceiver.topMargin = 10
            paramsReceiver.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            play_view_renderer1?.layoutParams = paramsReceiver
        } else {
            layoutBottomSheet.visibility = View.VISIBLE
            relLayToolbar?.visibility = View.VISIBLE
            val paramsReceiver: RelativeLayout.LayoutParams =
                play_view_renderer1?.getLayoutParams() as RelativeLayout.LayoutParams
            paramsReceiver.height = 510
            paramsReceiver.width = 432
            paramsReceiver.marginEnd = 48
            paramsReceiver.topMargin = 48
            paramsReceiver.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            play_view_renderer1?.layoutParams = paramsReceiver
        }
    }

    private fun startCallDurationTimer() {
        chronometer?.base = SystemClock.elapsedRealtime()
        chronometer?.start()

        chronometer?.setOnChronometerTickListener {
            val t: Long = SystemClock.elapsedRealtime() - it.getBase() - 19800000
            it.setText(DateFormat.format("HH:mm:ss", t))
        }
    }

    override fun onBackPressed() {
        val aspectRatio = Rational(relLayoutMain!!.getWidth(), relLayoutMain!!.getHeight())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pictureInPictureParamsBuilder.setAspectRatio(aspectRatio).build()
            enterPictureInPictureMode(pictureInPictureParamsBuilder.build())
        }
    }

    fun navToLauncherTask(appContext: Context) {
        val activityManager =
            (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        val appTasks = activityManager.appTasks
        for (task in appTasks) {
            val baseIntent = task.taskInfo.baseIntent
            val categories = baseIntent.categories
            if (categories != null && categories.contains(Intent.CATEGORY_LAUNCHER)) {
                task.moveToFront()
                return
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPictureInPictureMode)
            navToLauncherTask(this)
    }
}