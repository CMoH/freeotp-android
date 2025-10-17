package org.fedorahosted.freeotp.zauth;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.jetbrains.annotations.NotNull;

public class FirebaseService extends FirebaseMessagingService {

    public static final String LOGTAG = "FirebaseService";

    @Override
    public void onNewToken(@NonNull @NotNull String token) {
        super.onNewToken(token);
        Log.d(LOGTAG, String.format("Token: %s", token));
    }

    @Override
    public void onMessageReceived(@NonNull @NotNull RemoteMessage message) {
        String serverCode = message.getData().get("server_code");
        if (serverCode == null) {
            Log.w(LOGTAG, String.format("No server code received from %s, but got %s", message.getFrom(), message.getData()));
            return;
        }

        Data inputData = new Data.Builder()
                .putString("server_code", serverCode)
                .build();
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ZauthWorker.class)
                .setInputData(inputData)
                .build();
        WorkManager.getInstance(this).enqueue(workRequest);
    }

}