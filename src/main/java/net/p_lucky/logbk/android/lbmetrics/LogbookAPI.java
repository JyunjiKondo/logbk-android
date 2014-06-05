package net.p_lucky.logbk.android.lbmetrics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Contacts.People;
import android.util.Log;

/**
 * Core class for interacting with Logbook Analytics.
 *
 * <p>Call {@link #getInstance(Context, String)} with
 * your main application activity and your Logbook API token as arguments
 * an to get an instance you can use to report how users are using your
 * application.
 *
 * <p>Once you have an instance, you can send events to Logbook
 * using {@link #track(String, JSONObject)}
 *
 * <p>The Logbook library will periodically send information to
 * Logbook servers, so your application will need to have
 * <tt>android.permission.INTERNET</tt>. In addition, to preserve
 * battery life, messages to Logbook servers may not be sent immediately
 * when you call <tt>track</tt>.
 * The library will send messages periodically throughout the lifetime
 * of your application, but you will need to call {@link #flush()}
 * before your application is completely shutdown to ensure all of your
 * events are sent.
 *
 * <p>A typical use-case for the library might look like this:
 *
 * <pre>
 * {@code
 * public class MainActivity extends Activity {
 *      LogbookAPI mLogbook;
 *
 *      public void onCreate(Bundle saved) {
 *          mLogbook = LogbookAPI.getInstance(this, "YOUR LOGBOOK API TOKEN");
 *          ...
 *      }
 *
 *      public void whenSomethingInterestingHappens(int flavor) {
 *          JSONObject properties = new JSONObject();
 *          properties.put("flavor", flavor);
 *          mLogbook.track("Something Interesting Happened", properties);
 *          ...
 *      }
 *
 *      public void onDestroy() {
 *          mLogoobk.flush();
 *          super.onDestroy();
 *      }
 * }
 * }
 * </pre>
 */
public class LogbookAPI {
    /**
     * String version of the library.
     */
    public static final String VERSION = LBConfig.VERSION;

    /**
     * You shouldn't instantiate LogbookAPI objects directly.
     * Use LogbookAPI.getInstance to get an instance.
     */
    LogbookAPI(Context context, String token) {
        mContext = context;
        mToken = token;
        mMessages = getAnalyticsMessages();
        mPersistentIdentity = getPersistentIdentity(context, token);
    }

    /**
     * Get the instance of LogbookAPI associated with your Logbook project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of LogbookAPI you can use to send events
     * to Logbook.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned LogbookAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * LogbookAPI instance = LogbookAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Logbook project token. You can get your project token on the Logbook web site,
     *     in the settings dialog.
     * @return an instance of LogbookAPI associated with your project
     */
    public static LogbookAPI getInstance(Context context, String token) {
        if (null == token || null == context) {
            return null;
        }
        synchronized (sInstanceMap) {
            final Context appContext = context.getApplicationContext();

            Map <Context, LogbookAPI> instances = sInstanceMap.get(token);
            if (null == instances) {
                instances = new HashMap<Context, LogbookAPI>();
                sInstanceMap.put(token, instances);
            }

            LogbookAPI instance = instances.get(appContext);
            if (null == instance) {
                instance = new LogbookAPI(appContext, token);
                instances.put(appContext, instance);
            }
            return instance;
        }
    }

    /**
     * Track an event.
     *
     * <p>Every call to track eventually results in a data point sent to Logbook. These data points
     * are what are measured, counted, and broken down to create your Logbook reports. Events
     * have a string name, and an optional set of name/value pairs that describe the properties of
     * that event.
     *
     * @param eventName The name of the event to send
     * @param properties A JSONObject containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     */
    // DO NOT DOCUMENT, but track() must be thread safe since it is used to track events in
    // notifications from the UI thread, which might not be our LogbookAPI "home" thread.
    // This MAY CHANGE IN FUTURE RELEASES, so minimize code that assumes thread safety
    // (and perhaps document that code here).
    public void track(String eventName, JSONObject properties) {
        try {
            final JSONObject messageProps = new JSONObject();

            // Don't allow super properties or referral properties to override these fields,
            // but DO allow the caller to override them in their given properties.
            final long time = System.currentTimeMillis() / 1000;
            messageProps.put("time", time);
            messageProps.put("distinct_id", getDistinctId());

            if (null != properties) {
                final Iterator<?> propIter = properties.keys();
                while (propIter.hasNext()) {
                    final String key = (String) propIter.next();
                    messageProps.put(key, properties.get(key));
                }
            }

            final AnalyticsMessages.EventDescription eventDescription =
                    new AnalyticsMessages.EventDescription(eventName, messageProps, mToken);
            mMessages.eventsMessage(eventDescription);
        } catch (final JSONException e) {
            Log.e(LOGTAG, "Exception tracking event " + eventName, e);
        }
    }

    /**
     * Push all queued Logbook events to Logbook servers.
     *
     * <p>Events messages are pushed gradually throughout
     * the lifetime of your application. This means that to ensure that all messages
     * are sent to Logbook when your application is shut down, you will
     * need to call flush() to let the Logbook library know it should
     * send all remaining messages to the server. We strongly recommend
     * placing a call to flush() in the onDestroy() method of
     * your main application activity.
     */
    public void flush() {
        mMessages.postToServer();
    }

    /**
     * Returns the string id currently being used to uniquely identify the user associated
     * with events sent using {@link #track(String, JSONObject)}. 
     * This will be an id automatically generated by the library.
     *
     * <p>The id returned by getDistinctId is independent of the distinct id used to identify
     * any People Analytics properties in Logbook.
     *
     * @return The distinct id associated with event tracking
     */
    private String getDistinctId() {
        return mPersistentIdentity.getEventsDistinctId();
    }

    ////////////////////////////////////////////////////////////////////
    // Conveniences for testing. These methods should not be called by
    // non-test client code.

    /* package */ AnalyticsMessages getAnalyticsMessages() {
        return AnalyticsMessages.getInstance(mContext);
    }

    /* package */ PersistentIdentity getPersistentIdentity(final Context context, final String token) {
        final String prefsName = "net.p_lucky.logbk.android.lbmetrics.LogbookAPI_" + token;
        final Future<SharedPreferences> storedPreferences = sPrefsLoader.loadPreferences(context, prefsName, null);
        return new PersistentIdentity(storedPreferences);
    }

    ////////////////////////////////////////////////////

    private static final String LOGTAG = "LogbookAPI";

    private final Context mContext;
    private final AnalyticsMessages mMessages;
    private final String mToken;
    private final PersistentIdentity mPersistentIdentity;

    // Maps each token to a singleton LogbookAPI instance
    private static final Map<String, Map<Context, LogbookAPI>> sInstanceMap = new HashMap<String, Map<Context, LogbookAPI>>();
    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
}
