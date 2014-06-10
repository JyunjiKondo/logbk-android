package net.p_lucky.logbk.android.lbmetrics;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.Future;

public class TestUtils {
    public static byte[] bytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("This is not an android device, or a compatible java. WHO ARE YOU?");
        }
    }

    public static class CleanLogbookAPI extends LogbookAPI {
        public CleanLogbookAPI(final Context context, final Future<SharedPreferences> referrerPreferences, final String token) {
            super(context, token);
        }

        @Override
        /* package */ PersistentIdentity getPersistentIdentity(final Context context, final String token) {
            final String prefsName = "net.p_lucky.logbk.android.lbmetrics.LogbookAPI_" + token;
            final SharedPreferences ret = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            ret.edit().clear().commit();

            return super.getPersistentIdentity(context, token);
        }
    }
}
