package net.p_lucky.logbk.android.lbmetrics;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import android.content.Context;

/* package */ class DecideChecker {

    /* package */ static class Result {
        public Result() {
        }
    }

    public DecideChecker(final Context context, final MPConfig config) {
        mChecks = new LinkedList<DecideUpdates>();
    }

    public void addDecideCheck(final DecideUpdates check) {
        mChecks.add(check);
    }

    public void runDecideChecks(final ServerMessage poster) {
        final Iterator<DecideUpdates> itr = mChecks.iterator();
        while (itr.hasNext()) {
            final DecideUpdates updates = itr.next();
            if (updates.isDestroyed()) {
                itr.remove();
            } else {
                updates.reportResults();
            }
        }
    }

    private final List<DecideUpdates> mChecks;
}
