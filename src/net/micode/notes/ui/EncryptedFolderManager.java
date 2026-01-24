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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class EncryptedFolderManager {
    private static final String TAG = "EncryptedFolderManager";
    private final Context mContext;
    private final ContentResolver mResolver;
    private final Callback mCallback;

    public interface Callback {
        void onEncryptedFolderCreated();

        void onEncryptedFolderUnlocked(NoteItemData data);
    }

    public EncryptedFolderManager(Context context, ContentResolver resolver, Callback callback) {
        mContext = context;
        mResolver = resolver;
        mCallback = callback;
    }

    public void showCreateEncryptedFolderDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_encrypted_folder, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_encrypted_folder_name);
        final EditText etQuestion = (EditText) view.findViewById(R.id.et_encrypted_question);
        final EditText etAnswer = (EditText) view.findViewById(R.id.et_encrypted_answer);
        etAnswer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setTitle(R.string.encrypted_folder_title);
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, null);
        final Dialog dialog = builder.show();
        final Button positive = (Button) dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etName.getText().toString().trim();
                String question = etQuestion.getText().toString().trim();
                String answer = etAnswer.getText().toString().trim();
                if (TextUtils.isEmpty(name)) {
                    etName.setError(mContext.getString(R.string.hint_foler_name));
                    return;
                }
                if (TextUtils.isEmpty(question)) {
                    etQuestion.setError(mContext.getString(R.string.encrypted_question_empty));
                    return;
                }
                if (TextUtils.isEmpty(answer)) {
                    etAnswer.setError(mContext.getString(R.string.encrypted_answer_empty));
                    return;
                }
                if (DataUtils.checkVisibleFolderName(mResolver, name)) {
                    Toast.makeText(mContext,
                            mContext.getString(R.string.folder_exist, name), Toast.LENGTH_LONG).show();
                    return;
                }
                long folderId = createEncryptedFolder(name, question, answer);
                if (folderId > 0) {
                    dialog.dismiss();
                    if (mCallback != null) {
                        mCallback.onEncryptedFolderCreated();
                    }
                }
            }
        });
    }

    public EncryptedFolderInfo getEncryptedFolderInfo(long folderId) {
        Cursor cursor = mResolver.query(Notes.CONTENT_DATA_URI,
                new String[] { DataColumns.DATA3, DataColumns.DATA4 },
                DataColumns.NOTE_ID + "=? AND " + DataColumns.MIME_TYPE + "=?",
                new String[] { String.valueOf(folderId), Notes.DataConstants.ENCRYPTED_FOLDER },
                null);
        if (cursor == null) {
            return null;
        }
        try {
            if (cursor.moveToFirst()) {
                String question = cursor.getString(0);
                String answerHash = cursor.getString(1);
                if (!TextUtils.isEmpty(question) && !TextUtils.isEmpty(answerHash)) {
                    return new EncryptedFolderInfo(folderId, question, answerHash);
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public void showEncryptedUnlockDialog(final EncryptedFolderInfo info, final NoteItemData data) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_encrypted_unlock, null);
        TextView tvQuestion = (TextView) view.findViewById(R.id.tv_encrypted_question);
        final EditText etAnswer = (EditText) view.findViewById(R.id.et_encrypted_answer);
        tvQuestion.setText(info.question);
        builder.setTitle(R.string.encrypted_unlock_title);
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, null);
        final Dialog dialog = builder.show();
        final Button positive = (Button) dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String answer = etAnswer.getText().toString().trim();
                if (TextUtils.isEmpty(answer)) {
                    etAnswer.setError(mContext.getString(R.string.encrypted_answer_empty));
                    return;
                }
                if (!TextUtils.equals(hashAnswer(answer), info.answerHash)) {
                    Toast.makeText(mContext, R.string.encrypted_answer_wrong,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                if (mCallback != null) {
                    mCallback.onEncryptedFolderUnlocked(data);
                }
            }
        });
    }

    private long createEncryptedFolder(String name, String question, String answer) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.SNIPPET, name);
        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        Uri uri = mResolver.insert(Notes.CONTENT_NOTE_URI, values);
        if (uri == null) {
            return -1;
        }
        long folderId = -1;
        try {
            folderId = Long.parseLong(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Create encrypted folder failed", e);
            return -1;
        }
        ContentValues dataValues = new ContentValues();
        dataValues.put(DataColumns.NOTE_ID, folderId);
        dataValues.put(DataColumns.MIME_TYPE, Notes.DataConstants.ENCRYPTED_FOLDER);
        dataValues.put(DataColumns.DATA3, question);
        dataValues.put(DataColumns.DATA4, hashAnswer(answer));
        mResolver.insert(Notes.CONTENT_DATA_URI, dataValues);
        return folderId;
    }

    private String hashAnswer(String answer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(answer.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Hash error", e);
            return "";
        }
    }

    public static class EncryptedFolderInfo {
        private final long folderId;
        private final String question;
        private final String answerHash;

        private EncryptedFolderInfo(long folderId, String question, String answerHash) {
            this.folderId = folderId;
            this.question = question;
            this.answerHash = answerHash;
        }
    }
}
