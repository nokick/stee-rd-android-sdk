package com.roundesk.sdk.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.gson.Gson
import com.roundesk.sdk.R
import com.roundesk.sdk.base.AppBaseActivity
import com.roundesk.sdk.dataclass.*
import com.roundesk.sdk.network.ApiInterface
import com.roundesk.sdk.network.ServiceBuilder
import com.roundesk.sdk.socket.SocketListener
import com.roundesk.sdk.socket.SocketManager
import com.roundesk.sdk.util.Constants
import com.roundesk.sdk.util.LogUtil
import com.roundesk.sdk.util.ToastUtil
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class IncomingCallActivity : AppBaseActivity(), View.OnClickListener,
    EasyPermissions.PermissionCallbacks,
    EasyPermissions.RationaleCallbacks, SocketListener<Any> {

    private val TAG = IncomingCallActivity::class.java.simpleName

    //    private var mSocket: Socket? = null
    private var imgCallEnd: ImageView? = null
    private var imgCallAccept: ImageView? = null
    private var imgBack: ImageView? = null
    private var txtDoctorName: TextView? = null
    private var room_id: Int = 0
    private var meeting_id: Int = 0
    private var receiver_name: String? = null

    private val RC_CAMERA_PERM = 123
    private val RC_MICROPHONE_PERM = 124
    private val RC_STORAGE_PERM = 125
    private val RC_TELEPHONE_PERM = 126

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        /*   val app: SocketInstance = application as SocketInstance
           mSocket = app.getMSocket()
           //connecting socket
           mSocket?.connect()
           val options = IO.Options()
           options.reconnection = true //reconnection
           options.forceNew = true

           if (mSocket?.connected() == true) {
               Toast.makeText(this, "Socket is connected", Toast.LENGTH_SHORT).show()
           }

           mSocket?.on(Constants.SocketSuffix.SOCKET_ACCEPT_CALL, onCreateCallEmitter)
   */
        val extras = intent.extras
        if (extras != null) {
            room_id = extras.getInt("room_id")
            meeting_id = extras.getInt("meeting_id")
            receiver_name = extras.getString("receiver_name")
            //The key argument here must match that used in the other activity
            LogUtil.e(
                VideoCallActivityNew.TAG,
                " room_id : $room_id"
                        + " meeting_id : $meeting_id "
                        + " receiver_name : $receiver_name"
            )
        }
        initSocket()
        initView()
    }

    private fun initSocket() {
        SocketManager(
            this, Constants.socketConnection!!,
            Constants.SocketSuffix.SOCKET_CONNECT_SEND_CALL_TO_CLIENT
        ).createCallSocket()
    }

    private fun initView() {
        imgCallEnd = findViewById(R.id.imgCallEnd)
        imgCallAccept = findViewById(R.id.imgCallAccept)
        imgBack = findViewById(R.id.imgBack)
        txtDoctorName = findViewById(R.id.txtDoctorName)

        imgCallEnd?.setOnClickListener(this)
        imgCallAccept?.setOnClickListener(this)
        imgBack?.setOnClickListener(this)

        txtDoctorName?.text = receiver_name
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.imgCallAccept -> {
                acceptCall()
            }
            R.id.imgCallEnd -> {
                declineCall()
            }
            R.id.imgBack -> {
                finish()
            }
        }
    }

    private fun acceptCall() {
        val acceptCallRequest = AcceptCallRequest(
            Constants.UUIDs.USER_DEEPAK,
            "on",
            "on",
            "eyJ0eXAiOiJLV1PiLOJhbK1iOiJSUzI1NiJ9",
            meeting_id,
            room_id
        )
        val acceptCallJson = Gson().toJson(acceptCallRequest)
        LogUtil.e(TAG, "json : $acceptCallJson")

        val request = ServiceBuilder.buildService(ApiInterface::class.java)
        val acceptCall = request.getAcceptCallSocketData(acceptCallRequest)

        if (hasCameraPermission() && hasMicrophonePermission() && hasStoragePermission()) {

            acceptCall.enqueue(object : Callback<AcceptCallDataClassResponse?> {
                override fun onResponse(
                    call: Call<AcceptCallDataClassResponse?>,
                    response: Response<AcceptCallDataClassResponse?>
                ) {
                    LogUtil.e(TAG, "onSuccess: $response")
                    LogUtil.e(TAG, "onSuccess: ${Gson().toJson(response.body())}")

                    if (response.isSuccessful) {
//                            Handler(Looper.getMainLooper()).postDelayed({
                        val intent =
                            Intent(this@IncomingCallActivity, VideoCallActivityNew::class.java)
                        intent.putExtra("activity", "Incoming")
                        intent.putExtra("room_id", response.body()?.roomId)
                        intent.putExtra("meeting_id", response.body()?.meetingId)
                        intent.putExtra("receiver_stream_id", response.body()?.streamId)
                        intent.putExtra("stream_id", response.body()?.caller_streamId)
                        intent.putExtra("isIncomingCall", true)
                        startActivity(intent)
//                            }, 3000)

                    }
                }

                override fun onFailure(
                    call: Call<AcceptCallDataClassResponse?>,
                    t: Throwable
                ) {
                    Log.e(TAG, "onFailure : ${t.message}")
                }
            })
            finish()
        } else {
            if (!hasCameraPermission()) {
                EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.rationale_camera),
                    RC_CAMERA_PERM,
                    Manifest.permission.CAMERA
                )
            }

            if (!hasMicrophonePermission()) {
                EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.rationale_microphone),
                    RC_MICROPHONE_PERM,
                    Manifest.permission.RECORD_AUDIO
                )
            }

            if (!hasStoragePermission()) {
                EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.rationale_storage),
                    RC_STORAGE_PERM,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    private fun declineCall() {
        val declineCallRequest = DeclineCallRequest(
            Constants.UUIDs.USER_DEEPAK,
            "on",
            "on",
            "eyJ0eXAiOiJLV1PiLOJhbK1iOiJSUzI1NiJ9",
            meeting_id,
            room_id
        )
        val declineCallJson = Gson().toJson(declineCallRequest)
        LogUtil.e(TAG, "json : $declineCallJson")

        val request = ServiceBuilder.buildService(ApiInterface::class.java)
        val declineCall = request.declineCall(declineCallRequest)

        declineCall.enqueue(object : Callback<BaseDataClassResponse?> {
            override fun onResponse(
                call: Call<BaseDataClassResponse?>,
                response: Response<BaseDataClassResponse?>
            ) {
                LogUtil.e(TAG, "onSuccess: $response")
                LogUtil.e(TAG, "onSuccess: ${Gson().toJson(response.body())}")
                if (response.isSuccessful) {
                    finish()
                }
            }

            override fun onFailure(
                call: Call<BaseDataClassResponse?>,
                t: Throwable
            ) {
                LogUtil.e(TAG, "onFailure : ${t.message}")
            }
        })
    }

    private fun hasCameraPermission(): Boolean {
        return EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)
    }

    private fun hasMicrophonePermission(): Boolean {
        return (EasyPermissions.hasPermissions(this, Manifest.permission.RECORD_AUDIO))
    }

    private fun hasStoragePermission(): Boolean {
        return (EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                && EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        LogUtil.d(TAG, "onPermissionsGranted:" + requestCode + ":" + perms.size)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        LogUtil.d(TAG, "onPermissionsDenied:" + requestCode + ":" + perms.size)

        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }

    override fun onRationaleAccepted(requestCode: Int) {
        LogUtil.d(TAG, "onRationaleAccepted: $requestCode")
    }

    override fun onRationaleDenied(requestCode: Int) {
        LogUtil.d(TAG, "onRationaleDenied: $requestCode")
    }

    override fun handleSocketSuccessResponse(response: String, type: String) {
        LogUtil.e(TAG, "handleSocketSuccessResponse: $response")
        when (type) {
            Constants.SocketSuffix.SOCKET_CONNECT_SEND_CALL_TO_CLIENT -> {
                val createCallSocketDataClass: CreateCallSocketDataClass =
                    Gson().fromJson(response, CreateCallSocketDataClass::class.java)

                runOnUiThread {
                    if (createCallSocketDataClass.type == Constants.SocketSuffix.SOCKET_TYPE_NEW_CALL) {
                    }
                }
            }
        }
    }

    override fun handleSocketErrorResponse(error: Any) {
        LogUtil.e(TAG, "handleSocketErrorResponse: ${Gson().toJson(error)}")
        ToastUtil.displayShortDurationToast(
            this,
            "" + error.toString() + "\n" + resources.getString(R.string.toast_err_in_response) + " " +
                    resources.getString(R.string.toast_request_to_try_later)
        )
    }
}