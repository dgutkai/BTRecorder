# BTRecorder
## 简介
实现一个可以边录边播的工具，将蓝牙耳机麦克风录到的声音从耳机中播放出来。
最近在做一个语音助手工具软件，具体需求是使用蓝牙耳机唤醒APP并讲话，APP将讲话内容进行语音识别，通过云平台进行理解并返回相应的操作。比如当用户说“播放音乐”的时候，APP将会随机播放一首歌。期间在蓝牙耳机录音和播放中遇到了很多问题，APP录不到声音，声音从手机听筒播放，没有任何声音等等等。因此实现了这个BTRecorder DEMO，记录一些蓝牙录音及播放的问题，也方便后续做一些功能测试。
## Android录音（三种方式录音）
#### 1、通过Intent调用系统的录音机进行录音
通过发送一个Intent，系统开启录音机进行录音，录音完成之后，在onActivityResult中返回录音文件的URI，此时我们便可以使用MediaPlayer进行录音的播放。
该方法使用简单方便，只需要几句代码便可完成录音操作。然而由于使用的是系统录音机进行录音，我们没办法对其进行更多的操作，使用起来非常不方便，因此该方法一般不适用于APP的录音需求。
调用实例：
~~~java
private final static int REQUEST_RECORDER = 1;
private Uri uri;
public void startRecorder(){
    Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
    startActivityForResult(intent,REQUEST_RECORDER);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && REQUEST_RECORDER == requestCode){
        uri = data.getData();
    }
}
~~~
#### 2、使用MediaRecorder进行录音
先来看一下使用实例：
~~~java
MediaRecorder recorder = new MediaRecorder();
recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
recorder.setOutputFile(PATH_NAME);
recorder.prepare();
recorder.start();   // Recording is now started
// Recoding...
recorder.stop();
recorder.reset();   // You can reuse the object by going back to setAudioSource() step
recorder.release(); // Now the object cannot be reused
~~~
MediaRecorder可用来录制音频和视频。在使用时，为了能够捕获音频，在实例化MediaRecorder之后，需要调用setAudioSource和setAudioEncoder方法。如果没有调用这两个方法，音频、视频将不会被录制，通常在使用时，还要调用setOutputFormat和setOutputFile两个方法设置录音文件的信息。
##### setAudioSource
设置录音的音频源，定义在MediaRecorder.AudioSource中。默认情况下可以使用MediaRecorder.AudioSource.DEFAULT或者MediaRecorder.AudioSource.MIC。如果想要使用蓝牙耳机的麦克风进行录音，则需要设置为MediaRecorder.AudioSource.VOICE_COMMUNICATION。如果没有设置为VOICE_COMMUNICATION，可能在部分手机上无法实现蓝牙耳机录音。
##### setOutputFormat
设置输出文件的格式，该方法必须在setAudioSource()/setVideoSource()之后，prepare()之前调用。通常使用MediaRecorder.OutputFormat.THREE_GPP制定输出3GP文件，使用MediaRecorder.OutputFormat.MPEG_4制定输出MP4文件。
##### setAudioEncoder
设置用于录制的编码器，如果未调用此方法，则输出文件将不包含音轨。在setOutputFormat()之后但在prepare()之前调用。通常设置为MediaRecorder.AudioEncoder.AMR_NB。
#### 3、使用AudioRecord录制原始音频
使用AudioRecord类进行音频录制是三种音频录制方法中最为灵活的，它能直接得到录音的数据流，可以对数据流进行处理，从而实现更多有趣的功能。
使用AudioRecord录音也很简单，我们只需要构造一个AudioRecord实例对象，并传入不同的参数。
~~~java
AudioRecord(int audioSource, int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes)
~~~
_**audioSource**:音频源，和MediaRecorder中的一致。

**sampleRateInHz**：[采样率](https://baike.baidu.com/item/%E9%87%87%E6%A0%B7%E9%A2%91%E7%8E%87/1494233?fr=aladdin&fromid=972301&fromtitle=%E9%87%87%E6%A0%B7%E7%8E%87)，44100Hz是目前唯一保证可在所有设备上工作的速率。一般蓝牙耳机无法达到44100Hz的采样率，所有在使用蓝牙耳机录音的时候，设置为8000Hz或者16000Hz。

**channelConfig**:描述音频通道的配置。一般可设置为AudioFormat.CHANNEL_IN_MONO，它可以保证在所有设备上运行。

**audioFormat**：返回音频数据的格式。常用的可以设置为ENCODING_PCM_8BIT、ENCODING_PCM_16BIT。表示我们使用8位或者16为的PCM数据作为返回。PCM代表脉冲编码调制（Pulse Code Modulation），他实际上是原始的音频样本。因此能够设置每一个样本的分辨率为16位或8位。16位将占用很多其它的控件和处理能力，但表示的音频将更接近真实。

**bufferSizeInBytes**：指定缓冲区的大小，使用时，一般我们通过AudioRecord来查询最小的缓冲区大小。_
下面来看一下创建AudioRecord实例的代码：
~~~java
int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT) * 2
AudioRecord audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize)
~~~
创建完AudioRecord实例后，我们必须创建一个异步的任务或者线程来获取录音数据。
~~~kotlin
internal inner class RecordThread : Thread() {
    private val audioRecord: AudioRecord
    private val bufferSize: Int
    private var isRun: Boolean = false
    init {
        var audiosource = MediaRecorder.AudioSource.VOICE_RECOGNITION
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
                        val valume = calculateVolume(buffer)
                        Log.e("MediaRecord", "Volume() --> " + valume)
                    }
                }
                try {
                    this.audioRecord.stop()
                    this.audioRecord.release()
                }catch (audioException: Exception){
                }
            }
        } catch (e2: Exception) {
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

    // 计算录音音量
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
~~~
## Android播放声音（三种方式）
#### 1、SoundPool播放音频
SoundPool支持多个音频文件同时播放(组合音频也是有上限的)，延时短，比较适合短促、密集的场景，是游戏开发中音效播放的福音。
SoundPool只适合短促的音效播放，不能用于长时间的音乐播放。

** 1) 将音频文件复制到Raw目录中
2）使用SoundPool.Builder()进行实例化
3）加载音频文件load(Context context, int resId, int priority)
4）设置加载完成回调对象
5）在加载完成回调中播放声音play(int soundID, float leftVolume, float rightVolume, int priority, int loop, float rate)
6）在不需要的时候释放资源release()** 
具体可参考下面的代码实现：
~~~kotlin
// 初始化方法，实例化SoundPool对象。
fun initSoundPool() {
    val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    soundPool = SoundPool.Builder()
            .setAudioAttributes(attributes)
            .setMaxStreams(1)
            .build()
}

fun playNotif() {
    try {
        try {
            // 加载音频文件，音频文件存放于Raw目录下，
            soundPool!!.load(BaseApplication.getContext(), R.raw.ding, 0)
            soundPool!!.setOnLoadCompleteListener { soundPool, sampleId, status ->
                soundPool.play(sampleId,0.7f, 0.7f, 0, 0, 1.0f)
            }
        } catch (e: Exception) {
            if (BaseApplication.DEBUG) {
                e.printStackTrace()
            }
            soundPool!!.release()
        }

    } catch (e: Exception) {

    }
}
~~~
代码中，需要注意的是在初始化方法中，**.setUsage()** 的参数设置为AudioAttributes.USAGE_MEDIA表示声音类型为多媒体类型，使用蓝牙耳机的通话模式下是听不到声音的；使用AudioAttributes.USAGE_VOICE_COMMUNICATION则可以使蓝牙耳机在通话模式下也能听到声音，其主要原因还是和蓝牙耳机的通信链路相关。
### 2、MediaPlayer
对于android音频的播放，MediaPlayer确实强大而且方便使用，提供了对音频播放的各种控制，支持AAC、AMR、FLAC、MP3、MIDI、OGG、PCM等格式 ，生命周期：
![MediaPlayer生命周期](https://github.com/dgutkai/BTRecoder/blob/master/doc/mediaplayer_state_diagram.gif)</br>
使用时，创建一个MediaPlayer实例，设置数据源，不要忘记prepare()，尽量使用异步prepareAync()，这样不会阻塞UI线程，播放完毕即使释放资源。
~~~kotlin
mediaPlayer.stop()
mediaPlayer.release()
mediaPlayer = null
~~~
#### A）直接播放Raw目录中的音频文件
创建对象的时候直接指定文件ID，不需要设置setDataSource；不需要prepare()。
~~~kotlin
val meidaplayer = MediaPlayer.create(mContext, R.raw.network3)
meidaplayer.start()
meidaplayer.setOnCompletionListener {
    meidaplayer.release()
}
~~~
#### B）播放SD卡或网络上的音频文件
~~~kotlin
val mPlayer = MediaPlayer()
mPlayer.setOnPreparedListener(MyOnPrepareListener())
mPlayer.setOnCompletionListener(MyOnCompletionListener())
// 播放SD卡音频
mPlayer.setDataSource("../music/test.mp3")
// 播放网络音频
// mPlayer.setDataSource("https://../test.mp3")
mPlayer.prepareAsync();
mPlayer.start()
~~~
#### C）播放Asset目录中的音频文件
~~~kotlin
val mPlayer = MediaPlayer()
mPlayer.setOnPreparedListener(MyOnPrepareListener())
mPlayer.setOnCompletionListener(MyOnCompletionListener())
val fd = getAssets().openFd("samsara.mp3");
mPlayer.setDataSource(fd)
mPlayer.prepareAsync();
mPlayer.start()
~~~
## 3、AudioTrack
AudioTrack是管理和播放单一音频资源的类。它用于PCM音频流的回放，实现方式是通过write方法把数据push到AudioTrack对象。简单的应用可以参考下面的代码：
~~~kotlin
private var audioBufSize: Int = 0
private var player: AudioTrack? = null
// 初始化
audioBufSize = AudioTrack.getMinBufferSize(8000,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT)
player = AudioTrack(AudioManager.STREAM_VOICE_CALL, 8000,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT,
        audioBufSize,
        AudioTrack.MODE_STREAM)
...
// 调用播放方法启动播放器
player!!.play()
~~~
上面的代码运行之后，播放器就开始播放了，只是现在没有数据推送到AudioTrack，所以听不到声音。我们将麦克风采集到的PCM数据或解码后的PCM数据通过wirte方法写到AudioTarck缓存中，此时就能听到声音了。
~~~kotlin
player!!.write(buffer, 0, readBytes)
~~~
需要停止播放的时候，只要调用stop()方法即可停止播放。
~~~kotlin
player!!.stop()
~~~
## 实时录音播放
上面讲到了Android的录音和播放，我们使用AudioRecord，将获取到的PCM数据直接通过AudioTrack的write方法写到缓存中，即可实现功能，具体实现参考代码。
