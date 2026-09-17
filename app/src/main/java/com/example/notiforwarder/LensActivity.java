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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class LensActivity extends Activity {

    public static final String EXTRA_IMAGE_URI = "image_uri";

    private ImageView lensImage;
    private TextLensView overlay;
    private ProgressBar progress;
    private LinearLayout bottomSheet;
    private TextView selectedText, hint;
    private TextRecognizer recognizer;
    private Bitmap bitmap;
    private List<TextLensView.TextLineBox> boxes = new ArrayList<>();
    private String currentSelection = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lens);

        lensImage   = findViewById(R.id.lensImage);
        overlay     = findViewById(R.id.lensOverlay);
        progress    = findViewById(R.id.lensProgress);
        bottomSheet = findViewById(R.id.bottomSheet);
        selectedText = findViewById(R.id.selectedText);
        hint        = findViewById(R.id.lensHint);
        Button backBtn      = findViewById(R.id.backBtn);
        Button copySelBtn   = findViewById(R.id.copySelBtn);
        Button shareSelBtn  = findViewById(R.id.shareSelBtn);
        Button copyAllBtn   = findViewById(R.id.copyAllBtn);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        overlay.setOnTextSelectedListener(new TextLensView.OnTextSelectedListener() {
            @Override public void onSelected(String text) {
                currentSelection = text;
                bottomSheet.setVisibility(View.VISIBLE);
                selectedText.setText(text);
            }
            @Override public void onCleared() {
                currentSelection = "";
                bottomSheet.setVisibility(View.GONE);
            }
        });

        backBtn.setOnClickListener(v -> finish());

        copySelBtn.setOnClickListener(v -> {
            if (currentSelection.isEmpty()) { toast("Nothing selected"); return; }
            copyToClipboard(currentSelection);
            toast("Copied selection");
        });

        shareSelBtn.setOnClickListener(v -> {
            if (currentSelection.isEmpty()) { toast("Nothing selected"); return; }
            shareText(currentSelection);
        });

        copyAllBtn.setOnClickListener(v -> {
            String all = overlay.getAllText();
            if (all.isEmpty()) { toast("No text detected"); return; }
            copyToClipboard(all);
            toast("Copied all text");
        });

        // Load image
        String uriStr = getIntent().getStringExtra(EXTRA_IMAGE_URI);
        if (uriStr != null) {
            try {
                Uri uri = Uri.parse(uriStr);
                InputStream is = getContentResolver().openInputStream(uri);
                bitmap = BitmapFactory.decodeStream(is);
                if (is != null) is.close();
                if (bitmap != null) {
                    lensImage.setImageBitmap(bitmap);
                    runOcr();
                }
            } catch (Exception e) {
                toast("Load failed: " + e.getMessage());
            }
        }
    }

    private void runOcr() {
        progress.setVisibility(View.VISIBLE);
        hint.setText("Detecting text…");
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
            .addOnSuccessListener(this::onOcrSuccess)
            .addOnFailureListener(e -> {
                progress.setVisibility(View.GONE);
                hint.setText("Failed: " + e.getMessage());
            });
    }

    private void onOcrSuccess(Text visionText) {
        progress.setVisibility(View.GONE);
        boxes.clear();

        // Wait for layout so we know the actual displayed image bounds
        lensImage.post(() -> {
            float[] bounds = getDisplayedImageBounds();
            float offsetX = bounds[0], offsetY = bounds[1];
            float scale   = bounds[2];

            for (Text.TextBlock block : visionText.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    Rect r = line.getBoundingBox();
                    if (r == null) continue;
                    RectF mapped = new RectF(
                        r.left   * scale + offsetX,
                        r.top    * scale + offsetY,
                        r.right  * scale + offsetX,
                        r.bottom * scale + offsetY
                    );
                    boxes.add(new TextLensView.TextLineBox(mapped, line.getText()));
                }
            }

            overlay.setBoxes(boxes);
            hint.setText(boxes.isEmpty()
                ? "No text detected"
                : boxes.size() + " text regions • tap any to select");
        });
    }

    /**
     * Returns [offsetX, offsetY, scale] mapping bitmap pixel coords
     * to view coords of the ImageView with scaleType=fitCenter.
     */
    private float[] getDisplayedImageBounds() {
        int viewW = lensImage.getWidth();
        int viewH = lensImage.getHeight();
        int bmpW  = bitmap.getWidth();
        int bmpH  = bitmap.getHeight();
        float scale = Math.min((float) viewW / bmpW, (float) viewH / bmpH);
        float dispW = bmpW * scale;
        float dispH = bmpH * scale;
        float offX  = (viewW - dispW) / 2f;
        float offY  = (viewH - dispH) / 2f;
        return new float[]{offX, offY, scale};
    }

    private void copyToClipboard(String t) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("OCR", t));
    }

    private void shareText(String t) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, t);
        startActivity(Intent.createChooser(i, "Share"));
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}