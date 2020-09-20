package ai.cellbots.common.data;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * And ICE candidate for the WebRTC communication. ICE is the interactive connection establishment
 * standard, which is used to set up the WebRTC connection. Each candidate is a web address for
 * and protocol to attempt to open for connection.
 */
@IgnoreExtraProperties
public class IceCandidate {

    @PropertyName("sdp")
    private final String mSdp;
    @PropertyName("sdp_mid")
    private final String mSdpMid;
    @PropertyName("sdp_mline_index")
    private final int mSdpMLineIndex;
    @PropertyName("server_url")
    private final String mServerUrl;

    /**
     * Get the SDP.
     * @return The SDP.
     */
    @PropertyName("sdp")
    public String getSdp() {
        return mSdp;
    }
    /**
     * Get the SDP Mid.
     * @return The SDP Mid.
     */
    @PropertyName("sdp_mid")
    public String getSdpMid() {
        return mSdpMid;
    }
    /**
     * Get the SDP MLine Index.
     * @return The SDP MLine Index.
     */
    @PropertyName("sdp_mline_index")
    public int getSdpMLineIndex() {
        return mSdpMLineIndex;
    }
    /**
     * Get the server url.
     * @return The server url.
     */
    @PropertyName("server_url")
    public String getServerUrl() {
        return mServerUrl;
    }

    public IceCandidate() {
        mSdp = null;
        mSdpMid = null;
        mSdpMLineIndex = 0;
        mServerUrl = null;
    }

    /**
     * The ICE candidate.
     * @param sdp The SDP.
     * @param sdpMid The SDP Mid.
     * @param sdpMLineIndex The SDP MLine Index.
     * @param serverUrl The server url.
     */
    public IceCandidate(String sdp, String sdpMid, int sdpMLineIndex, String serverUrl) {
        mSdp = sdp;
        mSdpMid = sdpMid;
        mSdpMLineIndex = sdpMLineIndex;
        mServerUrl = serverUrl;
    }

    /**
     * Compute the uuid of the IceCandidate.
     * @return The uuid of the candidate.
     */
    @Exclude
    public String computeUuid() {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            ByteBuffer integers = ByteBuffer.allocate(4 * 4);
            integers.putInt(mSdp == null ? -1 : mSdp.length());
            integers.putInt(mSdpMid == null ? -1 : mSdpMid.length());
            integers.putInt(mSdpMLineIndex);
            integers.putInt(mServerUrl == null ? -1 : mServerUrl.length());
            md.update(integers.array());
            if (mSdp != null) {
                md.update(mSdp.getBytes());
            }
            if (mSdpMid != null) {
                md.update(mSdpMid.getBytes());
            }
            if (mServerUrl != null) {
                md.update(mServerUrl.getBytes());
            }
            StringBuilder hex = new StringBuilder();
            byte[] output = md.digest();
            for (byte b : output) {
                int i = (int)b;
                if (i < 0) {
                    i += 256;
                }
                String a = Integer.toHexString(i);
                if (a.isEmpty()) {
                    hex.append("0");
                }
                if (a.length() == 1) {
                    hex.append("0");
                }
                hex.append(a);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new Error(e);
        }
    }

    /**
     * Create human-readable string.
     * @return The string.
     */
    @Override
    @Exclude
    public String toString() {
        return computeUuid() +
                ":IceCandidate(" +
                (mSdp == null ? "NULL" : mSdp) +
                ", " +
                (mSdpMid == null ? "NULL" : mSdpMid) +
                ", " +
                mSdpMLineIndex +
                ", " +
                (mServerUrl == null ? "NULL" : mServerUrl) +
                ")";
    }
}
