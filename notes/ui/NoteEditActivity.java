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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.webkit.JavascriptInterface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.ForegroundColorSpan;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import android.text.InputType;
import android.content.ContentValues;


import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.tool.TranslateUtils;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;
import jp.wasabeef.richeditor.RichEditor;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import java.util.Calendar;
import java.util.Date;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;


public class NoteEditActivity extends Activity implements OnClickListener,
    NoteSettingChangedListener, OnTextViewChangeListener, OnSelectionChangeListener {
    private class HeadViewHolder {
        public TextView tvModified;

        public ImageView ivAlertIcon;

        public TextView tvAlertDate;

        public ImageView ibSetBgColor;
        public TextView tvCharNum;//新增字符数显示控件
    }

    // 显示习惯配置对话框（简单表单），将结果保存为 JSON 到 habit_config
    private void showHabitConfigDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.habit_config_dialog, null);
        final Spinner spinnerPeriod = (Spinner) view.findViewById(R.id.spinner_period);
        final TimePicker tpRemindTime = (TimePicker) view.findViewById(R.id.tp_remind_time);
        final Spinner spinnerTargetType = (Spinner) view.findViewById(R.id.spinner_target_type);
        final EditText etTargetValue = (EditText) view.findViewById(R.id.et_target_value);
        final LinearLayout llWeeklyTimes = (LinearLayout) view.findViewById(R.id.ll_weekly_times);
        final EditText etWeeklyTimes = (EditText) view.findViewById(R.id.et_weekly_times);

        // 设置 TimePicker 为 24 小时制
        tpRemindTime.setIs24HourView(true);

        // setup spinners
        ArrayAdapter<CharSequence> periodAdapter = ArrayAdapter.createFromResource(this,
                R.array.habit_period_options, android.R.layout.simple_spinner_item);
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPeriod.setAdapter(periodAdapter);

        ArrayAdapter<CharSequence> targetAdapter = ArrayAdapter.createFromResource(this,
                R.array.habit_target_type_options, android.R.layout.simple_spinner_item);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTargetType.setAdapter(targetAdapter);

        // 当选择周期变化时，显示/隐藏每周次数设置
        spinnerPeriod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String period = spinnerPeriod.getSelectedItem().toString();
                if (period.equals("每周 X 次")) {
                    llWeeklyTimes.setVisibility(View.VISIBLE);
                } else {
                    llWeeklyTimes.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // prefill if existing
        try {
            String cfg = mWorkingNote.getHabitConfig();
            if (cfg != null && cfg.length() > 0) {
                JSONObject jo = new JSONObject(cfg);
                String period = jo.optString("period", "每日");
                spinnerPeriod.setSelection(periodAdapter.getPosition(period));
                
                // 设置提醒时间
                String remindTime = jo.optString("remind_time", "08:00");
                if (!remindTime.isEmpty()) {
                    String[] parts = remindTime.split(":");
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);
                    tpRemindTime.setHour(hour);
                    tpRemindTime.setMinute(minute);
                }
                
                // 设置每周次数
                if (period.equals("每周 X 次")) {
                    llWeeklyTimes.setVisibility(View.VISIBLE);
                    etWeeklyTimes.setText(String.valueOf(jo.optInt("weekly_times", 3)));
                }
                
                spinnerTargetType.setSelection(targetAdapter.getPosition(jo.optString("target_type", "连续天数")));
                etTargetValue.setText(String.valueOf(jo.optInt("target_value", 0)));
            }
        } catch (Exception e) {
            // ignore
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.habit_config_title);
        b.setView(view);
        b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String period = spinnerPeriod.getSelectedItem().toString();
                
                // 获取提醒时间
                int hour = tpRemindTime.getHour();
                int minute = tpRemindTime.getMinute();
                String remind = String.format("%02d:%02d", hour, minute);
                
                String targetType = spinnerTargetType.getSelectedItem().toString();
                int targetValue = 0;
                try {
                    targetValue = Integer.parseInt(etTargetValue.getText().toString());
                } catch (Exception e) {
                    targetValue = 0;
                }
                
                // 获取每周次数
                int weeklyTimes = 3;
                if (period.equals("每周 X 次")) {
                    try {
                        weeklyTimes = Integer.parseInt(etWeeklyTimes.getText().toString());
                    } catch (Exception e) {
                        weeklyTimes = 3;
                    }
                }
                
                JSONObject jo = new JSONObject();
                try {
                    jo.put("period", period);
                    jo.put("remind_time", remind);
                    jo.put("target_type", targetType);
                    jo.put("target_value", targetValue);
                    jo.put("weekly_times", weeklyTimes);
                } catch (JSONException e) {
                    // ignore
                }
                if (mWorkingNote != null) {
                    mWorkingNote.setHabit(true, jo.toString());
                    // schedule alarm according to remind_time
                    scheduleHabitAlarm(mWorkingNote);
                    // refresh the menu to update the habit settings button
                    invalidateOptionsMenu();
                }
            }
        });
        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    // Schedule or cancel habit alarm according to current WorkingNote.habit_config
    private void scheduleHabitAlarm(WorkingNote note) {
        if (note == null || !note.isHabit()) {
            // cancel any existing alarm
            cancelHabitAlarm(note);
            return;
        }
        String cfg = note.getHabitConfig();
        if (cfg == null || cfg.length() == 0) {
            return;
        }
        try {
            JSONObject jo = new JSONObject(cfg);
            String remind = jo.optString("remind_time", "");
            if (remind == null || remind.length() == 0) {
                // no remind time provided
                return;
            }
            String[] parts = remind.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            // ensure note saved and has id
            if (!note.existInDatabase()) {
                note.saveNote();
            }
            if (!note.existInDatabase()) return;

            long noteId = note.getNoteId();
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));
            intent.putExtra("habit_alarm", 1);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));

            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            long trigger = c.getTimeInMillis();
            long now = System.currentTimeMillis();
            if (trigger <= now) {
                // schedule for next day
                trigger += AlarmManager.INTERVAL_DAY;
            }

            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, trigger,
                    AlarmManager.INTERVAL_DAY, pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Schedule habit alarm error", e);
        }
    }

    private void cancelHabitAlarm(WorkingNote note) {
        try {
            if (note == null || !note.existInDatabase()) return;
            long noteId = note.getNoteId();
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId));
            intent.putExtra("habit_alarm", 1);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            alarmManager.cancel(pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Cancel habit alarm error", e);
        }
    }

    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";

    private HeadViewHolder mNoteHeaderHolder;

    private View mHeadViewPanel;

    private View mNoteBgColorSelector;

    private View mFontSizeSelector;


    private RichEditor mNoteEditor;

    private View mNoteEditorPanel;

    private WorkingNote mWorkingNote;

    // habit calendar UI
    private LinearLayout mHabitPanel;
    private Button mBtnPrevMonth;
    private Button mBtnNextMonth;
    private TextView mTvHabitMonth;
    private GridLayout mHabitCalendarGrid;
    private TextView mTvHabitTotal;
    private ProgressBar mProgressHabitGoal;

    // calendar state
    private Calendar mRenderCalendar = Calendar.getInstance();

    // habit ui

    private SharedPreferences mSharedPrefs;
    private int mFontSizeId;

    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    private static final int PHOTO_REQUEST =100;  //请求照片
    public static final String TAG_CHECKED = String.valueOf('\u221A');
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');

    private LinearLayout mEditTextList;

    // translation state
    private boolean mHasTranslation = false;
    private String mOriginalContent = null;
    private ProgressDialog mProgressDialog;
    private String mTargetLangCode = "en"; // default

    private String mUserQuery;
    private Pattern mPattern;
    private ImageInsertHelper mImageInsertHelper; // 图片插入助手
    private String mText; // 富文本编辑器内容
    //新增字符统计方法，排除空白字符和HTML标签

    private int calcVisibleCharCount(CharSequence s) {
        if (s == null) return 0;
        String text = s.toString();
        // 移除HTML标签
        String plainText = text.replaceAll("<[^>]*>", "");
        // 移除空白字符
        String filtered = plainText.replaceAll("\\s+", "");
        return filtered.length();
    }

    private void updateCharNumForSelection(CharSequence text, int selStart, int selEnd) {
        if (text == null) {
            mNoteHeaderHolder.tvCharNum.setText("字符数：0");
            return;
        }
        int totalCount = calcVisibleCharCount(text);
        if (selStart == selEnd) {
            // 显示总字符数
            mNoteHeaderHolder.tvCharNum.setText("字符数：" + totalCount);
        } else {
            // 选中文本统计
            String selectedText = text.subSequence(selStart, selEnd).toString();
            int selectedCount = calcVisibleCharCount(selectedText);
            mNoteHeaderHolder.tvCharNum.setText("字符数：" + totalCount + "（选中：" + selectedCount + "）");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }
    
    // JavaScript接口类，用于接收富文本编辑器的选中文本
    private class JsInterface {
        @JavascriptInterface
        public void getSelectedText(String selectedText, String totalText) {
            // 计算选中字符数和总字符数
            int selectedCount = calcVisibleCharCount(selectedText);
            int totalCount = calcVisibleCharCount(totalText);
            
            // 更新字符统计显示
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (selectedCount > 0) {
                        mNoteHeaderHolder.tvCharNum.setText("字符数：" + totalCount + "（选中：" + selectedCount + "）");
                    } else {
                        mNoteHeaderHolder.tvCharNum.setText("字符数：" + totalCount);
                    }
                }
            });
        }
    }

    /**
     * Current activity may be killed when the memory is low. Once it is killed, for another time
     * user load this activity, we should restore the former state
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    private boolean initActivityState(Intent intent) {
        /**
         * If the user specified the {@link Intent#ACTION_VIEW} but not provided with id,
         * then jump to the NotesListActivity
         */
        mWorkingNote = null;
        // 新增：Intent/Action 判空
        if (intent == null || TextUtils.isEmpty(intent.getAction())) {
            Log.e(TAG, "Intent 或 Action 为空");
            finish();
            return false;
        }
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            /**
             * Starting from the searched result
             */
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else if(TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // New note
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // Parse call-record note
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    private void initNoteScreen() {
        // 检查必要的视图是否已初始化
        if (mHeadViewPanel == null || mNoteEditorPanel == null || mNoteEditor == null) {
            Log.e(TAG, "Some views are not initialized! Check initResources method.");
            return;
        }

        // 设置富文本编辑器字体大小
        setRichEditorFontSize(mFontSizeId);

        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
            mNoteEditor.setVisibility(View.GONE);
            mEditTextList.setVisibility(View.VISIBLE);
        }
        else {
            // 切换到富文本模式
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);

            // 1. 获取笔记原始内容（为空则赋空字符串，避免空指针）
            String content = mWorkingNote.getContent() == null ? "" : mWorkingNote.getContent();

            // 2. 旧文本（非 HTML）转换为 HTML，确保图片与换行能正确展示
            String finalHtml;
            if (TextUtils.isEmpty(content)) {
                finalHtml = "";
            } else if (isHtmlContent(content)) {
                finalHtml = content;
            } else {
                finalHtml = convertLegacyContentToHtml(content);
            }

            // 3. 核心：用 RichEditor 加载 HTML 内容
            mNoteEditor.setHtml(finalHtml);
            // 初始化字符统计显示
            updateCharNumForSelection(finalHtml, 0, 0);
        }

        // 设置背景颜色
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            View v = findViewById(sBgSelectorSelectionMap.get(id));
            if (v != null) {
                v.setVisibility(View.GONE);
            }
        }
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        /**
         * TODO: Add the menu for setting alert. Currently disable it because the DateTimePicker
         * is not ready
         */
        showAlertHeader();
        // render habit calendar if this note is a habit
        if (mWorkingNote.isHabit()) {
            mHabitPanel.setVisibility(View.VISIBLE);
            // ensure calendar shows current month
            mRenderCalendar = Calendar.getInstance();
            renderHabitPanel();
        } else {
            mHabitPanel.setVisibility(View.GONE);
        }
    }

    // Render calendar and statistics from habit_config.history
    private void renderHabitPanel() {
        if (mWorkingNote == null || !mWorkingNote.isHabit()) return;
        Map<String, String> dayStatus = new HashMap<String, String>(); // yyyy-MM-dd -> status
        int totalCompleted = 0;
        
        // 优先使用 WorkingNote 中的 habit_config，避免日历点击改状态后 DB 缓存导致视图不刷新
        String cfg = (mWorkingNote != null) ? mWorkingNote.getHabitConfig() : null;
        if (cfg == null || cfg.length() == 0) {
            if (mWorkingNote != null && mWorkingNote.existInDatabase()) {
                try {
                    Uri uri = ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId());
                    Cursor c = getContentResolver().query(uri, new String[]{Notes.NoteColumns.HABIT_CONFIG}, null, null, null);
                    if (c != null) {
                        if (c.moveToFirst()) {
                            String fromDb = c.getString(0);
                            if (fromDb != null && fromDb.length() > 0) cfg = fromDb;
                        }
                        c.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read latest habit config", e);
                }
            }
        }
        if (cfg == null) cfg = "";
        
        try {
            if (cfg != null && cfg.length() > 0) {
                JSONObject jo = new JSONObject(cfg);
                if (jo.has("history")) {
                    JSONArray history = jo.getJSONArray("history");
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject e = history.getJSONObject(i);
                        long date = e.optLong("date", 0);
                        String status = e.optString("status", "");
                        if (date > 0) {
                            Calendar c = Calendar.getInstance();
                            c.setTimeInMillis(date);
                            String key = sdf.format(c.getTime());
                            dayStatus.put(key, status);
                            if ("completed".equals(status)) totalCompleted++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore parse errors
            Log.e(TAG, "Failed to parse habit config", e);
        }

        // prepare month display
        Calendar cal = (Calendar) mRenderCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);
        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy年MM月", Locale.getDefault());
        mTvHabitMonth.setText(monthFmt.format(cal.getTime()));

        // clear grid
        mHabitCalendarGrid.removeAllViews();

        // add weekday headers
        String[] w = new String[] {"日","一","二","三","四","五","六"};
        for (int i = 0; i < 7; i++) {
            TextView tv = new TextView(this);
            tv.setText(w[i]);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(6,6,6,6);
            mHabitCalendarGrid.addView(tv);
        }

        int firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0..6
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // fill blanks
        for (int i = 0; i < firstWeekday; i++) {
            TextView tv = new TextView(this);
            mHabitCalendarGrid.addView(tv);
        }

        SimpleDateFormat sdfKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (int d = 1; d <= daysInMonth; d++) {
            cal.set(Calendar.DAY_OF_MONTH, d);
            String key = sdfKey.format(cal.getTime());
            TextView cell = new TextView(this);
            cell.setText(String.valueOf(d));
            cell.setGravity(android.view.Gravity.CENTER);
            cell.setPadding(12,12,12,12);
            String status = dayStatus.get(key);
            String icon = "";
            boolean isToday = isSameDay(cal, Calendar.getInstance());
            if (status != null) {
                if ("completed".equals(status)) {
                    cell.setBackgroundResource(isToday ? R.drawable.habit_day_today_bg : R.drawable.habit_day_completed_bg);
                    icon = "✅ ";
                } else if ("skipped".equals(status)) {
                    cell.setBackgroundResource(isToday ? R.drawable.habit_day_today_bg : R.drawable.habit_day_skipped_bg);
                    icon = "➖ ";
                } else {
                    cell.setBackgroundResource(isToday ? R.drawable.habit_day_today_bg : R.drawable.habit_day_pending_bg);
                    icon = "🔄 ";
                }
            } else {
                // future or empty
                if (isToday) {
                    cell.setBackgroundResource(R.drawable.habit_day_today_bg);
                    icon = "🔄 ";
                }
            }
            // 设置文本为图标+日期
            cell.setText(icon + String.valueOf(d));
            final String selKey = key;
            final Calendar clickedCal = (Calendar) cal.clone();
            final String cellStatus = status;
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 检查点击的日期是否是过去的日期或今天
                    Calendar today = Calendar.getInstance();
                    today.set(Calendar.HOUR_OF_DAY, 0);
                    today.set(Calendar.MINUTE, 0);
                    today.set(Calendar.SECOND, 0);
                    today.set(Calendar.MILLISECOND, 0);
                    
                    if (clickedCal.before(today) || isSameDay(clickedCal, today)) {
                        // 过去的日期或今天，显示三个选项
                        AlertDialog.Builder builder = new AlertDialog.Builder(NoteEditActivity.this);
                        builder.setTitle("设置打卡状态");
                        builder.setItems(new CharSequence[]{"设为✅ 已完成", "设为➖ 跳过", "设为🔄 待打卡"}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String status = "";
                                String reason = "";
                                
                                switch (which) {
                                    case 0:
                                        // 设为已完成
                                        status = "completed";
                                        reason = clickedCal.before(today) ? "补打卡" : "";
                                        break;
                                    case 1:
                                        // 设为跳过
                                        status = "skipped";
                                        reason = "手动设置跳过";
                                        break;
                                    case 2:
                                        // 设为待打卡
                                        status = "pending";
                                        reason = "手动设置待打卡";
                                        break;
                                }
                                
                                // 记录点击日期的打卡状态，传入点击的Calendar对象
                                recordHabitHistory(mWorkingNote.getNoteId(), status, reason, clickedCal);
                                renderHabitPanel();
                            }
                        });
                        builder.show();
                    } else {
                        // 未来日期，不允许打卡
                        Toast.makeText(NoteEditActivity.this, "未来日期暂不允许设置状态", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            mHabitCalendarGrid.addView(cell);
        }

        int goal = 0;
        try { if (cfg != null && cfg.length() > 0) { JSONObject j = new JSONObject(cfg); goal = j.optInt("target_value", 0); } } catch (Exception e) {}

        mTvHabitTotal.setText("总计" + totalCompleted + "天");
        if (goal > 0) {
            int prog = Math.min(100, (int) ((totalCompleted * 100L) / goal));
            mProgressHabitGoal.setProgress(prog);
        } else {
            mProgressHabitGoal.setProgress(0);
        }
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
    
    // 计算完成的天数
    private int calculateCompletedDays(JSONArray history) {
        int count = 0;
        try {
            Log.d(TAG, "Calculating completed days from history with length: " + history.length());
            for (int i = 0; i < history.length(); i++) {
                JSONObject entry = history.getJSONObject(i);
                String status = entry.optString("status", "");
                long date = entry.optLong("date", 0);
                Log.d(TAG, "History entry " + i + ": date=" + date + ", status=" + status);
                if ("completed".equals(status)) {
                    count++;
                }
            }
            Log.d(TAG, "Calculated completed days: " + count);
        } catch (JSONException e) {
            Log.e(TAG, "Calculate completed days error", e);
        }
        return count;
    }
    
    // 检查并显示目标达成弹窗
    private void checkAndShowGoalAchievement(JSONObject config, int completedBefore, int completedAfter) {
        try {
            int goal = config.optInt("target_value", 0);
            Log.d(TAG, "Goal achievement check - completedBefore: " + completedBefore + ", completedAfter: " + completedAfter + ", goal: " + goal);
            if (goal > 0) {
                // 检查是否刚刚达成目标（更新后达到或超过目标，更新前未达到）
                boolean shouldShowDialog = completedAfter >= goal && completedBefore < goal;
                Log.d(TAG, "Should show celebration dialog: " + shouldShowDialog);
                if (shouldShowDialog) {
                    // 显示喝彩弹窗
                    showCelebrationDialog();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Check goal achievement error", e);
        }
    }
    
    // 显示喝彩弹窗
    private void showCelebrationDialog() {
        // 确保Activity处于可见状态，避免Window handle错误
        if (isFinishing() || isDestroyed()) {
            Log.d(TAG, "Activity is not visible, skipping celebration dialog");
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🎉 恭喜你！");
        builder.setMessage("你已经达成了习惯目标！继续保持，加油！");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Record history into habit_config.history (same logic as AlarmAlertActivity)
    private void recordHabitHistory(long noteId, String status, String reason, Calendar recordCal) {
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
            
            // 计算更新前的完成天数
            int completedBefore = calculateCompletedDays(history);
            
            // 获取点击的日期的时间戳
            long recordTime = recordCal.getTimeInMillis();
            
            // 创建新记录
            JSONObject newEntry = new JSONObject();
            newEntry.put("date", recordTime);
            newEntry.put("status", status);
            newEntry.put("reason", reason == null ? "" : reason);
            
            // 检查是否已经存在该日期的记录，如果有则替换
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String recordKey = sdf.format(new Date(recordTime));
            boolean foundRecord = false;
            
            for (int i = 0; i < history.length(); i++) {
                JSONObject entry = history.getJSONObject(i);
                long entryDate = entry.optLong("date", 0);
                String entryKey = sdf.format(new Date(entryDate));
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
            
            // 计算更新后的完成天数
            int completedAfter = calculateCompletedDays(history);
            
            jo.put("history", history);
            String updatedConfig = jo.toString();
            
            // 更新数据库
            ContentValues values = new ContentValues();
            values.put(Notes.NoteColumns.HABIT_CONFIG, updatedConfig);
            getContentResolver().update(uri, values, null, null);
            getContentResolver().notifyChange(uri, null);

            // 同时更新本地 WorkingNote 缓存，确保 renderHabitPanel 使用最新数据、日历视图立即刷新
            if (mWorkingNote != null) {
                mWorkingNote.setHabit(true, updatedConfig);
            }
            
            // 检查是否达成目标
            checkAndShowGoalAchievement(jo, completedBefore, completedAfter);
        } catch (JSONException e) {
            Log.e(TAG, "Record habit history json error", e);
        }
    }
    
    // 兼容原有调用的重载方法
    private void recordHabitHistory(long noteId, String status, String reason) {
        recordHabitHistory(noteId, status, reason, Calendar.getInstance());
    }

    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            if (time > mWorkingNote.getAlertDate()) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        };
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /**
         * For new note without note id, we should firstly save it to
         * generate a id. If the editing note is not worth saving, there
         * is no id which is equivalent to create new note
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }

        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean inRangeOfView(View view, MotionEvent ev) {
        int []location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
                    return false;
                }
        return true;
    }

    private void initResources() {
        // 初始化mHeadViewPanel，这是关键修复
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.tvCharNum = (TextView) findViewById(R.id.tv_char_num);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);

        // 初始化富文本编辑器
        mNoteEditor = (RichEditor) findViewById(R.id.note_edit_view);
        if (mNoteEditor == null) {
            Log.e(TAG, "RichEditor is null! Check layout file.");
            return;
        }

        mImageInsertHelper = new ImageInsertHelper(this, PHOTO_REQUEST);

        // 初始化富文本编辑器配置
        initRichEditor();
        
        // 注册JavaScript接口，用于获取选中文本
        mNoteEditor.addJavascriptInterface(new JsInterface(), "noteEditor");

        // 设置富文本编辑器监听器
        mNoteEditor.setOnTextChangeListener(new RichEditor.OnTextChangeListener() {
            @Override
            public void onTextChange(String text) {
                String safeText = text == null ? "" : text;
                mText = safeText;
                // 更新修改时间和字符数显示
                mNoteHeaderHolder.tvModified.setText(
                        DateUtils.formatDateTime(NoteEditActivity.this,
                                mWorkingNote.getModifiedDate(),
                                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE
                                        | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR)
                );
                // 更新字符数显示
                updateCharNumForSelection(safeText, 0, 0);
            }
        });

        // 开启图文混排支持
        mNoteEditor.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        // 初始化其他视图
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            if (iv != null) {
                iv.setOnClickListener(this);
            }
        }

        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            if (view != null) {
                view.setOnClickListener(this);
            }
        };
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        if(mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }

        // 初始化编辑列表和富文本按钮
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);
        initRichEditorButtons();
        // habit UI binds
        mHabitPanel = (LinearLayout) findViewById(R.id.habit_panel);
        mBtnPrevMonth = (Button) findViewById(R.id.btn_prev_month);
        mBtnNextMonth = (Button) findViewById(R.id.btn_next_month);
        mTvHabitMonth = (TextView) findViewById(R.id.tv_habit_month);
        mHabitCalendarGrid = (GridLayout) findViewById(R.id.habit_calendar_grid);
        mTvHabitTotal = (TextView) findViewById(R.id.tv_habit_total);
        mProgressHabitGoal = (ProgressBar) findViewById(R.id.progress_habit_goal);

        mBtnPrevMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRenderCalendar.add(Calendar.MONTH, -1);
                renderHabitPanel();
            }
        });
        mBtnNextMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRenderCalendar.add(Calendar.MONTH, 1);
                renderHabitPanel();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
            mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.VISIBLE);
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                //mNoteEditor.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
                setRichEditorFontSize(mFontSizeId);
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (clearSettingState()) {
            return;
        }

        if (mHasTranslation) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.translate_confirm_keep_title);
            b.setPositiveButton(R.string.translate_keep, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    saveNote();
                    finish();
                }
            });
            b.setNeutralButton(R.string.translate_discard, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (mOriginalContent != null) {
                        mNoteEditor.setHtml(mOriginalContent);
                        mHasTranslation = false;
                    }
                    saveNote();
                    finish();
                }
            });
            b.setNegativeButton(R.string.translate_cancel_action, null);
            b.show();
            return;
        }

        saveNote();
        super.onBackPressed();
    }

    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    public void onBackgroundColorChanged() {
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                View.VISIBLE);
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        // 习惯相关菜单处理
        boolean isHabit = mWorkingNote.isHabit();
        menu.findItem(R.id.menu_set_habit).setVisible(!isHabit);
        menu.findItem(R.id.menu_habit_settings).setVisible(isHabit);
        menu.findItem(R.id.menu_stop_habit).setVisible(isHabit);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_new_note:
                createNewNote();
                break;
            case R.id.menu_delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_note));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteCurrentNote();
                                finish();
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case R.id.menu_font_size:
                mFontSizeSelector.setVisibility(View.VISIBLE);
                findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
                break;
            case R.id.menu_list_mode:
                mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                        TextNote.MODE_CHECK_LIST : 0);
                break;
            case R.id.menu_share:
                getWorkingText();
                sendTo(this, mWorkingNote.getContent());
                break;
            case R.id.menu_translate:
                showTranslateDialog();
                break;
            case R.id.menu_send_to_desktop:
                sendToDesktop();
                break;
            case R.id.menu_alert:
                setReminder();
                break;
            case R.id.menu_delete_remind:
                mWorkingNote.setAlertDate(0, false);
                break;
            case R.id.menu_set_habit:
                // 设置为习惯便签并显示设置对话框
                mWorkingNote.setHabit(true, "");
                showHabitConfigDialog();
                break;
            case R.id.menu_habit_settings:
                // 显示习惯设置对话框
                showHabitConfigDialog();
                break;
            case R.id.menu_stop_habit:
                // 停止习惯，转换为普通便签
                AlertDialog.Builder stopHabitBuilder = new AlertDialog.Builder(this);
                stopHabitBuilder.setTitle(R.string.habit_config_title);
                stopHabitBuilder.setMessage("确定要停止这个习惯吗？");
                stopHabitBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 将习惯便签转换为普通便签
                        mWorkingNote.setHabit(false, "");
                        // 取消习惯提醒
                        cancelHabitAlarm(mWorkingNote);
                        // 隐藏习惯面板
                        mHabitPanel.setVisibility(View.GONE);
                        // 刷新菜单
                        invalidateOptionsMenu();
                        // 刷新UI
                        renderHabitPanel();
                    }
                });
                stopHabitBuilder.setNegativeButton(android.R.string.cancel, null);
                stopHabitBuilder.show();
                break;
            case R.id.menu_insert_image:
                // 插入图片
                if (mImageInsertHelper != null) {
                    mImageInsertHelper.startPickImage();
                }
                break;
            default:
                break;
        }
        return true;
    }

    private void setReminder() {
         showReminderChoiceDialog();
    }
    private void showReminderChoiceDialog() {
        final String[] options = new String[] {
                getString(R.string.reminder_mode_absolute),
                getString(R.string.reminder_mode_relative)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.reminder_mode_title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showAbsoluteReminderDialog();
                        } else {
                            showRelativeReminderDialog();
                        }
                    }
                })
                .show();
    }

    private void showAbsoluteReminderDialog() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date, true);
            }
        });
        d.show();
    }

    private void showRelativeReminderDialog() {
        final EditText hoursInput = new EditText(this);
        final EditText minutesInput = new EditText(this);
        final EditText secondsInput = new EditText(this);
        hoursInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        secondsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        hoursInput.setHint(R.string.reminder_hours_hint);
        minutesInput.setHint(R.string.reminder_minutes_hint);
        secondsInput.setHint(R.string.reminder_seconds_hint);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 16);
        container.setPadding(padding, padding, padding, padding);
        container.addView(hoursInput);
        container.addView(minutesInput);
        container.addView(secondsInput);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.reminder_duration_title))
                .setView(container)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String hoursText = hoursInput.getText().toString().trim();
                        String minutesText = minutesInput.getText().toString().trim();
                        String secondsText = secondsInput.getText().toString().trim();
                        if (TextUtils.isEmpty(hoursText)
                                && TextUtils.isEmpty(minutesText)
                                && TextUtils.isEmpty(secondsText)) {
                            showToast(R.string.reminder_duration_empty);
                            return;
                        }
                        int hours;
                        int minutes;
                        int seconds;
                        try {
                            hours = TextUtils.isEmpty(hoursText) ? 0 : Integer.parseInt(hoursText);
                            minutes = TextUtils.isEmpty(minutesText) ? 0 : Integer.parseInt(minutesText);
                            seconds = TextUtils.isEmpty(secondsText) ? 0 : Integer.parseInt(secondsText);
                        } catch (NumberFormatException e) {
                            showToast(R.string.reminder_duration_invalid);
                            return;
                        }
                        if (hours < 0 || minutes < 0 || seconds < 0
                                || (hours == 0 && minutes == 0 && seconds == 0)) {
                            showToast(R.string.reminder_duration_invalid);
                            return;
                        }
                        long delta = hours * 60L * 60L * 1000L
                                + minutes * 60L * 1000L
                                + seconds * 1000L;
                        long target = System.currentTimeMillis() + delta;
                        mWorkingNote.setAlertDate(target, true);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Share note to apps that support {@link Intent#ACTION_SEND} action
     * and {@text/plain} type
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    private void showTranslateDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.translate_dialog, null);
        final Spinner spinner = (Spinner) v.findViewById(R.id.spinner_target_lang);
        final String[] langNames = new String[] {"英语", "中文", "日语", "韩语", "法语", "德语", "西班牙语"};
        final String[] langCodes = new String[] {"en", "zh-CHS", "ja", "ko", "fr", "de", "es"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, langNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.translate_dialog_title);
        b.setView(v);
        final AlertDialog d = b.create();

        Button btnCancel = (Button) v.findViewById(R.id.btn_cancel_translate);
        Button btnConfirm = (Button) v.findViewById(R.id.btn_confirm_translate);
        btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                d.dismiss();
            }
        });
        btnConfirm.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = spinner.getSelectedItemPosition();
                String code = "en";
                switch (pos) {
                    case 1: code = "zh-CHS"; break;
                    case 2: code = "ja"; break;
                    case 3: code = "ko"; break;
                    case 4: code = "fr"; break;
                    case 5: code = "de"; break;
                    case 6: code = "es"; break;
                    default: code = "en"; break;
                }
                d.dismiss();
                startTranslate(code);
            }
        });
        d.show();
    }

    private void startTranslate(String targetLang) {
        if (!TranslateUtils.isOnline(this)) {
            showToast(R.string.translate_offline_hint);
            return;
        }
        // backup original content
        if (!mHasTranslation) {
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                // 清单模式下获取文本内容
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                    View view = mEditTextList.getChildAt(i);
                    NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                    if (!TextUtils.isEmpty(edit.getText())) {
                        if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                            sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        } else {
                            sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                        }
                    }
                }
                mOriginalContent = sb.toString();
            } else {
                // 普通模式下获取文本内容
                mOriginalContent = mNoteEditor.getHtml();
            }
        }
        mTargetLangCode = targetLang;
        mProgressDialog = ProgressDialog.show(this, "", getString(R.string.translate_progress), true, false);
        
        // 根据当前模式获取文本内容
        String content;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            content = sb.toString();
        } else {
            content = mNoteEditor.getHtml();
        }
        
        new TranslateTask().execute(content);
    }

    private class TranslateTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... params) {
            String content = params[0] == null ? "" : params[0];
            String[] paragraphs = content.split("\\n");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < paragraphs.length; i++) {
                String p = paragraphs[i];
                sb.append(p);
                sb.append('\n');
                if (!p.trim().isEmpty()) {
                    String t = TranslateUtils.translateParagraph(p, mTargetLangCode);
                    if (t != null) {
                        sb.append(t);
                        sb.append('\n');
                    } else {
                        sb.append("[翻译失败]");
                        sb.append('\n');
                    }
                }
            }
            return sb.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            if (result != null) {
                mHasTranslation = true;
                if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                    // 清单模式下，将翻译结果转换为清单格式
                    mEditTextList.removeAllViews();
                    String[] items = result.split("\n");
                    int index = 0;
                    for (String item : items) {
                        if(!TextUtils.isEmpty(item)) {
                            mEditTextList.addView(getListItem(item, index));
                            index++;
                        }
                    }
                    mEditTextList.addView(getListItem("", index));
                    // 修改
                    View focused = mEditTextList.getChildAt(index);
                    focused.findViewById(R.id.et_edit_text).requestFocus();
                } else {
                    // 普通模式下，直接显示翻译结果
                    SpannableString spannable = new SpannableString(result);
                    // 分析文本结构：原文和翻译交替出现
                    // 格式：原文1\n翻译1\n原文2\n翻译2\n...
                    String[] lines = result.split("\n");
                    int currentPosition = 0;
                    boolean isTranslation = false;
                    
                    for (String line : lines) {
                        if (!TextUtils.isEmpty(line)) {
                            if (isTranslation) {
                                // 设置翻译结果为浅灰色
                                int start = currentPosition;
                                int end = currentPosition + line.length();
                                spannable.setSpan(new ForegroundColorSpan(Color.parseColor("#999999")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            isTranslation = !isTranslation;
                        }
                        currentPosition += line.length() + 1; // +1 for newline
                    }
                    
                    mNoteEditor.setHtml(spannable.toString());
                    // scroll to first translation
                    // RichEditor doesn't support setSelection method, so we skip this
                }
            } else {
                showToast(R.string.error_sync_network);
            }
        }
    }

    private void createNewNote() {
        // Firstly, save current editing notes
        saveNote();

        // For safety, start a new NoteEditActivity
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            long originFolderId = mWorkingNote.getFolderId();
            if (originFolderId <= 0) {
                originFolderId = Notes.ID_ROOT_FOLDER;
            }
            
            // 直接调用DataUtils.batchMoveToTrash确保便签被移动到回收站
            if (!DataUtils.batchMoveToTrash(getContentResolver(), ids, originFolderId)) {
                Log.e(TAG, "Move notes to trash folder error");
                // 如果批量移动失败，尝试单独移动
                if (!moveNoteToTrash(id, originFolderId)) {
                    Log.e(TAG, "Single move to trash folder also failed");
                }
            }
        }
        mWorkingNote.markDeleted(true);
    }

    private boolean moveNoteToTrash(long noteId, long originFolderId) {
        ContentValues values = new ContentValues();
        values.put(Notes.NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
        values.put(Notes.NoteColumns.ORIGIN_PARENT_ID, originFolderId);
        values.put(Notes.NoteColumns.LOCAL_MODIFIED, 1);
        values.put(Notes.NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        int updated = getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                values, null, null);
        return updated > 0;
    }

    public void onClockAlertChanged(long date, boolean set) {
        /**
         * User could set clock to an unsaved note, so before setting the
         * alert clock, we should save the note first
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            showAlertHeader();
            if(!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    public void onWidgetChanged() {
        updateWidget();
    }

    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;
        }

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        NoteEditText edit = null;
        if(index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    public void onEditTextEnter(int index, String text) {
        /**
         * Should not happen, check for debug
         */
        if(index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if(!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        // 1. 空值保护，避免空指针
        if (TextUtils.isEmpty(fullText)) {
            return new SpannableString("");
        }
        SpannableString spannable = new SpannableString(fullText);
        String TAG = "NoteEdit";

        // 2. 原有高亮逻辑（保留不动，仅补全空值判断）
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(
                        new BackgroundColorSpan(getResources().getColor(R.color.user_query_highlight, getTheme())),
                        m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }

        Pattern imagePattern = Pattern.compile("【图】([^\\n]+)");
        Matcher imageMatcher = imagePattern.matcher(spannable);
        while (imageMatcher.find()) {
            try {
                // 修复1：判空避免trim()空指针
                String imagePath = imageMatcher.group(1);
                if (TextUtils.isEmpty(imagePath)) continue;
                imagePath = imagePath.trim();

                File imageFile = new File(imagePath);
                if (!imageFile.exists() || !imageFile.isFile()) {
                    Log.e(TAG, "图片文件不存在：" + imagePath);
                    continue;
                }

                // 修复2：Bitmap预压缩，避免OOM
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true; // 先获取尺寸，不加载像素
                BitmapFactory.decodeFile(imagePath, options);
                // 计算采样率（按200x200压缩）
                options.inSampleSize = calculateInSampleSize(options, 200, 200);
                options.inJustDecodeBounds = false;
                // 解码压缩后的图片
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
                if (bitmap == null) {
                    Log.e(TAG, "图片解码失败：" + imagePath);
                    continue;
                }
                // 缩放（可选，压缩后已足够小）
                bitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true);

                ImageSpan imageSpan = new ImageSpan(this, bitmap);
                spannable.setSpan(
                        imageSpan,
                        imageMatcher.start(),
                        imageMatcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            } catch (Exception e) {
                Log.e(TAG, "解析图片失败", e);
                continue;
            }
        }

        return spannable;
    }
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int width = options.outWidth;
        final int height = options.outHeight;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        //新增选区变化回调接口设置
        edit.setOnSelectionChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }
    //新增选区变化回调接口实现

    @Override
    public void onSelectionChanged(int index, int selStart, int selEnd) {
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            if (index >= 0 && index < mEditTextList.getChildCount()) {
                View view = mEditTextList.getChildAt(index);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                updateCharNumForSelection(edit.getText(), selStart, selEnd);
            }
        } else {
            // 富文本模式下，直接更新字符数显示
            // RichEditor没有直接的选择范围获取方法，所以只显示总字符数
            updateCharNumForSelection(mNoteEditor.getHtml(), 0, 0);
        }
    }

    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if(hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getHtml());
        } else {
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            String content = mWorkingNote.getContent() == null ? "" : mWorkingNote.getContent();
            // 保留高亮逻辑，将结果转为HTML
            Spannable highlightSpannable = getHighlightQueryResult(content, mUserQuery);
            mNoteEditor.setHtml(highlightSpannable.toString());
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
            // 更新字符统计为整条便签字符数
            mNoteHeaderHolder.tvCharNum.setText("字符数：" + calcVisibleCharCount(mWorkingNote.getContent()));
        }
    }

    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            // 确保获取最新的富文本内容
            String currentHtml = normalizeEditorHtml(mNoteEditor.getHtml());
            if (TextUtils.isEmpty(currentHtml) && !TextUtils.isEmpty(mText)) {
                currentHtml = normalizeEditorHtml(mText);
            }
            mWorkingNote.setWorkingText(currentHtml);
            mText = currentHtml; // 更新mText变量，确保保存时使用最新内容
        }
        return hasChecked;
    }

    private boolean saveNote() {
        // 总是调用getWorkingText()获取最新内容，确保保存的是最新编辑的内容
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            /**
             * There are two modes from List view to edit view, open one note,
             * create/edit a node. Opening node requires to the original
             * position in the list when back from edit view, while creating a
             * new node requires to the top of the list. This code
             * {@link #RESULT_OK} is used to identify the create/edit state
             */
            setResult(RESULT_OK);
        }
        return saved;
    }

    private void showImagePreview(String localImagePath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("图片选择成功！");

        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        imageView.setImageURI(Uri.fromFile(new File(localImagePath)));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        builder.setView(imageView);

        builder.setPositiveButton("确认保存", (dialog, which) -> {
            boolean isSaved = saveNote();
            if (isSaved) {
                Toast.makeText(this, "图片信息已保存！", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void sendToDesktop() {
        /**
         * Before send message to home, we should make sure that current
         * editing note is exists in databases. So, for new note, firstly
         * save it
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            // 获取快捷方式名称
            String shortcutName = makeShortcutIconTitle(mWorkingNote.getContent());
            
            // 检查Android版本，使用相应的API
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Android 8.0+ 使用ShortcutManager API
                android.content.pm.ShortcutManager shortcutManager = getSystemService(android.content.pm.ShortcutManager.class);
                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
                    // 创建ShortcutInfo对象
                    android.content.pm.ShortcutInfo.Builder builder = new android.content.pm.ShortcutInfo.Builder(this, "note_" + mWorkingNote.getNoteId());
                    builder.setShortLabel(shortcutName);
                    builder.setLongLabel(shortcutName);
                    builder.setIntent(shortcutIntent);
                    // 设置图标
                    builder.setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.icon_app));
                    
                    // 创建PendingIntent用于确认
                    Intent intent = new Intent(this, NoteEditActivity.class);
                    intent.setAction("android.intent.action.CREATE_SHORTCUT");
                    intent.putExtra("note_id", mWorkingNote.getNoteId());
                    android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, 0);
                    
                    // 请求创建快捷方式
                    shortcutManager.requestPinShortcut(builder.build(), pendingIntent.getIntentSender());
                    showToast(R.string.info_note_enter_desktop);
                } else {
                    // 如果ShortcutManager不可用，使用旧方式
                    sendShortcutBroadcast(shortcutIntent, shortcutName);
                }
            } else {
                // Android 7.1及以下使用旧的广播方式
                sendShortcutBroadcast(shortcutIntent, shortcutName);
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }
    
    /**
     * 使用旧的广播方式创建快捷方式（兼容Android 7.1及以下）
     */
    private void sendShortcutBroadcast(Intent shortcutIntent, String shortcutName) {
        Intent sender = new Intent();
        sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        sender.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
        sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
        sender.putExtra("duplicate", true);
        sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        showToast(R.string.info_note_enter_desktop);
        sendBroadcast(sender);
    }

    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }

    // ========== 新增：打开系统相册选择图片 ==========
    private void addPicture() {
        if (mImageInsertHelper != null) {
            mImageInsertHelper.startPickImage();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mImageInsertHelper == null) {
            return;
        }
        ImageInsertHelper.Result result = mImageInsertHelper.handleActivityResult(
                requestCode, resultCode, data, mNoteEditor);
        if (result == null || !result.success) {
            return;
        }
        mWorkingNote.setWorkingText(result.html);
        mText = result.html;
        showImagePreview(result.localPath);
    }

    private String normalizeEditorHtml(String html) {
        if (TextUtils.isEmpty(html) || "null".equalsIgnoreCase(html)) {
            return "";
        }
        return html;
    }

    private boolean isHtmlContent(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        return content.contains("<img")
                || content.contains("<p")
                || content.contains("<div")
                || content.contains("<br")
                || content.contains("</");
    }

    private String convertLegacyContentToHtml(String content) {
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        Pattern imgPattern = Pattern.compile("【图】([^\\n]+)");
        Matcher imgMatcher = imgPattern.matcher(normalized);
        StringBuilder htmlContent = new StringBuilder();
        int lastEnd = 0;
        while (imgMatcher.find()) {
            String beforeText = normalized.substring(lastEnd, imgMatcher.start());
            htmlContent.append(TextUtils.htmlEncode(beforeText));

            String imgLocalPath = imgMatcher.group(1);
            if (!TextUtils.isEmpty(imgLocalPath)) {
                imgLocalPath = imgLocalPath.trim();
                File imgFile = new File(imgLocalPath);
                if (imgFile.exists() && imgFile.isFile()) {
                    htmlContent.append(mImageInsertHelper.buildImageHtmlTag(imgLocalPath));
                } else {
                    htmlContent.append(TextUtils.htmlEncode(imgMatcher.group(0)));
                }
            }
            lastEnd = imgMatcher.end();
        }
        htmlContent.append(TextUtils.htmlEncode(normalized.substring(lastEnd)));
        return htmlContent.toString().replace("\n", "<br/>");
    }
    // 自定义方法：给RichEditor设置字体大小（对应原EditText的setTextAppearance）
    private void setRichEditorFontSize(int fontSizeId) {
        switch (fontSizeId) {
            case ResourceParser.TEXT_SMALL:
                mNoteEditor.setEditorFontSize(14); // 小字体
                break;
            case ResourceParser.TEXT_MEDIUM:
                mNoteEditor.setEditorFontSize(18); // 中字体（默认）
                break;
            case ResourceParser.TEXT_LARGE:
                mNoteEditor.setEditorFontSize(22); // 大字体
                break;
            case ResourceParser.TEXT_SUPER:
                mNoteEditor.setEditorFontSize(26); // 超大字体
                break;
            default:
                mNoteEditor.setEditorFontSize(18); // 默认值
        }
    }
    private void initRichEditor() {
        mNoteEditor.setEditorHeight(600); // 设置编辑器高度
        mNoteEditor.setEditorFontSize(16); // 字体大小
        mNoteEditor.setEditorFontColor(Color.BLACK); // 字体颜色
        mNoteEditor.setPadding(10, 10, 10, 10); // 内边距
        mNoteEditor.setPlaceholder("请输入笔记内容..."); // 占位提示
        mNoteEditor.setInputEnabled(true); // 允许输入
        mNoteEditor.setBackgroundColor(Color.TRANSPARENT);
        mNoteEditor.getSettings().setAllowContentAccess(true);
        mNoteEditor.getSettings().setAllowFileAccess(true);
        
        // 启用JavaScript，用于获取选中文本
        mNoteEditor.getSettings().setJavaScriptEnabled(true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mNoteEditor.getSettings().setAllowFileAccessFromFileURLs(true);
            mNoteEditor.getSettings().setAllowUniversalAccessFromFileURLs(true);
        }
        
        // 为富文本编辑器添加触摸监听，当用户点击或选择文本时更新字符数
        mNoteEditor.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 当触摸结束时更新字符数
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    // 富文本模式下，尝试获取选中文本长度
                    getSelectedTextLength();
                }
                return false; // 继续传递触摸事件
            }
        });
        
        // 添加文本选择变化监听
        String currentHtml = mNoteEditor.getHtml();
        String scriptHtml = currentHtml + "<script>" +
                "document.addEventListener('selectionchange', function() {" +
                "    var selection = window.getSelection();" +
                "    var selectedText = selection.toString();" +
                "    var totalText = document.body.innerText;" +
                "    window.noteEditor.getSelectedText(selectedText, totalText);" +
                "});" +
                "</script>";
        mNoteEditor.setHtml(scriptHtml);
    }
    
    // 添加获取选中文本长度的方法
    private void getSelectedTextLength() {
        if (mNoteEditor == null) return;
        
        // 使用JavaScript获取选中文本
        String js = "javascript:(function() {" +
                "var selection = window.getSelection();" +
                "var selectedText = selection.toString();" +
                "var totalText = document.body.innerText;" +
                "window.noteEditor.getSelectedText(selectedText, totalText);" +
                "})();";
        
        // 执行JavaScript
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mNoteEditor.evaluateJavascript(js, null);
        } else {
            // 旧版本Android使用loadUrl
            mNoteEditor.loadUrl(js);
        }
    }
    // 添加富文本功能按钮初始化方法
    private void initRichEditorButtons() {
        // 撤销功能
        findViewById(R.id.action_undo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNoteEditor.undo();
            }
        });

        // 加粗功能
        findViewById(R.id.action_bold).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNoteEditor.setBold();
            }
        });

        // 斜体功能
        findViewById(R.id.action_italic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNoteEditor.setItalic();
            }
        });

        // 涂鸦功能
        findViewById(R.id.action_doodle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDoodleDialog();
            }
        });

    }

    private void showDoodleDialog() {
        DoodleDialog dialog = new DoodleDialog(this, new DoodleDialog.OnDoodleSavedListener() {
            @Override
            public void onSaved(String localPath) {
                insertImageFromLocal(localPath);
                showImagePreview(localPath);
            }
        });
        dialog.show();
    }

    private void insertImageFromLocal(String localImagePath) {
        if (mNoteEditor == null) {
            return;
        }
        String imgUrl = Uri.fromFile(new File(localImagePath)).toString();
        String imgHtmlTag = "<img src=\"" + imgUrl + "\" width=\"200\" height=\"200\"/><br/>";
        String curHtml = normalizeEditorHtml(mNoteEditor.getHtml());
        String newHtml = curHtml + imgHtmlTag;
        mNoteEditor.setHtml(newHtml);
        mNoteEditor.focusEditor();
        mText = newHtml;
        mWorkingNote.setWorkingText(newHtml);
    }


}