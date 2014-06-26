package net.p_lucky.logbk.android.lbmetrics;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/* package */ @SuppressLint("CommitPrefEdits")
class PersistentIdentity {

    public PersistentIdentity(Future<SharedPreferences> storedPreferences) {
        mLoadStoredPreferences = storedPreferences;
        mIdentitiesLoaded = false;
    }

    public synchronized String getEventsDistinctId() {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        return mEventsDistinctId;
    }

    public synchronized void setEventsDistinctId(String eventsDistinctId) {
        if (! mIdentitiesLoaded) {
            readIdentities();
        }
        mEventsDistinctId = eventsDistinctId;
        writeIdentities();
    }

    public synchronized void clearPreferences() {
        // Will clear distinct_ids.
        // Will have no effect
        // on messages already queued to send with AnalyticsMessages.

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEdit = prefs.edit();
            prefsEdit.clear();
            writeEdits(prefsEdit);
            readIdentities();
        } catch (final ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    //////////////////////////////////////////////////

    // All access should be synchronized on this
    private void readIdentities() {
        SharedPreferences prefs = null;
        try {
            prefs = mLoadStoredPreferences.get();
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e);
        }

        if (null == prefs) {
            return;
        }

        mEventsDistinctId = prefs.getString("events_distinct_id", null);

        if (null == mEventsDistinctId) {
            mEventsDistinctId = UUID.randomUUID().toString().replaceAll("-", "");
            writeIdentities();
        }

        mIdentitiesLoaded = true;
    }

    // All access should be synchronized on this
    private void writeIdentities() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEditor = prefs.edit();

            prefsEditor.putString("events_distinct_id", mEventsDistinctId);
            writeEdits(prefsEditor);
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Can't write distinct ids to shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Can't write distinct ids to shared preferences.", e);
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static void writeEdits(final SharedPreferences.Editor editor) {
        if (Build.VERSION.SDK_INT >= 9) {
            editor.apply();
        } else {
            editor.commit();
        }
    }

    private final Future<SharedPreferences> mLoadStoredPreferences;
    private boolean mIdentitiesLoaded;
    private String mEventsDistinctId;

    private static final String LOGTAG = "LogbookAPI PersistentIdentity";
}
