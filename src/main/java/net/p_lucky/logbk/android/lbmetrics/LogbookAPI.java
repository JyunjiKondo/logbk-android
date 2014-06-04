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
     * Sets the target frequency of messages to Mixpanel servers.
     * If no calls to {@link #flush()} are made, the Mixpanel
     * library attempts to send tracking information in batches at a rate
     * that provides a reasonable compromise between battery life and liveness of data.
     * Callers can override this value, for the whole application, by calling
     * <tt>setFlushInterval</tt>.
     *
     * If milliseconds is negative, Mixpanel will never flush the data automatically,
     * and require callers to call {@link #flush()} to send data. This can have
     * implications for storage and is not appropriate for most situations.
     *
     * @param context the execution context associated with this application, probably
     *      the main application activity.
     * @param milliseconds the target number of milliseconds between automatic flushes.
     *      this value is advisory, actual flushes may be more or less frequent
     * @deprecated in 4.0.0, use com.mixpanel.android.MPConfig.FlushInterval application metadata instead
     */
    @Deprecated
    public static void setFlushInterval(Context context, long milliseconds) {
        Log.i(
            LOGTAG,
            "LogbookAPI.setFlushInterval is deprecated.\n" +
            "    To set a custom Logbook flush interval for your application, add\n" +
            "    <meta-data android:name=\"net.p_lucky.logbk.android.LBConfig.FlushInterval\" android:value=\"YOUR_INTERVAL\" />\n" +
            "    to the <application> section of your AndroidManifest.xml."
        );
        final AnalyticsMessages msgs = AnalyticsMessages.getInstance(context);
        msgs.setFlushInterval(milliseconds);
    }

    /**
     * By default, if the MixpanelAPI cannot contact the API server over HTTPS,
     * it will attempt to contact the server via regular HTTP. To disable this
     * behavior, call enableFallbackServer(context, false)
     *
     * @param context the execution context associated with this context.
     * @param enableIfTrue if true, the library will fall back to using http
     *      when https is unavailable.
     * @deprecated in 4.0.0, use com.mixpanel.android.MPConfig.EventsFallbackEndpoint, com.mixpanel.android.MPConfig.PeopleFallbackEndpoint, or com.mixpanel.android.MPConfig.DecideFallbackEndpoint instead
     */
    @Deprecated
    public static void enableFallbackServer(Context context, boolean enableIfTrue) {
        Log.i(
            LOGTAG,
            "LogbookAPI.enableFallbackServer is deprecated.\n" +
            "    To enable fallback in your application, add\n" +
            "    <meta-data android:name=\"net.p_lucky.logbk.android.LBConfig.DisableFallback\" android:value=\"false\" />\n" +
            "    to the <application> section of your AndroidManifest.xml."
        );
        final AnalyticsMessages msgs = AnalyticsMessages.getInstance(context);
        msgs.setDisableFallback(! enableIfTrue);
    }

    /**
     * This function creates a distinct_id alias from alias to original. If original is null, then it will create an alias
     * to the current events distinct_id, which may be the distinct_id randomly generated by the Mixpanel library
     * before {@link #identify(String)} is called.
     *
     * <p>This call does not identify the user after. You must still call both {@link #identify(String)} and
     * {@link People#identify(String)} if you wish the new alias to be used for Events and People.
     *
     * @param alias the new distinct_id that should represent original.
     * @param original the old distinct_id that alias will be mapped to.
     */
    public void alias(String alias, String original) {
        if (original == null) {
            original = getDistinctId();
        }
        if (alias.equals(original)) {
            Log.w(LOGTAG, "Attempted to alias identical distinct_ids " + alias + ", returning.");
            return;
        }

        try {
            JSONObject j = new JSONObject();
            j.put("alias", alias);
            j.put("original", original);
            track("$create_alias", j);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Failed to alias", e);
        }
        flush();
    }

    /**
     * Associate all future calls to {@link #track(String, JSONObject)} with the user identified by
     * the given distinct id.
     *
     * <p>This call does not identify the user for People Analytics;
     * to do that, see {@link People#identify(String)}. Mixpanel recommends using
     * the same distinct_id for both calls, and using a distinct_id that is easy
     * to associate with the given user, for example, a server-side account identifier.
     *
     * <p>Calls to {@link #track(String, JSONObject)} made before corresponding calls to
     * identify will use an internally generated distinct id, which means it is best
     * to call identify early to ensure that your Mixpanel funnels and retention
     * analytics can continue to track the user throughout their lifetime. We recommend
     * calling identify as early as you can.
     *
     * <p>Once identify is called, the given distinct id persists across restarts of your
     * application.
     *
     * @param distinctId a string uniquely identifying this user. Events sent to
     *     Mixpanel using the same disinct_id will be considered associated with the
     *     same visitor/customer for retention and funnel reporting, so be sure that the given
     *     value is globally unique for each individual user you intend to track.
     *
     * @see People#identify(String)
     */
    public void identify(String distinctId) {
       mPersistentIdentity.setEventsDistinctId(distinctId);
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

            final JSONObject superProperties = mPersistentIdentity.getSuperProperties();
            final Iterator<?> superIter = superProperties.keys();
            while (superIter.hasNext()) {
                final String key = (String) superIter.next();
                messageProps.put(key, superProperties.get(key));
            }

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
    public String getDistinctId() {
        return mPersistentIdentity.getEventsDistinctId();
     }

    /**
     * Register properties that will be sent with every subsequent call to {@link #track(String, JSONObject)}.
     *
     * <p>SuperProperties are a collection of properties that will be sent with every event to Mixpanel,
     * and persist beyond the lifetime of your application.
     *
     * <p>Setting a superProperty with registerSuperProperties will store a new superProperty,
     * possibly overwriting any existing superProperty with the same name (to set a
     * superProperty only if it is currently unset, use {@link #registerSuperPropertiesOnce(JSONObject)})
     *
     * <p>SuperProperties will persist even if your application is taken completely out of memory.
     * to remove a superProperty, call {@link #unregisterSuperProperty(String)} or {@link #clearSuperProperties()}
     *
     * @param superProperties    A JSONObject containing super properties to register
     * @see #registerSuperPropertiesOnce(JSONObject)
     * @see #unregisterSuperProperty(String)
     * @see #clearSuperProperties()
     */
    public void registerSuperProperties(JSONObject superProperties) {
        mPersistentIdentity.registerSuperProperties(superProperties);
    }

    /**
     * Remove a single superProperty, so that it will not be sent with future calls to {@link #track(String, JSONObject)}.
     *
     * <p>If there is a superProperty registered with the given name, it will be permanently
     * removed from the existing superProperties.
     * To clear all superProperties, use {@link #clearSuperProperties()}
     *
     * @param superPropertyName name of the property to unregister
     * @see #registerSuperProperties(JSONObject)
     */
    public void unregisterSuperProperty(String superPropertyName) {
        mPersistentIdentity.unregisterSuperProperty(superPropertyName);
    }

    /**
     * Register super properties for events, only if no other super property with the
     * same names has already been registered.
     *
     * <p>Calling registerSuperPropertiesOnce will never overwrite existing properties.
     *
     * @param superProperties A JSONObject containing the super properties to register.
     * @see #registerSuperProperties(JSONObject)
     */
    public void registerSuperPropertiesOnce(JSONObject superProperties) {
        mPersistentIdentity.registerSuperPropertiesOnce(superProperties);
    }

    /**
     * Erase all currently registered superProperties.
     *
     * <p>Future tracking calls to Mixpanel (even those already queued up but not
     * yet sent to Mixpanel servers) will not be associated with the superProperties registered
     * before this call was made.
     *
     * <p>To remove a single superProperty, use {@link #unregisterSuperProperty(String)}
     *
     * @see #registerSuperProperties(JSONObject)
     */
    public void clearSuperProperties() {
        mPersistentIdentity.clearSuperProperties();
    }

    /**
     * Manage verbose logging about messages sent to Mixpanel.
     *
     * <p>Under ordinary circumstances, the Mixpanel library will only send messages
     * to the log when errors occur. However, after logPosts is called, Mixpanel will
     * send messages describing it's communication with the Mixpanel servers to
     * the system log.
     *
     * <p>Mixpanel will log its verbose messages tag "MixpanelAPI" with priority I("Information")
     */
    @Deprecated
    public void logPosts() {
        Log.i(
                LOGTAG,
                "LogbookAPI.logPosts() is deprecated.\n" +
                        "    To get verbose debug level logging, add\n" +
                        "    <meta-data android:name=\"net.p_lucky.logbk.android.LBConfig.EnableDebugLogging\" />\n" +
                        "    to the <application> section of your AndroidManifest.xml."
        );
    }

    // Package-level access. Used (at least) by GCMReceiver
    // when OS-level events occur.
    /* package */ interface InstanceProcessor {
        public void process(LogbookAPI m);
    }

    /* package */ static void allInstances(InstanceProcessor processor) {
        synchronized (sInstanceMap) {
            for (final Map<Context, LogbookAPI> contextInstances:sInstanceMap.values()) {
                for (final LogbookAPI instance:contextInstances.values()) {
                    processor.process(instance);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////
    // Conveniences for testing. These methods should not be called by
    // non-test client code.

    /* package */ AnalyticsMessages getAnalyticsMessages() {
        return AnalyticsMessages.getInstance(mContext);
    }

    /* package */ LBConfig getConfig() {
        return LBConfig.getInstance(mContext);
    }

    /* package */ PersistentIdentity getPersistentIdentity(final Context context, final String token) {
        final String prefsName = "net.p_lucky.logbk.android.lbmetrics.LogbookAPI_" + token;
        final Future<SharedPreferences> storedPreferences = sPrefsLoader.loadPreferences(context, prefsName, null);
        return new PersistentIdentity(storedPreferences);
    }

    /* package */ void clearPreferences() {
        // Will clear distinct_ids, superProperties,
        // and waiting People Analytics properties. Will have no effect
        // on messages already queued to send with AnalyticsMessages.
        mPersistentIdentity.clearPreferences();
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
