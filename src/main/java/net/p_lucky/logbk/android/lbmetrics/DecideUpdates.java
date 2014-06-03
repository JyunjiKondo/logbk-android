package net.p_lucky.logbk.android.lbmetrics;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

// Will be called from both customer threads and the Mixpanel worker thread.
/* package */ class DecideUpdates {

    public interface OnNewResultsListener {
        public void onNewResults(String distinctId);
    }

    public DecideUpdates(String token, String distinctId, OnNewResultsListener listener) {
        mToken = token;
        mDistinctId = distinctId;

        mListener = listener;
        mUnseenNotifications = new LinkedList<InAppNotification>();
        mNotificationIds = new HashSet<Integer>();
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
    public synchronized void reportResults(List<InAppNotification> newNotifications) {
        boolean newContent = false;

        for (final InAppNotification n: newNotifications) {
            final int id = n.getId();
            if (! mNotificationIds.contains(id)) {
                mNotificationIds.add(id);
                mUnseenNotifications.add(n);
                newContent = true;
            }
        }

        if (newContent && hasUpdatesAvailable() && null != mListener) {
            mListener.onNewResults(getDistinctId());
        }
    }

    public synchronized InAppNotification getNotification(boolean replace) {
        if (mUnseenNotifications.isEmpty()) {
            return null;
        }
        InAppNotification n = mUnseenNotifications.remove(0);
        if (replace) {
            mUnseenNotifications.add(mUnseenNotifications.size(), n);
        }
        return n;
    }

    public synchronized InAppNotification getNotification(int id, boolean replace) {
        if (mUnseenNotifications == null) {
            return null;
        }
        InAppNotification notif = null;
        for (int i = 0; i < mUnseenNotifications.size(); i++) {
            if (mUnseenNotifications.get(i).getId() == id) {
                notif = mUnseenNotifications.get(i);
                if (!replace) {
                    mUnseenNotifications.remove(i);
                }
                break;
            }
        }
        return notif;
    }

    public synchronized boolean hasUpdatesAvailable() {
        return (! mUnseenNotifications.isEmpty());
    }

    private final String mToken;
    private final String mDistinctId;
    private final Set<Integer> mNotificationIds;
    private final List<InAppNotification> mUnseenNotifications;
    private final OnNewResultsListener mListener;
    private final AtomicBoolean mIsDestroyed;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI DecideUpdates";
}
