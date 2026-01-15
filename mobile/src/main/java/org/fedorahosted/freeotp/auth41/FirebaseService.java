package org.fedorahosted.freeotp.auth41;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.jetbrains.annotations.NotNull;

public class FirebaseService extends FirebaseMessagingService {

    public static final String LOGTAG = "FirebaseService";

    private static volatile String DEVICE_TOKEN;

    public static String getDeviceToken() {
        return DEVICE_TOKEN;
    }

    static {
        refreshDeviceToken();
    }

    public static void refreshDeviceToken() {
        if (DEVICE_TOKEN == null) {
            synchronized (FirebaseService.class) {
                if (DEVICE_TOKEN == null) {
                    Log.d(LOGTAG, "Attempting to refresh device token");

                    FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
                        DEVICE_TOKEN = token;
                        Log.d(LOGTAG, String.format("Got device token: %s", token));
                    }).addOnFailureListener(e -> {
                        Log.e(LOGTAG, String.format("Failed to get device token: %s", e.getMessage()));
                    });
                }
            }
        }
    }

    @Override
    public void onNewToken(@NonNull @NotNull String token) {
        super.onNewToken(token);
        synchronized (FirebaseService.class) {
            DEVICE_TOKEN = token;  // TODO: update all known device token registrations
        }
        Log.d(LOGTAG, String.format("New device token: %s", token));
    }

    @Override
    public void onMessageReceived(@NonNull @NotNull RemoteMessage message) {
        String serverCode = message.getData().get("server_code");
        if (serverCode == null) {
            Log.w(LOGTAG, String.format("No server code received from %s, but got %s", message.getFrom(), message.getData()));
            return;
        }

        String correlationId = message.getData().get("correlation_id");
        if (correlationId == null) {
            Log.w(LOGTAG, String.format("No correlation id received from %s, but got %s", message.getFrom(), message.getData()));
            return;
        }

        Data inputData = new Data.Builder()
                .putString(Auth41ChallengeWorker.SERVER_CODE, serverCode)
                .putString(Auth41ChallengeWorker.CORRELATION_ID, correlationId)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(Auth41ChallengeWorker.class)
                .setInputData(inputData)
                .build();
        WorkManager.getInstance(this).enqueue(workRequest);
    }

}