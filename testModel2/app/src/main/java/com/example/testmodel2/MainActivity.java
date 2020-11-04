package com.example.testmodel2;

import androidx.appcompat.app.AppCompatActivity;

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

    EditText inputNumber;
    Button inferButton;
    TextView outputNumber;
    Interpreter tflite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inputNumber = (EditText) findViewById(R.id.editText);
        outputNumber = (TextView) findViewById(R.id.modelPredict);
        inferButton = (Button) findViewById(R.id.button);

        try {
            tflite = new Interpreter(loadModelFile());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        inferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String prediction = null;
                try {
                    prediction = doInference(inputNumber.getText().toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                outputNumber.setText(prediction);
            }
        });

    }

    public String doInference(String inputString) throws IOException {
        String actualSpeaker = "";
        String predictedSpeaker = "";
        int inum = Integer.parseInt(inputString);
        String ifile = "";
        switch (inum) {
            case 1: actualSpeaker = "Anna";
                    ifile = "ffts_anna.txt";
                    break;
            case 2: actualSpeaker = "Ben";
                    ifile = "ffts_ben.txt";
                    break;
            case 3: actualSpeaker = "Cindy";
                    ifile = "ffts_cindy.txt";
                    break;
            case 4: actualSpeaker = "Dan";
                    ifile = "ffts_dan.txt";
                    break;
            case 5: actualSpeaker = "Emma";
                    ifile = "ffts_emma.txt";
                    break;
        }

        InputStream is = getApplicationContext().getAssets().open(ifile);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        is.close();
        // StandardCharsets.UTF_8.name() > JDK 7
        float[] numbers;
        String fft_string;
        fft_string = result.toString("UTF-8");
        String[] flostr = fft_string.split("\\r?\\n");
        numbers = new float[flostr.length];
        for (int i = 0; i < flostr.length; ++i) {
            float number = Float.parseFloat(flostr[i]);
            numbers[i] = number;
        }

        float[][][] inputVal = new float[1][8000][1];
        for (int i = 0; i < numbers.length; ++i) {
            inputVal[0][i][0] = numbers[i];
        }

        float[][] outputVal = new float[1][5];
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
        Log.d("testval", String.valueOf(outputVal[0][2]));
        Log.d("testval", String.valueOf(outputVal[0][3]));
        Log.d("testval", String.valueOf(outputVal[0][4]));
        switch (maxInd) {
            case 0: predictedSpeaker = "Anna";
            break;
            case 1: predictedSpeaker = "Ben";
            break;
            case 2: predictedSpeaker = "Cindy";
            break;
            case 3: predictedSpeaker = "Dan";
            break;
            case 4: predictedSpeaker = "Emma";
            break;
        }

        String finalRes = "Predicted Speaker: " + predictedSpeaker;
        return finalRes;
}

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("speech.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}