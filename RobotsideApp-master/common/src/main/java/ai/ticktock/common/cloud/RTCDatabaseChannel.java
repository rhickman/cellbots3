package ai.cellbots.common.cloud;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import ai.cellbots.common.Strings;
import ai.cellbots.common.webrtc.RTCConnection;

/**
 * Sends data through the WebRTC interface, so that data paths can be copied through this method.
 * The system sends messages through WebRTC of the following format:
 * - version, byte, always 0
 * - headerLength, int32
 * - contentLength, int32
 * - header bytes, byte array of headerLength
 * - content bytes, byte array of contentLength
 * The header is a dictionary of the following:
 * - path: the CloudPath of the object as a JSONArray, from CloudPath.toJSONArray()
 * - robot: optional robot UUID string
 * - user: optional user UUID string
 * The content is the result of calling JsonSerializer on an object.
 *
 * To send data, all that must be done is that an object must be fed in with a database path. The
 * object will automatically be serialized and sent through the WebRTC data stream.
 *
 * To receive data, a CloudPath and DataFactory must be passed in via the storePaths. The object in the
 * DataFactory must implement TimestampManager.Timestamped or it will be ignored. The latest object
 * can then be obtained with getObject().
 *
 * Security: since WebRTC connections can only be created by a given user, we are certain that
 * RTCConnection.getUserUuid() and RTCConnection.getRobotUuid() are correct. Thus we only allow
 * writing to the database with those user and robot uuids. Objects that do not have the user uuid
 * and robot uuid of the connection will be ignored.
 */
public class RTCDatabaseChannel {
    private final String TAG = RTCDatabaseChannel.class.getSimpleName();
    private final Map<RTCConnection, LinkedList<byte[]>> mConnections = new HashMap<>();
    private final Map<CloudPath, Object> mLatestValues = new HashMap<>();
    private final Map<CloudPath, DataFactory> mStorePaths;
    private boolean mShutdown = false;

    /**
     * Create a new RTC database channel.
     * @param storePaths The mapping of CloudPath -> DataFactory for the objects to store.
     */
    public RTCDatabaseChannel(Map<CloudPath, DataFactory> storePaths) {
        mStorePaths = Collections.unmodifiableMap(new HashMap<>(storePaths));
        for (CloudPath path : storePaths.keySet()) {
            if (path.hasEntityPath()) {
                throw new IllegalArgumentException("Entity paths are not supported");
            }
        }
    }

    /**
     * Attach a new connection, called from the listener.
     * @param connection The connection to store.
     */
    public synchronized void onNewConnection(RTCConnection connection) {
        if (mShutdown) {
            return;
        }
        if (!mConnections.containsKey(connection)) {
            mConnections.put(connection, new LinkedList<byte[]>());
            Log.i(TAG, "New connection: " + (connection.haveSetRemote() ? "remote" : "local"));
        }
    }

    /**
     * Remove a connection, called from the listener.
     * @param connection The connection to be removed.
     */
    public synchronized void terminateConnection(RTCConnection connection) {
        if (mShutdown) {
            return;
        }
        if (mConnections.containsKey(connection)) {
            mConnections.remove(connection);
        }
    }

    /**
     * Send data to the system.
     * @param user The username to send.
     * @param robot The robot name to send.
     * @param dataPath The firebase CloudPath to send the object to.
     * @param object The object to send over the wire.
     */
    public void sendData(String user, String robot, CloudPath dataPath, Object object) {
        if (dataPath.hasRobotPath() && robot == null) {
            throw new IllegalArgumentException("CloudPath is robot path, robot is not set");
        }
        if (dataPath.hasUserPath() && user == null) {
            throw new IllegalArgumentException("CloudPath is user path, user is not set");
        }
        String json = JsonSerializer.toJson(object);
        if (json == null) {
            return;
        }
        byte[] jsonContent = json.getBytes();
        JSONObject header = new JSONObject();
        try {
            header.put("path", dataPath.toJSONArray());
            if (robot != null) {
                header.put("robot", robot);
            }
            if (user != null) {
                header.put("user", user);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON object for path:", e);
            return;
        }
        byte[] jsonHeader = header.toString().getBytes();
        byte[] output = new byte[1 + 4 + 4 + jsonHeader.length + jsonContent.length];
        ByteBuffer message = ByteBuffer.wrap(output);
        message.put((byte)0);
        message.putInt(jsonHeader.length);
        message.putInt(jsonContent.length);
        message.put(jsonHeader);
        message.put(jsonContent);

        synchronized (this) {
            if (mShutdown) {
                return;
            }
            for (RTCConnection connection : mConnections.keySet()) {
                if ((user == null || Strings.compare(user, connection.getUserUuid()))
                        && (robot == null || Strings.compare(robot, connection.getRobotUuid()))) {
                    connection.sendDataMessage(output);
                }
            }
        }
    }

    /**
     * Called when a new message is received from the connection listener.
     * @param connection The connection to check.
     * @param message The message to save.
     */
    public synchronized void onMessage(RTCConnection connection, byte[] message) {
        if (mShutdown) {
            return;
        }
        if (!mConnections.containsKey(connection)) {
            onNewConnection(connection);
        }
        if (mConnections.containsKey(connection)) {
            mConnections.get(connection).add(message);
        } else {
            Log.i(TAG, "Message ignored!");
        }
    }

    /**
     * Called to get current value of an object.
     * @param path The path to get the object from.
     * @param user The user uuid.
     * @param robot The robot uuid.
     * @return The object, or null if it does not exist.
     */
    public synchronized Object getObject(CloudPath path, String user, String robot) {
        if (mShutdown) {
            return null;
        }
        String objectUser = null, objectRobot = null;
        if (!mLatestValues.containsKey(path)) {
            return null;
        }
        Object object = mLatestValues.get(path);
        if (object instanceof DataFactory.UserUuidData) {
            objectUser = ((DataFactory.UserUuidData)object).getUserUuid();
        }
        if (object instanceof DataFactory.RobotUuidData) {
            objectRobot = ((DataFactory.RobotUuidData)object).getRobotUuid();
        }
        if (path.hasUserPath() && (objectUser == null || !Strings.compare(user, objectUser))) {
            return null;
        }
        if (path.hasRobotPath() && (objectRobot == null || !Strings.compare(robot, objectRobot))) {
            return null;
        }
        return object;
    }

    /**
     * Update the stored values.
     * @param user The user uuid for the system
     * @param robot The robot uuid for the system
     */
    public synchronized void update(String user, String robot) {
        if (mShutdown) {
            return;
        }

        LinkedList<CloudPath> removePaths = new LinkedList<>();
        LinkedList<RTCConnection> removeConnections = new LinkedList<>();

        for (RTCConnection connection : mConnections.keySet()) {
            if (connection.isShutdown()) {
                removeConnections.add(connection);
            } else if (Strings.compare(user, connection.getUserUuid())
                    && Strings.compare(robot, connection.getRobotUuid())) {
                int len = 0;
                for (byte[] message : mConnections.get(connection)) {
                    len += message.length;
                }
                ByteBuffer messages = ByteBuffer.allocate(len);
                for (byte[] message : mConnections.get(connection)) {
                    messages.put(message);
                }
                messages.rewind();
                mConnections.get(connection).clear();
                while (messages.remaining() > 9) {
                    byte version = messages.get();
                    if (version != 0) {
                        Log.w(TAG, "Invalid message version: " + version);
                        continue;
                    }
                    int headerLen = messages.getInt();
                    if (headerLen < 0) {
                        Log.w(TAG, "Invalid message, header length negative");
                        continue;
                    }
                    int contentLen = messages.getInt();
                    if (contentLen < 0) {
                        Log.w(TAG, "Invalid message, content length negative");
                        continue;
                    }

                    // If the message is too long, then we write it back for later
                    if (contentLen + headerLen > messages.remaining()) {
                        byte[] fragment = new byte[messages.remaining()];
                        byte[] newMessage = new byte[fragment.length + 9];
                        messages.get(fragment);
                        ByteBuffer rewrite = ByteBuffer.wrap(newMessage);
                        rewrite.put((byte)0);
                        rewrite.putInt(headerLen);
                        rewrite.putInt(contentLen);
                        rewrite.put(fragment);
                        mConnections.get(connection).add(newMessage);
                        break;
                    }

                    byte[] header = new byte[headerLen], content = new byte[contentLen];
                    messages.get(header);
                    messages.get(content);

                    JSONObject headerObject;
                    CloudPath targetPath = null;
                    try {
                        headerObject = new JSONObject(new String(header));
                        if ((!headerObject.has("user") || Strings.compare(headerObject.getString("user"), connection.getUserUuid()))
                            && (!headerObject.has("robot") || Strings.compare(headerObject.getString("robot"), connection.getRobotUuid()))
                                && headerObject.has("path")) {
                            targetPath = new CloudPath(headerObject.getJSONArray("path"));
                        }
                        if (targetPath != null && targetPath.hasUserPath() && !headerObject.has("user")) {
                            targetPath = null;
                        }
                        if (targetPath != null && targetPath.hasRobotPath() && !headerObject.has("robot")) {
                            targetPath = null;
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Invalid header: " + new String(header));
                        targetPath = null;
                    }
                    if (targetPath != null && mStorePaths.containsKey(targetPath)) {
                        DataFactory factory = mStorePaths.get(targetPath);
                        Object result = factory.create(connection.getUserUuid(), connection.getRobotUuid(), new String(content));
                        if (result != null && result instanceof TimestampManager.Timestamped) {
                            long oldTime = 0;
                            if (mLatestValues.containsKey(targetPath)
                                    && mLatestValues.get(targetPath) instanceof TimestampManager.Timestamped) {
                                oldTime = ((TimestampManager.Timestamped) mLatestValues.get(targetPath)).getTimestamp();
                            }
                            if (oldTime < ((TimestampManager.Timestamped) result).getTimestamp()) {
                                mLatestValues.put(targetPath, result);
                            }
                        } else {
                            Log.i(TAG, "Invalid object: " + result);
                        }
                    } else {
                        Log.i(TAG, "Ignoring path: " + targetPath);
                    }
                }
            }
        }

        for (RTCConnection connection : removeConnections) {
            mConnections.remove(connection);
        }

        // Remove paths that are no longer valid because of the wrong user of robot.
        for (Map.Entry<CloudPath, Object> entry : mLatestValues.entrySet()) {
            if (getObject(entry.getKey(), user, robot) == null) {
                removePaths.add(entry.getKey());
            }
        }
        for (CloudPath path : removePaths) {
            mLatestValues.remove(path);
        }
    }

    /**
     * Shutdown the database channel.
     */
    public synchronized void shutdown() {
        if (!mShutdown) {
            mShutdown = true;
            mLatestValues.clear();
            mConnections.clear();
        }
    }
}