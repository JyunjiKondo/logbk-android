package net.p_lucky.logbk.android.lbmetrics;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/* package */ class PersistentIdentity {

    public PersistentIdentity(Future<SharedPreferences> storedPreferences) {
        mLoadStoredPreferences = storedPreferences;
        mSuperPropertiesCache = null;
        mIdentitiesLoaded = false;
    }

    public synchronized JSONObject getSuperProperties() {
        if (null == mSuperPropertiesCache) {
            readSuperProperties();
        }
        return mSuperPropertiesCache;
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
        // Will clear distinct_ids and superProperties.
        // Will have no effect
        // on messages already queued to send with AnalyticsMessages.

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor prefsEdit = prefs.edit();
            prefsEdit.clear();
            writeEdits(prefsEdit);
            readSuperProperties();
            readIdentities();
        } catch (final ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    public synchronized void registerSuperProperties(JSONObject superProperties) {
        final JSONObject propCache = getSuperProperties();

        for (final Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
            final String key = (String) iter.next();
            try {
               propCache.put(key, superProperties.get(key));
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Exception registering super property.", e);
            }
        }

        storeSuperProperties();
    }

    public synchronized void unregisterSuperProperty(String superPropertyName) {
        final JSONObject propCache = getSuperProperties();
        propCache.remove(superPropertyName);

        storeSuperProperties();
    }

    public synchronized void registerSuperPropertiesOnce(JSONObject superProperties) {
        final JSONObject propCache = getSuperProperties();

        for (final Iterator<?> iter = superProperties.keys(); iter.hasNext(); ) {
            final String key = (String) iter.next();
            if (! propCache.has(key)) {
                try {
                    propCache.put(key, superProperties.get(key));
                } catch (final JSONException e) {
                    Log.e(LOGTAG, "Exception registering super property.", e);
                }
            }
        }// for

        storeSuperProperties();
    }

    public synchronized void clearSuperProperties() {
        mSuperPropertiesCache = new JSONObject();
        storeSuperProperties();
    }

    //////////////////////////////////////////////////

    // All access should be synchronized on this
    private void readSuperProperties() {
        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final String props = prefs.getString("super_properties", "{}");
            if (LBConfig.DEBUG) Log.d(LOGTAG, "Loading Super Properties " + props);
            mSuperPropertiesCache = new JSONObject(props);
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Cannot load superProperties from SharedPreferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Cannot load superProperties from SharedPreferences.", e);
        } catch (final JSONException e) {
            Log.e(LOGTAG, "Cannot parse stored superProperties");
            storeSuperProperties();
        } finally {
            if (null == mSuperPropertiesCache) {
                mSuperPropertiesCache = new JSONObject();
            }
        }
    }

    // All access should be synchronized on this
    private void storeSuperProperties() {
        if (null == mSuperPropertiesCache) {
            Log.e(LOGTAG, "storeSuperProperties should not be called with uninitialized superPropertiesCache.");
            return;
        }

        final String props = mSuperPropertiesCache.toString();
        if (LBConfig.DEBUG) Log.d(LOGTAG, "Storing Super Properties " + props);

        try {
            final SharedPreferences prefs = mLoadStoredPreferences.get();
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putString("super_properties", props);
            writeEdits(editor);
        } catch (final ExecutionException e) {
            Log.e(LOGTAG, "Cannot store superProperties in shared preferences.", e.getCause());
        } catch (final InterruptedException e) {
            Log.e(LOGTAG, "Cannot store superProperties in shared preferences.", e);
        }
    }

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
            mEventsDistinctId = UUID.randomUUID().toString();
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
    private JSONObject mSuperPropertiesCache;
    private boolean mIdentitiesLoaded;
    private String mEventsDistinctId;

    private static final String LOGTAG = "LogbookAPI PersistentIdentity";
}
