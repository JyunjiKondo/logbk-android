package net.p_lucky.logbk.android.lbmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;

import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class PersistentIdentityTest extends AndroidTestCase {
    public void setUp() {
        SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = testPreferences.edit();
        prefsEditor.clear();
        prefsEditor.putString("events_distinct_id", "EVENTS DISTINCT ID");
        prefsEditor.putString("push_id", "PUSH ID");
        prefsEditor.putString("waiting_array", "[ {\"thing\": 1}, {\"thing\": 2} ]");
        prefsEditor.commit();

        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> testLoader = loader.loadPreferences(getContext(), TEST_PREFERENCES, null);

        mPersistentIdentity = new PersistentIdentity(testLoader);
    }

    public void testUnsetEventsId() {
        final SharedPreferences testPreferences = getContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().clear().commit();
        final String eventsId = mPersistentIdentity.getEventsDistinctId();
        assertTrue(Pattern.matches("^[A-Fa-f0-9]{32}$", eventsId));

        final String autoId = testPreferences.getString("events_distinct_id", "NOPE");
        assertEquals(autoId, eventsId);

        mPersistentIdentity.setEventsDistinctId("TEST ID TO SET");
        final String heardId = mPersistentIdentity.getEventsDistinctId();
        assertEquals("TEST ID TO SET", heardId);

        final String storedId = testPreferences.getString("events_distinct_id", "NOPE");
        assertEquals("TEST ID TO SET", storedId);
    }

    private PersistentIdentity mPersistentIdentity;
    private static final String TEST_PREFERENCES = "TEST PERSISTENT PROPERTIES PREFS";
}
