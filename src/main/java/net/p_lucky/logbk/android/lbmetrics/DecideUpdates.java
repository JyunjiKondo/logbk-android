package net.p_lucky.logbk.android.lbmetrics;

import java.util.concurrent.atomic.AtomicBoolean;

// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideUpdates {

    public interface OnNewResultsListener {
        public void onNewResults(String distinctId);
    }

    public DecideUpdates(String token, String distinctId, OnNewResultsListener listener) {
        mToken = token;
        mDistinctId = distinctId;

        mIsDestroyed = new AtomicBoolean(false);
    }

    public String getToken() {
        return mToken;
    }

    public String getDistinctId() {
        return mDistinctId;
    }

    public void destroy() {
        mIsDestroyed.set(true);
    }

    public boolean isDestroyed() {
        return mIsDestroyed.get();
    }

    // Do not consult destroyed status inside of this method.
    public synchronized void reportResults() {
    }

    public synchronized boolean hasUpdatesAvailable() {
        return false;
    }

    private final String mToken;
    private final String mDistinctId;
    private final AtomicBoolean mIsDestroyed;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI DecideUpdates";
}
