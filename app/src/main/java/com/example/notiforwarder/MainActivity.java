package com.example.notiforwarder;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int PICK_IMAGE = 1;

    private ImageView preview;
    private CropOverlayView cropOverlay;
    private TextView status, resultText, emptyHint;
    private ProgressBar progress;
    private Button pickBtn, scanCropBtn, scanAllBtn;
    private TextRecognizer recognizer;
    private Bitmap currentBitmap;
    private int imageViewW, imageViewH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview      = findViewById(R.id.preview);
        cropOverlay  = findViewById(R.id.cropOverlay);
        status       = findViewById(R.id.status);
        resultText   = findViewById(R.id.resultText);
        emptyHint    = findViewById(R.id.emptyHint);
        progress     = findViewById(R.id.progress);
        pickBtn      = findViewById(R.id.pickBtn);
        scanCropBtn  = findViewById(R.id.scanCropBtn);
        scanAllBtn   = findViewById(R.id.scanAllBtn);
        ImageButton copyBtn  = findViewById(R.id.copyBtn);
        ImageButton shareBtn = findViewById(R.id.shareBtn);
        ImageButton clearBtn = findViewById(R.id.clearBtn);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        pickBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        });

        scanCropBtn.setOnClickListener(v -> {
            if (currentBitmap == null) return;
            if (!cropOverlay.hasCrop()) {
                Toast.makeText(this, "Drag on the image first", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap cropped = cropToBitmap();
            if (cropped != null) runOcr(cropped);
        });

        scanAllBtn.setOnClickListener(v -> {
            if (currentBitmap == null) return;
            runOcr(currentBitmap);
        });

        copyBtn.setOnClickListener(v -> {
            String t = resultText.getText().toString();
            if (t.isEmpty()) { toast("Nothing to copy"); return; }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("OCR", t));
            toast("Copied to clipboard");
        });

        shareBtn.setOnClickListener(v -> {
            String t = resultText.getText().toString();
            if (t.isEmpty()) { toast("Nothing to share"); return; }
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_TEXT, t);
            startActivity(Intent.createChooser(s, "Share OCR text"));
        });

        clearBtn.setOnClickListener(v -> {
            resultText.setText("");
            cropOverlay.clear();
            status.setText("Cleared");
        });

        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    private void handleShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())
                && "image/*".equals(intent.getType())) {
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) processUri(u);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null && data.getData() != null) {
            processUri(data.getData());
        }
    }

    private void processUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            if (bmp == null) { toast("Could not load image"); return; }

            currentBitmap = bmp;
            preview.setImageBitmap(bmp);
            emptyHint.setVisibility(View.GONE);
            cropOverlay.clear();
            scanCropBtn.setEnabled(true);
            scanAllBtn.setEnabled(true);
            status.setText("Image loaded. Drag to crop or tap Scan All.");
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
        }
    }

    private Bitmap cropToBitmap() {
        if (currentBitmap == null) return null;
        RectF crop = cropOverlay.getCropRect();
        float viewW = cropOverlay.getWidth();
        float viewH = cropOverlay.getHeight();

        // Map crop rect from overlay (fitCenter preview) to bitmap coordinates
        float bmpW = currentBitmap.getWidth();
        float bmpH = currentBitmap.getHeight();

        float scale = Math.min(viewW / bmpW, viewH / bmpH);
        float dispW = bmpW * scale;
        float dispH = bmpH * scale;
        float offX = (viewW - dispW) / 2f;
        float offY = (viewH - dispH) / 2f;

        float left   = (crop.left   - offX) / scale;
        float top    = (crop.top    - offY) / scale;
        float right  = (crop.right  - offX) / scale;
        float bottom = (crop.bottom - offY) / scale;

        left   = Math.max(0, left);
        top    = Math.max(0, top);
        right  = Math.min(bmpW, right);
        bottom = Math.min(bmpH, bottom);

        int l = (int) left, t = (int) top;
        int w = (int) (right - left), h = (int) (bottom - top);
        if (w <= 0 || h <= 0) return null;

        try {
            return Bitmap.createBitmap(currentBitmap, l, t, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    private void runOcr(Bitmap bmp) {
        progress.setVisibility(View.VISIBLE);
        status.setText("Scanning…");
        InputImage image = InputImage.fromBitmap(bmp, 0);
        recognizer.process(image)
            .addOnSuccessListener(this::onOcrSuccess)
            .addOnFailureListener(e -> {
                progress.setVisibility(View.GONE);
                status.setText("Failed: " + e.getMessage());
            });
    }

    private void onOcrSuccess(Text visionText) {
        progress.setVisibility(View.GONE);
        StringBuilder sb = new StringBuilder();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                sb.append(line.getText()).append("\n");
            }
            sb.append("\n");
        }
        String out = sb.toString().trim();
        if (out.isEmpty()) {
            resultText.setText("(No text found)");
            status.setText("Nothing detected");
        } else {
            resultText.setText(out);
            int chars = out.length();
            int words = out.split("\\s+").length;
            status.setText("Done • " + chars + " chars • " + words + " words");
        }
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}