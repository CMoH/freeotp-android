package org.fedorahosted.freeotp.zauth;

/**
 * Protocol bean.
 */
public class ZauthChallengeResponse {

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
        return "ZauthChallengeResponse{" +
                "otpCode='" + otpCode + '\'' +
                ", selectedCredentialId='" + selectedCredentialId + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }
}
