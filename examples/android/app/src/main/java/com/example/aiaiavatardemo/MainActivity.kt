package com.example.aiaiavatardemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoDestroyCompletionCallback
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoPlayerState
import im.zego.zegoexpress.constants.ZegoPublisherState
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.entity.ZegoCanvas
import im.zego.zegoexpress.entity.ZegoEngineProfile
import im.zego.zegoexpress.entity.ZegoRoomConfig
import im.zego.zegoexpress.entity.ZegoStream
import im.zego.zegoexpress.entity.ZegoUser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AIAvatarDemo"
        private const val REQUEST_AUDIO_PERMISSION = 100
        private const val AGENT_ID = "ai_avatar_agent"
        private const val DIGITAL_HUMAN_ID = "c4b56d5c-db98-4d91-86d4-5a97b507da97"
    }

    // ========== UI Components ==========
    private lateinit var tvStatus: TextView
    private lateinit var tvPlaceholder: TextView
    private lateinit var videoView: TextureView
    private lateinit var btnMic: MaterialButton
    private lateinit var btnStart: MaterialButton

    // ========== SDK Instances ==========
    private var engine: ZegoExpressEngine? = null
    private var isMicOn = true
    private var isConnected = false
    private var agentInstanceId: String? = null
    private var currentRoomId: String? = null
    private var currentStreamId: String? = null

    // ========== Network ==========
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // ========== User Info ==========
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Read userId from intent (passed from LoginActivity)
        userId = intent.getStringExtra("userId") ?: "user_${System.currentTimeMillis()}"

        initViews()

        // Check configuration
        if (BuildConfig.ZEGO_APP_ID == 0L) {
            updateStatus("Error: ZEGO_APP_ID not configured")
            return
        }
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvPlaceholder = findViewById(R.id.tvPlaceholder)
        videoView = findViewById(R.id.videoView)
        btnMic = findViewById(R.id.btnMic)
        btnStart = findViewById(R.id.btnStart)

        btnStart.setOnClickListener {
            if (!isConnected) {
                startConversation()
            } else {
                endConversation()
            }
        }

        btnMic.setOnClickListener {
            toggleMic()
        }
    }

    // ========== Start Conversation (Full Flow) ==========

    private fun startConversation() {
        // Check audio permission first
        if (!checkAudioPermission()) {
            requestAudioPermission()
            return
        }

        // Run the full flow in a background thread
        Thread {
            try {
                // Step 0: Clean up any stale instances
                updateStatus("Cleaning up...")
                cleanupAllInstances()

                // Step 1: Register agent
                updateStatus("Registering AI Agent...")
                registerAgent()

                // Step 2: Generate IDs
                val timestamp = System.currentTimeMillis()
                val roomId = "room_$timestamp"
                val userStreamId = "user_stream_$timestamp"
                val agentStreamId = "agent_stream_$timestamp"
                val agentUserId = "agent_user_$timestamp"

                currentRoomId = roomId
                currentStreamId = userStreamId

                // Step 3: Create digital human instance
                updateStatus("Creating digital human instance...")
                val instanceData = createInstance(
                    userId, roomId, agentStreamId, agentUserId, userStreamId
                )
                agentInstanceId = instanceData?.optString("agentInstanceId")
                val receivedAgentStreamId = instanceData?.optString("agentStreamId") ?: agentStreamId

                // Step 4: Get token
                updateStatus("Getting token...")
                val token = getToken(userId)
                if (token.isNullOrEmpty()) {
                    updateStatus("Error: Failed to get token")
                    return@Thread
                }

                // Step 5: Create engine, login room, and publish audio (must be on main thread for SDK)
                runOnUiThread {
                    createEngine()
                    setEventHandler()
                    loginRoom(roomId, token, receivedAgentStreamId, userStreamId)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Start conversation failed", e)
                updateStatus("Error: ${e.message}")
                runOnUiThread { cleanupLocal() }
            }
        }.start()
    }

    // ========== Create ZEGO Express Engine (Direct SDK Call) ==========

    private fun createEngine() {
        val profile = ZegoEngineProfile()
        profile.appID = BuildConfig.ZEGO_APP_ID
        profile.scenario = ZegoScenario.HIGH_QUALITY_CHATROOM
        profile.application = application
        engine = ZegoExpressEngine.createEngine(profile, null)
        Log.d(TAG, "ZegoExpressEngine created with appId: ${BuildConfig.ZEGO_APP_ID}")
    }

    // ========== Set Event Handler (Direct SDK Call) ==========

    private fun setEventHandler() {
        engine?.setEventHandler(object : IZegoEventHandler() {

            override fun onRoomStreamUpdate(
                roomID: String?,
                updateType: ZegoUpdateType,
                streamList: ArrayList<ZegoStream>?,
                extendedData: org.json.JSONObject?
            ) {
                super.onRoomStreamUpdate(roomID, updateType, streamList, extendedData)
                Log.d(TAG, "onRoomStreamUpdate: roomID=$roomID, type=$updateType, streams=${streamList?.size}")

                if (updateType == ZegoUpdateType.ADD) {
                    streamList?.forEach { stream ->
                        Log.d(TAG, "Stream ADD: ${stream.streamID}")
                        currentStreamId = stream.streamID
                        startPlayingStream(stream.streamID)
                    }
                } else if (updateType == ZegoUpdateType.DELETE) {
                    streamList?.forEach { stream ->
                        Log.d(TAG, "Stream DELETE: ${stream.streamID}")
                        engine?.stopPlayingStream(stream.streamID)
                    }
                }
            }

            override fun onRoomStateChanged(
                roomID: String?,
                reason: ZegoRoomStateChangedReason,
                errorCode: Int,
                extendedData: org.json.JSONObject?
            ) {
                super.onRoomStateChanged(roomID, reason, errorCode, extendedData)
                Log.d(TAG, "onRoomStateChanged: roomID=$roomID, reason=$reason, error=$errorCode")
                if (errorCode != 0) {
                    updateStatus("Room error: $errorCode")
                }
            }

            override fun onPublisherStateUpdate(
                streamID: String?,
                state: ZegoPublisherState,
                errorCode: Int,
                extendedData: org.json.JSONObject?
            ) {
                super.onPublisherStateUpdate(streamID, state, errorCode, extendedData)
                Log.d(TAG, "onPublisherStateUpdate: streamID=$streamID, state=$state, error=$errorCode")
            }

            override fun onPlayerStateUpdate(
                streamID: String?,
                state: ZegoPlayerState,
                errorCode: Int,
                extendedData: org.json.JSONObject?
            ) {
                super.onPlayerStateUpdate(streamID, state, errorCode, extendedData)
                Log.d(TAG, "onPlayerStateUpdate: streamID=$streamID, state=$state, error=$errorCode")
            }
        })
    }

    // ========== Login Room (Direct SDK Call) ==========

    private fun loginRoom(roomId: String, token: String, agentStreamId: String, userStreamId: String) {
        updateStatus("Logging into room...")

        val user = ZegoUser(userId, userId)
        val roomConfig = ZegoRoomConfig()
        roomConfig.token = token
        roomConfig.isUserStatusNotify = true

        engine?.loginRoom(roomId, user, roomConfig) { errorCode, _ ->
            if (errorCode == 0) {
                Log.d(TAG, "Room login success")
                updateStatus("Room connected, publishing audio...")
                // Start publishing audio stream with the SAME streamId used in createInstance
                startPublishingStream(userStreamId)
            } else {
                Log.e(TAG, "Room login failed: $errorCode")
                updateStatus("Login failed: $errorCode")
                cleanupLocal()
            }
        }
    }

    // ========== Start Publishing Stream (Direct SDK Call) ==========

    private fun startPublishingStream(streamId: String) {
        // Enable microphone capture before publishing
        engine?.enableCamera(false)
        engine?.muteMicrophone(false)
        engine?.startPublishingStream(streamId)
        Log.d(TAG, "Started publishing audio stream: $streamId")
        updateStatus("Publishing audio, waiting for AI Avatar...")
    }

    // ========== Start Playing Stream (Direct SDK Call) ==========

    private fun startPlayingStream(streamId: String) {
        runOnUiThread {
            val canvas = ZegoCanvas(videoView)
            engine?.startPlayingStream(streamId, canvas)
            Log.d(TAG, "Started playing stream: $streamId")

            tvPlaceholder.visibility = View.GONE
            isConnected = true
            updateStatus("Playing")
            updateButtons()
        }
    }

    // ========== End Conversation ==========

    private fun endConversation() {
        Thread {
            try {
                updateStatus("Ending conversation...")

                // Delete agent instance on server
                agentInstanceId?.let { deleteInstance(it) }

                runOnUiThread { cleanupLocal() }

                updateStatus("Ready")
            } catch (e: Exception) {
                Log.e(TAG, "End conversation error", e)
                runOnUiThread { cleanupLocal() }
                updateStatus("Ready")
            }
        }.start()
    }

    // ========== Toggle Mic (Direct SDK Call) ==========

    private fun toggleMic() {
        isMicOn = !isMicOn
        engine?.muteMicrophone(!isMicOn)

        runOnUiThread {
            if (isMicOn) {
                btnMic.text = getString(R.string.mic_on)
                btnMic.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_mic_on))
            } else {
                btnMic.text = getString(R.string.mic_off)
                btnMic.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_mic_off))
            }
        }
    }

    // ========== Cleanup ==========

    private fun cleanupLocal() {
        // Stop playing stream
        currentStreamId?.let { engine?.stopPlayingStream(it) }

        // Stop publishing
        engine?.stopPublishingStream()

        // Logout room
        engine?.logoutRoom()

        // Destroy engine
        ZegoExpressEngine.destroyEngine(null)
        engine = null

        // Reset state
        isConnected = false
        isMicOn = true
        agentInstanceId = null
        currentRoomId = null
        currentStreamId = null

        runOnUiThread {
            tvPlaceholder.visibility = View.VISIBLE
            updateButtons()
        }
    }

    private fun updateButtons() {
        runOnUiThread {
            if (isConnected) {
                btnStart.text = getString(R.string.btn_end_conversation)
                btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_end))
                btnMic.isEnabled = true
                btnMic.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_mic_on))
                btnMic.text = getString(R.string.mic_on)
            } else {
                btnStart.text = getString(R.string.btn_start_conversation)
                btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_start))
                btnMic.isEnabled = false
                btnMic.text = getString(R.string.mic_on)
                btnMic.setBackgroundColor(ContextCompat.getColor(this, R.color.btn_mic_on))
            }
        }
    }

    private fun updateStatus(msg: String) {
        runOnUiThread {
            tvStatus.text = msg
        }
    }

    // ========== Server API Calls (OkHttp) ==========

    private fun registerAgent(): JSONObject? {
        val url = "${BuildConfig.ZEGO_API_BASE_URL}/api/agent"
        val jsonBody = JSONObject().apply {
            put("agentId", AGENT_ID)
            put("agentName", "AI Avatar")
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null
        val result = JSONObject(responseBody)
        Log.d(TAG, "Register agent result: $result")

        if (result.optInt("code", -1) != 0) {
            throw Exception("Register agent failed: ${result.optString("message", "Unknown error")}")
        }

        return result
    }

    private fun createInstance(
        userId: String,
        roomId: String,
        agentStreamId: String,
        agentUserId: String,
        userStreamId: String
    ): JSONObject? {
        val url = "${BuildConfig.ZEGO_API_BASE_URL}/api/instance"
        val jsonBody = JSONObject().apply {
            put("agentId", AGENT_ID)
            put("userId", userId)
            put("roomId", roomId)
            put("agentStreamId", agentStreamId)
            put("agentUserId", agentUserId)
            put("userStreamId", userStreamId)
            put("digitalHumanId", DIGITAL_HUMAN_ID)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null
        val result = JSONObject(responseBody)
        Log.d(TAG, "Create instance result: $result")

        if (result.optInt("code", -1) != 0) {
            throw Exception("Create instance failed: ${result.optString("message", "Unknown error")}")
        }

        return result.optJSONObject("data")
    }

    private fun getToken(userId: String): String? {
        val url = "${BuildConfig.ZEGO_API_BASE_URL}/api/token?userId=$userId"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: return null
        val result = JSONObject(responseBody)
        Log.d(TAG, "Get token result: token=${result.optString("token").take(20)}...")

        return result.optString("token")
    }

    private fun deleteInstance(instanceId: String): JSONObject? {
        val url = "${BuildConfig.ZEGO_API_BASE_URL}/api/instance"
        val jsonBody = JSONObject().apply {
            put("agentInstanceId", instanceId)
        }

        val request = Request.Builder()
            .url(url)
            .delete(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return null
            val result = JSONObject(responseBody)
            Log.d(TAG, "Delete instance result: $result")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Delete instance error", e)
            return null
        }
    }

    private fun cleanupAllInstances() {
        val url = "${BuildConfig.ZEGO_API_BASE_URL}/api/instance"
        val jsonBody = "{}"

        val request = Request.Builder()
            .url(url)
            .delete(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup instances error (ignoring): ${e.message}")
        }
    }

    // ========== Audio Permission ==========

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_AUDIO_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startConversation()
            } else {
                Toast.makeText(this, "Audio permission is required for voice interaction", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ========== Lifecycle ==========

    override fun onDestroy() {
        super.onDestroy()
        if (isConnected) {
            Thread {
                agentInstanceId?.let { deleteInstance(it) }
            }.start()
            cleanupLocal()
        }
    }
}