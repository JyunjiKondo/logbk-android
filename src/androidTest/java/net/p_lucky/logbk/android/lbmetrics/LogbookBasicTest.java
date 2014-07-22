package net.p_lucky.logbk.android.lbmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.test.AndroidTestCase;
import android.test.mock.MockContext;
import android.test.mock.MockPackageManager;

import net.p_lucky.logbk.android.util.Base64Coder;

import org.apache.http.NameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LogbookBasicTest extends AndroidTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final SharedPreferences referrerPreferences = getContext().getSharedPreferences("LOGBOOK_TEST_PREFERENCES", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = referrerPreferences.edit();
        editor.clear();
        editor.commit();

        mMockPreferences = new Future<SharedPreferences>() {
            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public SharedPreferences get() throws InterruptedException, ExecutionException {
                return referrerPreferences;
            }

            @Override
            public SharedPreferences get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return referrerPreferences;
            }
        };

        AnalyticsMessages messages = AnalyticsMessages.getInstance(getContext());
        messages.hardKill();
        Thread.sleep(500);
    } // end of setUp() method definition

    public void testTrivialRunning() {
        assertTrue(getContext() != null);
    }

    public void testGeneratedDistinctId() {
        String fakeToken = UUID.randomUUID().toString();
        LogbookAPI logbook = new TestUtils.CleanLogbookAPI(getContext(), mMockPreferences, fakeToken);
        String generatedId1 = logbook.getDistinctId();
        assertTrue(generatedId1 != null);

        logbook.clearPreferences();
        String generatedId2 = logbook.getDistinctId();
        assertTrue(generatedId2 != null);
        assertTrue(generatedId1 != generatedId2);
    }

    public void testDeleteDB() {
        Map<String, String> beforeMap = new HashMap<String, String>();
        beforeMap.put("added", "before");
        JSONObject before = new JSONObject(beforeMap);

        Map<String, String> afterMap = new HashMap<String,String>();
        afterMap.put("added", "after");
        JSONObject after = new JSONObject(afterMap);

        LBDbAdapter adapter = new LBDbAdapter(getContext(), "DeleteTestDB");
        adapter.addJSON(before, LBDbAdapter.Table.EVENTS);
        adapter.deleteDB();

        String[] emptyEventsData = adapter.generateDataString(LBDbAdapter.Table.EVENTS);
        assertEquals(emptyEventsData, null);

        adapter.addJSON(after, LBDbAdapter.Table.EVENTS);

        try {
            String[] someEventsData = adapter.generateDataString(LBDbAdapter.Table.EVENTS);
            JSONArray someEvents = new JSONArray(someEventsData[1]);
            assertEquals(someEvents.length(), 1);
            assertEquals(someEvents.getJSONObject(0).get("added"), "after");
        } catch (JSONException e) {
            fail("Unexpected JSON or lack thereof in MPDbAdapter test");
        }
    }

    public void testLooperDestruction() {

        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<JSONObject>();

        // If something terrible happens in the worker thread, we
        // should make sure
        final LBDbAdapter explodingDb = new LBDbAdapter(getContext()) {
            @Override
            public int addJSON(JSONObject message, LBDbAdapter.Table table) {
                messages.add(message);
                throw new RuntimeException("BANG!");
            }
        };
        final AnalyticsMessages explodingMessages = new AnalyticsMessages(getContext()) {
            // This will throw inside of our worker thread.
            @Override
            public LBDbAdapter makeDbAdapter(Context context) {
                return explodingDb;
            }
        };
        LogbookAPI logbook = new TestUtils.CleanLogbookAPI(getContext(), mMockPreferences, "TEST TOKEN testLooperDisaster") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return explodingMessages;
            }
        };

        try {
            logbook.clearPreferences();
            assertFalse(explodingMessages.isDead());

            logbook.track("event1");
            JSONObject found = messages.poll(1, TimeUnit.SECONDS);
            assertNotNull(found);
            Thread.sleep(1000);
            assertTrue(explodingMessages.isDead());

            logbook.track("event2");
            JSONObject shouldntFind = messages.poll(1, TimeUnit.SECONDS);
            assertNull(shouldntFind);
            assertTrue(explodingMessages.isDead());
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        }
    }

    public void testMessageQueuing() {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();

        final LBDbAdapter mockAdapter = new LBDbAdapter(getContext()) {
            @Override
            public int addJSON(JSONObject message, LBDbAdapter.Table table) {
                try {
                    messages.put("TABLE " + table.getName());
                    messages.put(message.toString());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return super.addJSON(message, table);
            }
        };
        mockAdapter.cleanupEvents(Long.MAX_VALUE, LBDbAdapter.Table.EVENTS);

        final ServerMessage mockPoster = new ServerMessage() {
            @Override
            public byte[] performRequest(String endpointUrl, List<NameValuePair> nameValuePairs) {
                if (null == nameValuePairs) {
                    fail("User is unidentified, we shouldn't be checking decide. (URL WAS " + endpointUrl + ")");
                    return TestUtils.bytes("{}");
                }

                assertEquals(nameValuePairs.get(0).getName(), "code");
                assertEquals(nameValuePairs.get(1).getName(), "data");
                final String decoded = Base64Coder.decodeString(nameValuePairs.get(1).getValue());

                try {
                    messages.put("SENT FLUSH " + endpointUrl);
                    messages.put(decoded);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return TestUtils.bytes("1\n");
            }
        };


        final LBConfig mockConfig = new LBConfig(new Bundle()) {
            @Override
            public int getFlushInterval() {
                return -1;
            }

            @Override
            public int getBulkUploadLimit() {
                return 40;
            }

            @Override
            public String getEventsEndpoint() {
                return "EVENTS_ENDPOINT";
            }
        };

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            protected LBDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected LBConfig getConfig(Context context) {
                return mockConfig;
            }

            @Override
            protected ServerMessage getPoster() {
                return mockPoster;
            }
        };

        LogbookAPI metrics = new TestUtils.CleanLogbookAPI(getContext(), mMockPreferences, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        };

        // Test filling up the message queue
        for (int i=0; i < mockConfig.getBulkUploadLimit() - 1; i++) {
            metrics.track("frequent event");
        }

        metrics.track("final event");
        String expectedJSONMessage = "<No message actually received>";

        try {
            for (int i=0; i < mockConfig.getBulkUploadLimit() - 1; i++) {
                String messageTable = messages.poll(1, TimeUnit.SECONDS);
                assertEquals("TABLE " + LBDbAdapter.Table.EVENTS.getName(), messageTable);

                expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
                JSONObject message = new JSONObject(expectedJSONMessage);
                assertEquals("frequent event", message.getString("event"));
            }

            String messageTable = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("TABLE " + LBDbAdapter.Table.EVENTS.getName(), messageTable);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONObject message = new JSONObject(expectedJSONMessage);
            assertEquals("final event", message.getString("event"));

            String messageFlush = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH EVENTS_ENDPOINT", messageFlush);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONArray bigFlush = new JSONArray(expectedJSONMessage);
            assertEquals(mockConfig.getBulkUploadLimit(), bigFlush.length());

            metrics.track("next wave");
            metrics.flush();

            String nextWaveTable = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("TABLE " + LBDbAdapter.Table.EVENTS.getName(), nextWaveTable);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONObject nextWaveMessage = new JSONObject(expectedJSONMessage);
            assertEquals("next wave", nextWaveMessage.getString("event"));

            String manualFlush = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("SENT FLUSH EVENTS_ENDPOINT", manualFlush);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONArray nextWave = new JSONArray(expectedJSONMessage);
            assertEquals(1, nextWave.length());

            JSONObject nextWaveEvent = nextWave.getJSONObject(0);
            assertEquals("next wave", nextWaveEvent.getString("event"));
        } catch (InterruptedException e) {
            fail("Expected a log message about logbook communication but did not recieve it.");
        } catch (JSONException e) {
            fail("Expected a JSON object message and got something silly instead: " + expectedJSONMessage);
        }
    }

    public void testHTTPFailures() {
        final List<Object> flushResults = new ArrayList<Object>();
        final BlockingQueue<String> performRequestCalls = new LinkedBlockingQueue<String>();

        final ServerMessage mockPoster = new ServerMessage() {
            @Override
            public byte[] performRequest(String endpointUrl, List<NameValuePair> nameValuePairs) throws IOException {
                if (null == nameValuePairs) {
                    assertEquals("DECIDE ENDPOINT?version=1&lib=android&token=Test+Message+Queuing&distinct_id=new+person", endpointUrl);
                    return TestUtils.bytes("{}");
                }

                Object obj = flushResults.remove(0);
                try {
                    assertEquals(nameValuePairs.get(0).getName(), "code");
                    assertEquals(nameValuePairs.get(1).getName(), "data");
                    final String jsonData = Base64Coder.decodeString(nameValuePairs.get(1).getValue());
                    JSONArray msg = new JSONArray(jsonData);
                    JSONObject event = msg.getJSONObject(0);
                    performRequestCalls.put(event.getString("event"));

                    if (obj instanceof IOException) {
                        throw (IOException)obj;
                    } else if (obj instanceof MalformedURLException) {
                        throw (MalformedURLException)obj;
                    }
                } catch (JSONException e) {
                    throw new RuntimeException("Malformed data passed to test mock", e);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Could not write message to reporting queue for tests.", e);
                }
                return (byte[])obj;
            }
        };

        final LBConfig config = new LBConfig(new Bundle()) {
            public String getEventsEndpoint() {
                return "EVENTS ENDPOINT";
            }
        };

        final List<String> cleanupCalls = new ArrayList<String>();
        final LBDbAdapter mockAdapter = new LBDbAdapter(getContext()) {
            @Override
            public void cleanupEvents(String last_id, Table table) {
                cleanupCalls.add("called");
                super.cleanupEvents(last_id, table);
            }
        };
        mockAdapter.cleanupEvents(Long.MAX_VALUE, LBDbAdapter.Table.EVENTS);

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            protected LBDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected ServerMessage getPoster() {
                return mockPoster;
            }

            @Override
            protected LBConfig getConfig(Context context) {
                return config;
            }
        };

        LogbookAPI metrics = new TestUtils.CleanLogbookAPI(getContext(), mMockPreferences, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                 return listener;
            }
        };

        try {
            // Basic succeed
            cleanupCalls.clear();
            flushResults.add(TestUtils.bytes("1\n"));
            metrics.track("Should Succeed");
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", performRequestCalls.poll(2, TimeUnit.SECONDS));
            assertEquals(null, performRequestCalls.poll(2, TimeUnit.SECONDS));
            assertEquals(1, cleanupCalls.size());

            // One IOException -- assume temporary network failure, no cleanup should happen until
            // second flush
            cleanupCalls.clear();
            flushResults.add(new IOException());
            flushResults.add(TestUtils.bytes("1\n"));
            metrics.track("Should Succeed");
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", performRequestCalls.poll(2, TimeUnit.SECONDS));
            assertEquals(0, cleanupCalls.size());
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Succeed", performRequestCalls.poll(2, TimeUnit.SECONDS));
            assertEquals(null, performRequestCalls.poll(2, TimeUnit.SECONDS));
            assertEquals(1, cleanupCalls.size());

            // MalformedURLException -- should dump the events since this will probably never succeed
            cleanupCalls.clear();
            flushResults.add(new MalformedURLException());
            metrics.track("Should Fail");
            metrics.flush();
            Thread.sleep(500);
            assertEquals("Should Fail", performRequestCalls.poll(2, TimeUnit.SECONDS));
            assertEquals(null, performRequestCalls.poll(2, TimeUnit.SECONDS));
            assertEquals(1, cleanupCalls.size());
        } catch (InterruptedException e) {
            throw new RuntimeException("Test was interrupted.");
        }
    }

    public void testTrackInThread() throws InterruptedException, JSONException {
        class TestThread extends Thread {
            BlockingQueue<JSONObject> mMessages;

            public TestThread(BlockingQueue<JSONObject> messages) {
                this.mMessages = messages;
            }

            @Override
            public void run() {

                final LBDbAdapter dbMock = new LBDbAdapter(getContext()) {
                    @Override
                    public int addJSON(JSONObject message, LBDbAdapter.Table table) {
                        mMessages.add(message);
                        return 1;
                    }
                };

                final AnalyticsMessages analyticsMessages = new AnalyticsMessages(getContext()) {
                    @Override
                    public LBDbAdapter makeDbAdapter(Context context) {
                        return dbMock;
                    }
                };

                LogbookAPI logbook = new TestUtils.CleanLogbookAPI(getContext(), mMockPreferences, "TEST TOKEN") {
                    @Override
                    protected AnalyticsMessages getAnalyticsMessages() {
                        return analyticsMessages;
                    }
                };
                logbook.clearPreferences();
                logbook.track("test in thread");
            }
        }

        //////////////////////////////

        final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<JSONObject>();
        TestThread testThread = new TestThread(messages);
        testThread.start();
        JSONObject found = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(found);
        assertEquals(found.getString("event"), "test in thread");
        assertTrue(found.has("bluetoothVersion"));
    }

    public void testConfiguration() {
        final ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.metaData = new Bundle();
        appInfo.metaData.putInt("net.p_lucky.logbk.android.LBConfig.BulkUploadLimit", 1);
        appInfo.metaData.putInt("net.p_lucky.logbk.android.LBConfig.FlushInterval", 2);
        appInfo.metaData.putInt("net.p_lucky.logbk.android.LBConfig.DataExpiration", 3);

        appInfo.metaData.putString("net.p_lucky.logbk.android.LBConfig.EventsEndpoint", "EVENTS ENDPOINT");

        final PackageManager packageManager = new MockPackageManager() {
            @Override
            public ApplicationInfo getApplicationInfo(String packageName, int flags) {
                assertEquals(packageName, "TEST PACKAGE NAME");
                assertTrue((flags & PackageManager.GET_META_DATA) == PackageManager.GET_META_DATA);
                return appInfo;
            }
        };

        final Context context = new MockContext() {
            @Override
            public String getPackageName() {
                return "TEST PACKAGE NAME";
            }

            @Override
            public PackageManager getPackageManager() {
                return packageManager;
            }
        };

        final LBConfig testConfig = LBConfig.readConfig(context);
        assertEquals(1, testConfig.getBulkUploadLimit());
        assertEquals(2, testConfig.getFlushInterval());
        assertEquals(3, testConfig.getDataExpiration());
        assertEquals("EVENTS ENDPOINT", testConfig.getEventsEndpoint());
    }

    public void testTrackAcquisition() {
        doTestTrackEvent(Action.Acquisition);
    }

    public void testTrackActivation() {
        doTestTrackEvent(Action.Activation);
    }

    public void testTrackRetention() {
        doTestTrackEvent(Action.Retention);
    }

    public void testTrackReferral() {
        doTestTrackEvent(Action.Referral);
    }

    public void testTrackRevenue() {
        doTestTrackEvent(Action.Revenue);
    }

    private void doTestTrackEvent(Action action) {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<String>();

        final LBDbAdapter mockAdapter = new LBDbAdapter(getContext()) {
            @Override
            public int addJSON(JSONObject message, LBDbAdapter.Table table) {
                try {
                    messages.put("TABLE " + table.getName());
                    messages.put(message.toString());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                return super.addJSON(message, table);
            }
        };
        mockAdapter.cleanupEvents(Long.MAX_VALUE, LBDbAdapter.Table.EVENTS);

        final LBConfig mockConfig = new LBConfig(new Bundle()) {
            @Override
            public int getFlushInterval() {
                return -1;
            }
        };

        final AnalyticsMessages listener = new AnalyticsMessages(getContext()) {
            @Override
            protected LBDbAdapter makeDbAdapter(Context context) {
                return mockAdapter;
            }

            @Override
            protected LBConfig getConfig(Context context) {
                return mockConfig;
            }
        };

        LogbookAPI metrics = new TestUtils.CleanLogbookAPI(getContext(), mMockPreferences, "Test Message Queuing") {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return listener;
            }
        };

        try {
            metrics.getClass().getMethod("track" + action.toString(), (Class<?>[]) null).invoke(metrics, (Object[]) null);
        } catch (Exception e) {
            fail("Unexpected interruption");
        }

        String expectedJSONMessage = "<No message actually received>";
        try {
            String messageTable = messages.poll(1, TimeUnit.SECONDS);
            assertEquals("TABLE " + LBDbAdapter.Table.EVENTS.getName(), messageTable);

            expectedJSONMessage = messages.poll(1, TimeUnit.SECONDS);
            JSONObject message = new JSONObject(expectedJSONMessage);
            assertEquals(action.getEventName(), message.getString("event"));
        } catch (InterruptedException e) {
            fail("Expected a log message about logbook communication but did not recieve it.");
        } catch (JSONException e) {
            fail("Expected a JSON object message and got something silly instead: " + expectedJSONMessage);
        }
    }

    public void testGetToken() {
        LogbookAPI.getInstance(getContext(), "TOKEN1");
        LogbookAPI.getInstance(getContext(), "TOKEN2");
        assertEquals(LogbookAPI.getToken(), "TOKEN2");
    }

    enum Action {
        Acquisition("_acquisition"),
        Activation("_activation"),
        Retention("_retention"),
        Referral("_referral"),
        Revenue("_revenue");

        Action(String eventName) {
            mEventName = eventName;
        }

        public String getEventName() {
            return mEventName;
        }

        private final String mEventName;
    }

    private Future<SharedPreferences> mMockPreferences;
}
