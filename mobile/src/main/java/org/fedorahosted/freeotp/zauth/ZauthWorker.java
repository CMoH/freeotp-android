package org.fedorahosted.freeotp.zauth;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.*;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Log;
import android.util.Pair;
import androidx.core.app.NotificationCompat;
import androidx.work.CoroutineWorker;
import androidx.work.WorkerParameters;
import com.google.gson.Gson;
import kotlin.coroutines.Continuation;
import okhttp3.*;
import org.fedorahosted.freeotp.Code;
import org.fedorahosted.freeotp.R;
import org.fedorahosted.freeotp.TokenPersistence;
import org.fedorahosted.freeotp.main.Activity;
import org.fedorahosted.freeotp.main.Adapter;
import org.fedorahosted.freeotp.utils.SelectableAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.NavigableSet;

public class ZauthWorker extends CoroutineWorker implements SelectableAdapter.EventListener {

    private static final String LOGTAG = "ZauthWorker";

    private final Adapter mTokenAdapter;

    public ZauthWorker(@NotNull Context appContext, @NotNull WorkerParameters params) throws GeneralSecurityException, IOException {
        super(appContext, params);
        mTokenAdapter = new Adapter(appContext, this);
    }

    @Override
    public @Nullable Object doWork(@NotNull Continuation<? super Result> continuation) {
        String zauthServerCode = getInputData().getString("server_code");
        if (zauthServerCode == null) {
            return Result.failure();
        }

        Context appContext = getApplicationContext();
        processCode(appContext, zauthServerCode);
        return Result.success();
    }


    private void processCode(Context context, String zauthServerCode) {
        String label = "unknown";
        try {
            String zauthUrl = mTokenAdapter.getZauthUrl(zauthServerCode);
            if (zauthUrl == null) {
                notifyUrgent(context, zauthServerCode,
                        context.getString(R.string.zauth_notification_invalid_token_title),
                        context.getString(R.string.zauth_notification_invalid_token_message, label));
                return; // zauth disabled
            }

            Pair<String, String> labelIssuer = mTokenAdapter.getLabel(zauthServerCode);
            label = labelIssuer.first;
            Code code = mTokenAdapter.getCode(zauthServerCode);
            postZauthCode(context, zauthServerCode, new URL(zauthUrl), label, code);

            notifyInfo(context, zauthServerCode,
                    context.getString(R.string.zauth_notification_success),
                    context.getString(R.string.zauth_notification_success_message, label));
        } catch (UserNotAuthenticatedException e) {
            Log.w(LOGTAG, "Exception", e);

            // show notification
            Intent intent = new Intent(context, Activity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

            notifyUrgent(context, zauthServerCode,
                    context.getString(R.string.zauth_notification_auth_needed_title),
                    context.getString(R.string.zauth_notification_auth_needed_message, label));

            // TODO: activity that ask for login and completes the authentication, if user allows it
//            KeyguardManager km = (KeyguardManager) getSystemService(context, KEYGUARD_SERVICE);
//            Intent i = km.createConfirmDeviceCredentialIntent(issuer, label);
//            startActivityForResult(i, 0, null);
        } catch (KeyPermanentlyInvalidatedException e) {
            Log.w(LOGTAG, "Exception", e);
            try {
                mTokenAdapter.delete(zauthServerCode);
            } catch (GeneralSecurityException | IOException f) {
                Log.e(LOGTAG, "Exception", e);
            }
            notifyUrgent(context, zauthServerCode,
                    context.getString(R.string.zauth_notification_invalidated_title),
                    context.getString(R.string.zauth_notification_invalidated_message, label));
        } catch (MalformedURLException e) {
            Log.e(LOGTAG, "Exception", e);
            notifyUrgent(context, zauthServerCode,
                    context.getString(R.string.zauth_notification_invalid_url_title),
                    context.getString(R.string.zauth_notification_invalid_url_message, label));
        }
    }

    private void postZauthCode(Context context, String zauthServerCode, URL zauthUrl, String label, Code code) {
        Log.i(LOGTAG, String.format("postZauthCode[%s]: sending code %s to %s", label, code.getCode(), zauthUrl));

        Gson gson = new Gson();
        String json = gson.toJson(code.getCode(), String.class); // TODO: add more info, like device ID or time validity maybe
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(zauthUrl)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(LOGTAG, String.format("postZauthCode[%s]: failed with code %d", label, response.code()));
                notifyUrgent(context, zauthServerCode,
                        context.getString(R.string.zauth_notification_failed_title),
                        context.getString(R.string.zauth_notification_failed_message_http, label, response.code()));
            }
        } catch (IOException e) {
            Log.w(LOGTAG, String.format("postZauthCode[%s]: exception for item", label), e);
            notifyUrgent(context, zauthServerCode,
                    context.getString(R.string.zauth_notification_failed_title),
                    context.getString(R.string.zauth_notification_failed_message, label, e.getLocalizedMessage()));
        }
    }

    @Override
    public void onSelectEvent(NavigableSet<Integer> selected) {
        // nothing to do here
    }

    private static void notifyInfo(Context context, String zauthServerCode, String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Notifications.ZAUTH_CHANNEL_ID_INFO)
                .setSmallIcon(R.drawable.ic_freeotp)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true);
        Notifications.show(context, builder.build(), zauthServerCode.hashCode());
    }

    private static void notifyUrgent(Context context, String zauthServerCode, String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Notifications.ZAUTH_CHANNEL_ID_URGENT)
                .setSmallIcon(R.drawable.ic_freeotp)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true);
        Notifications.show(context, builder.build(), zauthServerCode.hashCode());
    }

}
