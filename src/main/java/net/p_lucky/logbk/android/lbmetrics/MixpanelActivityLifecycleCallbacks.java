package net.p_lucky.logbk.android.lbmetrics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

@TargetApi(14)
class MixpanelActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    public MixpanelActivityLifecycleCallbacks(LogbookAPI mpInstance) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (!activity.isTaskRoot()) {
            return; // No checks, no nothing.
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override
    public void onActivityPaused(Activity activity) { }

    @Override
    public void onActivityDestroyed(Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityResumed(Activity activity) { }

    @Override
    public void onActivityStopped(Activity activity) { }
}
