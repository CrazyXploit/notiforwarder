package com.example.notiforwarder;

import android.app.Activity;
import android.animation.ObjectAnimator;
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
import android.view.animation.DecelerateInterpolator;
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
    private TextView selectedText, hint, sheetCount;
    private Button copySelBtn, shareSelBtn, copyAllBtn;
    private TextRecognizer recognizer;
    private Bitmap bitmap;
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
        sheetCount  = findViewById(R.id.sheetCount);
        hint        = findViewById(R.id.lensHint);
        Button backBtn     = findViewById(R.id.backBtn);
        copySelBtn         = findViewById(R.id.copySelBtn);
        shareSelBtn        = findViewById(R.id.shareSelBtn);
        copyAllBtn         = findViewById(R.id.copyAllBtn);

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        overlay.setListener(new TextLensView.OnSelectionChangedListener() {
            @Override public void onSelectionChanged(String text, int lineCount) {
                currentSelection = text;
                selectedText.setText(text);
                sheetCount.setText(lineCount + (lineCount == 1 ? " line" : " lines") + " selected");
                showSheet(true);
            }
            @Override public void onSelectionCleared() {
                currentSelection = "";
                showSheet(false);
            }
        });

        backBtn.setOnClickListener(v -> finish());

        copySelBtn.setOnClickListener(v -> {
            if (currentSelection.isEmpty()) { toast("Select some text first"); return; }
            copyToClipboard(currentSelection);
            toast("Copied " + currentSelection.length() + " chars");
        });

        shareSelBtn.setOnClickListener(v -> {
            if (currentSelection.isEmpty()) { toast("Select some text first"); return; }
            shareText(currentSelection);
        });

        copyAllBtn.setOnClickListener(v -> {
            String all = overlay.getAllText();
            if (all.isEmpty()) { toast("No text detected"); return; }
            copyToClipboard(all);
            toast("Copied all");
        });

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
                } else {
                    toast("Could not load image");
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

        lensImage.post(() -> {
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
                    : boxes.size() + " regions • drag across lines to select");
        });
    }

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

    private boolean sheetVisible = false;

    private void showSheet(boolean show) {
        if (show == sheetVisible) return;
        sheetVisible = show;
        if (show) {
            bottomSheet.setVisibility(View.VISIBLE);
            bottomSheet.setTranslationY(bottomSheet.getHeight());
            bottomSheet.post(() -> {
                ObjectAnimator anim = ObjectAnimator.ofFloat(
                        bottomSheet, "translationY", bottomSheet.getHeight(), 0f);
                anim.setDuration(220);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.start();
            });
        } else {
            ObjectAnimator anim = ObjectAnimator.ofFloat(
                    bottomSheet, "translationY", 0f, bottomSheet.getHeight());
            anim.setDuration(180);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.start();
            bottomSheet.postDelayed(() -> {
                if (!sheetVisible) bottomSheet.setVisibility(View.GONE);
            }, 200);
        }
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