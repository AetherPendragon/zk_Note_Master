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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import net.micode.notes.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import jp.wasabeef.richeditor.RichEditor;

public class ImageInsertHelper {
    private static final String TAG = "ImageInsertHelper";
    private final Activity mActivity;
    private final int mRequestCode;

    public static class Result {
        public final boolean success;
        public final String localPath;
        public final String html;

        private Result(boolean success, String localPath, String html) {
            this.success = success;
            this.localPath = localPath;
            this.html = html;
        }
    }

    public ImageInsertHelper(Activity activity, int requestCode) {
        mActivity = activity;
        mRequestCode = requestCode;
    }

    public void startPickImage() {
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
            } else {
                intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            }
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            }
            mActivity.startActivityForResult(intent, mRequestCode);
        } catch (ActivityNotFoundException e) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                mActivity.startActivityForResult(intent, mRequestCode);
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(mActivity, R.string.error_picture_select, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "No image picker available", ex);
            }
        }
    }

    public Result handleActivityResult(int requestCode, int resultCode, Intent data, RichEditor editor) {
        if (requestCode != mRequestCode) {
            return null;
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            return new Result(false, null, null);
        }
        Uri uri = data.getData();
        if (uri == null) {
            Toast.makeText(mActivity, R.string.error_picture_select, Toast.LENGTH_SHORT).show();
            return new Result(false, null, null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                mActivity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                Log.w(TAG, "Persistable uri permission not granted", e);
            }
        }
        String localImagePath = saveImageToLocal(uri);
        if (TextUtils.isEmpty(localImagePath)) {
            return new Result(false, null, null);
        }
        String newHtml = appendImageHtml(editor, localImagePath);
        return new Result(true, localImagePath, newHtml);
    }

    private String appendImageHtml(RichEditor editor, String localImagePath) {
        String imgHtmlTag = buildImageHtmlTag(localImagePath);
        String curHtml = normalizeEditorHtml(editor.getHtml());
        String newHtml = curHtml + imgHtmlTag;
        editor.setHtml(newHtml);
        editor.focusEditor();
        return newHtml;
    }

    private String buildImageHtmlTag(String localImagePath) {
        String imgUrl = Uri.fromFile(new File(localImagePath)).toString();
        return "<img src=\"" + imgUrl + "\" width=\"200\" height=\"200\"/><br/>";
    }

    private String normalizeEditorHtml(String html) {
        if (TextUtils.isEmpty(html) || "null".equalsIgnoreCase(html)) {
            return "";
        }
        return html;
    }

    private String saveImageToLocal(Uri uri) {
        try {
            File baseDir = mActivity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (baseDir == null) {
                baseDir = mActivity.getFilesDir();
            }
            File appDir = new File(baseDir, "note_images");
            if (!appDir.exists() && !appDir.mkdirs()) {
                Log.e(TAG, "Create image directory failed: " + appDir.getAbsolutePath());
                Toast.makeText(mActivity, R.string.error_picture_select, Toast.LENGTH_SHORT).show();
                return null;
            }
            String fileName = "note_" + System.currentTimeMillis() + ".jpg";
            File targetFile = new File(appDir, fileName);
            try (InputStream is = mActivity.getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(targetFile)) {
                if (is == null) {
                    Log.e(TAG, "Open image stream failed: " + uri);
                    Toast.makeText(mActivity, R.string.error_picture_select, Toast.LENGTH_SHORT).show();
                    return null;
                }
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                }
            }
            return targetFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Save image failed", e);
            Toast.makeText(mActivity, R.string.error_picture_select, Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}
