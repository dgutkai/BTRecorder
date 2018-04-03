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
import android.media.MediaPlayer



class MainActivity : AppCompatActivity() {

    private lateinit var mAudioManager: AudioManager
    private var audioBufSize: Int = 0
    private var player: AudioTrack? = null
    private var mRecorder: Recorder? = null

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
        mRecorder = Recorder(this)

        mRecorder!!.startRecord(object : Recorder.RecoderListener{
            override fun onData(data: ByteArray) {
                player!!.write(data, 0, data.size)
            }

        })
        player!!.play()
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
}
