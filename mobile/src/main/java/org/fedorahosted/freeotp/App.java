package org.fedorahosted.freeotp;

import android.app.Application;
import org.fedorahosted.freeotp.auth41.Notifications;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.createNotificationChannels(this);
    }

}
