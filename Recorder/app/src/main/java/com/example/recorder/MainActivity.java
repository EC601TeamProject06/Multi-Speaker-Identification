package com.example.recorder;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Bundle;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {
    private Button play, stop, record;
    private MediaRecorder myAudioRecorder;
    private String outputFile;
    TextView outputNumber;
    Interpreter tflite;

    private static final int SAMPLING_RATE_IN_HZ = 16000;

    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int BUFFER_SIZE_FACTOR = 1;

    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR;

    private final AtomicBoolean recordingInProgress = new AtomicBoolean(false);

    private AudioRecord recorder = null;

    private Thread recordingThread = null;

    private short[] audioData = new short[16000];

    public static float[] floatMe(short[] pcms) {
        float[] floaters = new float[pcms.length];
        for (int i = 0; i < pcms.length; i++) {
            floaters[i] = pcms[i];
            //Log.i("DATA",Float.toString(floaters[i]));
        }
        return floaters;
    }

    public class FFT {

        int n, m;

        // Lookup tables. Only need to recompute when size of FFT changes.
        double[] cos;
        double[] sin;

        public FFT(int n) {
            this.n = n;
            this.m = (int) (Math.log(n) / Math.log(2));

            // Make sure n is a power of 2
            if (n != (1 << m))
                throw new RuntimeException("FFT length must be power of 2");

            // precompute tables
            cos = new double[n / 2];
            sin = new double[n / 2];

            for (int i = 0; i < n / 2; i++) {
                cos[i] = Math.cos(-2 * Math.PI * i / n);
                sin[i] = Math.sin(-2 * Math.PI * i / n);
            }

        }

        public void fft(double[] x, double[] y) {
            int i, j, k, n1, n2, a;
            double c, s, t1, t2;

            // Bit-reverse
            j = 0;
            n2 = n / 2;
            for (i = 1; i < n - 1; i++) {
                n1 = n2;
                while (j >= n1) {
                    j = j - n1;
                    n1 = n1 / 2;
                }
                j = j + n1;

                if (i < j) {
                    t1 = x[i];
                    x[i] = x[j];
                    x[j] = t1;
                    t1 = y[i];
                    y[i] = y[j];
                    y[j] = t1;
                }
            }

            // FFT
            n1 = 0;
            n2 = 1;

            for (i = 0; i < m; i++) {
                n1 = n2;
                n2 = n2 + n2;
                a = 0;

                for (j = 0; j < n1; j++) {
                    c = cos[a];
                    s = sin[a];
                    a += 1 << (m - i - 1);

                    for (k = j; k < n; k = k + n2) {
                        t1 = c * x[k + n1] - s * y[k + n1];
                        t2 = s * x[k + n1] + c * y[k + n1];
                        x[k + n1] = x[k] - t1;
                        y[k + n1] = y[k] - t2;
                        x[k] = x[k] + t1;
                        y[k] = y[k] + t2;
                    }
                }
            }
        }
    }

    private class RecordingRunnable implements Runnable {

        @Override
        public void run() {
            int offset = 0;
            int shortRead = 0;

            final File file = new File(Environment.getExternalStorageDirectory(), "recording.pcm");
            final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
            //Log.i("SIZE",Float.toString(BUFFER_SIZE));
            try (final FileOutputStream outStream = new FileOutputStream(file)) {
                while (recordingInProgress.get() & offset < BUFFER_SIZE) {
                    shortRead = recorder.read(audioData,0,BUFFER_SIZE - offset);
                    offset += shortRead;
                    //Log.i("RAW",Float.toString(shortRead));
                    //int result = recorder.read(buffer, BUFFER_SIZE);
                    if (shortRead < 0) {
                        throw new RuntimeException("Reading of audio buffer failed: " +
                                getBufferReadFailureReason(shortRead));
                    }
                    outStream.write(buffer.array(), 0, BUFFER_SIZE);
                    buffer.clear();
                }
            } catch (IOException e) {
                throw new RuntimeException("Writing of recorded audio failed", e);
            }
        }

        private String getBufferReadFailureReason(int errorCode) {
            switch (errorCode) {
                case AudioRecord.ERROR_INVALID_OPERATION:
                    return "ERROR_INVALID_OPERATION";
                case AudioRecord.ERROR_BAD_VALUE:
                    return "ERROR_BAD_VALUE";
                case AudioRecord.ERROR_DEAD_OBJECT:
                    return "ERROR_DEAD_OBJECT";
                case AudioRecord.ERROR:
                    return "ERROR";
                default:
                    return "Unknown (" + errorCode + ")";
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        play = (Button) findViewById(R.id.play);
        stop = (Button) findViewById(R.id.stop);
        record = (Button) findViewById(R.id.record);
        stop.setEnabled(false);
        play.setEnabled(false);

        outputNumber = (TextView) findViewById(R.id.modelPredict);

        //outputFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/recording.3gp";
        /*
        myAudioRecorder = new MediaRecorder();
        myAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        myAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        myAudioRecorder.setAudioEncoder(MediaRecorder.OutputFormat.AMR_NB);
        myAudioRecorder.setOutputFile(outputFile);

         */

        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                try {
                    myAudioRecorder.prepare();
                    myAudioRecorder.start();
                } catch (IllegalStateException ise) {
                    // make something ...
                } catch (IOException ioe) {
                    // make something
                }

                 */
                ByteArrayOutputStream mainBuffer = new ByteArrayOutputStream();

                int minimumBufferSize = BUFFER_SIZE;

                byte[] readBuffer = new byte[minimumBufferSize];

                recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLING_RATE_IN_HZ,
                        CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE);

                record.setEnabled(false);
                stop.setEnabled(true);
                Toast.makeText(getApplicationContext(), "Recording started", Toast.LENGTH_LONG).show();

                recorder.startRecording();

                while (mainBuffer.size() < 32000) {

                    // read() is a blocking call
                    int bytesRead = recorder.read(readBuffer, 0, minimumBufferSize);

                    mainBuffer.write(readBuffer, 0, bytesRead);
                }

                byte b [] = mainBuffer.toByteArray();
                short[] shorts = new short[b.length/2];
                // to turn bytes to shorts as either big endian or little endian.
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                for(int x = 0; x < shorts.length; x++) {
                    // printing the characters
                    audioData[x] = shorts[x];
                    //Log.i("DATA", String.valueOf(shorts[x]));
                }

                recordingInProgress.set(true);
                /*
                recordingThread = new Thread(new RecordingRunnable(), "Recording Thread");
                recordingThread.start();

                 */
            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null == recorder) {
                    return;
                }

                recordingInProgress.set(false);

                recorder.stop();

                recorder.release();

                recorder = null;

                //recordingThread = null;
                /*
                myAudioRecorder.stop();
                myAudioRecorder.release();
                myAudioRecorder = null;

                 */

                record.setEnabled(true);
                stop.setEnabled(false);
                play.setEnabled(true);
                Toast.makeText(getApplicationContext(), "Audio Recorder successfully", Toast.LENGTH_LONG).show();
            }
        });

        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float[] pcmAsFloats = floatMe(audioData);
                double[] audioSamples = new double[16384];
                for(int i = 0; i < pcmAsFloats.length; i++) {
                    double val = pcmAsFloats[i];
                    audioSamples[i] = val;
                }
                FFT fft = new FFT(audioSamples.length);
                double[] re = audioSamples;
                double[] im = new double[audioSamples.length];
                fft.fft(re, im);
                double[] mags = new double[8000];
                for(int x = 0; x < mags.length; x++){
                    double mag = Math.sqrt(Math.pow(re[x],2)+Math.pow(im[x],2));
                    mags[x] = mag * Math.pow(10, -4);
                    //Log.i("FFT", String.valueOf(mags[x]));
                }

                String prediction = null;
                try {
                    prediction = doInference(mags);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                outputNumber.setText(prediction);

            }
        });
    }

    public String doInference(double[] mags) throws IOException {
        String predictedSpeaker = "";
        float[] modelInput = new float[mags.length];

        for(int i = 0; i < mags.length; i++) {
            modelInput[i] = (float)mags[i];
        }
        float[][][] inputVal = new float[1][8000][1];
        for (int i = 0; i < modelInput.length; ++i) {
            Log.i("SIZE", String.valueOf((modelInput.length)));
            inputVal[0][i][0] = modelInput[i];
        }

        float[][] outputVal = new float[1][2];
        tflite.run(inputVal, outputVal);
        float maxOutVal = (float) 0;
        int maxInd = 10;
        for (int i = 0; i < outputVal[0].length; ++i) {
            if (outputVal[0][i] > maxOutVal) {
                maxOutVal = outputVal[0][i];
                maxInd = i;
            }
        }
        Log.d("testval", String.valueOf(maxInd));
        Log.d("testval", String.valueOf(outputVal[0][0]));
        Log.d("testval", String.valueOf(outputVal[0][1]));
        switch (maxInd) {
            case 0: predictedSpeaker = "Hubert";
                break;
            case 1: predictedSpeaker = "Yuhan";
                break;
            case 2: predictedSpeaker = "None";
                break;
        }

        String finalRes = "Predicted Speaker: " + predictedSpeaker;
        return finalRes;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("user_speech.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}
