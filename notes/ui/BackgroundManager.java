package net.micode.notes.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.Toast;
import android.view.View;

import net.micode.notes.R;

import java.io.InputStream;

public class BackgroundManager {
    public static final int REQUEST_CODE_PICK_IMAGE = 2001; // 选图请求码
    private static final String PREF_BG_TYPE = "pref_bg_type"; // 背景类型key：0=颜色，1=内置资源，2=自定义图片URI
    private static final String PREF_BG_COLOR = "pref_bg_color"; // 背景颜色key
    private static final String PREF_BG_RES_ID = "pref_bg_res_id"; // 内置背景资源ID key
    private static final String PREF_BG_URI = "pref_bg_uri"; // 自定义图片URI key
    private static final int BG_TYPE_COLOR = 0; // 颜色类型标识
    private static final int BG_TYPE_BUILTIN = 1; // 内置资源类型标识
    private static final int BG_TYPE_URI = 2; // 自定义图片类型标识

    private Activity mActivity;
    private View mRootView;//要设置背景的根View

    public BackgroundManager(Activity activity, int rootViewId) {
        mActivity = activity;
        mRootView = activity.findViewById(rootViewId);//绑定指定的根View
        if (mRootView == null) {
            mRootView = activity.getWindow().getDecorView();//若传入的View ID无效，取窗口根View
        }
    }

    // 根据偏好设置应用背景
    public void applyBackgroundFromPrefs() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        int type = sp.getInt(PREF_BG_TYPE, -1);//读取背景类型
        if (type == BG_TYPE_COLOR) {//颜色类型
            int color = sp.getInt(PREF_BG_COLOR, 0xFFFFFFFF);//读取颜色值
            applyColorBackground(color);//应用颜色背景
        } else if (type == BG_TYPE_BUILTIN) {//内置资源类型
            int resId = sp.getInt(PREF_BG_RES_ID, R.drawable.list_background);//读取资源ID
            applyBuiltinBackground(resId);//应用内置背景
        } else if (type == BG_TYPE_URI) {//自定义图片类型
            String uriStr = sp.getString(PREF_BG_URI, null);//读取URI字符串
            if (uriStr != null) {
                applyUriBackground(Uri.parse(uriStr));//应用自定义图片背景
            }
        } else {
            mRootView.setBackgroundResource(R.drawable.list_background);//默认背景
        }
    }

    // 应用颜色背景并保存偏好设置
    public void applyColorAndSave(int color) {
        applyColorBackground(color);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);//获取默认SharedPreferences
        sp.edit().putInt(PREF_BG_TYPE, BG_TYPE_COLOR).putInt(PREF_BG_COLOR, color).commit();//保存背景类型和颜色值
    }

    // 应用内置背景并保存偏好设置
    public void applyBuiltinAndSave(int resId) {
        applyBuiltinBackground(resId);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        sp.edit().putInt(PREF_BG_TYPE, BG_TYPE_BUILTIN).putInt(PREF_BG_RES_ID, resId).commit();
    }

    // 启动图库选择图片
    public void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    // 处理图库选择结果
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                applyUriAndSave(uri);
            }
            return true;
        }
        return false;
    }

    // 重置为默认背景并清除偏好设置
    public void resetToDefaultAndClear() {
        mRootView.setBackgroundResource(R.drawable.list_background);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
        sp.edit().remove(PREF_BG_TYPE).remove(PREF_BG_COLOR).remove(PREF_BG_RES_ID).remove(PREF_BG_URI).commit();
    }

    private void applyColorBackground(int color) {
        mRootView.setBackgroundColor(color);
    }

    private void applyBuiltinBackground(int resId) {
        mRootView.setBackgroundResource(resId);
    }

    // 应用URI背景
    private void applyUriBackground(Uri uri) {
        try {
            InputStream is = mActivity.getContentResolver().openInputStream(uri);//通过内容解析器打开URI对应的输入流
            if (is != null) {
                Drawable d = Drawable.createFromStream(is, uri.toString());
                mRootView.setBackgroundDrawable(d);
                is.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mActivity, "无法加载图片", Toast.LENGTH_SHORT).show();
        }
    }

    // 应用URI背景并保存偏好设置
    private void applyUriAndSave(Uri uri) {
        try {
            InputStream is = mActivity.getContentResolver().openInputStream(uri);
            if (is != null) {
                Drawable d = Drawable.createFromStream(is, uri.toString());
                mRootView.setBackgroundDrawable(d);
                is.close();
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mActivity);
                sp.edit().putInt(PREF_BG_TYPE, BG_TYPE_URI).putString(PREF_BG_URI, uri.toString()).commit();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mActivity, "无法加载图片", Toast.LENGTH_SHORT).show();
        }
    }
}
