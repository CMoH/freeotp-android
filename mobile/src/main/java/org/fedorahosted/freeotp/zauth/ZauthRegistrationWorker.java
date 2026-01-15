package org.fedorahosted.freeotp.zauth;

import android.app.Notification;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.work.CoroutineWorker;
import androidx.work.Data;
import androidx.work.WorkerParameters;
import com.google.gson.Gson;
import kotlin.coroutines.Continuation;
import okhttp3.*;
import org.fedorahosted.freeotp.R;
import org.fedorahosted.freeotp.utils.SelectableAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.NavigableSet;

public class ZauthRegistrationWorker extends CoroutineWorker implements SelectableAdapter.EventListener {

    public static final String CORRELATION_ID = "correlation_id";
    public static final String LABEL = "label";
    public static final String OTP_CODE = "otp_code";
    public static final String SERVER_CODE = "server_code";
    public static final String ZAUTH_URL = "zauth_url";

    private static final String LOGTAG = "ZauthChallengeWorker";

    public ZauthRegistrationWorker(@NotNull Context appContext, @NotNull WorkerParameters params) throws GeneralSecurityException, IOException {
        super(appContext, params);
    }

    @Override
    public @Nullable Object doWork(@NotNull Continuation<? super Result> continuation) {
        Data inputData = getInputData();
        String correlationId = inputData.getString(CORRELATION_ID);
        String label = inputData.getString(LABEL);
        String otpCode = inputData.getString(OTP_CODE);
        String serverCode = inputData.getString(SERVER_CODE);
        String zauthUrl = inputData.getString(ZAUTH_URL);

        if (correlationId == null || label == null || otpCode == null || serverCode == null || zauthUrl == null) {
            Log.e(LOGTAG, String.format("postRegistrationRequest[%s]: missing required information to register device", label));
            return Result.failure();
        }

        ZauthDeviceRegistrationRequest request = new ZauthDeviceRegistrationRequest();
        request.setDeviceToken(FirebaseService.getDeviceToken());
        request.setServerCode(serverCode);
        request.setCorrelationId(correlationId);
        request.setUserLabel("FreeOTP");
        request.setOtpCode(otpCode);

        Context appContext = getApplicationContext();
        Uri zauthRegisterUrl = Uri.parse(zauthUrl).buildUpon().appendPath("register").build();
        return postRegistrationRequest(appContext, request, zauthRegisterUrl, label);
    }


    private Result postRegistrationRequest(Context context, ZauthDeviceRegistrationRequest registrationRequest, Uri zauthUrl, String label) {
        Log.i(LOGTAG, String.format("postRegistrationRequest[%s]: sending registration request %s to %s", label, registrationRequest, zauthUrl));

        Gson gson = new Gson();
        String json = gson.toJson(registrationRequest, ZauthDeviceRegistrationRequest.class);
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(zauthUrl.toString())
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                notifyInfo(context, registrationRequest.getServerCode(),
                        context.getString(R.string.zauth_notification_success),
                        context.getString(R.string.zauth_notification_registration_success_message, label));
                return Result.success();
            } else {
                Log.w(LOGTAG, String.format("postRegistrationRequest[%s]: failed with code %d", label, response.code()));
                notifyUrgent(context, registrationRequest.getServerCode(),
                        context.getString(R.string.zauth_notification_registration_failed_title),
                        context.getString(R.string.zauth_notification_registration_failed_message_http, label, response.code()));
            }
        } catch (IOException e) {
            Log.w(LOGTAG, String.format("postRegistrationRequest[%s]: exception for item", label), e);
            notifyUrgent(context, registrationRequest.getServerCode(),
                    context.getString(R.string.zauth_notification_registration_failed_title),
                    context.getString(R.string.zauth_notification_registration_failed_message, label, e.getLocalizedMessage()));
        }
        return Result.failure();
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
