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
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Calendar;

public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    private static final String TAG = "AlarmAlertActivity";
    private long mNoteId;
    private String mSnippet;
    private static final int SNIPPET_PREW_MAX_LEN = 60;
    MediaPlayer mPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        Intent intent = getIntent();

        try {
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN ? mSnippet.substring(0,
                    SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;
        }

        mPlayer = new MediaPlayer();
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            int isHabit = intent.getIntExtra("habit_alarm", 0);
            if (isHabit == 1) {
                showHabitDialog(intent);
            } else {
                showActionDialog();
            }
            playAlarmSound();
        } else {
            finish();
        }
    }

    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    private void playAlarmSound() {
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        try {
            mPlayer.setDataSource(this, url);
            mPlayer.prepare();
            mPlayer.setLooping(true);
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);
        dialog.setMessage(mSnippet);
        dialog.setPositiveButton(R.string.notealert_ok, this);
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        dialog.show().setOnDismissListener(this);
    }

    // Habit specific dialog with actions: complete, snooze, skip, abandon
    private void showHabitDialog(Intent intent) {
        View v = getLayoutInflater().inflate(R.layout.habit_alert_dialog, null);
        TextView tvTitle = (TextView) v.findViewById(R.id.habit_alert_title);
        TextView tvSnippet = (TextView) v.findViewById(R.id.habit_alert_snippet);
        final Button btnComplete = (Button) v.findViewById(R.id.habit_btn_complete);
        final Button btnSnooze10 = (Button) v.findViewById(R.id.habit_btn_snooze10);
        final Button btnSnooze30 = (Button) v.findViewById(R.id.habit_btn_snooze30);
        final Button btnSkip = (Button) v.findViewById(R.id.habit_btn_skip);
        final Button btnAbandon = (Button) v.findViewById(R.id.habit_btn_abandon);

        tvTitle.setText(getString(R.string.app_name));
        tvSnippet.setText(mSnippet);

        final AlertDialog d = new AlertDialog.Builder(this)
                .setView(v)
                .create();

        btnComplete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordHabitHistory(mNoteId, "completed", "");
                Toast.makeText(AlarmAlertActivity.this, R.string.habit_record_complete, Toast.LENGTH_SHORT).show();
                d.dismiss();
            }
        });

        btnSnooze10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scheduleSnooze(mNoteId, 10);
                Toast.makeText(AlarmAlertActivity.this, R.string.habit_snoozed, Toast.LENGTH_SHORT).show();
                d.dismiss();
            }
        });

        btnSnooze30.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scheduleSnooze(mNoteId, 30);
                Toast.makeText(AlarmAlertActivity.this, R.string.habit_snoozed, Toast.LENGTH_SHORT).show();
                d.dismiss();
            }
        });

        btnSkip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSkipReasonDialog();
                // 不要立即关闭对话框，让用户选择原因
            }
        });

        btnAbandon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abandonHabit(mNoteId);
                Toast.makeText(AlarmAlertActivity.this, R.string.habit_abandoned, Toast.LENGTH_SHORT).show();
                d.dismiss();
            }
        });

        d.setOnDismissListener(this);
        d.show();
    }

    private void showSkipReasonDialog() {
        final String[] reasons = new String[] { getString(R.string.skip_reason_busy),
                getString(R.string.skip_reason_sick), getString(R.string.skip_reason_other) };
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.habit_skip_title);
        b.setItems(reasons, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String reason = reasons[which];
                recordHabitHistory(mNoteId, "skipped", reason);
                Toast.makeText(AlarmAlertActivity.this, R.string.habit_record_skipped, Toast.LENGTH_SHORT).show();
                // 选择原因后关闭主对话框
                dialog.dismiss();
                finish();
            }
        });
        b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        b.show();
    }

    private void scheduleSnooze(long noteId, int minutes) {
        try {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));
            intent.putExtra("habit_alarm", 1);
            int req = (int) (noteId ^ 0x100000) + minutes; // unique-ish
            PendingIntent pi = PendingIntent.getBroadcast(this, req, intent, 0);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            long trigger = System.currentTimeMillis() + minutes * 60 * 1000L;
            am.set(AlarmManager.RTC_WAKEUP, trigger, pi);
        } catch (Exception e) {
            Log.e(TAG, "Schedule snooze error", e);
        }
    }

    private void abandonHabit(long noteId) {
        try {
            ContentValues values = new ContentValues();
            values.put(Notes.NoteColumns.IS_HABIT, 0);
            values.put(Notes.NoteColumns.HABIT_CONFIG, "");
            getContentResolver().update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), values, null, null);
            // cancel repeating alarm
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));
            intent.putExtra("habit_alarm", 1);
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            am.cancel(pi);
        } catch (Exception e) {
            Log.e(TAG, "Abandon habit error", e);
        }
    }

    // Record history into habit_config.history (append object {date,status,reason})
    private void recordHabitHistory(long noteId, String status, String reason) {
        try {
            Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
            Cursor c = getContentResolver().query(uri, new String[]{Notes.NoteColumns.HABIT_CONFIG}, null, null, null);
            String cfg = "";
            if (c != null) {
                if (c.moveToFirst()) cfg = c.getString(0);
                c.close();
            }
            JSONObject jo = cfg != null && cfg.length() > 0 ? new JSONObject(cfg) : new JSONObject();
            JSONArray history = jo.has("history") ? jo.getJSONArray("history") : new JSONArray();
            
            // 获取当前时间戳
            long recordTime = System.currentTimeMillis();
            
            // 创建新记录
            JSONObject newEntry = new JSONObject();
            newEntry.put("date", recordTime);
            newEntry.put("status", status);
            newEntry.put("reason", reason == null ? "" : reason);
            
            // 检查是否已经存在该日期的记录，如果有则替换
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            String recordKey = sdf.format(new java.util.Date(recordTime));
            boolean foundRecord = false;
            
            for (int i = 0; i < history.length(); i++) {
                JSONObject entry = history.getJSONObject(i);
                long entryDate = entry.optLong("date", 0);
                String entryKey = sdf.format(new java.util.Date(entryDate));
                if (recordKey.equals(entryKey)) {
                    // 替换该日期的记录
                    history.put(i, newEntry);
                    foundRecord = true;
                    break;
                }
            }
            
            // 如果没有该日期的记录，添加新记录
            if (!foundRecord) {
                history.put(newEntry);
            }
            
            jo.put("history", history);
            ContentValues values = new ContentValues();
            values.put(Notes.NoteColumns.HABIT_CONFIG, jo.toString());
            getContentResolver().update(uri, values, null, null);
            // 通知数据变化，以便日历视图刷新
            getContentResolver().notifyChange(uri, null);
        } catch (JSONException e) {
            Log.e(TAG, "Record habit history json error", e);
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();
        finish();
    }

    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}
