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

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.ResourceParser;

/**
 * 通话记录服务，用于创建通话记录便签
 */
public class CallRecordService extends IntentService {
    private static final String TAG = "CallRecordService";

    public CallRecordService() {
        super("CallRecordService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            String phoneNumber = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER);
            long callDate = intent.getLongExtra("call_date", System.currentTimeMillis());
            long callDuration = intent.getLongExtra("call_duration", 0);

            if (phoneNumber != null) {
                Log.d(TAG, "Creating call note for number: " + phoneNumber + ", date: " + callDate);
                createCallNote(phoneNumber, callDate, callDuration);
            }
        }
    }

    /**
     * 创建通话记录便签并跳转到编辑视图
     * @param phoneNumber 电话号码
     * @param callDate 通话时间
     * @param callDuration 通话时长
     */
    private void createCallNote(String phoneNumber, long callDate, long callDuration) {
        try {
            Log.d(TAG, "Starting createCallNote for number: " + phoneNumber);
            
            // 创建空便签
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_CALL_RECORD_FOLDER,
                    0, Notes.TYPE_WIDGET_INVALIDE, ResourceParser.BLUE);

            // 转换为通话便签
            note.convertToCallNote(phoneNumber, callDate);

            // 设置空格作为内容，确保能保存
            note.setWorkingText(" ");

            // 保存便签获取ID
            long noteId = -1;
            if (note.saveNote()) {
                noteId = note.getNoteId();
                Log.d(TAG, "Call note created successfully for: " + phoneNumber + ", noteId: " + noteId);
            } else {
                Log.e(TAG, "Failed to create call note for: " + phoneNumber);
                return;
            }

            // 跳转到刚刚创建的通话记录便签的编辑视图
            if (noteId > 0) {
                Log.d(TAG, "Attempting to start NoteEditActivity for noteId: " + noteId);
                Intent intent = new Intent(this, NoteEditActivity.class);
                // 使用 ACTION_VIEW 以便 NoteEditActivity 正确处理并打开已有便签
                intent.setAction(Intent.ACTION_VIEW);
                // 使用正确的键名传递便签ID
                intent.putExtra(Intent.EXTRA_UID, noteId);
                intent.putExtra(Notes.INTENT_EXTRA_CALL_DATE, callDate);
                intent.putExtra(Intent.EXTRA_PHONE_NUMBER, phoneNumber);
                // 从服务启动 Activity，需要 NEW_TASK 标志；避免清除整个任务栈
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                    Log.d(TAG, "Successfully started NoteEditActivity for call note: " + noteId);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting NoteEditActivity", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating call note", e);
        }
    }

}
