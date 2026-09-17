package com.example.notiforwarder;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private static final int CROP_IMAGE = 2;

    private ImageView preview;
    private TextView status, resultText, emptyHint;
    private ProgressBar progress;
    private Button pickBtn, scanAllBtn;
    private TextRecognizer recognizer;
    private Bitmap currentBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview     = findViewById(R.id.preview);
        status      = findViewById(R.id.status);
        resultText  = findViewById(R.id.resultText);
        emptyHint   = findViewById(R.id.emptyHint);
        progress    = findViewById(R.id.progress);
        pickBtn     = findViewById(R.id.pickBtn);
        scanAllBtn  = findViewById(R.id.scanAllBtn);
        ImageButton copyBtn  = findViewById(R.id.copyBtn);
        ImageButton shareBtn = findViewById(R.id.shareBtn);
        ImageButton clearBtn = findViewById(R.id.clearBtn);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        pickBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE);
        });

        scanAllBtn.setOnClickListener(v -> {
            if (currentBitmap == null) { toast("Pick an image first"); return; }
            Intent i = new Intent(this, CropActivity.class);
            i.putExtra(CropActivity.EXTRA_IMAGE_URI, getLastUriString());
            startActivityForResult(i, CROP_IMAGE);
        });

        copyBtn.setOnClickListener(v -> {
            String t = resultText.getText().toString();
            if (t.isEmpty()) { toast("Nothing to copy"); return; }
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("OCR", t));
            toast("Copied");
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
            status.setText("Cleared");
        });

        handleShareIntent(getIntent());
    }

    private String lastUri = "";
    private String getLastUriString() { return lastUri; }

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
            Uri uri = data.getData();
            lastUri = uri.toString();
            loadAndCrop(uri);
        } else if (req == CROP_IMAGE) {
            if (res == RESULT_OK) {
                Bitmap cropped = CropActivity.CroppedHolder.bitmap;
                CropActivity.CroppedHolder.bitmap = null;
                if (cropped != null) {
                    preview.setImageBitmap(cropped);
                    emptyHint.setVisibility(View.GONE);
                    runOcr(cropped);
                }
            }
        }
    }

    private void loadAndCrop(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            currentBitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            if (currentBitmap == null) { toast("Could not load image"); return; }
            preview.setImageBitmap(currentBitmap);
            emptyHint.setVisibility(View.GONE);
            scanAllBtn.setEnabled(true);

            // Immediately open crop screen
            Intent i = new Intent(this, CropActivity.class);
            i.putExtra(CropActivity.EXTRA_IMAGE_URI, uri.toString());
            startActivityForResult(i, CROP_IMAGE);
        } catch (Exception e) {
            toast("Error: " + e.getMessage());
        }
    }

    private void processUri(Uri uri) {
        lastUri = uri.toString();
        loadAndCrop(uri);
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