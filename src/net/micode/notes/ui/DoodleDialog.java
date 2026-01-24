/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.micode.notes.R;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class DoodleDialog extends Dialog {
    public interface OnDoodleSavedListener {
        void onSaved(String localPath);
    }

    private final Context mContext;
    private final OnDoodleSavedListener mListener;
    private DoodleView mDoodleView;

    public DoodleDialog(Context context, OnDoodleSavedListener listener) {
        super(context);
        mContext = context;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.doodle_title);
        setContentView(createContentView());
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(params);
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(12);
        root.setPadding(padding, padding, padding, padding);

        mDoodleView = new DoodleView(mContext);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0);
        canvasParams.weight = 1f;
        root.addView(mDoodleView, canvasParams);

        LinearLayout actions = new LinearLayout(mContext);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actions.setLayoutParams(actionParams);

        Button clearButton = new Button(mContext);
        clearButton.setText(R.string.doodle_clear);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDoodleView.clear();
            }
        });

        Button cancelButton = new Button(mContext);
        cancelButton.setText(android.R.string.cancel);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        Button saveButton = new Button(mContext);
        saveButton.setText(R.string.doodle_save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = saveDoodle();
                if (!TextUtils.isEmpty(path)) {
                    if (mListener != null) {
                        mListener.onSaved(path);
                    }
                    dismiss();
                } else {
                    Toast.makeText(mContext, R.string.doodle_save_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });

        actions.addView(clearButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(cancelButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(saveButton, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(actions);
        return root;
    }

    private String saveDoodle() {
        Bitmap bitmap = mDoodleView.exportBitmap();
        if (bitmap == null) {
            return null;
        }
        try {
            File baseDir = mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (baseDir == null) {
                baseDir = mContext.getFilesDir();
            }
            File appDir = new File(baseDir, "note_images");
            if (!appDir.exists() && !appDir.mkdirs()) {
                return null;
            }
            File file = new File(appDir, "doodle_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private int dpToPx(int dp) {
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        return Math.round(dm.density * dp);
    }

    private static class DoodleView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Path> mPaths = new ArrayList<>();
        private Path mCurrentPath;

        DoodleView(Context context) {
            super(context);
            mPaint.setColor(Color.BLACK);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setStrokeWidth(6f);
            setBackgroundColor(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (Path path : mPaths) {
                canvas.drawPath(path, mPaint);
            }
            if (mCurrentPath != null) {
                canvas.drawPath(mCurrentPath, mPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mCurrentPath = new Path();
                    mCurrentPath.moveTo(x, y);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mCurrentPath != null) {
                        mCurrentPath.lineTo(x, y);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (mCurrentPath != null) {
                        mCurrentPath.lineTo(x, y);
                        mPaths.add(mCurrentPath);
                        mCurrentPath = null;
                        invalidate();
                    }
                    return true;
                default:
                    return false;
            }
        }

        void clear() {
            mPaths.clear();
            mCurrentPath = null;
            invalidate();
        }

        Bitmap exportBitmap() {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return null;
            }
            Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            for (Path path : mPaths) {
                canvas.drawPath(path, mPaint);
            }
            return bitmap;
        }
    }
}
