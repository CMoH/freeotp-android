package org.fedorahosted.freeotp.auth41;

public class Auth41DeviceRegistrationRequest {

    /**
     * The Firebase device token.
     */
    private String deviceToken;
    /**
     * The OTA entry ID in the authenticator app.
     * <p>
     * Since authenticators can serve multiple OTA applications.
     */
    private String serverCode;
    /**
     * The Auth41 correlation ID.
     */
    private String correlationId;
    /**
     * The name of the OTP authenticator, to be seen in keycloak.
     */
    private String userLabel;
    /**
     * Initial OTP code after parsing the QR code.
     */
    private String otpCode;
    // TODO: private String publicKey;

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public String getServerCode() {
        return serverCode;
    }

    public void setServerCode(String serverCode) {
        this.serverCode = serverCode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getUserLabel() {
        return userLabel;
    }

    public void setUserLabel(String userLabel) {
        this.userLabel = userLabel;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }


    @Override
    public String toString() {
        return "Auth41DeviceRegistrationRequest{" +
                "deviceToken='" + deviceToken + '\'' +
                ", serverCode='" + serverCode + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", userLabel='" + userLabel + '\'' +
                ", otpCode='" + otpCode + '\'' +
                '}';
    }

}
