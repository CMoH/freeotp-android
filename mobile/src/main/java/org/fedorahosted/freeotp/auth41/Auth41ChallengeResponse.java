package org.fedorahosted.freeotp.auth41;

/**
 * Protocol bean.
 */
public class Auth41ChallengeResponse {

    private String otpCode;
    private String selectedCredentialId;
    private String signature;

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public String getSelectedCredentialId() {
        return selectedCredentialId;
    }

    public void setSelectedCredentialId(String selectedCredentialId) {
        this.selectedCredentialId = selectedCredentialId;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "Auth41ChallengeResponse{" +
                "otpCode='" + otpCode + '\'' +
                ", selectedCredentialId='" + selectedCredentialId + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }
}
