package com.adityaarora.liveedgedetection;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.adityaarora.liveedgedetection.activity.ScanActivity;
import com.adityaarora.liveedgedetection.constants.ScanConstants;
import com.adityaarora.liveedgedetection.util.ScanUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 101;
    private ImageView scannedImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scannedImageView = findViewById(R.id.scanned_image);
        findViewById(R.id.open).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScan();
            }
        });

        String[] paths = ScanUtils.getFilesList(getApplicationContext());
        if (paths != null) {
            for (String path : paths) {
                Log.d(TAG, "Path: " + path);
            }
        }
    }

    private void startScan() {
        Intent intent = new Intent(this, ScanActivity.class);
        startActivityForResult(intent, REQUEST_CODE);
    }

    private static final String TAG = "MainActivity";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (null != data && null != data.getExtras()) {
                    try {
                        JSONObject json = new JSONObject();
                        String pathFile = data.getExtras().getString(ScanConstants.PATH_RESULT);
                        String typeFile = data.getExtras().getString(ScanConstants.TYPE_RESULT);
                        String acquisitionMode = data.getExtras().getString(ScanConstants.ACQUISITION_MODE);
                        json.put(ScanConstants.PATH_RESULT, pathFile);
                        json.put(ScanConstants.TYPE_RESULT, typeFile);
                        json.put(ScanConstants.ACQUISITION_MODE, acquisitionMode);
                        Log.d(TAG, "onActivityResult: " + json.toString());
                        Bitmap baseBitmap = ScanUtils.decodeBitmapFromFile(pathFile);
                        scannedImageView.setImageBitmap(baseBitmap);
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            else if (resultCode == Activity.RESULT_CANCELED) {
                finish();
            }
        }
    }
}
