package com.qcymall.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.text.format.DateUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var mAudioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initPermission()
        mAudioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audioBufSize = AudioTrack.getMinBufferSize(8000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT)
        player = AudioTrack(AudioManager.STREAM_VOICE_CALL, 8000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufSize,
                AudioTrack.MODE_STREAM)

        startRecord()
    }

    private fun initPermission() {
        val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS)
        val toApplyList = ArrayList<String>()
        for (perm in permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm)
                //进入到这里代表没有权限.
            }
        }

        if (!toApplyList.isEmpty()) {
            val tmpList = arrayOfNulls<String>(toApplyList.size)
            ActivityCompat.requestPermissions(this, toApplyList.toTypedArray(), 1)
        }
    }

    private var audioBufSize: Int = 0

    private var player: AudioTrack? = null

    private var mRecordingThread: RecordThread? = null

    fun startRecord() {

        Thread(Runnable {
            if (mAudioManager.isBluetoothScoAvailableOffCall) {

                if (mAudioManager.isBluetoothScoOn) {
                    mAudioManager.stopBluetoothSco()
                    Log.e("BTRecordImpl", "1mAudioManager.stopBluetoothSco()")
//                mAudioManager.isBluetoothScoOn = false
                }
                Log.e("BTRecordImpl", "1startBluetoothSco")
                mAudioManager.startBluetoothSco()
//                HeadsetStateManager.startBluetoothSCO()
//            var starttime = Date().time
                var timeout = 100
//                while (!HeadsetStateManager.isAudioConnected() && timeout-- > 0){
                while (!mAudioManager.isBluetoothScoOn && timeout-- > 0){
                    Thread.sleep(10)
                    if (timeout == 50){
                        Log.e("BTRecordImpl", "2startBluetoothSco")
                        mAudioManager.startBluetoothSco()
//                        HeadsetStateManager.startBluetoothSCO()
                    }
                    Log.e("BTRecordImpl", "change BluetoothScoOn" + mAudioManager.isBluetoothScoOn + ":" + timeout)
                }

//                mAudioManager.setSpeakerphoneOn(false);
//                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

//                mAudioManager.mode = AudioManager.MODE_NORMAL;
//                mAudioManager.isBluetoothScoOn = true

//                mAudioManager.isSpeakerphoneOn = true

                if (mRecordingThread != null) {
                    mRecordingThread!!.pause()
                    mRecordingThread!!.interrupt()
                }
//                mRecordingThread = null
//                if (mRecordingThread == null) {
                mRecordingThread = RecordThread()
//                }
                mRecordingThread!!.start()
                player!!.play()

            }
        }).start()

    }

    private val SAMPLE_RATE_HZ = 16000

    internal inner class RecordThread : Thread() {
        private val audioRecord: AudioRecord
        private val bufferSize: Int
        private var isRun: Boolean = false

        private var mStartTime = 0L

//        private val audioTrack: AudioTrack

        init {

            var audiosource = MediaRecorder.AudioSource.VOICE_RECOGNITION
            if (Build.VERSION.SDK_INT > 19){
                audiosource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }
            this.bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT) * 2
            this.audioRecord = AudioRecord(audiosource,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    this.bufferSize)

//            this.audioTrack = AudioTrack(AudioManager.STREAM_VOICE_CALL,
//                    SAMPLE_RATE_HZ,
//                    AudioFormat.CHANNEL_OUT_MONO,
//                    AudioFormat.ENCODING_PCM_16BIT,
//                    this.bufferSize,
//                    AudioTrack.MODE_STREAM)
        }

        override fun run() {
            super.run()
            this.isRun = true
            try {
                if (audioRecord.state == 1) {


                    this.audioRecord.startRecording()

                    mStartTime = System.currentTimeMillis()

                    while (this.isRun) {

                        val buffer = ByteArray(bufferSize)
                        val readBytes = audioRecord.read(buffer, 0, bufferSize)
                        if (readBytes > 0) {
                            val valume = calculateVolume(buffer)

                            player!!.write(buffer, 0, readBytes)
                            Log.e("RecordingManager", "endVoiceRequest() --> " + valume)
                        }

                    }

                    try {
                        this.audioRecord.stop()
                        this.audioRecord.release()
                    }catch (audioException: Exception){

                    }

                    Log.e("RecordingManager", "endVoiceRequest() --> ")
//                  this.audioTrack.stop()

                }
            } catch (e2: Exception) {
                Log.e("BtRecordImpl", "error: " + e2.message)
                try {
                    this.audioRecord.stop()
                    this.audioRecord.release()
                }catch (audioException: Exception){

                }

                isRun = false

            }

        }

        fun pause() {
            this.isRun = false
            try {
                this.audioRecord.stop()
                this.audioRecord.release()
            }catch (e: Exception){

            }
        }

        @Synchronized override fun start() {
            if (!isRun) {
                super.start()
            }
        }

        private fun calculateVolume(buffer: ByteArray): Int {
            val audioData = ShortArray(buffer.size / 2)
            ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(audioData)
            var sum = 0.0
            // 将 buffer 内容取出，进行平方和运算
            for (i in audioData.indices) {
                sum += (audioData[i] * audioData[i]).toDouble()
            }
            // 平方和除以数据总长度，得到音量大小
            val mean = sum / audioData.size.toDouble()
            val volume = 10 * Math.log10(mean)
            return volume.toInt()
        }
    }
}
