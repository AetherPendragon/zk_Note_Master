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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**     
  * 
  * @Package:        net.micode.notes.data
  * @ClassName:      Contact
  * @Description:    通过传入的电话号码获取对应的姓名
 */
public class Contact {
    //哈希图型变量sContactCache，用于存储联系人姓名和电话号码的映射关系
    private static HashMap<String, String> sContactCache;
    private static final String TAG = "Contact";

    /**
     * 查找联系人的sql语句:
     * 执行过程：从phone_lookup中查询符合最小匹配值的raw_contact_id，在data表里找到raw_contact_id，选择电话号码类型，并与传入的电话号码进行精确匹配
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER +  ",?) AND "  + Data.MIMETYPE
            +  "='"  + Phone.CONTENT_ITEM_TYPE + "'" + " AND " + Data.RAW_CONTACT_ID + " IN " + "(SELECT raw_contact_id "
            + " FROM phone_lookup" + " WHERE min_match = '+')";

    /**
     * @method  getContact
     * @description       通过传入的电话号码获取对应的姓名
     * @param context     上下文
     * @param phoneNumber 传入的电话号码
     * @return string     姓名
     */ 
    public static String getContact(Context context, String phoneNumber) {
        //初始化 sContactCache
        if (sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        //通过电话号码查找sContactCache，如果命中，直接返回对应的姓名
        if (sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        //selection:将CALLER_ID_SELECTION中的“+”替换成实际电话号码的最小匹配字符串
        String selection = CALLER_ID_SELECTION.replace("+", PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        /**查询数据库，返回光标
         * 数据库表：Data.CONTENT_URI
         * 查找的列：Phone.DISPLAY_NAME 只显示名称
         * 查找的sql语句：selection
         * 替换掉占位符“？”：phoneNumber
         * 排序方式：不排序
         */
        Cursor cursor = context.getContentResolver().query(Data.CONTENT_URI, new String[]{Phone.DISPLAY_NAME},
                selection, new String[]{phoneNumber}, null);

        //如果光标不为空且移到第一行，即已在数据库中查询到
        if (cursor != null && cursor.moveToFirst()) {
            //获取联系人姓名 并将电话号码和联系人姓名的映射关系存到sContactCache中 返回姓名
            try {
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            }
            //异常处理
            catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();
            }
        }
        //如果未在数据库中查询到，则记录错误日志
        else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
