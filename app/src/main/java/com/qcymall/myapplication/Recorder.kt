package com.qcymall.myapplication

import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Created by lanmi on 2018/4/3.
 */

class Recorder(context: Context){

    public interface RecoderListener{
        fun onData(data: ByteArray)
    }

    private lateinit var mAudioManager: AudioManager
    private var mRecordingThread: RecordThread? = null
    var mListener: RecoderListener? = null


    init {
        mAudioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    fun startRecord(listener: RecoderListener) {
        mListener = listener

        if (mAudioManager.isBluetoothScoAvailableOffCall) {

            if (mAudioManager.isBluetoothScoOn) {
                mAudioManager.stopBluetoothSco()
                Log.e("BTRecordImpl", "1mAudioManager.stopBluetoothSco()")
            }
            Log.e("BTRecordImpl", "1startBluetoothSco")
            mAudioManager.startBluetoothSco()
            var timeout = 100
            while (!mAudioManager.isBluetoothScoOn && timeout-- > 0){
                Thread.sleep(10)
                if (timeout == 50){
                    Log.e("BTRecordImpl", "2startBluetoothSco")
                    mAudioManager.startBluetoothSco()
                }
                Log.e("BTRecordImpl", "change BluetoothScoOn" + mAudioManager.isBluetoothScoOn + ":" + timeout)
            }

            if (mRecordingThread != null) {
                mRecordingThread!!.pause()
                mRecordingThread!!.interrupt()
            }
//                mRecordingThread = null
//                if (mRecordingThread == null) {
            mRecordingThread = RecordThread()
//                }
            mRecordingThread!!.start()

        }

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
//                            val valume = calculateVolume(buffer)
                            if (mListener != null){
                                mListener!!.onData(buffer)
                            }
//                            Log.e("RecordingManager", "endVoiceRequest() --> " + valume)
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
