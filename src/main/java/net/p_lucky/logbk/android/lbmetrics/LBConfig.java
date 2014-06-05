package net.p_lucky.logbk.android.lbmetrics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;

/**
 * Stores global configuration options for the Mixpanel library.
 */
public class LBConfig {
    public static final String VERSION = "4.2.1";

    public static boolean DEBUG = false;

    // Instances are safe to store, since they're immutable and always the same.
    public static LBConfig getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (null == sInstance) {
                final Context appContext = context.getApplicationContext();
                sInstance = readConfig(appContext);
            }
        }

        return sInstance;
    }

    /* package */ LBConfig(Bundle metaData) {
        DEBUG = metaData.getBoolean("net.p_lucky.logbk.android.LBConfig.EnableDebugLogging", false);

        mBulkUploadLimit = metaData.getInt("net.p_lucky.logbk.android.LBConfig.BulkUploadLimit", 40); // 40 records default
        mFlushInterval = metaData.getInt("net.p_lucky.logbk.android.LBConfig.FlushInterval", 60 * 1000); // one minute default
        mDataExpiration = metaData.getInt("net.p_lucky.logbk.android.LBConfig.DataExpiration",  1000 * 60 * 60 * 24 * 5); // 5 days default
        mDisableFallback = metaData.getBoolean("net.p_lucky.logbk.android.LBConfig.DisableFallback", true);

        String eventsEndpoint = metaData.getString("net.p_lucky.logbk.android.LBConfig.EventsEndpoint");
        if (null == eventsEndpoint) {
            eventsEndpoint = "https://api.mixpanel.com/track?ip=1";
        }
        mEventsEndpoint = eventsEndpoint;

        String eventsFallbackEndpoint = metaData.getString("net.p_lucky.logbk.android.LBConfig.EventsFallbackEndpoint");
        if (null == eventsFallbackEndpoint) {
            eventsFallbackEndpoint = "http://api.mixpanel.com/track?ip=1";
        }
        mEventsFallbackEndpoint = eventsFallbackEndpoint;

        if (DEBUG) {
            Log.d(LOGTAG,
                "Logbook configured with:\n" +
                "    BulkUploadLimit " + getBulkUploadLimit() + "\n" +
                "    FlushInterval " + getFlushInterval() + "\n" +
                "    DataExpiration " + getDataExpiration() + "\n" +
                "    DisableFallback " + getDisableFallback() + "\n" +
                "    EnableDebugLogging " + DEBUG + "\n" +
                "    EventsEndpoint " + getEventsEndpoint() + "\n" +
                "    EventsFallbackEndpoint " + getEventsFallbackEndpoint() + "\n"
            );
        }
    }

    // Max size of queue before we require a flush. Must be below the limit the service will accept.
    public int getBulkUploadLimit() {
        return mBulkUploadLimit;
    }

    // Target max milliseconds between flushes. This is advisory.
    public int getFlushInterval() {
        return mFlushInterval;
    }

    // Throw away records that are older than this in milliseconds. Should be below the server side age limit for events.
    public int getDataExpiration() {
        return mDataExpiration;
    }

    public boolean getDisableFallback() {
        return mDisableFallback;
    }

    // Preferred URL for tracking events
    public String getEventsEndpoint() {
        return mEventsEndpoint;
    }

    // Fallback URL for tracking events if post to preferred URL fails
    public String getEventsFallbackEndpoint() {
        return mEventsFallbackEndpoint;
    }

    ///////////////////////////////////////////////

    // Package access for testing only- do not call directly in library code
    /* package */ static LBConfig readConfig(Context appContext) {
        final String packageName = appContext.getPackageName();
        try {
            final ApplicationInfo appInfo = appContext.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            Bundle configBundle = appInfo.metaData;
            if (null == configBundle) {
                configBundle = new Bundle();
            }
            return new LBConfig(configBundle);
        } catch (final NameNotFoundException e) {
            throw new RuntimeException("Can't configure Logbook with package name " + packageName, e);
        }
    }

    private final int mBulkUploadLimit;
    private final int mFlushInterval;
    private final int mDataExpiration;
    private final boolean mDisableFallback;
    private final String mEventsEndpoint;
    private final String mEventsFallbackEndpoint;

    private static LBConfig sInstance;
    private static final Object sInstanceLock = new Object();
    private static final String LOGTAG = "LogbookAPI.MPConfig";
}
