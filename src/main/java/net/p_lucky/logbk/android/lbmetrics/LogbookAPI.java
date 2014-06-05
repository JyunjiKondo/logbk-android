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
 * Core class for interacting with Mixpanel Analytics.
 *
 * <p>Call {@link #getInstance(Context, String)} with
 * your main application activity and your Mixpanel API token as arguments
 * an to get an instance you can use to report how users are using your
 * application.
 *
 * <p>Once you have an instance, you can send events to Mixpanel
 * using {@link #track(String, JSONObject)}, and update People Analytics
 * records with {@link #getPeople()}
 *
 * <p>The Mixpanel library will periodically send information to
 * Mixpanel servers, so your application will need to have
 * <tt>android.permission.INTERNET</tt>. In addition, to preserve
 * battery life, messages to Mixpanel servers may not be sent immediately
 * when you call <tt>track</tt> or {@link People#set(String, Object)}.
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
 *      MixpanelAPI mMixpanel;
 *
 *      public void onCreate(Bundle saved) {
 *          mMixpanel = MixpanelAPI.getInstance(this, "YOUR MIXPANEL API TOKEN");
 *          ...
 *      }
 *
 *      public void whenSomethingInterestingHappens(int flavor) {
 *          JSONObject properties = new JSONObject();
 *          properties.put("flavor", flavor);
 *          mMixpanel.track("Something Interesting Happened", properties);
 *          ...
 *      }
 *
 *      public void onDestroy() {
 *          mMixpanel.flush();
 *          super.onDestroy();
 *      }
 * }
 * }
 * </pre>
 *
 * <p>In addition to this documentation, you may wish to take a look at
 * <a href="https://github.com/mixpanel/sample-android-mixpanel-integration">the Mixpanel sample Android application</a>.
 * It demonstrates a variety of techniques, including
 * updating People Analytics records with {@link People} and registering for
 * and receiving push notifications with {@link People#initPushHandling(String)}.
 *
 * <p>There are also <a href="https://mixpanel.com/docs/">step-by-step getting started documents</a>
 * available at mixpanel.com
 *
 * @see <a href="https://mixpanel.com/docs/integration-libraries/android">getting started documentation for tracking events</a>
 * @see <a href="https://mixpanel.com/docs/people-analytics/android">getting started documentation for People Analytics</a>
 * @see <a href="https://mixpanel.com/docs/people-analytics/android-push">getting started with push notifications for Android</a>
 * @see <a href="https://github.com/mixpanel/sample-android-mixpanel-integration">The Mixpanel Android sample application</a>
 */
public class LogbookAPI {
    /**
     * String version of the library.
     */
    public static final String VERSION = LBConfig.VERSION;

    /**
     * You shouldn't instantiate MixpanelAPI objects directly.
     * Use MixpanelAPI.getInstance to get an instance.
     */
    LogbookAPI(Context context, String token) {
        mContext = context;
        mToken = token;
        mMessages = getAnalyticsMessages();
        mPersistentIdentity = getPersistentIdentity(context, token);
    }

    /**
     * Get the instance of MixpanelAPI associated with your Mixpanel project token.
     *
     * <p>Use getInstance to get a reference to a shared
     * instance of MixpanelAPI you can use to send events
     * and People Analytics updates to Mixpanel.</p>
     * <p>getInstance is thread safe, but the returned instance is not,
     * and may be shared with other callers of getInstance.
     * The best practice is to call getInstance, and use the returned MixpanelAPI,
     * object from a single thread (probably the main UI thread of your application).</p>
     * <p>If you do choose to track events from multiple threads in your application,
     * you should synchronize your calls on the instance itself, like so:</p>
     * <pre>
     * {@code
     * MixpanelAPI instance = MixpanelAPI.getInstance(context, token);
     * synchronized(instance) { // Only necessary if the instance will be used in multiple threads.
     *     instance.track(...)
     * }
     * }
     * </pre>
     *
     * @param context The application context you are tracking
     * @param token Your Mixpanel project token. You can get your project token on the Mixpanel web site,
     *     in the settings dialog.
     * @return an instance of MixpanelAPI associated with your project
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
     * <p>Every call to track eventually results in a data point sent to Mixpanel. These data points
     * are what are measured, counted, and broken down to create your Mixpanel reports. Events
     * have a string name, and an optional set of name/value pairs that describe the properties of
     * that event.
     *
     * @param eventName The name of the event to send
     * @param properties A JSONObject containing the key value pairs of the properties to include in this event.
     *                   Pass null if no extra properties exist.
     */
    // DO NOT DOCUMENT, but track() must be thread safe since it is used to track events in
    // notifications from the UI thread, which might not be our MixpanelAPI "home" thread.
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
     * Push all queued Mixpanel events and People Analytics changes to Mixpanel servers.
     *
     * <p>Events and People messages are pushed gradually throughout
     * the lifetime of your application. This means that to ensure that all messages
     * are sent to Mixpanel when your application is shut down, you will
     * need to call flush() to let the Mixpanel library know it should
     * send all remaining messages to the server. We strongly recommend
     * placing a call to flush() in the onDestroy() method of
     * your main application activity.
     */
    public void flush() {
        mMessages.postToServer();
    }

    /**
     * Returns the string id currently being used to uniquely identify the user associated
     * with events sent using {@link #track(String, JSONObject)}. Before any calls to
     * {@link #identify(String)}, this will be an id automatically generated by the library.
     *
     * <p>The id returned by getDistinctId is independent of the distinct id used to identify
     * any People Analytics properties in Mixpanel. To read and write that identifier,
     * use {@link People#identify(String)} and {@link People#getDistinctId()}.
     *
     * @return The distinct id associated with event tracking
     *
     * @see #identify(String)
     * @see People#getDistinctId()
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

    // Maps each token to a singleton MixpanelAPI instance
    private static final Map<String, Map<Context, LogbookAPI>> sInstanceMap = new HashMap<String, Map<Context, LogbookAPI>>();
    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
}
