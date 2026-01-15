package org.fedorahosted.freeotp.auth41;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.*;
import android.net.Uri;
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
import org.fedorahosted.freeotp.main.Activity;
import org.fedorahosted.freeotp.main.Adapter;
import org.fedorahosted.freeotp.utils.SelectableAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.NavigableSet;

public class Auth41ChallengeWorker extends CoroutineWorker implements SelectableAdapter.EventListener {

    public static final String SERVER_CODE = "server_code";
    public static final String CORRELATION_ID = "correlation_id";

    private static final String LOGTAG = "Auth41ChallengeWorker";

    private final Adapter mTokenAdapter;

    public Auth41ChallengeWorker(@NotNull Context appContext, @NotNull WorkerParameters params) throws GeneralSecurityException, IOException {
        super(appContext, params);
        mTokenAdapter = new Adapter(appContext, this, null);
    }

    @Override
    public @Nullable Object doWork(@NotNull Continuation<? super Result> continuation) {
        String auth41ServerCode = getInputData().getString(SERVER_CODE);
        if (auth41ServerCode == null) {
            return Result.failure();
        }
        String auth41CorrelationId = getInputData().getString(CORRELATION_ID);
        if (auth41CorrelationId == null) {
            return Result.failure();
        }

        Context appContext = getApplicationContext();
        processCode(appContext, auth41ServerCode, auth41CorrelationId);
        return Result.success();
    }


    private void processCode(Context context, String auth41ServerCode, String auth41CorrelationId) {
        String label = "unknown";
        try {
            String auth41Url = mTokenAdapter.getAuth41Url(auth41ServerCode);
            if (auth41Url == null) {
                notifyUrgent(context, auth41ServerCode,
                        context.getString(R.string.auth41_notification_invalid_token_title),
                        context.getString(R.string.auth41_notification_invalid_token_message, label));
                return; // auth41 disabled
            }

            Uri uri = Uri.parse(auth41Url).buildUpon()
                    .appendPath("verify")
                    .appendQueryParameter("correlationId", auth41CorrelationId)
                    .build();

            Pair<String, String> labelIssuer = mTokenAdapter.getLabel(auth41ServerCode);
            label = labelIssuer.first;
            Code code = mTokenAdapter.getCode(auth41ServerCode);
            postAuth41Code(context, auth41ServerCode, uri, label, code);

            notifyInfo(context, auth41ServerCode,
                    context.getString(R.string.auth41_notification_success),
                    context.getString(R.string.auth41_notification_success_message, label));
        } catch (UserNotAuthenticatedException e) {
            Log.w(LOGTAG, "Exception", e);

            // show notification
            Intent intent = new Intent(context, Activity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

            notifyUrgent(context, auth41ServerCode,
                    context.getString(R.string.auth41_notification_auth_needed_title),
                    context.getString(R.string.auth41_notification_auth_needed_message, label));

            // TODO: activity that ask for login and completes the authentication, if user allows it
//            KeyguardManager km = (KeyguardManager) getSystemService(context, KEYGUARD_SERVICE);
//            Intent i = km.createConfirmDeviceCredentialIntent(issuer, label);
//            startActivityForResult(i, 0, null);
        } catch (KeyPermanentlyInvalidatedException e) {
            Log.w(LOGTAG, "Exception", e);
            try {
                mTokenAdapter.delete(auth41ServerCode);
            } catch (GeneralSecurityException | IOException f) {
                Log.e(LOGTAG, "Exception", e);
            }
            notifyUrgent(context, auth41ServerCode,
                    context.getString(R.string.auth41_notification_invalidated_title),
                    context.getString(R.string.auth41_notification_invalidated_message, label));
        }
    }

    private void postAuth41Code(Context context, String auth41ServerCode, Uri auth41Url, String label, Code code) {
        Log.i(LOGTAG, String.format("postAuth41Code[%s]: sending code %s to %s", label, code.getCode(), auth41Url));

        Auth41ChallengeResponse payload = new Auth41ChallengeResponse();
        payload.setOtpCode(code.getCode());
        // TODO: signature

        Gson gson = new Gson();
        String json = gson.toJson(payload, Auth41ChallengeResponse.class);
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(auth41Url.toString())
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(LOGTAG, String.format("postAuth41Code[%s]: failed with code %d", label, response.code()));
                notifyUrgent(context, auth41ServerCode,
                        context.getString(R.string.auth41_notification_failed_title),
                        context.getString(R.string.auth41_notification_failed_message_http, label, response.code()));
            }
        } catch (IOException e) {
            Log.w(LOGTAG, String.format("postAuth41Code[%s]: exception for item", label), e);
            notifyUrgent(context, auth41ServerCode,
                    context.getString(R.string.auth41_notification_failed_title),
                    context.getString(R.string.auth41_notification_failed_message, label, e.getLocalizedMessage()));
        }
    }

    @Override
    public void onSelectEvent(NavigableSet<Integer> selected) {
        // nothing to do here
    }

    private static void notifyInfo(Context context, String auth41ServerCode, String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Notifications.AUTH41_CHANNEL_ID_INFO)
                .setSmallIcon(R.drawable.ic_freeotp)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true);
        Notifications.show(context, builder.build(), auth41ServerCode.hashCode());
    }

    private static void notifyUrgent(Context context, String auth41ServerCode, String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Notifications.AUTH41_CHANNEL_ID_URGENT)
                .setSmallIcon(R.drawable.ic_freeotp)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true);
        Notifications.show(context, builder.build(), auth41ServerCode.hashCode());
    }

}
