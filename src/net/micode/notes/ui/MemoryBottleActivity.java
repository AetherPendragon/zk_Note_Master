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
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MemoryBottleActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "MemoryBottle";
    private static final String PREF_MEMORY_FOLDER_ID = "pref_memory_bottle_folder_id";

    private static final List<MemoryEntry> sAllEntries = new ArrayList<>();
    private static final List<MemoryEntry> sRemainingEntries = new ArrayList<>();
    private static final Random sRandom = new Random();
    private static long sFolderId = Long.MIN_VALUE;

    private Button mAddButton;
    private Button mBrowseButton;
    private long mMemoryFolderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.memory_bottle);
        initResources();
        mMemoryFolderId = ensureMemoryFolder();
        loadEntriesIfNeeded();
    }

    private void initResources() {
        mAddButton = (Button) findViewById(R.id.btn_memory_add);
        mBrowseButton = (Button) findViewById(R.id.btn_memory_browse);
        mAddButton.setOnClickListener(this);
        mBrowseButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_memory_add:
                showAddDialog();
                break;
            case R.id.btn_memory_browse:
                browseMemory();
                break;
            default:
                break;
        }
    }

    private void loadEntriesIfNeeded() {
        if (mMemoryFolderId <= 0) {
            return;
        }
        if (sFolderId != mMemoryFolderId) {
            sFolderId = mMemoryFolderId;
            sAllEntries.clear();
            sRemainingEntries.clear();
        }
        if (!sAllEntries.isEmpty()) {
            return;
        }
        loadEntriesFromDatabase();
    }

    private void loadEntriesFromDatabase() {
        if (mMemoryFolderId <= 0) {
            return;
        }
        sAllEntries.clear();
        sRemainingEntries.clear();
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(
                Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.ID, NoteColumns.CREATED_DATE },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(Notes.TYPE_NOTE), String.valueOf(mMemoryFolderId) },
                NoteColumns.CREATED_DATE + " DESC");
        if (cursor == null) {
            return;
        }
        try {
            while (cursor.moveToNext()) {
                long noteId = cursor.getLong(0);
                long createdDate = cursor.getLong(1);
                String content = queryNoteContent(resolver, noteId);
                if (TextUtils.isEmpty(content)) {
                    continue;
                }
                MemoryEntry entry = new MemoryEntry(noteId, createdDate, content);
                sAllEntries.add(entry);
                sRemainingEntries.add(entry);
            }
        } finally {
            cursor.close();
        }
    }

    private void showAddDialog() {
        final EditText editText = new EditText(this);
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        editText.setPadding(padding, padding, padding, padding);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setMinLines(4);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setHint(R.string.memory_bottle_add_hint);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.memory_bottle_add_title);
        builder.setView(editText);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = builder.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String content = editText.getText().toString().trim();
                if (TextUtils.isEmpty(content)) {
                    Toast.makeText(MemoryBottleActivity.this,
                            R.string.memory_bottle_empty_input, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (createMemoryNote(content)) {
                    dialog.dismiss();
                }
            }
        });
    }

    private boolean createMemoryNote(String content) {
        if (mMemoryFolderId <= 0) {
            Toast.makeText(this, R.string.memory_bottle_folder_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        WorkingNote note = WorkingNote.createEmptyNote(this, mMemoryFolderId,
                AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                ResourceParser.getDefaultBgId(this));
        note.setWorkingText(content);
        if (!note.saveNote()) {
            Toast.makeText(this, R.string.memory_bottle_save_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        long noteId = note.getNoteId();
        long createdDate = queryNoteCreatedDate(noteId);
        MemoryEntry entry = new MemoryEntry(noteId, createdDate, content);
        sAllEntries.add(entry);
        sRemainingEntries.add(entry);
        Toast.makeText(this, R.string.memory_bottle_save_success, Toast.LENGTH_SHORT).show();
        return true;
    }

    private long queryNoteCreatedDate(long noteId) {
        Cursor cursor = getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                new String[] { NoteColumns.CREATED_DATE },
                null, null, null);
        if (cursor == null) {
            return System.currentTimeMillis();
        }
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        return System.currentTimeMillis();
    }

    private void browseMemory() {
        if (mMemoryFolderId <= 0) {
            Toast.makeText(this, R.string.memory_bottle_folder_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (sAllEntries.isEmpty()) {
            Toast.makeText(this, R.string.memory_bottle_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (sRemainingEntries.isEmpty()) {
            showBrowseFinishedDialog();
            return;
        }
        showRandomEntry();
    }

    private void showBrowseFinishedDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.memory_bottle_browse_done)
                .setPositiveButton(R.string.memory_bottle_restart, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        resetRemainingEntries();
                        showRandomEntry();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetRemainingEntries() {
        sRemainingEntries.clear();
        sRemainingEntries.addAll(sAllEntries);
    }

    private void showRandomEntry() {
        int index = sRandom.nextInt(sRemainingEntries.size());
        MemoryEntry entry = sRemainingEntries.remove(index);
        String message = formatEntryMessage(entry);
        new AlertDialog.Builder(this)
                .setTitle(R.string.memory_bottle_title)
                .setMessage(message)
                .setPositiveButton(R.string.memory_bottle_close, null)
                .show();
    }

    private String formatEntryMessage(MemoryEntry entry) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String date = format.format(new Date(entry.createdDate));
        return getString(R.string.memory_bottle_entry_format, date, entry.content);
    }

    private long ensureMemoryFolder() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        long storedId = sp.getLong(PREF_MEMORY_FOLDER_ID, Long.MIN_VALUE);
        if (storedId > 0 && DataUtils.visibleInNoteDatabase(getContentResolver(),
                storedId, Notes.TYPE_FOLDER)) {
            return storedId;
        }
        String folderName = getString(R.string.memory_bottle_folder_name);
        long folderId = queryFolderIdByName(folderName);
        if (folderId > 0) {
            sp.edit().putLong(PREF_MEMORY_FOLDER_ID, folderId).commit();
            return folderId;
        }
        folderId = createMemoryFolder(folderName);
        if (folderId > 0) {
            sp.edit().putLong(PREF_MEMORY_FOLDER_ID, folderId).commit();
            return folderId;
        }
        Toast.makeText(this, R.string.memory_bottle_folder_error, Toast.LENGTH_SHORT).show();
        return -1;
    }

    private long queryFolderIdByName(String name) {
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(
                Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.ID },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?"
                        + " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER), name },
                null);
        if (cursor == null) {
            return 0;
        }
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        return 0;
    }

    private long createMemoryFolder(String name) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.SNIPPET, name);
        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
        values.put(NoteColumns.PARENT_ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        Uri uri = getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);
        if (uri == null) {
            return 0;
        }
        try {
            return Long.parseLong(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Create memory folder failed", e);
        }
        return 0;
    }

    private String queryNoteContent(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(
                Notes.CONTENT_DATA_URI,
                new String[] { DataColumns.CONTENT },
                DataColumns.NOTE_ID + "=? AND " + DataColumns.MIME_TYPE + "=?",
                new String[] { String.valueOf(noteId), TextNote.CONTENT_ITEM_TYPE },
                null);
        if (cursor == null) {
            return "";
        }
        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            cursor.close();
        }
        return "";
    }

    private static final class MemoryEntry {
        private final long id;
        private final long createdDate;
        private final String content;

        private MemoryEntry(long id, long createdDate, String content) {
            this.id = id;
            this.createdDate = createdDate;
            this.content = content;
        }
    }
}
