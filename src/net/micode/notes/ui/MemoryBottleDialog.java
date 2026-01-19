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
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MemoryBottleDialog extends Dialog implements View.OnClickListener {
    private static final String PREF_MEMORY_FOLDER_ID = "pref_memory_bottle_folder_id";
    private static final List<MemoryEntry> sAllEntries = new ArrayList<>();
    private static final List<MemoryEntry> sRemainingEntries = new ArrayList<>();
    private static final Random sRandom = new Random();
    private static long sFolderId = Long.MIN_VALUE;

    private final Activity mActivity;
    private Button mAddButton;
    private Button mBrowseButton;
    private long mMemoryFolderId = -1;
    private boolean mEntriesLoaded;
    private boolean mLoading;
    private boolean mBrowseLoading;
    private LoadTask mLoadTask;
    private BrowseTask mBrowseTask;
    private PendingAction mPendingAction = PendingAction.NONE;

    public MemoryBottleDialog(Activity activity) {
        super(activity, android.R.style.Theme_Light_NoTitleBar);
        mActivity = activity;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.memory_bottle);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT);
        initResources();
    }

    @Override
    public void dismiss() {
        if (mLoadTask != null) {
            mLoadTask.cancel(true);
            mLoadTask = null;
        }
        if (mBrowseTask != null) {
            mBrowseTask.cancel(true);
            mBrowseTask = null;
        }
        super.dismiss();
    }

    private void initResources() {
        mAddButton = (Button) findViewById(R.id.btn_memory_add);
        mBrowseButton = (Button) findViewById(R.id.btn_memory_browse);
        mAddButton.setOnClickListener(this);
        mBrowseButton.setOnClickListener(this);
        updateButtonState();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_memory_add:
                requestAction(PendingAction.ADD);
                break;
            case R.id.btn_memory_browse:
                requestAction(PendingAction.BROWSE);
                break;
            default:
                break;
        }
    }

    private void requestAction(PendingAction action) {
        if (mLoading || mBrowseLoading) {
            Toast.makeText(mActivity, R.string.memory_bottle_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mMemoryFolderId <= 0) {
            mPendingAction = action;
            Toast.makeText(mActivity, R.string.memory_bottle_loading, Toast.LENGTH_SHORT).show();
            startLoadTask(action == PendingAction.BROWSE);
            return;
        }
        if (action == PendingAction.BROWSE && !mEntriesLoaded) {
            mPendingAction = action;
            Toast.makeText(mActivity, R.string.memory_bottle_loading, Toast.LENGTH_SHORT).show();
            startLoadTask(true);
            return;
        }
        if (action == PendingAction.ADD) {
            showAddDialog();
        } else if (action == PendingAction.BROWSE) {
            browseMemory();
        }
    }

    private void startLoadTask(boolean loadEntries) {
        if (mLoadTask != null) {
            return;
        }
        setLoading(true);
        mLoadTask = new LoadTask(this, loadEntries);
        mLoadTask.execute();
    }

    private void setLoading(boolean loading) {
        mLoading = loading;
        updateButtonState();
    }

    private void setBrowseLoading(boolean loading) {
        mBrowseLoading = loading;
        updateButtonState();
    }

    private void updateButtonState() {
        boolean enabled = !(mLoading || mBrowseLoading);
        if (mAddButton != null) {
            mAddButton.setEnabled(enabled);
        }
        if (mBrowseButton != null) {
            mBrowseButton.setEnabled(enabled);
        }
    }

    private void showAddDialog() {
        final EditText editText = new EditText(mActivity);
        int padding = (int) (mActivity.getResources().getDisplayMetrics().density * 16);
        editText.setPadding(padding, padding, padding, padding);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setMinLines(4);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setHint(R.string.memory_bottle_add_hint);

        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
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
                    Toast.makeText(mActivity, R.string.memory_bottle_empty_input,
                            Toast.LENGTH_SHORT).show();
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
            Toast.makeText(mActivity, R.string.memory_bottle_folder_error, Toast.LENGTH_SHORT).show();
            return false;
        }
        WorkingNote note = WorkingNote.createEmptyNote(mActivity, mMemoryFolderId,
                AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                ResourceParser.getDefaultBgId(mActivity));
        note.setWorkingText(content);
        if (!note.saveNote()) {
            Toast.makeText(mActivity, R.string.memory_bottle_save_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        long noteId = note.getNoteId();
        long createdDate = queryNoteCreatedDate(noteId);
        MemoryEntry entry = new MemoryEntry(noteId, createdDate, content);
        sAllEntries.add(entry);
        sRemainingEntries.add(entry);
        mEntriesLoaded = true;
        Toast.makeText(mActivity, R.string.memory_bottle_save_success, Toast.LENGTH_SHORT).show();
        return true;
    }

    private long queryNoteCreatedDate(long noteId) {
        Cursor cursor = mActivity.getContentResolver().query(
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
        if (sAllEntries.isEmpty()) {
            Toast.makeText(mActivity, R.string.memory_bottle_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (sRemainingEntries.isEmpty()) {
            showBrowseFinishedDialog();
            return;
        }
        showRandomEntry();
    }

    private void showBrowseFinishedDialog() {
        new AlertDialog.Builder(mActivity)
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
        startBrowseTask(entry);
    }

    private void startBrowseTask(MemoryEntry entry) {
        if (mBrowseTask != null) {
            return;
        }
        setBrowseLoading(true);
        mBrowseTask = new BrowseTask(this, entry);
        mBrowseTask.execute();
    }

    private String formatEntryMessage(long createdDate, String content) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String date = format.format(new Date(createdDate));
        return mActivity.getString(R.string.memory_bottle_entry_format, date, content);
    }

    private long ensureMemoryFolder() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        long storedId = sp.getLong(PREF_MEMORY_FOLDER_ID, Long.MIN_VALUE);
        if (storedId > 0 && DataUtils.visibleInNoteDatabase(mActivity.getContentResolver(),
                storedId, Notes.TYPE_FOLDER)) {
            return storedId;
        }
        String folderName = mActivity.getString(R.string.memory_bottle_folder_name);
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
        return -1;
    }

    private long queryFolderIdByName(String name) {
        ContentResolver resolver = mActivity.getContentResolver();
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
        android.net.Uri uri = mActivity.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);
        if (uri == null) {
            return 0;
        }
        try {
            return Long.parseLong(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            return 0;
        }
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

    private List<MemoryEntry> loadEntriesFromDatabase(long folderId) {
        List<MemoryEntry> entries = new ArrayList<>();
        ContentResolver resolver = mActivity.getContentResolver();
        Cursor cursor = resolver.query(
                Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.ID, NoteColumns.CREATED_DATE },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(Notes.TYPE_NOTE), String.valueOf(folderId) },
                NoteColumns.CREATED_DATE + " DESC");
        if (cursor == null) {
            return entries;
        }
        try {
            while (cursor.moveToNext()) {
                long noteId = cursor.getLong(0);
                long createdDate = cursor.getLong(1);
                entries.add(new MemoryEntry(noteId, createdDate, ""));
            }
        } finally {
            cursor.close();
        }
        return entries;
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

    private static final class LoadResult {
        private final long folderId;
        private final List<MemoryEntry> entries;
        private final boolean loadedEntries;

        private LoadResult(long folderId, List<MemoryEntry> entries, boolean loadedEntries) {
            this.folderId = folderId;
            this.entries = entries;
            this.loadedEntries = loadedEntries;
        }
    }

    private static final class LoadTask extends AsyncTask<Void, Void, LoadResult> {
        private final WeakReference<MemoryBottleDialog> mRef;
        private final boolean mLoadEntries;

        private LoadTask(MemoryBottleDialog dialog, boolean loadEntries) {
            mRef = new WeakReference<>(dialog);
            mLoadEntries = loadEntries;
        }

        @Override
        protected LoadResult doInBackground(Void... params) {
            MemoryBottleDialog dialog = mRef.get();
            if (dialog == null) {
                return null;
            }
            long folderId = dialog.ensureMemoryFolder();
            List<MemoryEntry> entries = new ArrayList<>();
            if (folderId > 0 && mLoadEntries) {
                entries = dialog.loadEntriesFromDatabase(folderId);
            }
            return new LoadResult(folderId, entries, mLoadEntries);
        }

        @Override
        protected void onPostExecute(LoadResult result) {
            MemoryBottleDialog dialog = mRef.get();
            if (dialog == null || !dialog.isShowing()) {
                return;
            }
            dialog.mLoadTask = null;
            dialog.setLoading(false);
            if (result == null || result.folderId <= 0) {
                Toast.makeText(dialog.mActivity, R.string.memory_bottle_folder_error,
                        Toast.LENGTH_SHORT).show();
                dialog.mPendingAction = PendingAction.NONE;
                return;
            }
            dialog.mMemoryFolderId = result.folderId;
            sFolderId = result.folderId;
            if (result.loadedEntries) {
                sAllEntries.clear();
                sAllEntries.addAll(result.entries);
                sRemainingEntries.clear();
                sRemainingEntries.addAll(result.entries);
                dialog.mEntriesLoaded = true;
            } else if (sFolderId == result.folderId) {
                dialog.mEntriesLoaded = !sAllEntries.isEmpty();
            }
            PendingAction pending = dialog.mPendingAction;
            dialog.mPendingAction = PendingAction.NONE;
            if (pending == PendingAction.ADD) {
                dialog.showAddDialog();
            } else if (pending == PendingAction.BROWSE) {
                dialog.browseMemory();
            }
        }
    }

    private static final class BrowseResult {
        private final MemoryEntry entry;
        private final String content;

        private BrowseResult(MemoryEntry entry, String content) {
            this.entry = entry;
            this.content = content;
        }
    }

    private static final class BrowseTask extends AsyncTask<Void, Void, BrowseResult> {
        private final WeakReference<MemoryBottleDialog> mRef;
        private final MemoryEntry mEntry;

        private BrowseTask(MemoryBottleDialog dialog, MemoryEntry entry) {
            mRef = new WeakReference<>(dialog);
            mEntry = entry;
        }

        @Override
        protected BrowseResult doInBackground(Void... params) {
            MemoryBottleDialog dialog = mRef.get();
            if (dialog == null) {
                return null;
            }
            String content = mEntry.content;
            if (TextUtils.isEmpty(content)) {
                content = dialog.queryNoteContent(dialog.mActivity.getContentResolver(), mEntry.id);
            }
            if (TextUtils.isEmpty(content)) {
                content = dialog.mActivity.getString(R.string.memory_bottle_missing_content);
            }
            return new BrowseResult(mEntry, content);
        }

        @Override
        protected void onPostExecute(BrowseResult result) {
            MemoryBottleDialog dialog = mRef.get();
            if (dialog == null || !dialog.isShowing()) {
                return;
            }
            dialog.mBrowseTask = null;
            dialog.setBrowseLoading(false);
            if (result == null) {
                return;
            }
            String message = dialog.formatEntryMessage(result.entry.createdDate, result.content);
            new AlertDialog.Builder(dialog.mActivity)
                    .setTitle(R.string.memory_bottle_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.memory_bottle_close, null)
                    .show();
        }
    }

    private enum PendingAction {
        NONE,
        ADD,
        BROWSE
    }
}
