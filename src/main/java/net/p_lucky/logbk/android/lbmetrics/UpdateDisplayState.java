package net.p_lucky.logbk.android.lbmetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * This is a class intended for internal use by the library.
 * Users of the library should not interact with it directly.
 *
 * The interface to this class may change or disappear from release to release.
 */
@TargetApi(14)
public class UpdateDisplayState implements Parcelable {

    /**
     * This class is intended for internal use by the Mixpanel library.
     * Users of the library should not interact directly with this class.
     */
    public static abstract class DisplayState implements Parcelable {

        private DisplayState() {}

        public abstract String getType();


        public static final Creator<DisplayState> CREATOR =
                new Creator<DisplayState>() {
                    @Override
                    public DisplayState createFromParcel(final Parcel source) {
                        final Bundle read = new Bundle(DisplayState.class.getClassLoader());
                        read.readFromParcel(source);
                        final String type = read.getString(STATE_TYPE_KEY);
                        throw new RuntimeException("Unrecognized display state type " + type);
                    }

                    @Override
                    public DisplayState[] newArray(final int size) {
                        return new DisplayState[size];
                    }
                };

        public static final String STATE_TYPE_KEY = "com.mixpanel.android.mpmetrics.UpdateDisplayState.DisplayState.STATE_TYPE_KEY";
        public static final String STATE_IMPL_KEY = "com.mixpanel.android.mpmetrics.UpdateDisplayState.DisplayState.STATE_IMPL_KEY";
    }

    /**
     * This class is intended for internal use by the Mixpanel library.
     * Users of the library should not interact directly with this class.
     */
    public static class AnswerMap implements Parcelable {

        @SuppressLint("UseSparseArrays")
        public AnswerMap() {
            mMap = new HashMap<Integer, String>();
        }

        public void put(Integer i, String s) {
            mMap.put(i, s);
        }

        public String get(Integer i) {
            return mMap.get(i);
        }

        public boolean contentEquals(AnswerMap other) {
            return mMap.equals(other.mMap);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            final Bundle out = new Bundle();
            for (final Map.Entry<Integer, String> entry:mMap.entrySet()) {
                final String keyString = Integer.toString(entry.getKey());
                out.putString(keyString, entry.getValue());
            }
            dest.writeBundle(out);
        }

        public static final Parcelable.Creator<AnswerMap> CREATOR =
                new Parcelable.Creator<AnswerMap>() {
                    @Override
                    public AnswerMap createFromParcel(Parcel in) {
                        final Bundle read = new Bundle(AnswerMap.class.getClassLoader());
                        final AnswerMap ret = new AnswerMap();
                        read.readFromParcel(in);
                        for (final String kString:read.keySet()) {
                            final Integer kInt = Integer.valueOf(kString);
                            ret.put(kInt, read.getString(kString));
                        }
                        return ret;
                    }

                    @Override
                    public AnswerMap[] newArray(int size) {
                        return new AnswerMap[size];
                    }
                };

        private final HashMap<Integer, String> mMap;
    }

    /* package */ static ReentrantLock getLockObject() {
        // Returns an unlocked lock object. Does *not* acquire a lock!
        return sUpdateDisplayLock;
    }

    /* package */ static boolean hasCurrentProposal() {
        // Almost certainly a race condition of caller doesn't hold our lock object.
        assert sUpdateDisplayLock.isHeldByCurrentThread();

        final long currentTime = System.currentTimeMillis();
        final long deltaTime = currentTime - sUpdateDisplayLockMillis;

        if (sNextIntentId > 0 && deltaTime > MAX_LOCK_TIME_MILLIS) {
            Log.i(LOGTAG, "UpdateDisplayState set long, long ago, without showing.");
            sUpdateDisplayState = null;
        }

        return null != sUpdateDisplayState;
    }

    // Returned id should either be -1, or POSITIVE (nonzero). Don't return zero, please.
    /* package */ static int proposeDisplay(final DisplayState state, final String distinctId, final String token) {
        int ret = -1;

        assert sUpdateDisplayLock.isHeldByCurrentThread();
        if (! hasCurrentProposal()) {
            sUpdateDisplayLockMillis = System.currentTimeMillis();
            sUpdateDisplayState = new UpdateDisplayState(state, distinctId, token);
            sNextIntentId++;
            ret = sNextIntentId;
        } else {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Already showing (or cooking) a Mixpanel update, declining to show another.");
        }

        return ret;
    }

    /**
     * Client code should not call this method.
     */
    public static void releaseDisplayState(int intentId) {
        sUpdateDisplayLock.lock();
        try {
            if (intentId == sShowingIntentId) {
                sShowingIntentId = -1;
                sUpdateDisplayState = null;
            }
        } finally {
            sUpdateDisplayLock.unlock();
        }
    }

    /**
     * Client code should not call this method.
     */
    public static UpdateDisplayState claimDisplayState(final int intentId) {
        sUpdateDisplayLock.lock();
        try {
            if (sShowingIntentId > 0 && sShowingIntentId != intentId) {
                // Someone else has claimed another intent already
                return null;
            } else if (sUpdateDisplayState == null) {
                // Nothing to claim, caller is too late (or crazy)
                return null;
            } else {
                // Claim is successful
                sUpdateDisplayLockMillis = System.currentTimeMillis();
                sShowingIntentId = intentId;
                return sUpdateDisplayState;
            }
        } finally {
            sUpdateDisplayLock.unlock();
        }
    }

    public static final Parcelable.Creator<UpdateDisplayState> CREATOR = new Parcelable.Creator<UpdateDisplayState>() {
        @Override
        public UpdateDisplayState createFromParcel(Parcel in) {
            final Bundle read = new Bundle(UpdateDisplayState.class.getClassLoader());
            read.readFromParcel(in);
            return new UpdateDisplayState(read);
        }

        @Override
        public UpdateDisplayState[] newArray(int size) {
            return new UpdateDisplayState[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        final Bundle bundle = new Bundle();
        bundle.putString(DISTINCT_ID_BUNDLE_KEY, mDistinctId);
        bundle.putString(TOKEN_BUNDLE_KEY, mToken);
        bundle.putParcelable(DISPLAYSTATE_BUNDLE_KEY, mDisplayState);
        dest.writeBundle(bundle);
    }

    public DisplayState getDisplayState() {
        return mDisplayState;
    }

    public String getDistinctId() {
       return mDistinctId;
    }

    public String getToken() {
        return mToken;
    }

    // Package access for testing only- DO NOT CALL in library code
    /* package */ UpdateDisplayState(final DisplayState displayState, final String distinctId, final String token) {
        mDistinctId = distinctId;
        mToken = token;
        mDisplayState = displayState;
    }

    // Bundle must have the same ClassLoader that loaded this constructor.
    private UpdateDisplayState(Bundle read) {
        mDistinctId = read.getString(DISTINCT_ID_BUNDLE_KEY);
        mToken = read.getString(TOKEN_BUNDLE_KEY);
        mDisplayState = read.getParcelable(DISPLAYSTATE_BUNDLE_KEY);
    }

    private final String mDistinctId;
    private final String mToken;
    private final DisplayState mDisplayState;

    private static final ReentrantLock sUpdateDisplayLock = new ReentrantLock();
    private static long sUpdateDisplayLockMillis = -1;
    private static UpdateDisplayState sUpdateDisplayState = null;
    private static int sNextIntentId = 0;
    private static int sShowingIntentId = -1;

    private static final String LOGTAG = "MixpanelAPI UpdateDisplayState";
    private static final long MAX_LOCK_TIME_MILLIS = 12 * 60 * 60 * 1000; // Twelve hour timeout on survey activities

    private static final String DISTINCT_ID_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.UpdateDisplayState.DISTINCT_ID_BUNDLE_KEY";
    private static final String TOKEN_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.UpdateDisplayState.TOKEN_BUNDLE_KEY";
    private static final String DISPLAYSTATE_BUNDLE_KEY = "com.mixpanel.android.mpmetrics.UpdateDisplayState.DISPLAYSTATE_BUNDLE_KEY";

}
