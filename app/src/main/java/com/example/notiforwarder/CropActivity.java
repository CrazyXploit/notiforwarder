package com.example.notiforwarder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;

import java.io.InputStream;

public class CropActivity extends Activity {

    public static final String EXTRA_IMAGE_URI = "image_uri";
    public static final String EXTRA_CROPPED   = "cropped_bitmap";

    private CropImageView cropView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropView = findViewById(R.id.cropImage);
        Button cancelBtn = findViewById(R.id.cancelBtn);
        Button rotateBtn = findViewById(R.id.rotateBtn);
        Button doneBtn   = findViewById(R.id.doneBtn);

        String uriStr = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        if (uriStr != null) {
            try {
                Uri uri = Uri.parse(uriStr);
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                if (bmp != null) cropView.setBitmap(bmp);
            } catch (Exception ignored) {}
        }

        cancelBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        rotateBtn.setOnClickListener(v -> cropView.rotate90());

        doneBtn.setOnClickListener(v -> {
            Bitmap cropped = cropView.getCroppedBitmap();
            if (cropped == null) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
            // Pass cropped bitmap back via a static holder
            CroppedHolder.bitmap = cropped;
            setResult(RESULT_OK, new Intent());
            finish();
        });
    }

    /** Simple static holder to pass the cropped bitmap back. */
    public static class CroppedHolder {
        public static Bitmap bitmap;
    }
}