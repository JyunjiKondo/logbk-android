package net.p_lucky.logbk.android.lbmetrics;

/**
 * For use with {@link LogbookAPI.People#addOnMixpanelUpdatesReceivedListener(OnMixpanelUpdatesReceivedListener)}
 */
public interface OnMixpanelUpdatesReceivedListener {
    /**
     * Called when the Mixpanel library has updates, for example, Surveys or Notifications.
     * This method will not be called once per update, but rather any time a batch of updates
     * becomes available. The related updates can be checked with
     * {@link LogbookAPI.People#getSurveyIfAvailable()} or {@link LogbookAPI.People#getNotificationIfAvailable()}
     */
    public void onMixpanelUpdatesReceived();
}
