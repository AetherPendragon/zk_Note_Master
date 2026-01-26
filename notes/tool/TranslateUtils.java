package net.micode.notes.tool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public class TranslateUtils {
    private static final String TAG = "TranslateUtils";

    
    private static final String YOUDAO_APP_KEY = "3abfa533dbdc44d1";
    private static final String YOUDAO_APP_SECRET = "aliNHKWhhTlaLjRAkOce4cHTubriEl0c";
    private static final String YOUDAO_URL = "https://openapi.youdao.com/api";

    public static boolean isOnline(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            // 兼容低版本
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
      
    }

    public static String translateParagraph(String text, String targetLang) {
        if (TextUtils.isEmpty(YOUDAO_APP_KEY) || TextUtils.isEmpty(YOUDAO_APP_SECRET)) {
            Log.w(TAG, "Youdao app key/secret not configured");
            return null;
        }
        try {
            Log.d(TAG, "Starting translation: text=" + text + ", targetLang=" + targetLang);
            
            String q = text;
            String from = "auto";
            String to = targetLang == null ? "en" : targetLang;
            String salt = String.valueOf(System.currentTimeMillis());
            String sign = md5(YOUDAO_APP_KEY + q + salt + YOUDAO_APP_SECRET);

            StringBuilder sb = new StringBuilder();
            sb.append("appKey=").append(urlEncode(YOUDAO_APP_KEY));
            sb.append("&q=").append(urlEncode(q));
            sb.append("&salt=").append(urlEncode(salt));
            sb.append("&from=").append(urlEncode(from));
            sb.append("&to=").append(urlEncode(to));
            sb.append("&sign=").append(urlEncode(sign));

            URL url = new URL(YOUDAO_URL);
            Log.d(TAG, "Connecting to: " + YOUDAO_URL);
            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
            writer.write(sb.toString());
            writer.flush();
            writer.close();

            int code = conn.getResponseCode();
            Log.d(TAG, "Response code: " + code);
            
            if (code != 200) {
                Log.w(TAG, "Youdao response code:" + code);
                // 读取错误响应
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                    StringBuilder errorResp = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        errorResp.append(line);
                    }
                    Log.w(TAG, "Error response: " + errorResp.toString());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read error response", e);
                }
                return null;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder resp = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                resp.append(line);
            }
            br.close();
            conn.disconnect();
            
            Log.d(TAG, "Response: " + resp.toString());

            JSONObject json = new JSONObject(resp.toString());
            if (json.has("translation")) {
                JSONArray arr = json.getJSONArray("translation");
                if (arr.length() > 0) {
                    String result = arr.getString(0);
                    Log.d(TAG, "Translation result: " + result);
                    return result;
                }
            }
            // fallback: try webdict or basic
            if (json.has("web") && json.getJSONArray("web").length() > 0) {
                JSONObject w = json.getJSONArray("web").getJSONObject(0);
                if (w.has("value")) {
                    JSONArray v = w.getJSONArray("value");
                    if (v.length() > 0) {
                        String result = v.getString(0);
                        Log.d(TAG, "Web dict fallback result: " + result);
                        return result;
                    }
                }
            }
            Log.w(TAG, "No translation found in response");
            return null;
        } catch (Exception e) {
            Log.w(TAG, "translate error: " + e.getMessage(), e);
            return null;
        }
    }

    private static String urlEncode(String s) throws Exception {
        return java.net.URLEncoder.encode(s, "UTF-8");
    }

    private static String md5(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }
}
