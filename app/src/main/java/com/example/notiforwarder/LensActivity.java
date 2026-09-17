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
    private TextView selectedText, hint, sheetCount;
    private TextRecognizer recognizer;
    private Bitmap bitmap;
    private String currentSelection = "";
    private boolean sheetVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_lens);

            lensImage    = findViewById(R.id.lensImage);
            overlay      = findViewById(R.id.lensOverlay);
            progress     = findViewById(R.id.lensProgress);
            bottomSheet  = findViewById(R.id.bottomSheet);
            selectedText = findViewById(R.id.selectedText);
            sheetCount   = findViewById(R.id.sheetCount);
            hint         = findViewById(R.id.lensHint);

            findViewById(R.id.backBtn).setOnClickListener(v -> finish());
            findViewById(R.id.copyAllBtn).setOnClickListener(v -> {
                String all = overlay.getAllText();
                if (all.isEmpty()) { toast("No text detected"); return; }
                copyToClipboard(all);
                toast("Copied all");
            });
            findViewById(R.id.copySelBtn).setOnClickListener(v -> {
                if (currentSelection.isEmpty()) { toast("Select some text first"); return; }
                copyToClipboard(currentSelection);
                toast("Copied");
            });
            findViewById(R.id.shareSelBtn).setOnClickListener(v -> {
                if (currentSelection.isEmpty()) { toast("Select some text first"); return; }
                shareText(currentSelection);
            });

            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            overlay.setListener(new TextLensView.OnSelectionChangedListener() {
                @Override public void onSelectionChanged(String text, int lineCount) {
                    currentSelection = text;
                    if (selectedText != null) selectedText.setText(text);
                    if (sheetCount != null)
                        sheetCount.setText(lineCount + (lineCount == 1 ? " line" : " lines") + " selected");
                    showSheet(true);
                }
                @Override public void onSelectionCleared() {
                    currentSelection = "";
                    showSheet(false);
                }
            });

            String uriStr = getIntent().getStringExtra(EXTRA_IMAGE_URI);
            if (uriStr == null) {
                toast("No image URI");
                finish();
                return;
            }
            loadBitmap(uriStr);
        } catch (Exception e) {
            Toast.makeText(this, "Crash prevented: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadBitmap(String uriStr) {
        try {
            Uri uri = Uri.parse(uriStr);
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) { toast("Cannot open image"); finish(); return; }
            bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) { toast("Cannot decode image"); finish(); return; }

            lensImage.setImageBitmap(bitmap);
            runOcr();
        } catch (Exception e) {
            toast("Load failed: " + e.getMessage());
            finish();
        }
    }

    private void runOcr() {
        try {
            progress.setVisibility(View.VISIBLE);
            hint.setText("Detecting text\u2026");
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            recognizer.process(image)
                .addOnSuccessListener(this::onOcrSuccess)
                .addOnFailureListener(e -> {
                    progress.setVisibility(View.GONE);
                    hint.setText("Failed: " + e.getMessage());
                });
        } catch (Exception e) {
            progress.setVisibility(View.GONE);
            hint.setText("OCR error: " + e.getMessage());
        }
    }

    private void onOcrSuccess(Text visionText) {
        try {
            progress.setVisibility(View.GONE);

            lensImage.post(() -> {
                try {
                    float[] b = getDisplayedImageBounds();
                    float offX = b[0], offY = b[1], scale = b[2];

                    List<TextLensView.TextLineBox> boxes = new ArrayList<>();
                    int idx = 0;
                    for (Text.TextBlock block : visionText.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            Rect r = line.getBoundingBox();
                            if (r == null) continue;
                            RectF mapped = new RectF(
                                    r.left   * scale + offX,
                                    r.top    * scale + offY,
                                    r.right  * scale + offX,
                                    r.bottom * scale + offY);
                            boxes.add(new TextLensView.TextLineBox(mapped, line.getText(), idx++));
                        }
                    }

                    overlay.setBoxes(boxes);
                    overlay.sortReadingOrder();

                    hint.setText(boxes.isEmpty()
                            ? "No text detected"
                            : boxes.size() + " regions \u2022 drag across lines to select");
                } catch (Exception e) {
                    hint.setText("Overlay error: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            hint.setText("Error: " + e.getMessage());
        }
    }

    private float[] getDisplayedImageBounds() {
        int viewW = lensImage.getWidth();
        int viewH = lensImage.getHeight();
        int bmpW  = bitmap.getWidth();
        int bmpH  = bitmap.getHeight();
        if (viewW == 0 || viewH == 0) return new float[]{0, 0, 1};
        float scale = Math.min((float) viewW / bmpW, (float) viewH / bmpH);
        float dispW = bmpW * scale;
        float dispH = bmpH * scale;
        float offX  = (viewW - dispW) / 2f;
        float offY  = (viewH - dispH) / 2f;
        return new float[]{offX, offY, scale};
    }

    private void showSheet(boolean show) {
        try {
            if (show == sheetVisible) return;
            sheetVisible = show;
            if (bottomSheet == null) return;

            if (show) {
                bottomSheet.setVisibility(View.VISIBLE);
                bottomSheet.post(() -> {
                    int h = bottomSheet.getHeight();
                    if (h <= 0) h = 400;
                    bottomSheet.setTranslationY(h);
                    bottomSheet.animate()
                        .translationY(0f)
                        .setDuration(200)
                        .start();
                });
            } else {
                bottomSheet.animate()
                    .translationY(bottomSheet.getHeight())
                    .setDuration(160)
                    .withEndAction(() -> {
                        if (!sheetVisible) bottomSheet.setVisibility(View.GONE);
                    })
                    .start();
            }
        } catch (Exception ignored) {}
    }

    private void copyToClipboard(String t) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("OCR", t));
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