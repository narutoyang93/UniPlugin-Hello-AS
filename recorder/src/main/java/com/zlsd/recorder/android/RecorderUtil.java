package com.zlsd.recorder.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @Description 录音工具类
 * @Author Naruto Yang
 * @CreateDate 2021/11/23 0023
 * @Note
 */
public class RecorderUtil {
    //指定采样率 （MediaRecoder 的采样率通常是8000Hz AAC的通常是44100Hz。 设置采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
    private static final int SAMPLE_RATE_IN_HZ = 44100;
    //指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    //指定音频量化位数 ,在AudioFormaat类中指定了以下各种可能的常量。通常我们选择ENCODING_PCM_16BIT和ENCODING_PCM_8BIT PCM代表的是脉冲编码调制，它实际上是原始音频样本。
    //因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    //指定缓冲区大小。调用AudioRecord类的getMinBufferSize方法可以获得。
    private static final int MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT);//计算最小缓冲区
    //创建AudioRecord。AudioRecord类实际上不会保存捕获的音频，因此需要手动创建文件并保存下载。
    private static AudioRecord mAudioRecord;


    /**
     * 开始录音
     *
     * @param context
     * @param listener
     * @return
     */
    public static boolean start(Context context, Listener listener) {
        if (AudioRecord.ERROR_BAD_VALUE == MIN_BUFFER_SIZE || AudioRecord.ERROR == MIN_BUFFER_SIZE) {
            Toast.makeText(context, "Unable to getMinBufferSize", Toast.LENGTH_SHORT).show();
            return false;
        }

        int bufferSizeInBytes = Math.max(listener.bufferSizeInBytes, MIN_BUFFER_SIZE);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError(new Throwable("录音未授权"));
            return false;
        }
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSizeInBytes);

        switch (mAudioRecord.getState()) {
            case AudioRecord.STATE_UNINITIALIZED:
                Toast.makeText(context, "The AudioRecord is not uninitialized", Toast.LENGTH_SHORT).show();
                return false;
            case AudioRecord.RECORDSTATE_RECORDING:
                Toast.makeText(context, "The AudioRecord is recording", Toast.LENGTH_SHORT).show();
                return false;
        }

        if (listener.onStart()) mAudioRecord.startRecording();//开始录音
        else return false;
        //开启线程处理音频数据
        new Thread(() -> {
            byte[] audioData = new byte[bufferSizeInBytes];
            try {
                while (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    int readSize = mAudioRecord.read(audioData, 0, bufferSizeInBytes);
                    Log.i("naruto", "--->start: readSize=" + readSize);
                    if (readSize > 0) listener.onDataRead(audioData, readSize);
                }
                listener.onStop();
            } catch (IOException e) {
                e.printStackTrace();
                listener.onError(e);
            } finally {
                if (mAudioRecord != null) {
                    mAudioRecord.release();
                    mAudioRecord = null;
                }
            }
        }).start();
        return true;
    }

    /**
     * 停止录音
     */
    public static void stop() {
        if (mAudioRecord != null) mAudioRecord.stop();
    }


    /**
     * 播放录音
     *
     * @param file
     */
    public static void play(File file) {
        new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);//设置线程的优先级
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, MIN_BUFFER_SIZE, AudioTrack.MODE_STREAM);
            byte[] audioData = new byte[MIN_BUFFER_SIZE];
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                while (dis.available() > 0) {
                    int readSize = dis.read(audioData);
                    if (readSize == AudioTrack.ERROR_INVALID_OPERATION || readSize == AudioTrack.ERROR_BAD_VALUE)
                        continue;
                    if (readSize > 0) {//一边播放一边写入语音数据
                        if (audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED)//判断AudioTrack未初始化，停止播放的时候释放了，状态就为STATE_UNINITIALIZED
                            throw new RuntimeException("The AudioTrack is not uninitialized");
                        audioTrack.play();
                        audioTrack.write(audioData, 0, readSize);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                audioTrack.stop();
                audioTrack.release();
            }
        }).start();
    }

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2021/11/24 0024
     * @Note
     */
    public static abstract class Listener {
        int bufferSizeInBytes;//读取多少数据才回调

        public Listener() {
            this(-1);//默认bufferSizeInBytes = AudioRecord.getMinBufferSize
        }

        public Listener(int bufferSizeInBytes) {
            this.bufferSizeInBytes = bufferSizeInBytes;
        }

        protected boolean onStart() {
            return true;
        }

        protected void onStop() throws IOException {
        }

        /**
         * 读取到音频数据的时候
         *
         * @param audioData 音频数据
         * @param readSize  数据大小
         * @throws IOException
         */
        protected abstract void onDataRead(byte[] audioData, int readSize) throws IOException;

        protected abstract void onError(Throwable throwable);
    }
}
