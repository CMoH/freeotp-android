package org.fedorahosted.freeotp.auth41;

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

public class Auth41RegistrationWorker extends CoroutineWorker implements SelectableAdapter.EventListener {

    public static final String CORRELATION_ID = "correlation_id";
    public static final String LABEL = "label";
    public static final String OTP_CODE = "otp_code";
    public static final String SERVER_CODE = "server_code";
    public static final String AUTH41_URL = "auth41_url";

    private static final String LOGTAG = "Auth41ChallengeWorker";

    public Auth41RegistrationWorker(@NotNull Context appContext, @NotNull WorkerParameters params) throws GeneralSecurityException, IOException {
        super(appContext, params);
    }

    @Override
    public @Nullable Object doWork(@NotNull Continuation<? super Result> continuation) {
        Data inputData = getInputData();
        String correlationId = inputData.getString(CORRELATION_ID);
        String label = inputData.getString(LABEL);
        String otpCode = inputData.getString(OTP_CODE);
        String serverCode = inputData.getString(SERVER_CODE);
        String auth41Url = inputData.getString(AUTH41_URL);

        if (correlationId == null || label == null || otpCode == null || serverCode == null || auth41Url == null) {
            Log.e(LOGTAG, String.format("postRegistrationRequest[%s]: missing required information to register device", label));
            return Result.failure();
        }

        Auth41DeviceRegistrationRequest request = new Auth41DeviceRegistrationRequest();
        request.setDeviceToken(FirebaseService.getDeviceToken());
        request.setServerCode(serverCode);
        request.setCorrelationId(correlationId);
        request.setUserLabel("FreeOTP");
        request.setOtpCode(otpCode);

        Context appContext = getApplicationContext();
        Uri auth41RegisterUrl = Uri.parse(auth41Url).buildUpon().appendPath("register").build();
        return postRegistrationRequest(appContext, request, auth41RegisterUrl, label);
    }


    private Result postRegistrationRequest(Context context, Auth41DeviceRegistrationRequest registrationRequest, Uri auth41Url, String label) {
        Log.i(LOGTAG, String.format("postRegistrationRequest[%s]: sending registration request %s to %s", label, registrationRequest, auth41Url));

        Gson gson = new Gson();
        String json = gson.toJson(registrationRequest, Auth41DeviceRegistrationRequest.class);
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder()
                .url(auth41Url.toString())
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                notifyInfo(context, registrationRequest.getServerCode(),
                        context.getString(R.string.auth41_notification_success),
                        context.getString(R.string.auth41_notification_registration_success_message, label));
                return Result.success();
            } else {
                Log.w(LOGTAG, String.format("postRegistrationRequest[%s]: failed with code %d", label, response.code()));
                notifyUrgent(context, registrationRequest.getServerCode(),
                        context.getString(R.string.auth41_notification_registration_failed_title),
                        context.getString(R.string.auth41_notification_registration_failed_message_http, label, response.code()));
            }
        } catch (IOException e) {
            Log.w(LOGTAG, String.format("postRegistrationRequest[%s]: exception for item", label), e);
            notifyUrgent(context, registrationRequest.getServerCode(),
                    context.getString(R.string.auth41_notification_registration_failed_title),
                    context.getString(R.string.auth41_notification_registration_failed_message, label, e.getLocalizedMessage()));
        }
        return Result.failure();
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
