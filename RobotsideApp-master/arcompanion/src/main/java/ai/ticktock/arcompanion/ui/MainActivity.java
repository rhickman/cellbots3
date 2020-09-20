package ai.cellbots.arcompanion.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoException;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.projecttango.tangosupport.TangoPointCloudManager;
import com.projecttango.tangosupport.TangoSupport;

import org.rajawali3d.Object3D;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Cylinder;
import org.rajawali3d.primitives.RectangularPrism;
import org.rajawali3d.scene.ASceneFrameCallback;
import org.rajawali3d.view.SurfaceView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.cellbots.arcompanion.ARPermissionsHelper;
import ai.cellbots.arcompanion.dialog.AddPOIDialogFragment;
import ai.cellbots.arcompanion.model.ARViewPoint;
import ai.cellbots.arcompanion.model.path.line.GoalPathLine;
import ai.cellbots.arcompanion.model.path.rootnode.CustomPathRootNode;
import ai.cellbots.arcompanion.model.path.rootnode.GoalPathRootNode;
import ai.cellbots.arcompanion.model.path.rootnode.OriginalPathRootNode;
import ai.cellbots.arcompanion.model.path.rootnode.SmoothedPathRootNode;
import ai.cellbots.arcompanion.R;
import ai.cellbots.arcompanion.ui.render.ARCompanionRenderer;
import ai.cellbots.arcompanion.utils.FloatingPointUtils;
import ai.cellbots.arcompanion.utils.RendererUtils;
import ai.cellbots.arcompanion.utils.TangoUtils;
import ai.cellbots.common.AboutActivity;
import ai.cellbots.common.CloudConnector;
import ai.cellbots.common.CloudHelper;
import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.RobotConnectionStatus;
import ai.cellbots.common.RobotConnectionStatusTimer;
import ai.cellbots.common.World;
import ai.cellbots.common.cloud.TimestampManager;
import ai.cellbots.common.data.AnimationInfo;
import ai.cellbots.common.data.DriveGoal;
import ai.cellbots.common.data.DrivePointGoal;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.NavigationStateCommand;
import ai.cellbots.common.data.PlannerGoal;
import ai.cellbots.common.data.PointOfInterest;
import ai.cellbots.common.data.Robot;
import ai.cellbots.common.data.SpeedStateCommand;
import ai.cellbots.common.data.Transform;
import ai.cellbots.common.voice.OnVoiceDetectedListener;
import ai.cellbots.common.voice.VoiceCommands;
import ai.cellbots.common.utils.PointOfInterestValidator;
import ai.cellbots.tangocommon.CloudWorldManager;
import ai.cellbots.tangocommon.TangoPermissionsHelper;

public class MainActivity extends AppCompatActivity implements CloudWorldManager.Listener,
        View.OnTouchListener, AddPOIDialogFragment.OnVoiceResultListener, OnVoiceDetectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    // Tag for robot's ping time updates.
    private static final String PING_TIME_TAG = TAG + "PingTime";

    // Request Codes
    private static final int REQUEST_SIGN_IN = 0;
    private static final int REQUEST_TANGO_PERMISSION_CODE = 1;
    private static final int INVALID_TEXTURE_ID = 100;

    private enum State {
        INIT,       // Initializing the Tango
        WAITING,    // Waiting for user to select the robot OR waiting for import request
        IMPORTING,  // Importing the worlds (maps)
        LOADING,    // Loading robot's map
        RUNNING     // Running the map with robot
    }

    interface Tags {
        String dialogAddPOI = "MainActivity.addPOIDialogFragment";
    }

    // Helper classes for managing manifest and Tango permissions.
    private ARPermissionsHelper mPermissionsHelper;
    private TangoPermissionsHelper mTangoPermissionsHelper;

    // Firebase Authentication
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseUser mUser;

    // Data Persistence
    private SharedPreferences mSharedPreferences;
    private Gson mGson;

    // For accessing Firebase database and storage
    private final CloudHelper mCloudHelper = CloudHelper.getInstance();

    // Firebase operations and processing
    private CloudWorldManager mCloudWorldManager;
    private CloudConnector mCloudConnector;
    private final Object mCloudConnectorLock = new Object();

    private final CloudWorldManager.PromptLink mWorldPromptLink
            = new CloudWorldManager.PromptLink(this);

    private MainActivity.State mState;

    private SurfaceView mSurfaceView;                       // AR display

    // App info such as localization state and robot name
    private TextView mAppInfoTextView;
    private LinearLayout mLoggingContainer;                 // Container for logging information
    private TextView mTimestampTextView;                    // Timestamps from system and Tango service

    // Tango-related info such as service, is connected, ADF, current device's pose, timestamp,
    // frames of reference
    private TextView mTangoInfoTextView;

    private TextView mReferenceFramesTextView;              // Frames of reference for Tango data
    private TextView mPoseInfoTextView;                     // Pose for device and robot
    private TextView mMapInfoTextView;                      // Information about map goals, POIs, etc.
    private TextView mClickCoordinatesTextView;             // Click coordinates (from ADF to click position)
    private LinearLayout mRobotsListContainer;              // Container for list of robots
    private ListView mRobotsListView;                       // List of available robots for current user (from Firebase)
    private FloatingActionButton mVoiceCommandInputButton;  // Floating Action Button for voice command input
    private TextView mStateTextView;                        // State of app

    // Menu Items
    private MenuItem mToggleDebugInfoMenuItem;  // Toggles visibility of logging info
    private MenuItem mToggleRobotPosition;  // Toggles visibility of a blue cylinder on the robot
    private MenuItem mToggleOriginalPathMenuItem;  // Toggles visibility of world's original path
    private MenuItem mToggleSmoothedPathMenuItem;  // Toggles visibility of world's smoothed path
    private MenuItem mToggleCustomPathMenuItem;  // Toggles visibility of world's custom path
    private MenuItem mTogglePOIMenuItem;  // Toggles visibility of Points of Interest (POI)

    private ARCompanionRenderer mRenderer;

    private Tango mTango;                                   // Interface for connecting to Tango Service
    private TangoConfig mTangoConfig;                       // Config parameters for Tango Service

    // Maintains point cloud buffers in a thread-safe fashion
    private final TangoPointCloudManager mPointCloudManager = new TangoPointCloudManager();

    // NOTE: Naming indicates which thread is in charge of updating this variable.
    private int mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
    private final AtomicBoolean mIsFrameAvailableTangoThread = new AtomicBoolean(false);
    private double mCameraPoseTimestamp = 0;
    private double mRgbTimestampGlThread = 0;

    private ValueEventListener mPingTimeListener;  // Listener for robot's ping time.
    private ValueEventListener mActiveGoalsListener;
    private ValueEventListener mPOIListener;
    private ValueEventListener mAnimationListener;

    private final List<PlannerGoal> mActiveGoalsList = new ArrayList<>();
    private final Map<String, PointOfInterest> mKeyedPoi = new HashMap<>();
    private final List<String> mAnimations = new ArrayList<>();

    // Robot's pose (position)
    private final Vector3 mRobotPosition = new Vector3();

    // Stack of path nodes for passing to path line (required)
    private final Stack<Vector3> mOriginalPathNodes = new Stack<>();
    private final Stack<Vector3> mSmoothedPathNodes = new Stack<>();
    private final Stack<Vector3> mCustomPathNodes = new Stack<>();
    private final Stack<Vector3> mGoalPathNodes = new Stack<>();

    // List of Valid Travel Path Transforms for Robot (from current world/ADF)
    private final List<ai.cellbots.common.Transform> mOriginalPathTransforms = new ArrayList<>();
    private final List<ai.cellbots.common.Transform> mSmoothedPathTransforms = new ArrayList<>();
    private final List<ai.cellbots.common.Transform> mCustomPathTransforms = new ArrayList<>();
    private final List<ai.cellbots.common.Transform> mGoalPathTransforms = new ArrayList<>();

    // Objects for rendering
    private Cylinder mRobotCylinderForRendering;
    private OriginalPathRootNode mOriginalPathRootNodeForRendering;
    private SmoothedPathRootNode mSmoothedPathRootNodeForRendering;
    private CustomPathRootNode mCustomPathRootNodeForRendering;
    private GoalPathRootNode mGoalPathRootNodeForRendering;
    private GoalPathLine mGoalPathLineForRendering;
    private Cube mRootGoalCubeForRendering;
    private Cylinder mRootPOICylinderForRendering;
    private final List<RectangularPrism> mPOILabelsForRendering = new ArrayList<>();

    private DetailedWorld mDetailedWorld = null; // Current world
    private Robot mRobot = null; // Current robot

    // Robot connection status and timer classes for checking the connection status
    // between the RobotApp and cloud.
    private RobotConnectionStatus mRobotConnectionStatus;
    private RobotConnectionStatusTimer mRobotConnectionStatusTimer;

    private Transform mARViewPointTransform;  // Transform for a newly created ARViewPoint object
    private GestureDetectorCompat mGestureDetector;

    private VoiceCommands mVoiceCommands;

    // Current status of robot (Started/Stopped)
    // TODO: Create an enum object in "common" folder for RobotStatus.
    private String mRobotStatus;

    // Minimum depth of current world/ADF. Used for binding render objects to the floor.
    private double mFloorZ;

    private boolean mIsTangoConnected = false;  // Connected to Tango Service
    private boolean mIsPhoneLocalized = false;  // Phone's Localization State relative to loaded ADF
    private boolean mIsRobotLocalized = false;  // Robot's Localization State (from cloud)
    private boolean mIsLoggingVisible = false;
    private boolean mIsGoalPathUpdated = false;
    private boolean mIsGoalsListUpdated = false;
    private boolean mIsPOIListUpdated = false;

    private int mDisplayRotation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        mFirebaseAuth = FirebaseAuth.getInstance();
        setContentView(R.layout.activity_main);

        mSurfaceView = findViewById(R.id.ar_surfaceview);
        mAppInfoTextView = findViewById(R.id.app_info_textview);
        mLoggingContainer = findViewById(R.id.logging_container);
        mTimestampTextView = findViewById(R.id.timestamp_textview);
        mTangoInfoTextView = findViewById(R.id.tango_info_textview);
        mReferenceFramesTextView = findViewById(R.id.reference_frames_textview);
        mPoseInfoTextView = findViewById(R.id.pose_info_textview);
        mMapInfoTextView = findViewById(R.id.map_info_textview);
        mClickCoordinatesTextView = findViewById(R.id.click_coordinates_textview);
        mRobotsListContainer = findViewById(R.id.robots_list_container);
        mRobotsListView = findViewById(R.id.robots_listview);
        mVoiceCommandInputButton = findViewById(R.id.voice_command_input_button);
        mStateTextView = findViewById(R.id.state_textview);

        mPermissionsHelper = new ARPermissionsHelper(this);
        mTangoPermissionsHelper = new TangoPermissionsHelper(this);

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_ar_view, menu);
        mToggleDebugInfoMenuItem = menu.findItem(R.id.menu_toggle_debug_info);
        mToggleRobotPosition = menu.findItem(R.id.menu_toggle_robot_position);
        mToggleOriginalPathMenuItem = menu.findItem(R.id.menu_toggle_original_path);
        mToggleSmoothedPathMenuItem = menu.findItem(R.id.menu_toggle_smoothed_path);
        mToggleCustomPathMenuItem = menu.findItem(R.id.menu_toggle_custom_path);
        mTogglePOIMenuItem = menu.findItem(R.id.menu_toggle_poi);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Called on every onResume via AppCompatActivity#invalidateOptionsMenu.
     *
     * @param menu
     * @return
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mState != State.RUNNING) {
            if (mToggleDebugInfoMenuItem.isVisible()) {
                // Set the Toggle Debug Info menu item to invisible as having the menu item be visible
                // would result in the logging information overlapping the non-AR views.
                mToggleDebugInfoMenuItem.setVisible(false);
            }
        } else {
            MenuItem itemClearActiveGoals = menu.findItem(R.id.menu_clear_active_goals);

            // Only display "clear active goals" button if a robot exists.
            if (mRobot != null && mRobot.uuid != null) {
                itemClearActiveGoals.setEnabled(true);
            } else {
                itemClearActiveGoals.setEnabled(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_toggle_debug_info:
                if (mIsLoggingVisible) {
                    // Hide logging info and change menu item to "Show"
                    mLoggingContainer.setVisibility(View.GONE);
                    mToggleDebugInfoMenuItem.setTitle(R.string.menu_show_logging_info);
                } else {
                    // Show logging info and change menu item to "Hide"
                    mLoggingContainer.setVisibility(View.VISIBLE);
                    mToggleDebugInfoMenuItem.setTitle(R.string.menu_hide_logging_info);
                }
                mIsLoggingVisible = !mIsLoggingVisible;
                return true;
            case R.id.menu_sign_out:
                disconnectTangoAndRenderer();
                signOutOfCloud();
                updateState(State.INIT);  // For restarting the view layouts
                return true;
            case R.id.menu_change_robot:
                disconnectTangoAndRenderer();
                updateState(State.INIT);  // For restarting the view layouts

                // Remove user's robot from SharedPreferences
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.remove(mUser.getUid());
                editor.apply();

                selectRobot();
                return true;
            case R.id.menu_about:
                // Show About activity
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.menu_clear_active_goals:
                Toast.makeText(MainActivity.this, "Cancelling all active goals.",
                        Toast.LENGTH_SHORT).show();
                mCloudHelper.cancelAllRobotGoals(mRobot.uuid);
                return true;
            case R.id.menu_toggle_robot_position:
            case R.id.menu_toggle_original_path:
            case R.id.menu_toggle_smoothed_path:
            case R.id.menu_toggle_custom_path:
            case R.id.menu_toggle_poi:
                // General checkbox behavior
                boolean isChecked = item.isChecked();
                item.setChecked(!isChecked);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-initialize the app, so that the views have their appropriate visibility states.
        updateState(State.INIT);
        invalidateOptionsMenu();

        // Read from shared preferences
        String robotJson = mSharedPreferences.getString("Robot", null);
        Robot robot = mGson.fromJson(robotJson, Robot.class);
        mRobot = robot;

        if (mRobot != null && mDetailedWorld != null && mIsTangoConnected) {
            startSystem(mDetailedWorld);
        }

        mCloudConnector = new CloudConnector(this, new CloudConnector.Listener() {
            @Override
            public void onExecutiveCommand(ExecutiveStateCommand command, boolean sync) {
                // Nothing to do.
            }

            @Override
            public void onSpeedCommand(SpeedStateCommand command, boolean sync) {
                if (!sync) return;

                if (command != null && command.isStarted()) {
                    mRobotStatus = "Running";
                } else {
                    mRobotStatus = "Stopped";
                }
            }
        });
        synchronized (mCloudConnectorLock) {
            mCloudConnector.updateWithRobot(mCloudHelper.getCurrentUserid(), mRobot);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mPermissionsHelper.hasAllPermissions()) {
            mPermissionsHelper.requestPermissions();
            return;
        }
        if (!mTangoPermissionsHelper.hasPermission()) {
            mTangoPermissionsHelper.requestTangoPermission(REQUEST_TANGO_PERMISSION_CODE);
            return;
        }
        signInToCloud();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (results.length > 0) {
            // Iterate through all permissions results and check if any permission was denied.
            // App should only proceed if all permissions are granted.
            for (int result : results) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showsToastAndFinishOnUiThread(R.string.permission_all_permissions_failed);
                    return;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
                                    final Intent data) {

        switch (requestCode) {
            case REQUEST_SIGN_IN:
                if (resultCode == RESULT_OK) {
                        // Sign-in succeeded, set up the UI
                        Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
                } else if (resultCode == RESULT_CANCELED) {
                    // Sign in was canceled by the user, finish the activity
                    Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case REQUEST_TANGO_PERMISSION_CODE:
                if (resultCode != RESULT_OK) {
                    showsToastAndFinishOnUiThread(R.string.tango_permissions_error);
                } else {
                    mTangoPermissionsHelper.setPermission(true);
                }
                break;
            default:
                if (mWorldPromptLink.isActivityResult(requestCode, resultCode, data)) {
                    final String error = mWorldPromptLink.onActivityResult(requestCode, resultCode, data);
                    if (error != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                } else {
                    Log.i(TAG, "Got activity result: request code: " + requestCode
                            + " result code: " + resultCode + " "
                            + ((data == null) ? "NULL" : data.toString()));
                }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////// Cloud World Manager Listener Methods ////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Locks state to IMPORTING in order to use CloudWorldManager to download ADF
     *
     * Locks and clears state for each ADF in the database
     *
     * @return if import/export state will be locked
     */
    @Override
    public synchronized boolean lockExportState() {
        Log.i(TAG, "Locking importing state.");

        if (mState == State.WAITING) {
            updateState(State.IMPORTING);
            return true;
        }
        return false;
    }

    @Override
    public synchronized void clearExportState() {
        Log.i(TAG, "Clearing importing state.");

        if (mState == State.IMPORTING) {
            updateState(State.WAITING);
        }
    }

    @Override
    public synchronized boolean isExportState() {
        return mState == State.IMPORTING;
    }

    @Override
    public void onStateUpdate() {

    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onStop() {
        super.onStop();
        disconnectTangoAndRenderer();
        synchronized (mCloudConnectorLock) {
            if (mCloudConnector != null) {
                mCloudConnector.shutdown();
                mCloudConnector = null;
            }
        }
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        // If not localized, then we cannot retrieve depth, so stop here
        if (!mIsPhoneLocalized) {
            Toast.makeText(MainActivity.this, R.string.toast_device_not_localized, Toast.LENGTH_SHORT).show();
            return true;
        }

        // Retrieve uv coordinates of clicked spot on phone screen
        float u = event.getX() / view.getWidth();
        float v = event.getY() / view.getHeight();

        // Retrieve touch position in 3D space with respect to the user's device by calculating
        // the touch position's depth from the user's device
        // Base Frame: User's device
        // Target Frame: Touch position
        synchronized (this) {
            ARViewPoint arViewPoint = null;

            try {
                arViewPoint = getClickPositionFromDevice(u, v);
                Log.i(TAG, "Touch Position: " + arViewPoint);
            } catch (TangoException e) {
                Log.e(TAG, "Tango error. Failed to retrieve touch position values");
            } catch (SecurityException t) {
                Log.e(TAG, "Permissions required!");
            }

            if (arViewPoint == null) {
                Log.i(TAG, getString(R.string.toast_touch_position_invalid));
                Toast.makeText(MainActivity.this, getString(R.string.toast_touch_position_invalid),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            showClickCoordinates(arViewPoint);

            mARViewPointTransform = new Transform(
                    arViewPoint.getCoordinate(0),
                    arViewPoint.getCoordinate(1),
                    arViewPoint.getCoordinate(2),
                    0,
                    0,
                    0,
                    0,
                    arViewPoint.getTimestamp()
            );

            // Since we want our touch position to always be on the ground,
            // we need to set the z coordinate to be the minimum depth
            mARViewPointTransform.pz = mDetailedWorld.getLevels().get(0).getMinZ();
        }

        // Determines whether to create goal or POI based on gesture
        // Click = Create goal
        // Long Press = Create POI
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public void onPOINameResult(String poiName) {
        PointOfInterest poi = new PointOfInterest(mARViewPointTransform, poiName);
        mCloudHelper.addPOI(poi, mDetailedWorld.getUuid());
    }

    @Override
    public void onVoiceActionDetected(VoiceCommands.VoiceAction voiceAction) {
        switch (voiceAction) {
            case START:
                synchronized (mCloudConnectorLock) {
                    if (mCloudConnector != null) {
                        mCloudConnector.setRobotSpeedCommand(mRobot, new SpeedStateCommand(true));
                    }
                }
                break;
            case STOP:
                synchronized (mCloudConnectorLock) {
                    if (mCloudConnector != null) {
                        mCloudConnector.setRobotSpeedCommand(mRobot, new SpeedStateCommand(false));
                    }
                }
                break;
            case GOTO:
            case COME_HERE:
            case ACTION1:
            case ACTION2:
            case ANIMATION:
                break;
        }
    }

    /**
     * Given a list of Robots provided by CloudHelper, the user picks a robot from the list to control.
     *
     * State: WAITING
     */
    public void selectRobot() {
        Log.i(TAG, "Showing robots list.");
        updateState(State.WAITING);

        mCloudHelper.listRobots(new CloudHelper.ListResultsListener<Robot>() {
            @Override
            public void onResult(final List<Robot> results) {

                if (results.isEmpty()) {
                    Log.e(TAG, "No robots found for this user.");
                } else {
                    synchronized (MainActivity.this) {

                        // Create a list of names of the robots.
                        List<String> names = new ArrayList<>();

                        for (Robot robot : results) {
                            names.add(robot.getName());
                        }

                        // Populate robots listview with the robot names.
                        ArrayAdapter<String> robotsAdapter = new ArrayAdapter<>(MainActivity.this,
                                android.R.layout.simple_list_item_1, names);
                        mRobotsListView.setAdapter(robotsAdapter);
                        mRobotsListView.setVisibility(View.VISIBLE);

                        mRobotsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                // Retrieve selected robot from list
                                final Robot robot = results.get(position);
                                if (robot.map == null || robot.map.isEmpty()) {
                                    Toast.makeText(MainActivity.this, R.string.toast_error_loading_robot_map,
                                            Toast.LENGTH_LONG).show();
                                    Log.d(TAG, getString(R.string.toast_error_loading_robot_map));
                                    return;
                                }
                                setSelectedRobot(robot);

                                // Updates position of robot and its path to goals
                                watchRobot();

                                // Add listener for watching robot's ping time.
                                watchPingTimeForRobot();

                                // Update list of active goals for current robot
                                updateActiveGoalsList();

                                // Update map of POIs for current world
                                updatePoiMap();

                                // Update list of animations for current robot
                                updateAnimationList();

                                World world = new World(mRobot.map, null);
                                startSystem(world);
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * Given a DetailedWorld ADF, initializes Tango and starts AR experience
     *
     * @param world
     */
    public void startSystem(final World world) {
        restartSystem(new Runnable() {
            @Override
            public void run() {
                updateState(State.LOADING);

                // Load world, if not null
                if (world != null) {

                    mDetailedWorld = mCloudWorldManager.loadDetailedWorld(world, null);

                    if (mDetailedWorld == null) {
                        Log.e(TAG, "Attempted to start on an invalid world");
                        restartSystem(null);
                    }

                } else {
                    mDetailedWorld = null;
                }

                // Send user back to WAITING state for re-selecting a robot.
                // The user can only go into AR view if the robot chosen has a valid detailed world.
                if (mDetailedWorld == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, R.string.toast_robot_detailed_world_invalid,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    updateState(State.WAITING);
                    return;
                }

                // Set world for robot navigation to be the current ADF.
                synchronized (mCloudConnectorLock) {
                    if (mCloudConnector != null) {
                        NavigationStateCommand stateCommand =
                                new NavigationStateCommand(mDetailedWorld.getUuid(), false);
                        mCloudConnector.setRobotNavigationCommand(mRobot, stateCommand);
                    }
                }

                // Store minimum depth of mDetailedWorld for binding rendered objects to ground.
                mFloorZ = mDetailedWorld.getLevels().get(0).getMinZ();

                // Store paths (original, smoothed, custom) from mDetailedWorld
                storeWorldPaths();

                synchronized (MainActivity.this) {
                    try {
                        mTangoConfig = setupTangoConfig(mTango, mDetailedWorld);
                        mTango.connect(mTangoConfig);
                        startupTango();
                        TangoSupport.initialize(mTango);
                        connectRenderer();
                        mIsTangoConnected = true;
                        setDisplayRotation();
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.exception_tango_out_of_date), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.exception_tango_error), e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.exception_tango_invalid), e);
                    }
                }
                updateState(State.RUNNING);
            }
        });
    }

    /**
     * Kills Tango and reinitializes it
     * Refreshes CloudWorldManager and retrieves list of available worlds
     *
     * @param command
     */
    public void restartSystem(final Runnable command) {

        Log.i(TAG, "Restarting system...");

        synchronized (this) {

            if (command != null) {
                updateState(State.INIT);
            }

            // Kills every Tango alive
            // If we have a tango running, stop it in a new thread and call restart system
            // again with the same arguments after the tango is stopped
            if (mTango != null && mIsTangoConnected) {
                final Tango stopTango = mTango;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mTango.disconnect();
                        } catch (TangoErrorException e) {
                            Log.e(TAG, e.toString(), e);
                        } catch (IllegalArgumentException e) {
                            // Do nothing since Tango is already disconnected
                        }
                        synchronized (MainActivity.this) {
                            if (mTango == stopTango) {
                                mTango = null;
                                mCloudWorldManager.setTango(null);
                            }
                        }
                        restartSystem(command);
                    }
                }).start();
                return;
            }

            mDetailedWorld = null;
            mIsPhoneLocalized = false;
            mIsTangoConnected = false;

            mTango = new Tango(MainActivity.this, new Runnable() {
                @Override
                public void run() {
                    synchronized (MainActivity.this) {
                        mCloudWorldManager.setTango(mTango);

                        if (command == null) {
                            Log.i(TAG, "Command is null. Refreshing Cloud World Manager and retrieving worlds.");
                            mCloudWorldManager.refreshAndGetWorlds();
                        } else {
                            Log.i(TAG, "Running command...");
                            command.run();
                        }
                    }
                }
            });
        }
    }

    /**
     * Adds the authentication state listener, which then opens the dialog for
     * signing in to the cloud.
     */
    private void signInToCloud() {
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    /**
     * Signs out of cloud, then removes the authentication state listener.
     */
    private void signOutOfCloud() {
        AuthUI.getInstance().signOut(this);
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
    }

    @SuppressLint("ShowToast")
    private void init() {
        updateState(State.INIT);

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    mUser = user;

                    // Retrieve saved robot from shared preferences
                    String robotJson = mSharedPreferences.getString(mUser.getUid(), null);
                    Robot robot = mGson.fromJson(robotJson, Robot.class);

                    if (robot == null) {
                        Log.i(TAG, "Showing robots list.");
                        selectRobot();
                    } else {
                        Log.i(TAG, "Retrieved user\'s saved robot from shared preferences.");
                        mRobot = robot;
                        watchRobot();
                        watchPingTimeForRobot();
                        updateActiveGoalsList();
                        updatePoiMap();
                        updateAnimationList();

                        World world = new World(mRobot.map, null);
                        startSystem(world);
                    }
                } else {
                    // User is signed out
                    AuthUI.IdpConfig mIdentityProviderConfig =
                            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(
                                            Collections.singletonList(mIdentityProviderConfig))
                                    .build(),
                            REQUEST_SIGN_IN);
                }
                // Update cloud connector with the current robot
                synchronized (mCloudConnectorLock) {
                    if (mCloudConnector != null) {
                        mCloudConnector.updateWithRobot(mCloudHelper.getCurrentUserid(), mRobot);
                    }
                }
            }
        };
        mSharedPreferences = getPreferences(MODE_PRIVATE);
        mGson = new Gson();
        mCloudWorldManager = new CloudWorldManager(MainActivity.this, this);
        mCloudWorldManager.setTango(null);
        mCloudWorldManager.setPromptLink(mWorldPromptLink);
        mRobotConnectionStatus = new RobotConnectionStatus(new RobotConnectionStatus.Listener() {
            @Override
            public void onStatusUpdate(RobotConnectionStatus.Status status) {
                updateAppInfoTextView();
            }
        });
        mRobotConnectionStatusTimer = new RobotConnectionStatusTimer(mRobotConnectionStatus);
        mRenderer = new ARCompanionRenderer(this);
        mSurfaceView.setSurfaceRenderer(mRenderer);
        mSurfaceView.setOnTouchListener(this);
        mGestureDetector = new GestureDetectorCompat(MainActivity.this,
                new ClickAndLongPressGestureListener());
        mVoiceCommands = new VoiceCommands(this);
        mVoiceCommands.setOnVoiceDetectedListener(this);
        mVoiceCommandInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVoiceCommands.startVoiceRecognition(mRobot, mKeyedPoi, mAnimations);
            }
        });

        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    synchronized (this) {
                        setDisplayRotation();
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            }, null);
        }
    }

    private void setSelectedRobot(Robot robot) {
        Log.i(TAG, "Robot selected: " + robot.getName());
        mRobot = robot;

        // Store robot in shared preferences for offline use
        SharedPreferences.Editor prefsEditor = mSharedPreferences.edit();
        String robotJson = mGson.toJson(robot);
        prefsEditor.putString(mUser.getUid(), robotJson);
        prefsEditor.apply();

        // Update cloud connector with the current robot.
        synchronized (mCloudConnectorLock) {
            if (mCloudConnector != null) {
                mCloudConnector.updateWithRobot(mCloudHelper.getCurrentUserid(), mRobot);
            }
        }
    }

    private void watchRobot() {
        mCloudHelper.watchRobot(new CloudHelper.ResultListener<Robot>() {
            @Override
            public void onResult(Robot robotResult) {
                synchronized (MainActivity.this) {

                    // Check if the robot result from firebase is the same as our robot's.
                    if (mRobot == null || !mRobot.uuid.equals(robotResult.uuid)) {
                        return;
                    }

                    // Store robot's localization state
                    mIsRobotLocalized = robotResult.localized;

                    // Set robot position
                    if (robotResult.tf != null) {
                        mRobotPosition.setAll(robotResult.tf.px, mFloorZ, -robotResult.tf.py);
                    }

                    // Store path transforms
                    if (robotResult.path == null) {
                        // Robot reached goal successfully or is stationary.
                        // De-render all path nodes and lines.
                        clearGoalPath();
                        return;
                    }

                    // If a goal is rejected, path stays the same but the goal disappears.
                    // Since goal markers are already updated in another method, manually clear
                    // the path line and nodes when a goal is rejected.
                    if (robotResult.state != null && robotResult.state.contains("[Goal rejected]")) {
                        clearGoalPath();
                    } else {
                        // If goal path transforms changed, update the transforms list by comparing
                        // to the new transforms
                        List<ai.cellbots.common.Transform> newTransforms = new ArrayList<>();

                        for (int i = 0; i < robotResult.path.size(); ++i) {
                            // Robot position on firebase is represented using common.data.Transform
                            // while the path nodes are represented using common.Transform.
                            // Thus, until we decide to use only one Transform object, we'll need
                            // to convert the firebase transform to the common.Transform object.
                            ai.cellbots.common.Transform tf =
                                    new ai.cellbots.common.Transform(
                                            robotResult.path.get("goal_" + Integer.toString(i)));
                            newTransforms.add(tf);
                        }

                        if (!mGoalPathTransforms.equals(newTransforms)) {
                            // Goal path has changed
                            // Update transforms in mGoalPathTransforms list
                            mGoalPathTransforms.clear();
                            mGoalPathTransforms.addAll(newTransforms);

                            mIsGoalPathUpdated = true;
                        }
                    }
                }
            }
        }, mRobot);
    }

    /**
     * Adds a listener for checking the robot's last ping time, which is retrieved from the cloud.
     * Useful for determining if the RobotApp on the robot is active or hanging/crashed.
     */
    private void watchPingTimeForRobot() {
        // Remove previous ping time listener, if it exists.
        if (mPingTimeListener != null) {
            mCloudHelper.removePingTimeListener(mPingTimeListener, mRobot.uuid);
        }

        // Add a new ping time listener for the current robot.
        mPingTimeListener = mCloudHelper.addPingTimeListener(
                new CloudHelper.ResultListener<Long>() {
            /**
             * Called whenever the CloudHelper receives an update from the cloud for
             * changes in the field "ping_time".
             *
             * @param pingTime Ping timestamp result from cloud.
             */
            @Override
            public void onResult(Long pingTime) {
                if (pingTime == null) return;

                // Restart timer on every new ping time result.
                mRobotConnectionStatusTimer.restart();

                // Check if TimestampManager is synchronized in order to get a good timestamp.
                if (!isTimestampManagerSynchronized()) return;

                // Calculate latency using current timestamp and the ping time.
                long latency = getLatency(pingTime);

                // Latency becomes negative if the TimestampManager is not synchronized
                // or the current timestamp is incorrect. Since this should never happen unless
                // the TimestampManager is traveling backwards in time, stop code execution here.
                if (!isLatencyPositive(latency)) return;

                mRobotConnectionStatus.setStatusBasedOnLatency(latency);
            }
        }, mRobot.uuid);
    }

    /**
     * Checks if the TimestampManager is synchronized. If not, prints a log saying the manager is
     * not synchronized and attempts to synchronize it.
     *
     * Note: TimestampManager tends to de-synchronize, which outputs an incorrect timestamp and
     * results in negative latency. This will cause problems when setting the status of the
     * robot connection, so the TimestampManager must always be synchronized.
     *
     * @return True if the TimestampManager is synchronized.
     */
    private boolean isTimestampManagerSynchronized() {
        if (TimestampManager.isSynchronized()) {
            return true;
        }
        Log.d(PING_TIME_TAG, getString(R.string.log_debug_timestampmanager_not_synchronized));
        TimestampManager.synchronize();
        return false;
    }

    /**
     * Calculates the latency since the last ping time using the current timestamp and
     * ping time (from cloud).
     *
     * @param pingTime Last ping time sent by the robot to the cloud.
     * @return The calculated latency.
     */
    private long getLatency(@NonNull Long pingTime) {
        long latency = TimestampManager.getCurrentTimestamp() - pingTime;
        Log.v(PING_TIME_TAG, "Latency: " + latency);
        return latency;
    }

    /**
     * Checks if the given latency is positive or not.
     *
     * Note: Sometimes, the TimestampManager would be synchronized but still output an incorrect
     * timestamp, resulting in a negative latency. Instead of crashing the app, log the error and
     * re-synchronize the manager. This will ignore the "bad" latency value.
     *
     * @param latency The calculated latency value.
     * @return True if the latency value is positive.
     */
    private boolean isLatencyPositive(long latency) {
        if (latency >= 0) {
            return true;
        }
        Log.d(PING_TIME_TAG, "Latency was " + latency + " but expected positive. "
                + "Re-synchronizing TimestampManager.");
        TimestampManager.synchronize();
        return false;
    }

    private void clearGoalPath() {
        mGoalPathNodes.clear();
        mGoalPathTransforms.clear();
        mGoalPathRootNodeForRendering = null;
        mGoalPathLineForRendering = null;
        mIsGoalPathUpdated = true;
    }

    private void storeWorldPaths() {
        mOriginalPathTransforms.clear();
        mOriginalPathTransforms.addAll(mDetailedWorld.getOriginalPath());
        mSmoothedPathTransforms.clear();
        mSmoothedPathTransforms.addAll(mDetailedWorld.getSmoothedPath());
        mCustomPathTransforms.clear();
        mCustomPathTransforms.addAll(mDetailedWorld.getCustomTransforms());
    }

    private void updateActiveGoalsList() {
        // First, remove the listener if the user switched robots.
        if (mActiveGoalsListener != null) {
            mCloudHelper.removeActiveGoalListener(mActiveGoalsListener, mRobot);
        }

        // Add a new listener for the current robot.
        mActiveGoalsListener = mCloudHelper.addActiveGoalListener(
                new CloudHelper.ListResultsListener<PlannerGoal>() {
                    @Override
                    public void onResult(final List<PlannerGoal> result) {
                        synchronized (MainActivity.this) {
                            if (mRobot != null) {
                                // Update goals list
                                mActiveGoalsList.clear();
                                mActiveGoalsList.addAll(result);

                                mIsGoalsListUpdated = true;
                            }
                        }
                    }
                },
                mRobot
        );
    }

    /**
     * Updates map of POIs using data from Firebase.
     */
    private void updatePoiMap() {
        // First remove the listener if the user switched robots.
        if (mPOIListener != null) {
            mCloudHelper.removePOIListener(mPOIListener, mRobot);
        }

        // Add a new listener for the current robot.
        mPOIListener = mCloudHelper.addPOIListener(
                new CloudHelper.ListResultsListener<PointOfInterest>() {
                    @Override
                    public void onResult(final List<PointOfInterest> result) {
                        synchronized (MainActivity.this) {
                            if (mRobot != null) {
                                // Update map of POIs.
                                mKeyedPoi.clear();
                                for (PointOfInterest poi : result) {
                                    boolean isValid = PointOfInterestValidator.areAllFieldsValid(poi);
                                    if (isValid) {
                                        Log.i(TAG, "All fields for POI " + poi.uuid + " were valid. " +
                                                "Adding to POI list for rendering.");
                                        mKeyedPoi.put(poi.variables.name, poi);
                                    } else {
                                        Log.w(TAG, "The POI " + poi.uuid + " contains some invalid fields. " +
                                                "Rejected!");
                                    }
                                }
                                mIsPOIListUpdated = true;
                            }
                        }
                    }
                }, mRobot);
    }

    /**
     * Updates list of animations using data from Firebase.
     */
    private void updateAnimationList() {
        final String robotName = mRobot.getName();

        // First remove the listener if the user switched robots.
        if (mAnimationListener != null) {
            mCloudHelper.removeAnimationListener(robotName, mAnimationListener);
        }

        // Add a new listener for the current robot.
        mAnimationListener = mCloudHelper.addAnimationListener(
                robotName,
                new CloudHelper.ListResultsListener<AnimationInfo>() {
                    @Override
                    public void onResult(final List<AnimationInfo> result) {
                        synchronized (MainActivity.this) {
                            ArrayList<AnimationInfo> results = new ArrayList<>(result);
                            Collections.sort(results);

                            // Copy the animations to a list
                            for (AnimationInfo animation : results) {
                                mAnimations.add(animation.getName().toLowerCase());
                            }
                        }
                    }
                });
    }

    private TangoConfig setupTangoConfig(@NonNull Tango tango, @NonNull DetailedWorld world) {

        Log.i(TAG, "Setting up Tango Config");

        TangoConfig config = new TangoConfig();

        try {
            config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);

            // NOTE: Low latency integration is necessary to achieve a
            // precise alignment of virtual objects with the RBG image and
            // produce a good AR effect.
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LOWLATENCYIMUINTEGRATION, true);

            // Enable depth perception and color camera to retrieve relative coordinates of clicks
            config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
            config.putBoolean(TangoConfig.KEY_BOOLEAN_COLORCAMERA, true);
            config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);

            // Tango Service should automatically attempt to recover when it enters an invalid state.
            config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);

        } catch (TangoErrorException e) {
            Log.e(TAG, getString(R.string.exception_tango_config_error), e);
        }

        // Loads area description via uuid and attempts to localize against it
        CloudWorldManager.loadToTango(world, config);

        Log.i(TAG, "Tango Config has been set up");

        return config;
    }

    private void startupTango() {

        Log.i(TAG, "Starting up Tango");

        final List<TangoCoordinateFramePair> framePairs = new ArrayList<>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        mTango.connectListener(framePairs, new Tango.TangoUpdateCallback() {
            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                super.onPoseAvailable(pose);
                Log.d(TAG, "onPoseAvailable");

                // Check if localized (base = ADF, target = start of service)
                if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                        && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
                    mIsPhoneLocalized = (pose.statusCode == TangoPoseData.POSE_VALID);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateAppInfoTextView(); // Update whenever localization state changes
                        if (mIsLoggingVisible) {
                            updateTangoInfoTextView();
                            updateMapInfoTextView();
                            updatePoseInfoTextView(pose);
                        }
                    }
                });
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                super.onXyzIjAvailable(xyzIj);
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                super.onFrameAvailable(cameraId);
                // This will get called every time a new RGB camera frame is available to be
                // rendered.
                Log.d(TAG, "onFrameAvailable");

                if (cameraId == TangoCameraIntrinsics.TANGO_CAMERA_COLOR) {
                    // Marks camera frame as available for rendering in the OpenGL thread
                    mIsFrameAvailableTangoThread.set(true);
                    // Trigger an OpenGL render to update the OpenGL scene with the new RGB data.
                    mSurfaceView.requestRender();
                }
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                super.onTangoEvent(event);
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                super.onPointCloudAvailable(pointCloud);
                mPointCloudManager.updatePointCloud(pointCloud);
            }
        });
    }

    private void connectRenderer() {

        Log.i(TAG, "Setting up Renderer");

        // Register a Rajawali Scene Frame Callback to update the scene camera pose whenever a new
        // RGB frame is rendered.
        // (@see https://github.com/Rajawali/Rajawali/wiki/Scene-Frame-Callbacks)
        mRenderer.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {

            /**
             * By default, returns false to prevent preframe callbacks.
             * Enable true for onPreFrame callbacks
             *
             * @return
             */
            @Override
            public boolean callPreFrame() {
                return true;
            }

            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This is called from the OpenGL render thread, after all the renderer
                // onRender callbacks have a chance to run and before scene objects are rendered
                // into the scene.
                try {
                    // Prevent concurrent access to {@code mIsFrameAvailableTangoThread} from the
                    // Tango callback thread and service disconnection from an onPause event.
                    synchronized (MainActivity.this) {
                        // Don't execute any tango API actions if we're not connected to the
                        // service.
                        if (!mIsTangoConnected) {
                            return;
                        }

                        // Set up scene camera projection to match RGB camera intrinsics.
                        if (!mRenderer.isSceneCameraConfigured()) {
                            TangoCameraIntrinsics intrinsics =
                                    TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                            mDisplayRotation);
                            mRenderer.setProjectionMatrix(
                                    TangoUtils.CameraIntrinsicsToProjection(intrinsics));
                        }

                        // Connect the camera texture to the OpenGL Texture if necessary
                        // NOTE: When the OpenGL context is recycled, Rajawali may regenerate the
                        // texture with a different ID.
                        if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
                            mTango.connectTextureId(TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
                                    mRenderer.getTextureId());
                            mConnectedTextureIdGlThread = mRenderer.getTextureId();
                            Log.d(TAG, "connected to texture id: " + mRenderer.getTextureId());
                        }

                        // If there is a new RGB camera frame available, update the texture with it.
                        if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
                            mRgbTimestampGlThread =
                                    mTango.updateTexture(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);

                            // {@code rgbTimestamp} contains the exact timestamp at which the
                            // rendered RGB frame was acquired.

                            // In order to see more details on how to use this timestamp to modify
                            // the scene camera and achieve an augmented reality effect,
                            // refer to java_augmented_reality_example and/or
                            // java_augmented_reality_opengl_example projects.

                            Log.d(TAG, "Frame updated. Timestamp: " + mRgbTimestampGlThread);

                            // Updating the UI needs to be in a separate thread. Do it through a
                            // final local variable to avoid concurrency issues.
                            final String tangoTimestampText = String.format(getString(R.string.tango_timestamp_format),
                                    mRgbTimestampGlThread);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateTimestampTextView(tangoTimestampText);
                                }
                            });
                        }

                        // If a new RGB frame has been rendered, update the camera pose to match
                        if (mRgbTimestampGlThread > mCameraPoseTimestamp) {
                            // Calculate the camera color pose at the camera frame update time in
                            // OpenGL engine.
                            //
                            // We used mColorCameraToDisplayRotation to rotate the
                            // transformation to align with the display frame. The reason we use
                            // color camera instead depth camera frame is because the
                            // getDepthAtPointNearestNeighbor transformed depth point to camera
                            // frame.
                            //
                            // Requires OPENGL for coordinate convention, or else the cubes will be
                            // floating randomly in space
                            TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(
                                    mRgbTimestampGlThread,
                                    TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    TangoSupport.TANGO_SUPPORT_ENGINE_OPENGL,
                                    mDisplayRotation
                            );

                            if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                                mRenderer.updateRenderCameraPose(lastFramePose);
                                mCameraPoseTimestamp = lastFramePose.timestamp;
                            } else {
                                // When the pose status is not valid, it indicates the tracking has
                                // been lost. In this case, we simply stop rendering.
                                //
                                // This is also the place to display UI to suggest the user walk
                                // to recover tracking.
                                Log.w(TAG, "Can't get device pose at time: " +
                                        mRgbTimestampGlThread);
                            }

                            // Only render objects when phone is localized
                            if (mIsPhoneLocalized) {

                                // Robot's Blue Cylinder
                                if (mToggleRobotPosition.isChecked()) {
                                    if (mRobotCylinderForRendering == null) {
                                        mRobotCylinderForRendering = RendererUtils.buildRobotCylinder();
                                    }
                                    // Render the cylinder and set its position
                                    mRobotCylinderForRendering.setPosition(mRobotPosition);
                                    mRenderer.updateRobotCylinder(mRobotCylinderForRendering);
                                } else {
                                    if (mRobotCylinderForRendering != null) {
                                        // De-render and remove cylinder from scene.
                                        // This will occur only once instead of during every frame.
                                        mRobotCylinderForRendering = null;
                                        //noinspection ConstantConditions
                                        mRenderer.updateRobotCylinder(mRobotCylinderForRendering);
                                    }
                                }

                                // Build world paths (original, smoothed, custom)
                                // Update path ONLY ONCE to build (if null) and to destroy (if non-null)

                                // Original Path
                                if (mToggleOriginalPathMenuItem.isChecked()) {
                                    if (mOriginalPathRootNodeForRendering == null) {
                                        buildOriginalPathNodes();
                                        mRenderer.updateOriginalPath(mOriginalPathRootNodeForRendering);
                                    }
                                } else {
                                    if (mOriginalPathRootNodeForRendering != null) {
                                        destroyOriginalPathNodes();
                                        mRenderer.updateOriginalPath(mOriginalPathRootNodeForRendering);
                                    }
                                }

                                // Smoothed Path
                                if (mToggleSmoothedPathMenuItem.isChecked()) {
                                    if (mSmoothedPathRootNodeForRendering == null) {
                                        buildSmoothedPathNodes();
                                        mRenderer.updateSmoothedPath(mSmoothedPathRootNodeForRendering);
                                    }
                                } else {
                                    if (mSmoothedPathRootNodeForRendering != null) {
                                        destroySmoothedPathNodes();
                                        mRenderer.updateSmoothedPath(mSmoothedPathRootNodeForRendering);
                                    }
                                }

                                // Custom Path
                                if (mToggleCustomPathMenuItem.isChecked()) {
                                    if (mCustomPathRootNodeForRendering == null) {
                                        buildCustomPathNodes();
                                        mRenderer.updateCustomPath(mCustomPathRootNodeForRendering);
                                    }
                                } else {
                                    if (mCustomPathRootNodeForRendering != null) {
                                        destroyCustomPathNodes();
                                        mRenderer.updateCustomPath(mCustomPathRootNodeForRendering);
                                    }
                                }

                                if (mIsGoalPathUpdated) {
                                    // Instead of updating goal path on every frame and getting
                                    // flickering path nodes and line, update and render only when
                                    // path has been updated.
                                    buildGoalPathNodesAndLine();
                                    mRenderer.updateGoalPath(
                                            mGoalPathRootNodeForRendering,
                                            mGoalPathLineForRendering
                                    );
                                    mIsGoalPathUpdated = false;
                                }

                                // Build goal objects
                                if (mIsGoalsListUpdated) {
                                    if (!mActiveGoalsList.isEmpty()) {
                                        // Build root cube and child cubes (goals).

                                        // Since Rajawali's Cube class does not have a clear() or
                                        // deleteChildren() method, we need to recreate the root
                                        // cube in order to update its children
                                        mRootGoalCubeForRendering = RendererUtils.buildGoalRootCube();

                                        for (PlannerGoal plannerGoal : mActiveGoalsList) {
                                            if (plannerGoal instanceof DriveGoal) {
                                                DriveGoal goal = (DriveGoal) plannerGoal;
                                                Object3D childCube = RendererUtils.buildChild(
                                                        mRootGoalCubeForRendering,
                                                        goal.parameters.location.px,
                                                        mFloorZ, // Sets goal to floor
                                                        -goal.parameters.location.py
                                                );
                                                mRootGoalCubeForRendering.addChild(childCube);
                                            } else if (plannerGoal instanceof DrivePointGoal) {
                                                DrivePointGoal goal = (DrivePointGoal) plannerGoal;
                                                Object3D childCube = RendererUtils.buildChild(
                                                        mRootGoalCubeForRendering,
                                                        goal.parameters.location.px,
                                                        mFloorZ, // Sets goal to floor
                                                        -goal.parameters.location.py
                                                );
                                                mRootGoalCubeForRendering.addChild(childCube);
                                            }
                                        }
                                    } else {
                                        mRootGoalCubeForRendering = null;
                                    }
                                    mIsGoalsListUpdated = false;
                                }

                                // Build POI objects
                                if (mIsPOIListUpdated) {
                                    if (!mKeyedPoi.isEmpty()) {
                                        buildPOIs();
                                    } else {
                                        mRootPOICylinderForRendering = null;
                                        mPOILabelsForRendering.clear();
                                    }
                                    mIsPOIListUpdated = false;
                                }

                                // Show/Hide POIs depending on if the "Points of Interest"
                                // checkbox is checked in the menu.
                                if (mTogglePOIMenuItem.isChecked()) {
                                    // Update and show POIs
                                    mRenderer.updateRootObjectsWithAllPOIs(
                                            mRootPOICylinderForRendering,
                                            mPOILabelsForRendering
                                    );
                                } else {
                                    // Hide POIs
                                    mRenderer.hidePOIs();
                                }

                                // Render the rest of the objects
                                mRenderer.updateRootCubeWithAllGoals(mRootGoalCubeForRendering);

                            } else {
                                // If not localized, no render objects should be visible.
                                mRenderer.clear();
                                mOriginalPathRootNodeForRendering = null;
                                mSmoothedPathRootNodeForRendering = null;
                                mCustomPathRootNodeForRendering = null;
                            }
                        }
                    }
                } catch (TangoErrorException e) {
                    Log.e(TAG, getString(R.string.exception_tango_error), e);
                } catch (Throwable t) {
                    Log.e(TAG, getString(R.string.exception_thread_opengl), t);
                }
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }
        });
    }

    /**
     * Build the POI cylinders and their respective rectangular labels.
     */
    private void buildPOIs() {
        // Build root cylinder for holding POIs
        mRootPOICylinderForRendering = RendererUtils.buildPOIRootCylinder();

        // Clear list of POI labels
        mPOILabelsForRendering.clear();

        for (PointOfInterest poi : mKeyedPoi.values()) {
            // Build child cylinder for each POI
            double poiX = poi.getTf().px;
            double poiY = mFloorZ;
            double poiZ = -poi.getTf().py;
            double poiRotationAngle = 90.0;  // Vertical like Y-axis

            Object3D poiCylinder = RendererUtils.buildChildWithRotation(
                    mRootPOICylinderForRendering,
                    poiX,
                    poiY,
                    poiZ,
                    poiRotationAngle, 0.0, 0.0);
            mRootPOICylinderForRendering.addChild(poiCylinder);

            // Build rectangular prism for each POI's label
            String poiName = poi.variables.name;
            double poiLabelX = poi.getTf().px;
            double poiLabelY =
                    poiCylinder.getY() + RendererUtils.POI_CYLINDER_LENGTH;
            double poiLabelZ = -poi.getTf().py;

            RectangularPrism poiLabel = RendererUtils.buildPOILabel(
                    MainActivity.this,
                    poiName,
                    poiLabelX,
                    poiLabelY,
                    poiLabelZ);
            mPOILabelsForRendering.add(poiLabel);
        }
    }

    /**
     * Clears renderer's frame callbacks, disconnects Tango's camera, then disconnects Tango itself.
     */
    private void disconnectTangoAndRenderer() {
        // Synchronize against disconnecting while the service is being used in the OpenGL thread or
        // in the UI thread.
        // NOTE: DO NOT lock against this same object in the Tango callback thread. Tango.disconnect
        // will block here until all Tango callback calls are finished. If you lock against this
        // object in a Tango callback thread it will cause a deadlock.
        try {
            mRenderer.getCurrentScene().clearFrameCallbacks();
            if (mTango != null && mIsTangoConnected) {
                mTango.disconnectCamera(TangoCameraIntrinsics.TANGO_CAMERA_COLOR);
                mTango.disconnect();
            }
            // We need to invalidate the connected texture ID so that we cause a
            // re-connection in the OpenGL thread after resume.
            mConnectedTextureIdGlThread = INVALID_TEXTURE_ID;
            mIsTangoConnected = false;
        } catch (TangoErrorException e) {
            Log.e(TAG, getString(R.string.exception_tango_error), e);
        }
    }

    /**
     * Set the color camera background texture rotation and save the camera to display rotation.
     */
    @SuppressLint("WrongConstant")
    private void setDisplayRotation() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayRotation = display.getRotation();

        // We also need to update the camera texture UV coordinates. This must be run in the OpenGL
        // thread.
        mSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (mIsTangoConnected) {
                    mRenderer.updateColorCameraTextureUv(mDisplayRotation);
                }
            }
        });
    }

    private void buildOriginalPathNodes() {
        mOriginalPathRootNodeForRendering = new OriginalPathRootNode();
        mOriginalPathNodes.clear();  // Clear old original path nodes
        buildPathNodesWithRotation(
                mOriginalPathRootNodeForRendering,
                mOriginalPathTransforms,
                mOriginalPathNodes,
                90.0, 0.0, 0.0);
    }

    private void destroyOriginalPathNodes() {
        mOriginalPathRootNodeForRendering = null;
    }

    private void buildSmoothedPathNodes() {
        mSmoothedPathRootNodeForRendering = new SmoothedPathRootNode();
        mSmoothedPathNodes.clear();  // Clear old smoothed path nodes
        buildPathNodesWithRotation(
                mSmoothedPathRootNodeForRendering,
                mSmoothedPathTransforms,
                mSmoothedPathNodes, 90.0, 0.0, 0.0);
    }

    private void destroySmoothedPathNodes() {
        mSmoothedPathRootNodeForRendering = null;
    }

    private void buildCustomPathNodes() {
        mCustomPathRootNodeForRendering = new CustomPathRootNode();
        mCustomPathNodes.clear();  // Clear old custom path nodes
        buildPathNodesWithRotation(
                mCustomPathRootNodeForRendering,
                mCustomPathTransforms,
                mCustomPathNodes, 0.0, 0.0, 0.0);
    }

    private void destroyCustomPathNodes() {
        mCustomPathRootNodeForRendering = null;
    }

    private void buildGoalPathNodesAndLine() {
        mGoalPathRootNodeForRendering = new GoalPathRootNode();
        mGoalPathNodes.clear();  // Clear old goal path nodes
        buildPathNodesWithRotation(
                mGoalPathRootNodeForRendering,
                mGoalPathTransforms,
                mGoalPathNodes, 0.0, 0.0, 0.0);
        mGoalPathLineForRendering = new GoalPathLine(mGoalPathNodes);
    }

    /**
     * Builds a respective path's node objects and adds them to the path's root node
     * for rendering in batch and stack of Vector3 objects (for rendering the path line).
     *
     * @param pathRootNode  The root node object
     * @param pathTransforms  List of path node Transforms.
     * @param pathNodes  Output stack of root node position vectors.
     * @param angleX X position of the child object
     * @param angleY Y position of the child object
     * @param angleZ Z position of the child object
     */
    private void buildPathNodesWithRotation(Object3D pathRootNode,
            List<ai.cellbots.common.Transform> pathTransforms, Stack<Vector3> pathNodes,
            double angleX, double angleY, double angleZ) {
        for (ai.cellbots.common.Transform transform : pathTransforms) {
            Vector3 nodeVector = new Vector3(
                    transform.getPosition(0),
                    mFloorZ,
                    -transform.getPosition(1));
            Object3D child = RendererUtils.buildChildWithRotation(
                    pathRootNode, nodeVector.x, nodeVector.y, nodeVector.z,
                    angleX, angleY, angleZ);
            pathNodes.push(nodeVector);  // Add node to stack for rendering the Line3D object.
            pathRootNode.addChild(child);  // Add node to root node's children for batch rendering.
        }
    }

    /**
     * Use the Tango Support Library with point cloud data to calculate the depth
     * of the touch position in 3D space (from the user's device)
     *
     * Base Frame: User's device
     * Target Frame: Touch position
     */
    private ARViewPoint getClickPositionFromDevice(float u, float v) {
        final TangoPointCloudData pointCloud = mPointCloudManager.getLatestPointCloud();
        final TangoPoseData depthPose = getCurrentPoseOfDepthCamera(pointCloud);
        final TangoPoseData colorPose = getCurrentPoseOfColorCamera();

        // Calculate depth of touch position from device using point cloud
        float[] depth = getDepthOfTouchPositionFromDevice(u, v, pointCloud, depthPose, colorPose);

        if (depth == null) {
            return null;
        }

        // Convert each value in depth[] to double.
        final double[] doubleDepth = new double[depth.length];
        for (int i = 0; i < depth.length; ++i) {
            doubleDepth[i] = depth[i];
        }

        return new ARViewPoint(pointCloud.timestamp, doubleDepth);
    }

    private TangoPoseData getCurrentPoseOfDepthCamera(TangoPointCloudData pointCloudData) {
        final TangoPoseData openglTDepthPose = TangoSupport.getPoseAtTime(
                pointCloudData.timestamp,
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                TangoSupport.ROTATION_IGNORED
        );

        if (openglTDepthPose.statusCode != TangoPoseData.POSE_VALID) {
            final String tangoPoseDataInvalidString =
                    getString(R.string.exception_tango_pose_data_depth_camera_invalid, getTimestamp());

            Log.e(TAG, tangoPoseDataInvalidString);
            Toast.makeText(MainActivity.this, tangoPoseDataInvalidString, Toast.LENGTH_SHORT).show();
            return null;
        }
        return openglTDepthPose;
    }

    private TangoPoseData getCurrentPoseOfColorCamera() {
        final TangoPoseData pose = TangoSupport.getPoseAtTime(
                mRgbTimestampGlThread,
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                TangoSupport.TANGO_SUPPORT_ENGINE_TANGO,
                TangoSupport.ROTATION_IGNORED
        );

        if (pose.statusCode != TangoPoseData.POSE_VALID) {
            final String tangoPoseDataInvalidString =
                    getString(R.string.exception_tango_pose_data_color_camera_invalid, getTimestamp());

            Log.e(TAG, tangoPoseDataInvalidString);
            Toast.makeText(MainActivity.this, tangoPoseDataInvalidString, Toast.LENGTH_SHORT).show();
            return null;
        }
        return pose;
    }

    private float[] getDepthOfTouchPositionFromDevice(float u,
                                                      float v,
                                                      TangoPointCloudData pointCloud,
                                                      TangoPoseData depthPose,
                                                      TangoPoseData colorPose) {
        float[] depth = TangoSupport.getDepthAtPointNearestNeighbor(
                pointCloud,
                depthPose.translation,
                depthPose.rotation,
                u,
                v,
                mDisplayRotation,
                colorPose.translation,
                colorPose.rotation
        );

        if (depth == null) {
            final String depthOfTouchPositionIsNullString =
                    getString(R.string.exception_depth_of_touch_position_null, getTimestamp());

            Log.e(TAG, depthOfTouchPositionIsNullString);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mClickCoordinatesTextView.setText(depthOfTouchPositionIsNullString);
                }
            });

            return null;
        }
        return depth;
    }

    /**
     * Displays the transformed coordinates of the click position
     *
     * Base Frame: ADF origin
     * Target Frame: Click position
     *
     * @param ARViewPoint
     */
    private void showClickCoordinates(final ARViewPoint ARViewPoint) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String[] clickCoordinates = new String[3];

                // Format ARViewPoint coordinates to 3 decimal places
                for (int i = 0; i < ARViewPoint.getCoordinates().length; ++i) {
                    String coord = FloatingPointUtils.formatToThreeDecimals(ARViewPoint.getCoordinate(i));
                    clickCoordinates[i] = coord;
                }

                String touchPositionVals = "Click Position:\n" +
                        "  x: " + clickCoordinates[0] + "\n" +
                        "  y: " + clickCoordinates[1] + "\n" +
                        "  z: " + clickCoordinates[2];
                mClickCoordinatesTextView.setText(touchPositionVals);
            }
        });
    }

    private synchronized void updateState(State state) {
        Log.i(TAG, "Updating state to: " + state.toString());
        mState = state;
        updateStateTextView();
    }

    private void updateStateTextView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Lists the Views that are visible in each state

                // INIT: State TextView
                // WAITING: Robots List container, State TextView
                // IMPORTING: State TextView
                // Loading: State TextView
                // Running: Info TextView, Surface View, Voice Command Input Button,
                //          Logging Layout (toggled), Toggle Debug Info Menu Item
                String textState = "";

                switch (mState) {
                    case INIT:
                        textState = "Init";
                        mStateTextView.setVisibility(View.VISIBLE);
                        mSurfaceView.setVisibility(View.GONE);
                        mAppInfoTextView.setVisibility(View.GONE);
                        mLoggingContainer.setVisibility(View.GONE);
                        mRobotsListContainer.setVisibility(View.GONE);
                        mVoiceCommandInputButton.setVisibility(View.GONE);
                        break;
                    case WAITING:
                        textState = "Waiting...";
                        mRobotsListContainer.setVisibility(View.VISIBLE);
                        mCloudWorldManager.setShouldDownloadWorlds(true);
                        restartSystem(null);
                        break;
                    case IMPORTING:
                        textState = "Importing...";
                        mRobotsListContainer.setVisibility(View.GONE);
                        break;
                    case LOADING:
                        textState = "Loading...";
                        mRobotsListContainer.setVisibility(View.GONE);
                        break;
                    case RUNNING:
                        textState = "Running!";
                        mSurfaceView.setVisibility(View.VISIBLE);
                        mAppInfoTextView.setVisibility(View.VISIBLE);
                        mLoggingContainer.setVisibility(View.GONE);
                        mStateTextView.setVisibility(View.GONE);
                        mCloudWorldManager.setShouldDownloadWorlds(false);
                        mVoiceCommandInputButton.setVisibility(View.VISIBLE);
                        mToggleDebugInfoMenuItem.setVisible(true);
                        mToggleDebugInfoMenuItem.setTitle(R.string.menu_show_logging_info);

                        mIsLoggingVisible = false;
                        break;
                }
                mStateTextView.setText(textState);
            }
        });
    }

    /**
     * Updates text for the following:
     * - Localization states (phone, robot)
     * - World uuid
     * - Robot name
     * - Robot status
     * - Connection Status
     */
    private void updateAppInfoTextView() {
        final DetailedWorld world = mDetailedWorld;
        final Robot robot = mRobot;
        final String robotStatus = mRobotStatus;

        String worldUuid = (world == null) ? "" : world.getUuid();
        String robotName = (robot == null) ? "" : robot.getName();
        String robotConnectionStatus =
                (mRobotConnectionStatus == null) ?
                        "Not Connected" : mRobotConnectionStatus.getStatus().toString();

        String infoText = "Phone Localized: " + mIsPhoneLocalized + "\n"
                + "Robot Localized: " + mIsRobotLocalized + "\n"
                + "Map: " + worldUuid + "\n"
                + "Robot: " + robotName + "\n"
                + "State: " + robotStatus + "\n"
                + "Connection Status: " + robotConnectionStatus;

        mAppInfoTextView.setText(infoText);
    }

    /**
     * Updates text for current timestamp and tango timestamp.
     *
     * @param tangoTimestampText Timestamp provided by the Tango service.
     */
    private void updateTimestampTextView(String tangoTimestampText) {
        String timestampText = "Current Timestamp: " + getTimestamp() + "\n"
                + tangoTimestampText;
        mTimestampTextView.setText(timestampText);
    }

    /**
     * Updates text for the Tango service and if we're connected.
     */
    private void updateTangoInfoTextView() {
        Tango tango = mTango;
        String tangoString = (tango == null) ? "" : mTango.toString();

        String tangoInfoString = "Tango: " + tangoString + "\n" +
                "Connected to Tango Service: " + mIsTangoConnected;

        mTangoInfoTextView.setText(tangoInfoString);
    }

    private void updateMapInfoTextView() {
        final List<PlannerGoal> goalsList = mActiveGoalsList;
        final Map<String, PointOfInterest> pointOfInterestList = mKeyedPoi;
        final Stack<Vector3> pathNodes = mGoalPathNodes;

        String mapInfoText = "Number of Active Goals: " + goalsList.size() + "\n" +
                "Number of POIs: " + pointOfInterestList.size() + "\n" +
                "Number of current path nodes: " + pathNodes.size();
        mMapInfoTextView.setText(mapInfoText);
    }

    private void updatePoseInfoTextView(TangoPoseData pose) {
        String baseFrame = TangoPoseData.FRAME_NAMES[pose.baseFrame];
        String targetFrame = TangoPoseData.FRAME_NAMES[pose.targetFrame];
        String framesText = "Base: " + baseFrame + "\n" +
                "Target: " + targetFrame;

        mReferenceFramesTextView.setText(framesText);

        // Format x, y, z coordinates of TangoPoseData to three decimal places
        String[] coordinates = new String[3];

        for (int i = 0; i < coordinates.length; ++i) {
            double poseVal = pose.translation[i];
            coordinates[i] = FloatingPointUtils.formatToThreeDecimals(poseVal);
        }

        String devicePose = "Pose:\n" +
                "\tx: " + coordinates[0] + "\n" +
                "\ty: " + coordinates[1] + "\n" +
                "\tz: " + coordinates[2];

        String robotPose = "Robot Pose:\n" +
                "\tx: " + mRobotPosition.x + "\n" +
                "\ty: " + mRobotPosition.y + "\n" +
                "\tz: " + mRobotPosition.z;

        String poseInfoText = devicePose + "\n" + robotPose;
        mPoseInfoTextView.setText(poseInfoText);
    }

    private long getTimestamp() {
        return new Date().getTime();
    }

    /**
     * Display toast on UI thread.
     *
     * Cloned from Tango Basic Examples Project
     *
     * @param resId The resource id of the string resource to use. Can be formatted text.
     */
    private void showsToastAndFinishOnUiThread(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this,
                        getString(resId), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    class ClickAndLongPressGestureListener extends GestureDetector.SimpleOnGestureListener {

        private final String TAG = ClickAndLongPressGestureListener.class.getSimpleName();

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            createGoalAndSendToCloud(mARViewPointTransform);
            return super.onSingleTapUp(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            showAddPOIDialog();
            super.onLongPress(e);
        }

        private void createGoalAndSendToCloud(Transform transform) {
            DrivePointGoal drivePointGoal = new DrivePointGoal(transform, mDetailedWorld.getUuid());
            mCloudHelper.sendPlannerGoalToRobot(drivePointGoal, mRobot);
        }

        private void showAddPOIDialog() {
            AddPOIDialogFragment dialogFragment = AddPOIDialogFragment.newInstance();
            dialogFragment.show(getSupportFragmentManager(), Tags.dialogAddPOI);
        }
    }
}
