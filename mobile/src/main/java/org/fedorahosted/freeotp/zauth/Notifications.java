package org.fedorahosted.freeotp.zauth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import org.fedorahosted.freeotp.R;

import java.util.List;

import static androidx.core.content.ContextCompat.getSystemService;

public class Notifications {

    public static final String ZAUTH_CHANNEL_ID_URGENT = "org.fedorahosted.freeotp.zauth.urgent";
    public static final String ZAUTH_CHANNEL_ID_INFO = "org.fedorahosted.freeotp.zauth.info";

    public static void createNotificationChannels(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel urgentChannel = new NotificationChannel(ZAUTH_CHANNEL_ID_URGENT,
                    context.getString(R.string.zauth_channel_important_name),
                    NotificationManager.IMPORTANCE_HIGH);
            urgentChannel.setDescription(context.getString(R.string.zauth_channel_important_description));

            NotificationChannel infoChannel = new NotificationChannel(ZAUTH_CHANNEL_ID_INFO,
                    context.getString(R.string.zauth_channel_info_name),
                    NotificationManager.IMPORTANCE_MIN);
            infoChannel.setDescription(context.getString(R.string.zauth_channel_info_description));

            NotificationManager notificationManager = getSystemService(context, NotificationManager.class);
            // TODO: do we check for null?
            notificationManager.createNotificationChannels(List.of(urgentChannel, infoChannel));
        }
    }

    public static void show(Context context, Notification notification, int id) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            // ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            // public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                        int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        NotificationManagerCompat.from(context).notify(id, notification);
    }
}
