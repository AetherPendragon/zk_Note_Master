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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import net.micode.notes.data.Notes;

/**
 * 通话记录广播接收器，用于监听系统通话状态变化
 */
public class CallRecordReceiver extends BroadcastReceiver {
    private static final String TAG = "CallRecordReceiver";
    private static String mLastPhoneNumber;// 记录最后一个电话号码
    private static long mCallStartTime;// 记录通话开始时间
    private static boolean mIsOutgoingCall;// 是否为拨出电话

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null) {
            Log.d(TAG, "Received broadcast: " + intent.getAction());
            
            if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

                Log.d(TAG, "Phone state changed: " + state + ", number: " + phoneNumber);

                if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                    // 电话响铃，记录来电号码
                    mLastPhoneNumber = phoneNumber;
                    mCallStartTime = System.currentTimeMillis();
                    mIsOutgoingCall = false;
                    Log.d(TAG, "Incoming call ringing: " + phoneNumber);
                } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    // 电话接通，记录开始时间
                    if (mCallStartTime == 0) {
                        mCallStartTime = System.currentTimeMillis();
                    }
                    if (phoneNumber != null) {
                        mLastPhoneNumber = phoneNumber;
                    }
                    Log.d(TAG, "Call answered");
                } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    // 电话挂断，创建通话记录便签
                    if (mLastPhoneNumber != null && mCallStartTime > 0) {
                        long callEndTime = System.currentTimeMillis();
                        long callDuration = callEndTime - mCallStartTime;

                        // 创建通话记录便签
                        Log.d(TAG, "Call ended, creating call note for: " + mLastPhoneNumber);
                        Intent serviceIntent = new Intent(context, CallRecordService.class);
                        serviceIntent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, mLastPhoneNumber);
                        serviceIntent.putExtra("call_date", callEndTime);
                        serviceIntent.putExtra("call_duration", callDuration);

                        // 启动服务创建通话记录便签
                        try {
                            context.startService(serviceIntent);
                            Log.d(TAG, "Successfully started CallRecordService");
                        } catch (Exception e) {
                            Log.e(TAG, "Error starting CallRecordService", e);
                        }
                    }

                    // 重置状态
                    mLastPhoneNumber = null;
                    mCallStartTime = 0;
                    mIsOutgoingCall = false;
                    Log.d(TAG, "Call state reset to idle");
                }
            } else if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
                // 拨出电话
                String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                if (phoneNumber != null) {
                    mLastPhoneNumber = phoneNumber;
                    mCallStartTime = System.currentTimeMillis();
                    mIsOutgoingCall = true;
                    Log.d(TAG, "Outgoing call started: " + phoneNumber);
                }
            }
        }
    }
}
