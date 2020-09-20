package ai.cellbots.common;

import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Network utility.
 */
public final class NetworkUtil {
    private static final String TAG = NetworkUtil.class.getSimpleName();

    /**
     * Gets a list of IP v4 addresses.
     *
     * @return The list of IP V4 addresses.
     */
    public static List<String> getIPv4List() {
        List<String> out = new ArrayList<>();
        try {
            // For each network interface
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface networkInterface = en.nextElement();
                // For each IP address on each network interface
                for (Enumeration<InetAddress> ipAddresses = networkInterface
                        .getInetAddresses(); ipAddresses.hasMoreElements(); ) {
                    InetAddress inetAddress = ipAddresses.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String ipv4 = inetAddress.getHostAddress();
                        out.add(ipv4);
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * Gets the mac address of the wifi card.
     *
     * @return The wifi mac string.
     */
    public static String getMacAddress() {
        try {
            List<NetworkInterface> interfaces;
            interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                if (!networkInterface.getName().equalsIgnoreCase("wlan0")) {
                    continue;
                }
                byte[] out = networkInterface.getHardwareAddress();
                String result = Strings.bytesToHexString(out);
                Log.v(TAG, "MAC address: " + result);
                return result;
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }
}
