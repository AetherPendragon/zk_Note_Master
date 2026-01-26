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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.HashSet;

public class TrashManager {
    private static final String TAG = "TrashManager";
    private final Context mContext;
    private final ContentResolver mResolver;
    private final Callback mCallback;

    public interface Callback {
        void onWidgetsNeedUpdate(HashSet<AppWidgetAttribute> widgets);

        void onListChanged();

        void onActionModeFinished();

        void onRestoreInvalid();
    }

    public TrashManager(Context context, ContentResolver resolver, Callback callback) {
        mContext = context;
        mResolver = resolver;
        mCallback = callback;
    }

    public void cleanupExpiredTrash() {
        long expireTime = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        mResolver.delete(Notes.CONTENT_NOTE_URI,
                NoteColumns.PARENT_ID + "=? AND " + NoteColumns.MODIFIED_DATE + "<?",
                new String[] { String.valueOf(Notes.ID_TRASH_FOLER), String.valueOf(expireTime) });
    }

    public void batchDelete(final boolean inTrash, final HashSet<Long> ids,
                            final HashSet<AppWidgetAttribute> widgets, final long originFolderId) {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                if (inTrash) {
                    if (!DataUtils.batchDeleteNotes(mResolver, ids)) {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    if (!DataUtils.batchMoveToTrash(mResolver, ids, originFolderId)) {
                        Log.e(TAG, "Move notes to trash folder error");
                    }
                }
                return widgets;
            }

            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> resultWidgets) {
                if (mCallback != null) {
                    mCallback.onWidgetsNeedUpdate(resultWidgets);
                    mCallback.onListChanged();
                    mCallback.onActionModeFinished();
                }
            }
        }.execute();
    }

    public void restoreSelected(final HashSet<Long> ids, final HashSet<AppWidgetAttribute> widgets) {
        new AsyncTask<Void, Void, Boolean>() {
            protected Boolean doInBackground(Void... params) {
                boolean hasInvalid = false;
                long now = System.currentTimeMillis();
                for (long id : ids) {
                    Cursor cursor = mResolver.query(
                            ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                            new String[] { NoteColumns.ORIGIN_PARENT_ID, NoteColumns.TYPE },
                            null, null, null);
                    if (cursor == null) {
                        continue;
                    }
                    long originParent = Notes.ID_ROOT_FOLDER;
                    int type = Notes.TYPE_NOTE;
                    try {
                        if (cursor.moveToFirst()) {
                            originParent = cursor.getLong(0);
                            type = cursor.getInt(1);
                        }
                    } finally {
                        cursor.close();
                    }
                    long targetParent = resolveRestoreParent(originParent);
                    if (targetParent != originParent) {
                        hasInvalid = true;
                    }
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.PARENT_ID, targetParent);
                    values.put(NoteColumns.ORIGIN_PARENT_ID, 0);
                    values.put(NoteColumns.LOCAL_MODIFIED, 1);
                    values.put(NoteColumns.MODIFIED_DATE, now);
                    mResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                            values, null, null);
                    if (type == Notes.TYPE_FOLDER) {
                        restoreNotesForFolder(id, now);
                    }
                }
                return hasInvalid;
            }

            @Override
            protected void onPostExecute(Boolean hasInvalid) {
                if (mCallback != null) {
                    if (hasInvalid != null && hasInvalid) {
                        mCallback.onRestoreInvalid();
                    }
                    mCallback.onWidgetsNeedUpdate(widgets);
                    mCallback.onListChanged();
                    mCallback.onActionModeFinished();
                }
            }
        }.execute();
    }

    public HashSet<AppWidgetAttribute> moveFolderToTrash(long folderId, long originFolderId) {
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mResolver, folderId);
        DataUtils.moveNotesToTrashForFolder(mResolver, folderId);
        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        if (!DataUtils.batchMoveToTrash(mResolver, ids, originFolderId)) {
            Log.e(TAG, "Move folder to trash error");
        }
        return widgets;
    }

    private void restoreNotesForFolder(long folderId, long now) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, folderId);
        values.put(NoteColumns.ORIGIN_PARENT_ID, 0);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.MODIFIED_DATE, now);
        mResolver.update(Notes.CONTENT_NOTE_URI, values,
                NoteColumns.PARENT_ID + "=? AND " + NoteColumns.ORIGIN_PARENT_ID + "=?",
                new String[] { String.valueOf(Notes.ID_TRASH_FOLER), String.valueOf(folderId) });
    }

    private long resolveRestoreParent(long originParentId) {
        if (originParentId == Notes.ID_ROOT_FOLDER || originParentId == Notes.ID_CALL_RECORD_FOLDER) {
            return originParentId;
        }
        if (originParentId <= 0) {
            return Notes.ID_ROOT_FOLDER;
        }
        Cursor cursor = mResolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, originParentId),
                new String[] { NoteColumns.ID, NoteColumns.PARENT_ID },
                NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
        if (cursor == null) {
            return Notes.ID_ROOT_FOLDER;
        }
        try {
            if (cursor.moveToFirst()) {
                return originParentId;
            }
        } finally {
            cursor.close();
        }
        return Notes.ID_ROOT_FOLDER;
    }
}
