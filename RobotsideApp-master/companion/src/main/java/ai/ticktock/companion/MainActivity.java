package ai.cellbots.companion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.cellbots.common.AboutActivity;
import ai.cellbots.common.CloudConnector;
import ai.cellbots.common.CloudHelper;
import ai.cellbots.common.DetailedWorld;
import ai.cellbots.common.RobotConnectionStatus;
import ai.cellbots.common.RobotConnectionStatusTimer;
import ai.cellbots.common.teleop.VirtualJoystickView;
import ai.cellbots.common.voice.OnVoiceDetectedListener;
import ai.cellbots.common.Strings;
import ai.cellbots.common.voice.VoiceCommands;
import ai.cellbots.common.World;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.RTCDatabaseChannel;
import ai.cellbots.common.cloud.TimestampManager;
import ai.cellbots.common.data.AnimationGoal;
import ai.cellbots.common.data.AnimationInfo;
import ai.cellbots.common.data.BatteryStatus;
import ai.cellbots.common.data.DrivePOIGoal;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.LogMessage;
import ai.cellbots.common.data.NavigationStateCommand;
import ai.cellbots.common.data.PatrolDriverPOIGoal;
import ai.cellbots.common.data.PlannerGoal;
import ai.cellbots.common.data.PointOfInterest;
import ai.cellbots.common.data.Robot;
import ai.cellbots.common.data.RobotMetadata;
import ai.cellbots.common.data.RobotPreferences;
import ai.cellbots.common.data.SoundInfo;
import ai.cellbots.common.data.SpeedStateCommand;
import ai.cellbots.common.data.Transform;
import ai.cellbots.common.poicontroller.PoiControllerFragment;
import ai.cellbots.common.settings.RobotPreferencesFragment;
import ai.cellbots.common.utils.PointOfInterestValidator;
import ai.cellbots.common.webrtc.WebRTCView;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener, Preference.OnPreferenceChangeListener,
        PoiControllerFragment.OnPoiControllerResultListener {

    private static final int RC_SIGN_IN = 1;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String LOG_TAG_APP = "APP";
    private static final String LOG_TAG_ROBOT = "ROBOT";
    // Tag for robot's ping time logs.
    private static final String LOG_TAG_PING_TIME = TAG + "PingTime";

    // FragmentManager Transactions
    private static final String TRANSACTION_ADD_POI_CONTROLLER_FRAGMENT =
            "TRANSACTION_ADD_POI_CONTROLLER_FRAGMENT";

    // Fragment Tags
    private static final String FRAGMENT_TAG_POI_CONTROLLER = "FRAGMENT_TAG_POI_CONTROLLER";

    private static final String BASE_BATTERY = "kobuki";

    private static final long CLEAN_GOAL_DEFAULT_DURATION_SEC = 5;
    private static final long PATROL_GOAL_DEFAULT_DURATION_SEC = 5;

    private final RTCDatabaseChannel mRTCDatabaseChannel
            = new RTCDatabaseChannel(new HashMap<CloudPath, DataFactory>());

    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    private SettingsFragment mSettings;

    private CloudHelper mCloudHelper;
    private FirebaseAnalyticsEvents mFirebaseEventLogger;
    private Robot mCurrentRobot = null;
    private List<World> mWorlds = new ArrayList<>();
    private List<String> mAnimations = new ArrayList<>();

    private View mButtonsGroup;
    private FloatingActionButton mButtonSaveMap;
    private FloatingActionButton mButtonRunning;
    private Button mButtonExecutiveMode;
    private Button mButtonShowDebug;
    private Button mButtonShowPOIs;
    private Button mButtonShowTeleop;
    private Button mButtonShowSounds;
    private Button mButtonShowAnims;
    private Button mButtonShowVideo;
    private View mLoadingProgressBar;
    private ProgressBar mBatteryIndicatorProgressBar;
    private TextView mCurrentRobotTextView;
    private TextView mRobotConnectionStatusTextView;
    private TextView mRobotLocalizedTextView;
    private TextView mStatusTextView;
    private ScrollView mStatusTextViewScroll;
    private FloorplanView mFloorplan;
    private LinearLayout mTeleopView;
    private Button mButtonAction1Switch;
    private Button mButtonAction2Switch;
    private VirtualJoystickView mVirtualJoystickView;
    private WebRTCView mWebRTCView;
    private FrameLayout mFragmentContainer;

    private CloudHelper.WorldListener mWorldListener = null;
    private CloudHelper.RobotListener mRobotListener = null;

    private ValueEventListener mPingTimeListener;  // Listener for robot's ping time.

    // Mapping flag, indicates when the application is in mapping mode
    private boolean mMapping = false;

    private ListView mListViewPOIs;
    private ListView mListViewSounds;
    private ListView mListViewAnims;
    private POIListAdapter mPoiListAdapter;
    // Variables to save, display and manage POIs
    private ArrayList<String> mPoiKeys;
    private final Map<String, PointOfInterest> mKeyedPoi = new HashMap<>();
    private ValueEventListener mPOIListener;

    private ValueEventListener mSoundListener;
    private ValueEventListener mGlobalSoundListener;
    private final List<SoundInfo> mLocalSounds = new ArrayList<>();
    private final List<SoundInfo> mGlobalSounds = new ArrayList<>();

    private ValueEventListener mAnimationListener;

    private String mAnimationRobot = null;

    private ValueEventListener mActiveGoalsListener;

    private SharedPreferences mPreferences;
    private ValueEventListener mMetadataListener;
    private RobotMetadata mRobotMetadata;

    private AlertDialog mSelectRobotDialog = null;
    private boolean mActivityActive = false;

    private static final SimpleDateFormat LOG_DATE_FORMAT
            = new SimpleDateFormat("hh:mm:ss", Locale.US);

    private CloudConnector mCloudConnector = null;

    // Robot connection status and timer classes for checking the connection status
    // between the RobotApp and cloud.
    private RobotConnectionStatus mRobotConnectionStatus;
    private RobotConnectionStatusTimer mRobotConnectionStatusTimer;

    // Requesting permission to RECORD_AUDIO
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private final HashMap<String, LogMessage> mLogMessages = new HashMap<>();
    private static final long LOG_MESSAGE_TIMEOUT = 60000; // milliseconds

    private VoiceCommands mVoiceCommands;

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                if (grantResults.length > 0) {
                    permissionToRecordAccepted =
                            (grantResults[0] == PackageManager.PERMISSION_GRANTED);
                }
                break;
        }
        if (!permissionToRecordAccepted && requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            for (String perm : this.permissions) {
                if (ActivityCompat.checkSelfPermission(this, perm)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Do not have: " + perm);
                    Toast.makeText(this, getString(R.string.no_record), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            permissionToRecordAccepted = true;
        }
    }

    @SuppressLint("ShowToast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If app is back from background, then retrieve the current robot id.
        if (savedInstanceState != null) {
            mCurrentRobot = new Robot();
            mCurrentRobot.uuid = savedInstanceState.getString("currentRobotUuid");
        }

        FirebaseApp.initializeApp(this);
        mCloudHelper = CloudHelper.getInstance();
        mFirebaseEventLogger = FirebaseAnalyticsEvents.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        mButtonsGroup = findViewById(R.id.group_buttons);
        mButtonSaveMap = this.findViewById(R.id.btn_save_map);
        mButtonRunning = this.findViewById(R.id.btn_running);
        mButtonExecutiveMode = this.findViewById(R.id.btn_executive_mode);
        mButtonShowDebug = this.findViewById(R.id.btn_show_debug);
        mButtonShowPOIs = this.findViewById(R.id.btn_show_POIS);
        mButtonShowSounds = this.findViewById(R.id.btn_show_sounds);
        mButtonShowAnims = this.findViewById(R.id.btn_show_anims);
        mButtonShowVideo = this.findViewById(R.id.btn_show_video);
        mButtonShowTeleop = this.findViewById(R.id.btn_show_teleop);
        mLoadingProgressBar = findViewById(R.id.loading_progress_bar);
        mBatteryIndicatorProgressBar = findViewById(R.id.batteryProgressBar);
        mStatusTextViewScroll = findViewById(R.id.text_log_scroll);
        mStatusTextView = findViewById(R.id.text_log);
        mFloorplan = findViewById(R.id.floorplan);
        mTeleopView = findViewById(R.id.teleop_view);
        mButtonAction1Switch = findViewById(R.id.btn_action1_switch);
        mButtonAction2Switch = findViewById(R.id.btn_action2_switch);
        mVirtualJoystickView = findViewById(R.id.joystick_view);
        mWebRTCView = findViewById(R.id.WebRTCView);
        mFragmentContainer = findViewById(R.id.fragment_container);

        mWebRTCView.setDatabaseChannel(mRTCDatabaseChannel);
        mVirtualJoystickView.setDatabaseChannel(mRTCDatabaseChannel);

        // Readjust the joystick dimensions to fit the teleop view and remain squared.
        mTeleopView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft,
                                       int oldTop, int oldRight, int oldBottom) {
                mVirtualJoystickView.setLayoutParams(
                        new LinearLayout.LayoutParams(v.getWidth(), v.getWidth()));
            }
        });

        mFloorplan.setListener(this);
        registerForContextMenu(mFloorplan);
        // ListView to display POIs
        mListViewPOIs = this.findViewById(R.id.POIListView);
        // Dictionary of POI names and POI data
        mPoiKeys = new ArrayList<>();
        mPoiListAdapter = new POIListAdapter(this, R.layout.list_item, mPoiKeys);
        mPoiListAdapter.setListener(this);
        mListViewPOIs.setAdapter(mPoiListAdapter);
        mListViewPOIs.setOnItemClickListener(this);
        mListViewPOIs.setOnItemLongClickListener(this);

        // ListView to display the soundboard
        mListViewSounds = this.findViewById(R.id.SoundListView);
        mListViewSounds.setOnItemClickListener(this);
        mListViewSounds.setVisibility(View.INVISIBLE);

        // ListView to display the animations
        mListViewAnims = this.findViewById(R.id.AnimsListView);
        mListViewAnims.setOnItemClickListener(this);
        mListViewAnims.setVisibility(View.INVISIBLE);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mSettings = new SettingsFragment();

        mSettings.setPreferencesListener(
                new RobotPreferencesFragment.PreferenceKeyChangeListener<String>() {
                    @Override
                    public void onChange(final String key) {
                        // If a key is being updated synchronize with robot
                        return;
                    }
                });

        mSettings.setUpdatePreferencesListener(
                new RobotPreferencesFragment.updatePreferencesListener() {
                    @Override
                    public void update() {
                        // Configure update function, called before and after showing
                        // preferences dialog
                        updateRobotPreferencesFromDBFleet();
                    }
                });

        // Do not show these when the app is launched.
        mButtonSaveMap.setVisibility(View.GONE);
        mButtonRunning.setVisibility(View.GONE);
        mStatusTextViewScroll.setVisibility(View.GONE);
        mTeleopView.setVisibility(View.GONE);
        mListViewPOIs.setVisibility(View.GONE);
        mListViewSounds.setVisibility(View.GONE);
        mListViewAnims.setVisibility(View.GONE);
        setShowVideoView(false);

        // Show robot name on a custom view in action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            Log.wtf(TAG, "Cannot access action bar");
        } else {
            // Align view right.
            ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT,
                    Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            View robotName = LayoutInflater.from(this).inflate(R.layout.layout_robot_info, null);
            actionBar.setCustomView(robotName, layoutParams);
            actionBar.setDisplayShowCustomEnabled(true);
            // Show app name too.
            actionBar.setDisplayShowTitleEnabled(true);
        }
        mCurrentRobotTextView = findViewById(R.id.text_current_robot);
        mRobotConnectionStatusTextView = findViewById(R.id.textview_robot_connection_status);
        mRobotLocalizedTextView = findViewById(R.id.textview_robot_localized);

        mRobotConnectionStatus = new RobotConnectionStatus(new RobotConnectionStatus.Listener() {
            @Override
            public void onStatusUpdate(RobotConnectionStatus.Status status) {
                String statusText = String.format(Locale.US, "Status: %s", status);
                mRobotConnectionStatusTextView.setText(statusText);
            }
        });
        mRobotConnectionStatusTimer = new RobotConnectionStatusTimer(mRobotConnectionStatus);

        mVoiceCommands = new VoiceCommands(MainActivity.this);
        mVoiceCommands.setOnVoiceDetectedListener(new OnVoiceDetectedListener() {
            @Override
            public void onVoiceActionDetected(VoiceCommands.VoiceAction actionDetected) {
                CloudConnector cc = mCloudConnector;
                switch (actionDetected) {
                    case START:
                        if (cc != null) {
                            cc.setRobotSpeedCommand(mCurrentRobot, new SpeedStateCommand(true));
                        }
                        break;
                    case STOP:
                        if (cc != null) {
                            cc.setRobotSpeedCommand(mCurrentRobot, new SpeedStateCommand(false));
                        }
                        break;
                    case GOTO:
                        break;
                    case COME_HERE:
                        break;
                    case ACTION1:
                        break;
                    case ACTION2:
                        break;
                    case ANIMATION:
                        break;
                }
            }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                //noinspection StatementWithEmptyBody
                if (user != null) {
                    // User is signed in
                    //onSignedInInitialize(user.getDisplayName());
                } else {
                    // User is signed out
                    AuthUI.IdpConfig mIdentityProviderConfig =
                            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build();

                    //onSignedOutCleanup();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(
                                            Collections.singletonList(mIdentityProviderConfig))
                                    .build(),
                            RC_SIGN_IN);
                }
                CloudConnector cc = mCloudConnector;
                if (cc != null) {
                    cc.updateWithRobot(mCloudHelper.getCurrentUserid(), mCurrentRobot);
                }
            }
        };
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is killed and restarted.
        if (mCurrentRobot != null) {
            savedInstanceState.putString("currentRobotUuid", mCurrentRobot.uuid);
        }
    }

    /**
     * Allows the user to set a new name for the robot. Opens dialog for new name, syncs with the
     * cloud, and updates the text view on the action bar.
     *
     * @param robotUuid uuid of the robot to rename.
     */
    private void setNewRobotName(final String robotUuid) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                MainActivity.this);

        final EditText input = new EditText(MainActivity.this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setTitle("Enter new name for the robot").setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String text = input.getText().toString();
                if (!text.isEmpty()) {
                    if (mFirebaseEventLogger != null) {
                        // Report a "Robot Renamed" event to Firebase Analytics.
                        mFirebaseEventLogger.reportRobotRenamedEvent(getApplicationContext());
                    }
                    // Send name to Firebase.
                    mCloudHelper.sendRobotName(mCloudHelper.getCurrentUserid(), robotUuid, text);
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopWatchingWorlds();

        synchronized (this) {
            mActivityActive = false;
            if (mSelectRobotDialog != null) {
                mSelectRobotDialog.dismiss();
                mSelectRobotDialog = null;
            }
            try {
                mTimeThread.join();
            } catch (InterruptedException e) {
                Log.w(TAG, "Time shutdown interrupted: ", e);
            }
            if (mCloudConnector != null) {
                mCloudConnector.shutdown();
                mCloudConnector = null;
            }
        }

        // Clear out the video
        setShowVideoView(false);

        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        mVirtualJoystickView.stopTimer();
        setAction1State(false);
        setAction2State(false);
    }

    private void setAction1State(boolean state) {
        mVirtualJoystickView.setAction1State(state);
        if (state) {
            mButtonAction1Switch.setText(getString(R.string.action_1_on));
        } else {
            mButtonAction1Switch.setText(getString(R.string.action_1_off));
        }
    }

    private void setAction2State(boolean state) {
        mVirtualJoystickView.setAction2State(state);
        if (state) {
            mButtonAction2Switch.setText(getString(R.string.action_2_on));
        } else {
            mButtonAction2Switch.setText(getString(R.string.action_2_off));
        }
    }

    public void onAction1ButtonClicked(View view) {
        synchronized (this) {
            if (mFirebaseEventLogger != null) {
                // Report "Action 1" button click events to Firebase Analytics.
                mFirebaseEventLogger.reportAction1ButtonClickEvent(this);
            }
            if (mActivityActive) {
                setAction1State(!mVirtualJoystickView.getAction1State());
            }
        }
    }

    public void onAction2ButtonClicked(View view) {
        synchronized (this) {
            if (mActivityActive) {
                if (mFirebaseEventLogger != null) {
                    // Report "Action 2" button click events to Firebase Analytics.
                    mFirebaseEventLogger.reportAction2ButtonClickEvent(this);
                }
                setAction2State(!mVirtualJoystickView.getAction2State());
            }
        }
    }

    private Thread mTimeThread = null;

    @Override
    protected void onResume() {
        super.onResume();

        synchronized (this) {
            mActivityActive = true;
            mTimeThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mActivityActive) {
                        TimestampManager.synchronize();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Time thread interrupted: ", e);
                        }
                    }
                }
            });
            mTimeThread.start();
        }

        if (mAuthStateListener != null) {
            mFirebaseAuth.addAuthStateListener(mAuthStateListener);
        }

        mVirtualJoystickView.startTimer();

        // Get worlds list
        if (mCloudHelper.isLoggedIn()) {
            watchWorlds();
        }

        // If the app is first launched, then we need to select a robot to use. If not, set
        // selected robot with the stored data.
        if (mCurrentRobot == null && mCloudHelper.isLoggedIn()) {
            addLogEntry(LOG_TAG_APP, "Selecting robot");
            selectRobot();
        } else if (mCloudHelper.isLoggedIn() && mCurrentRobot.uuid != null) {
            setSelectedRobot(mCurrentRobot);
        }

        CloudConnector cc = new CloudConnector(this, new CloudConnector.Listener() {
            @Override
            public void onExecutiveCommand(ExecutiveStateCommand command, boolean sync) {
                if (sync) {
                    if (command == null || command.getExecutiveMode() == null
                            || command.getExecutiveMode() == ExecutiveStateCommand.ExecutiveState.STOP) {
                        mButtonExecutiveMode.setText(R.string.executive_mode_stop);
                        mButtonExecutiveMode.setTag(ExecutiveStateCommand.ExecutiveState.STOP);
                    } else if (command.getExecutiveMode() == ExecutiveStateCommand.ExecutiveState.RANDOM_DRIVER) {
                        mButtonExecutiveMode.setText(R.string.executive_mode_random_driver);
                        mButtonExecutiveMode.setTag(ExecutiveStateCommand.ExecutiveState.RANDOM_DRIVER);
                    }
                    mButtonExecutiveMode.setVisibility(View.VISIBLE);
                } else {
                    mButtonExecutiveMode.setVisibility(View.INVISIBLE);
                    mButtonExecutiveMode.setTag(null);
                }
            }

            @Override
            public void onSpeedCommand(SpeedStateCommand command, boolean sync) {
                if (!sync) {
                    mButtonRunning.setVisibility(View.INVISIBLE);
                    mButtonRunning.setTag(null);
                } else if (command != null && command.isStarted()) {
                    mButtonRunning.setVisibility(View.VISIBLE);
                    mButtonRunning.setImageResource(android.R.drawable.ic_media_pause);
                    mButtonRunning.setBackgroundTintList(
                            getResources().getColorStateList(R.color.running_button_stop));
                    mButtonRunning.setTag(1);
                } else {
                    mButtonRunning.setVisibility(View.VISIBLE);
                    mButtonRunning.setImageResource(android.R.drawable.ic_media_play);
                    mButtonRunning.setBackgroundTintList(
                            getResources().getColorStateList(R.color.running_button_start));
                    mButtonRunning.setTag(0);
                }
            }
        });
        mCloudConnector = cc;
        cc.updateWithRobot(mCloudHelper.getCurrentUserid(), mCurrentRobot);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Handle ActionBar actions.
     * See /res/menu/main.xml for declared actions.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                if (mFirebaseEventLogger != null) {
                    // Report "About" action selected events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionAboutEvent(this);
                }
                // Show About activity
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.action_select_robot:
                if (mFirebaseEventLogger != null) {
                    // Report "Select Robot" action selected events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionSelectRobotEvent(this);
                }
                // Display dialog to select a new robot and set it as the current robot.
                selectRobot();
                return true;
            case R.id.action_rename_robot:
                if (mFirebaseEventLogger != null) {
                    // Report "Rename Robot" action selected events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionRenameRobotEvent(this);
                }
                // Display dialog to rename a robot and update displayed name.
                setNewRobotName(mCurrentRobot.uuid);
                return true;
            case R.id.action_manage_maps:
                if (mFirebaseEventLogger != null) {
                    // Report "Manage Maps" action selected events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionManageMapsEvent(this);
                }
                // Show maps for this user
                manageMaps();
                return true;
            case R.id.action_logout:
                if (mFirebaseEventLogger != null) {
                    // Report "Log Out" action click events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionSignOutEvent(this);
                }
                AuthUI.getInstance().signOut(this);
                return true;
            case R.id.action_cancel_goals:
                if (mFirebaseEventLogger != null) {
                    // Report "Cancel Active Goals" action click events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionCancelActiveGoalsEvent(this);
                }
                // Cancel active goals.
                mCloudHelper.cancelAllRobotGoals(mCurrentRobot.uuid);
                Toast.makeText(MainActivity.this, "Cancelling all active goals",
                        Toast.LENGTH_LONG).show();
                return true;
            case R.id.action_voice_command:
                startVoiceCommandsAction();
                return true;
            case R.id.action_preferences:
                if (mFirebaseEventLogger != null) {
                    // Report "Settings" action click events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionSettingsEvent(this);
                }
                // Show robot_preferences fragment
                getFragmentManager().beginTransaction().
                        replace(android.R.id.content, mSettings).
                        addToBackStack(null).commit();
                return true;
            case R.id.action_reset_zoom:
                if (mFirebaseEventLogger != null) {
                    // Report "Reset Zoom" action click events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionResetZoomEvent(this);
                }
                mFloorplan.resetPanRotationZoomMatrix();
                return true;
            case R.id.action_start_poi_controller:
                // Start POI Controller Fragment
                if (mFirebaseEventLogger != null) {
                    // Report "Start POI Controller" action click events to Firebase Analytics.
                    mFirebaseEventLogger.reportActionStartPoiController(this);
                }

                // Convert mKeyedPoi data structure from Map to List for easier (and appropriate)
                // querying.
                List<PointOfInterest> poiList = new ArrayList<>(mKeyedPoi.values());
                startPoiControllerFragment(poiList);

                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    private void startVoiceCommandsAction() {
        if (mFirebaseEventLogger != null) {
            // Report "Voice Command" action click events to Firebase Analytics.
            mFirebaseEventLogger.reportActionVoiceCommandEvent(this);
        }
        mVoiceCommands.startVoiceRecognition(mCurrentRobot, mKeyedPoi, mAnimations);
    }

    /**
     * Starts the POI Controller Fragment by replacing the FrameLayout "fragment_container" with
     * this fragment's contents.
     *
     * @param poiList The list of POIs in the current map.
     */
    private void startPoiControllerFragment(List<PointOfInterest> poiList) {
        // Add the Poi Controller fragment to the FragmentManager by replacing the layout
        // R.id.fragment_container with the fragment's contents.
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_container);

        if (fragment == null) {
            fragment = PoiControllerFragment.newInstance(poiList);
            fragmentManager.beginTransaction()
                    .addToBackStack(TRANSACTION_ADD_POI_CONTROLLER_FRAGMENT)
                    .replace(R.id.fragment_container, fragment, FRAGMENT_TAG_POI_CONTROLLER)
                    .commit();

            // Show Fragment Container
            mFragmentContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Upon receiving the POI clicked result from PoiControllerFragment, creates a new DrivePOIGoal
     * and sends the goal to firebase.
     *
     * @param poi
     */
    @Override
    public void onPoiControllerResult(PointOfInterest poi) {
        Toast.makeText(this,
                "Sending robot " + mCurrentRobot.getName() + " to POI: " + poi.variables.name,
                Toast.LENGTH_SHORT).show();
        DrivePOIGoal drivePOIGoal = new DrivePOIGoal(poi.uuid);
        mCloudHelper.sendPlannerGoalToRobot(drivePOIGoal, mCurrentRobot);
    }

    /**
     * Starts voice commands action for giving speech commands to the robot.
     *
     * Triggered on click event from PoiControllerFragment.
     */
    @Override
    public void onMicButtonClick() {
        startVoiceCommandsAction();
    }

    /**
     * Saves the current settings as fleet settings on the DB when the button is pressed
     *
     * @param v Current view
     */
    @SuppressWarnings("unused")
    public void onSaveFleetSettingsClick(View v) {
        if (mCloudHelper != null && mSettings != null) {
            if (mFirebaseEventLogger != null) {
                // Report "Save as Fleet" button click events to Firebase Analytics.
                mFirebaseEventLogger.reportSaveAsFleetButtonClickEvent(this);
            }
            mCloudHelper.setRobotFleetPreferences(mSettings.getRobotPreferencesData(this),
                    new CloudHelper.ResultListener<Boolean>() {
                        @Override
                        public void onResult(final Boolean result) {
                            String resultString = "Settings have been saved successfully.";
                            if (!result) {
                                resultString = "Settings haven't been saved.";
                            }
                            Toast.makeText(MainActivity.this, resultString,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    /**
     * Update settings from the data from Firebase.
     */
    private void updateRobotPreferencesFromDBFleet() {
        if (mCloudHelper != null && mSettings != null) {
            mCloudHelper.getRobotFleetPreferences(
                    new CloudHelper.ResultListener<RobotPreferences>() {
                        @Override
                        public void onResult(final RobotPreferences result) {
                            if (result != null) {
                                updateRobotConfigFromSettings(result);
                            }
                        }
                    });
        }
    }

    /**
     * Update preferences in memory of the robot setting shared preferences and the settings on
     * robot runner
     *
     * @param preferences preferences that are going to be used to update robot preferences
     */
    private void updateRobotConfigFromSettings(RobotPreferences preferences) {
        // Update shared preferences
        if (mSettings != null) {
            mSettings.setRobotPreferencesData(this, preferences);
        } else {
            return;
        }
    }

    /**
     * Prepare the Screen's options menu to be displayed..
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // If a robot is not selected yet, then disable cancel goals action.
        if (mCurrentRobot != null && mCurrentRobot.uuid != null) {
            menu.findItem(R.id.action_cancel_goals).setEnabled(true);
        } else {
            menu.findItem(R.id.action_cancel_goals).setEnabled(false);
        }
        return true;
    }

    /**
     * Allows the user to choose a robot to work with from all the ones listed under his account.
     */
    private void selectRobot() {
        // Hide buttons
        //mButtonsGroupRun.setVisibility(View.GONE);
        // Show loading spinner
        mLoadingProgressBar.setVisibility(View.VISIBLE);
        mFloorplan.setVisibility(View.GONE);
        // Request list of available robots
        Log.v(TAG, "Call function list robots");
        mCloudHelper.listRobots(new CloudHelper.ListResultsListener<Robot>() {
            @Override
            public void onResult(final List<Robot> results) {
                // Finish showing loading spinner
                mLoadingProgressBar.setVisibility(View.GONE);
                if (results.size() == 0) {
                    Log.e(TAG, "No robots found for this user");
                    addLogEntry(LOG_TAG_APP, "No robots found for this user!");
                    // TODO(adamantivm) Give the user some way to react to this
                } else {
                    synchronized (this) {

                        if (!mActivityActive) {
                            return;
                        }
                        if (mSelectRobotDialog != null) {
                            mSelectRobotDialog.dismiss();
                            mSelectRobotDialog = null;
                        }
                        Log.v(TAG, "List robots");
                        CharSequence robotNames[] = new CharSequence[results.size()];
                        // Verifies that the robot has a user selected name
                        int i = 0;
                        for (Robot robot : results) {
                            robotNames[i] = robot.getName();
                            i++;
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle("Pick a Robot");
                        builder.setItems(robotNames, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (mFirebaseEventLogger != null) {
                                    // Report "Robot Selected" events to Firebase Analytics.
                                    mFirebaseEventLogger.reportRobotSelectedEvent(
                                            getApplicationContext());
                                }
                                Robot robot = results.get(i);
                                if (robot.getName().contains(robot.uuid)) {
                                    Log.v(TAG, "Rename robot " + robot.getName());
                                    setNewRobotName(robot.uuid);
                                }
                                setSelectedRobot(robot);
                            }
                        });
                        mSelectRobotDialog = builder.show();
                    }
                }
            }
        });
    }

    /**
     * Allows the user to handle the maps from all the ones listed under his account.
     */
    private void manageMaps() {

        // Show very basic Dialog for the user to handle/create maps
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Manage maps:");

        if (mWorlds.isEmpty()) {
            Log.e(TAG, "No worlds found for this user");
            addLogEntry(LOG_TAG_APP, "No worlds found for this user!");
            Toast.makeText(this, "No worlds found for this user, please create one.",
                    Toast.LENGTH_SHORT).show();
        } else {
            final World[] worlds = mWorlds.toArray(new World[0]);
            final CharSequence[] worldNames = new CharSequence[worlds.length];
            for (int i = 0; i < worlds.length; i++) {
                worldNames[i] = worlds[i].getName();
            }
            builder.setItems(worldNames, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (mFirebaseEventLogger != null) {
                        // Report "Map Selected" events to Firebase Analytics.
                        mFirebaseEventLogger.reportMapSelectedEvent(getApplicationContext());
                    }
                    CloudConnector cc = mCloudConnector;
                    if (cc != null && 0 <= i && i < worldNames.length) {
                        cc.setRobotNavigationCommand(mCurrentRobot,
                                new NavigationStateCommand(worlds[i].getUuid(), false));
                    }
                }
            });
        }
        builder.setNeutralButton("Create new map", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                onStartMappingButtonClicked();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Allows the user to monitor the robot state.
     */
    // TODO(shokman) we should add a stop watching method
    private synchronized void watchRobot() {
        if (mRobotListener != null) {
            mCloudHelper.removeRobotListener(mRobotListener);
            mRobotListener = null;
        }
        // Get latest state of the robot
        mRobotListener = mCloudHelper.watchRobot(new CloudHelper.ResultListener<Robot>() {
            @Override
            public void onResult(final Robot result) {
                if (mCurrentRobot != null) {
                    // Make sure that the robot is the same that the user selected
                    if (result.uuid.equals(mCurrentRobot.uuid)) {
                        synchronized (this) {
                            // Add any new log messages to the mLogMessages store.
                            if (result.mLogMessages != null) {
                                for (Map.Entry<String, LogMessage> m : result.mLogMessages
                                        .entrySet()) {
                                    if (!mLogMessages.containsKey(m.getKey())
                                            && result.uuid.equals(mCurrentRobot.uuid)) {
                                        mLogMessages.put(m.getKey(), m.getValue());
                                        long globalTime = m.getValue().getTimestamp()
                                                - result.mLocalTime + result.mLastUpdateTime;
                                        addLogEntry(new Date(globalTime), LOG_TAG_ROBOT,
                                                m.getValue().getText());
                                    }
                                }
                            }
                            // Calculate the latest log message.
                            long latestMessage = 0;
                            for (LogMessage e : mLogMessages.values()) {
                                if (e.getTimestamp() > latestMessage) {
                                    latestMessage = e.getTimestamp();
                                }
                            }
                            // Remove really old messages
                            LinkedList<String> removeLogs = new LinkedList<>();
                            for (Map.Entry<String, LogMessage> m : mLogMessages.entrySet()) {
                                if (m.getValue().getTimestamp()
                                        < latestMessage - LOG_MESSAGE_TIMEOUT) {
                                    removeLogs.add(m.getKey());
                                }
                            }
                            for (String remove : removeLogs) {
                                mLogMessages.remove(remove);
                            }
                        }

                        synchronized (this) {
                            // Update robot's localization state in textview.
                            String robotLocalizedText = "Localized: " + result.localized;
                            mRobotLocalizedTextView.setText(robotLocalizedText);

                            if (result.tf != null) {
                                // Plot robot on the floorplan view
                                Transform tf = result.tf;
                                double t3 = 2.0 * ((tf.qw * tf.qz) + (tf.qx * tf.qy));
                                double t4 = 1.0 - (2.0 * ((tf.qy * tf.qy) + (tf.qz * tf.qz)));
                                float yaw = (float) Math.atan2(t3, t4);
                                mFloorplan.updateCameraMatrix((float) tf.px, (float) tf.py, yaw);

                                // Update robot's position
                                mCurrentRobot.tf = tf;
                                mVirtualJoystickView.onRobotPositionUpdate(tf);
                            }

                            // Update the battery status
                            if (mCurrentRobot.batteries != null) {
                                BatteryStatus mBattery = mCurrentRobot.batteries.get(BASE_BATTERY);
                                if (mBattery != null) {
                                    // Get the percentage of available battery from mCurrentRobot
                                    double dBatteryPercentage = mBattery.getPercentage();
                                    // Convert to int in order to be managed by a progress bar
                                    int iBatteryPercentage = (int) (dBatteryPercentage * 100);
                                    // Setting the progress
                                    mBatteryIndicatorProgressBar.setProgress(iBatteryPercentage);
                                    // The color of the bar will change depending on the value
                                    if (dBatteryPercentage <= mBattery.getCriticalPercentage()) {
                                        mBatteryIndicatorProgressBar.setProgressTintList(
                                                ColorStateList.valueOf(Color.RED));
                                    } else if (dBatteryPercentage <= mBattery.getLowPercentage()) {
                                        mBatteryIndicatorProgressBar.setProgressTintList(
                                                ColorStateList.valueOf(Color.YELLOW));
                                    } else {
                                        // Otherwise is green
                                        mBatteryIndicatorProgressBar.setProgressTintList(
                                                ColorStateList.valueOf(Color.GREEN));
                                    }
                                }
                            }

                            // Update robot's path
                            mCurrentRobot.path = result.path;
                            List<Transform> newPath = new ArrayList<>();

                            if (mCurrentRobot.path != null) {
                                for (int i = 0; i < mCurrentRobot.path.size(); i++) {
                                    newPath.add(mCurrentRobot.path.get("goal_" + Integer.toString(i)));
                                }
                            }
                            mFloorplan.updateRobotPath(newPath);

                            if (!Strings.compare(mCurrentRobot.map, result.map)) {
                                // If the map is different from the previous map.
                                mCurrentRobot.map = result.map;
                                if (mCurrentRobot.map != null) {
                                    // Get the map
                                    getCurrentMap();
                                    // Update list of POIs
                                    updatePOIList();
                                }
                                // Update list of active goals
                                updateActiveGoalsList();
                            }
                            mCurrentRobot.mMappingRunId = result.mMappingRunId;
                        }
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        mWebRTCView.setCallRobot(user == null ? null : user.getUid(),
                                mCurrentRobot);
                    }
                }
            }
        }, mCurrentRobot);
    }

    /**
     * Adds a ping time listener for checking the current robot's ping time.
     * Useful for determining if the RobotApp on the robot is active or hanging/crashed.
     */
    private void watchPingTimeForRobot() {
        // Remove previous ping time listener, if it exists.
        if (mPingTimeListener != null) {
            mCloudHelper.removePingTimeListener(mPingTimeListener, mCurrentRobot.uuid);
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
                }, mCurrentRobot.uuid);
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
        Log.d(LOG_TAG_PING_TIME, getString(R.string.log_debug_timestampmanager_not_synchronized));
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
        Log.v(LOG_TAG_PING_TIME, "Latency: " + latency);
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
        Log.d(LOG_TAG_PING_TIME, "Latency was " + latency + " but expected positive. "
                + "Re-synchronizing TimestampManager.");
        TimestampManager.synchronize();
        return false;
    }

    /**
     * Subscribe to available worlds for the user
     */
    private synchronized void watchWorlds() {
        stopWatchingWorlds();
        // Get latest state of the worlds
        mWorldListener = mCloudHelper.watchWorlds(new CloudHelper.ListResultsListener<World>() {
            @Override
            public void onResult(final List<World> result) {
                if (result != null) {
                    synchronized (this) {
                        mWorlds = new ArrayList<>(result);
                    }
                }
            }
        });
    }

    // Stop watching the worlds
    private synchronized void stopWatchingWorlds() {
        if (mWorldListener != null) {
            mCloudHelper.removeWorldListener(mWorldListener);
            mWorldListener = null;
        }
    }

    /**
     * Updates list of POIs getting data from Firebase.
     */
    private void updatePOIList() {
        // First remove the listener if the user switched robots.
        if (mPOIListener != null) {
            mCloudHelper.removePOIListener(mPOIListener, mCurrentRobot);
        }

        // Add a new listener for the current robot.
        mPOIListener = mCloudHelper.addPOIListener(
                new CloudHelper.ListResultsListener<PointOfInterest>() {
                    @Override
                    public void onResult(final List<PointOfInterest> result) {
                        if (mCurrentRobot != null) {
                            // Clean current list of POIs.
                            mKeyedPoi.clear();
                            mPoiKeys.clear();

                            // Filter valid POIs from result
                            List<PointOfInterest> validPOIs = new ArrayList<>();
                            for (PointOfInterest poi : result) {
                                boolean isValid = PointOfInterestValidator.areAllFieldsValid(poi);
                                if (isValid) {
                                    Log.i(TAG, "All fields for POI " + poi.uuid + " were valid. " +
                                            "Adding to POI collections.");
                                    validPOIs.add(poi);
                                } else {
                                    Log.w(TAG, "The POI " + poi.uuid + " contains some invalid fields. " +
                                            "Rejected!");
                                }
                            }

                            // Update POI collection objects, companion's floor plan, and
                            // PoiControllerFragment's list of POIs
                            mFloorplan.setPOIs(validPOIs);
                            for (int i = 0; i < validPOIs.size(); i++) {
                                mKeyedPoi.put(validPOIs.get(i).variables.name, validPOIs.get(i));
                                addPOIToList(validPOIs.get(i).variables.name);
                            }

                            updatePoiControllerFragmentPoiList(validPOIs);
                        }
                    }
                }, mCurrentRobot);
    }

    /**
     * Updates list of POIs in PoiControllerFragment.
     *
     * @param validPOIs The list of newly updated, valid POIs from Firebase.
     */
    private void updatePoiControllerFragmentPoiList(List<PointOfInterest> validPOIs) {
        PoiControllerFragment poiControllerFragment =
                (PoiControllerFragment) getSupportFragmentManager()
                        .findFragmentByTag(FRAGMENT_TAG_POI_CONTROLLER);

        if (poiControllerFragment != null) {
            poiControllerFragment.updatePoiList(validPOIs);
        }
    }

    /**
     * Updates list of sounds by getting data from Firebase.
     */
    private void updateSoundList() {
        // First remove the listener if the user switched robots.
        if (mSoundListener != null) {
            mCloudHelper.removeSoundListener(mSoundListener);
        }
        // First remove the listener if the user switched robots.
        if (mGlobalSoundListener != null) {
            mCloudHelper.removeGlobalSoundListener(mGlobalSoundListener);
        }
        // Add a new listener for the current robot.
        mSoundListener = mCloudHelper.addSoundListener(
                new CloudHelper.ListResultsListener<SoundInfo>() {
                    @Override
                    public void onResult(final List<SoundInfo> result) {
                        synchronized (mGlobalSounds) {
                            mLocalSounds.clear();
                            mLocalSounds.addAll(result);
                            onSoundsListUpdate();
                        }
                    }
                });
        // Add a new listener for the current robot.
        mGlobalSoundListener = mCloudHelper.addGlobalSoundListener(
                new CloudHelper.ListResultsListener<SoundInfo>() {
                    @Override
                    public void onResult(final List<SoundInfo> result) {
                        synchronized (mGlobalSounds) {
                            mGlobalSounds.clear();
                            mGlobalSounds.addAll(result);
                            onSoundsListUpdate();
                        }
                    }
                });
    }

    /**
     * Called when the sounds are updated.
     */
    private void onSoundsListUpdate() {
        synchronized (mGlobalSounds) {
            HashMap<String, SoundInfo> map = new HashMap<>();
            for (SoundInfo s : mGlobalSounds) {
                map.put(s.getId(), s);
            }
            for (SoundInfo s : mLocalSounds) {
                map.put(s.getId(), s);
            }
            ArrayList<SoundInfo> results = new ArrayList<>(map.values());
            Collections.sort(results);
            ArrayAdapter<SoundInfo> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_list_item_1, results);
            mListViewSounds.setAdapter(adapter);
        }
    }

    /**
     * Updates list of animations by getting data from Firebase.
     */
    private void updateAnimationList() {
        // First remove the listener if the user switched robots.
        if (mAnimationListener != null) {
            mCloudHelper.removeAnimationListener(mAnimationRobot, mAnimationListener);
        }

        String robot = null;
        Robot cRobot = mCurrentRobot;
        if (cRobot != null) {
            robot = cRobot.uuid;
        }
        if (robot == null) {
            return;
        }

        mAnimationRobot = robot;

        // Add a new listener for the current robot.
        mAnimationListener = mCloudHelper.addAnimationListener(robot,
                new CloudHelper.ListResultsListener<AnimationInfo>() {
                    @Override
                    public void onResult(final List<AnimationInfo> result) {
                        ArrayList<AnimationInfo> results = new ArrayList<>(result);
                        Collections.sort(results);

                        // Copy the animations to a list
                        for (AnimationInfo animation : results) {
                            mAnimations.add(animation.getName().toLowerCase());
                        }

                        ArrayAdapter<AnimationInfo> adapter = new ArrayAdapter<>(
                                MainActivity.this,
                                android.R.layout.simple_list_item_1,
                                results);
                        mListViewAnims.setAdapter(adapter);
                    }
                });
    }

    /**
     * Adds a POI to the list and updates the list view.
     *
     * @param name POI name.
     */
    private void addPOIToList(String name) {
        mPoiKeys.add(name);
        mPoiListAdapter.notifyDataSetChanged();
    }

    /**
     * Updates list of active goals.
     */
    private void updateActiveGoalsList() {
        // First remove the listener if the user switched robots.
        if (mActiveGoalsListener != null) {
            mCloudHelper.removeActiveGoalListener(mActiveGoalsListener, mCurrentRobot);
        }

        // Add a new listener for the current robot.
        mActiveGoalsListener = mCloudHelper.addActiveGoalListener(
                new CloudHelper.ListResultsListener<PlannerGoal>() {
                    @Override
                    public void onResult(final List<PlannerGoal> result) {
                        if (mCurrentRobot != null) {
                            mFloorplan.setActiveGoals(result);
                        }
                    }
                }, mCurrentRobot);
    }

    /**
     * Updates robot metadata.
     */
    private void updateMetadata() {
        // First remove the listener if the user switched robots.
        if (mMetadataListener != null) {
            mCloudHelper.removeMetadataListener(mMetadataListener, mCurrentRobot);
        }

        // Add a new metadata listener for the current robot.
        mMetadataListener = mCloudHelper.addMetadataListener(
                new CloudHelper.ResultListener<RobotMetadata>() {
                    @Override
                    public void onResult(final RobotMetadata result) {
                        if (mCurrentRobot != null) {
                            mRobotMetadata = result;
                            String name = result.getRobotName();
                            if (name != null) {
                                mCurrentRobot.name = name;
                                mCurrentRobotTextView.setText(name);
                            }
                        }
                    }
                }, mCurrentRobot);
    }

    /**
     * Inserts a line to the client-side application log.
     */
    private void addLogEntry(String source, String line) {
        addLogEntry(new Date(), source, line);
    }

    /**
     * Inserts a line to the client-side application log.
     */
    private void addLogEntry(Date date, String source, String line) {
        CharSequence chars = mStatusTextView.getText();

        mStatusTextView.setText("* " + source + " " + LOG_DATE_FORMAT.format(date)
                + ":   " + line + "\n" + chars);
    }

    /**
     * Sets the given robot as the currently selected robot and updates the UI accordingly.
     */
    private void setSelectedRobot(Robot robot) {
        // If we only have a single robot, the choice is made!
        mCurrentRobot = robot;

        // Set selected robot name.
        mCurrentRobotTextView.setText(mCurrentRobot.getName());

        // Show goal buttons
        mButtonsGroup.setVisibility(View.VISIBLE);

        // Set the teleop robot
        mVirtualJoystickView.setRobot(mCurrentRobot);

        addLogEntry(LOG_TAG_APP, "Robot selected: [" + robot.getName() + "]");

        synchronized (mLogMessages) {
            mLogMessages.clear();
        }

        // Start to look for the robot state
        watchRobot();

        // Listen for updates to ping time on cloud.
        watchPingTimeForRobot();

        // Update metadata for current settings
        updateMetadata();

        if (mCurrentRobot.map != null) {
            // Get the map
            getCurrentMap();

            // Update list of active goals
            updateActiveGoalsList();

            // Update list of POIs
            updatePOIList();
        }

        // Update the sound list
        updateSoundList();

        // Update the animations list
        updateAnimationList();

        // Configure buttons
        mButtonSaveMap.setVisibility(View.GONE); // Set mapping to false at the beginning
        mButtonRunning.setVisibility(View.VISIBLE); // Show running button
        setShowDebugStatus(false);      // By default don't show the button
        setShowPOIList(false);          // By default don't show the button
        setShowTeleop(false);           // By default don't show the teleop
        setShowSoundList(false);        // By default don't show the sounds
        setShowAnimList(false);         // By default don't show the anims
        setShowVideoView(false);        // By default don't show the video
        resetActiveGoalsPreference();   // By default uncheck active goals preference
        // TODO: update state of executive mode button according to current executive mode.

        // Set the call robot.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        mWebRTCView.setCallRobot(user == null ? null : user.getUid(), mCurrentRobot);

        // Set the cloud connector robot
        CloudConnector cc = mCloudConnector;
        if (cc != null) {
            cc.updateWithRobot(mCloudHelper.getCurrentUserid(), mCurrentRobot);
        }
    }

    /**
     * Get the map that the robot is currently using.
     */
    private void getCurrentMap() {

        final String mapDirectory = getFilesDir() + "/";
        final String mapFileName = mCurrentRobot.map + ".dat";
        final String mapCompletePath = mapDirectory + mapFileName;

        Log.d(TAG, "Downloading " + mCurrentRobot.map + " map for user " +
                mCloudHelper.getCurrentUserid() + " to " + mapDirectory + mapFileName);

        mCloudHelper.downloadMap(mCurrentRobot.map, mapCompletePath,
                new Runnable() {
                    public void run() {
                        FileInputStream fs;
                        try {
                            fs = openFileInput(mapFileName);
                        } catch (FileNotFoundException e) {
                            Log.e(TAG, "Error loading detailed world uuid " + mCurrentRobot.map +
                                    " - data file does not exist", e);
                            //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
                            throw new Error("Could not open world dat file");
                        }
                        // TODO(shokman): it should get the name of the map using the local
                        // copy on available user maps instead of using "current_map"
                        DetailedWorld ourWorld = DetailedWorld.loadDetailedWorldFromInputStream(
                                mCurrentRobot.map, "current_map", fs,
                                mRobotMetadata == null ? null : mRobotMetadata.getSmootherParams());
                        if (ourWorld != null) {
                            getCameraPoseFromFirebase();
                            mFloorplan.setFloorplan(ourWorld);
                            mFloorplan.updateMarkers();
                            mFloorplan.setRobot(mCurrentRobot);
                            mFloorplan.setVisibility(View.VISIBLE);
                        } else {
                            Log.d(TAG, "Can't download " + mCurrentRobot.map);
                            throw new Error("Could not read world dat file");
                        }
                        Log.d(TAG, "Finished downloading map " + mCurrentRobot.map);
                    }
                });
    }

    /**
     * Get camera position from firebase
     */
    private void getCameraPoseFromFirebase() {
        if (mCurrentRobot != null && mCurrentRobot.map != null) {
            mCloudHelper.getMapOrientation(
                    new CloudHelper.ResultListener<ai.cellbots.common.data.Transform>() {
                        @Override
                        public void onResult(final ai.cellbots.common.data.Transform result) {
                            mFloorplan.setPanRotationZoomMatrix(result);
                        }
                    }, mCurrentRobot.map);
        }
    }

    /**
     * Handle mapping button clicked event
     */
    public void onStartMappingButtonClicked() {
        if (mFirebaseEventLogger != null) {
            // Report "Start Mapping" button click events to Firebase Analytics.
            mFirebaseEventLogger.reportStartMappingButtonClickEvent(getApplicationContext());
        }
        if (!mMapping) {
            setMappingView(true);
            CloudConnector cc = mCloudConnector;
            if (cc != null) {
                cc.setRobotNavigationCommand(mCurrentRobot,
                        new NavigationStateCommand(null, true));
            }
        }
    }

    /**
     * Handle save map button clicked event
     */
    public void onSaveMapButtonClicked(View view) {
        if (mFirebaseEventLogger != null) {
            // Report "Save Map" button click events to Firebase Analytics.
            mFirebaseEventLogger.reportSaveMapButtonClickEvent(getApplicationContext());
        }
        // Open a dialog to enter the map name desired.
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                MainActivity.this);

        final EditText input = new EditText(MainActivity.this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setTitle("Enter Map Name").setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String mapName = input.getText().toString();
                if (!mapName.isEmpty()) {
                    setMappingView(false);
                    CloudConnector cc = mCloudConnector;
                    if (cc != null) {
                        cc.saveRobotMap(mCurrentRobot, mapName);
                    }
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                setMappingView(false);
            }
        });

        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                setMappingView(false);
            }
        });

        builder.show();
        mButtonSaveMap.setVisibility(View.GONE);
    }

    /**
     * Handle running button clicked event
     */
    public synchronized void onRunningButtonClicked(final View view) {
        final Integer isRunning = (Integer) view.getTag();
        if (mCloudConnector == null) {
            return;
        }
        if (isRunning == null || isRunning == 0) {
            if (mFirebaseEventLogger != null) {
                // Report "Play" button click events to Firebase Analytics.
                mFirebaseEventLogger.reportPlayButtonClickEvent(getApplicationContext());
            }
            mCloudConnector.setRobotSpeedCommand(mCurrentRobot,
                    new SpeedStateCommand(true));
        } else {
            if (mFirebaseEventLogger != null) {
                // Report "Pause" button click events to Firebase Analytics.
                mFirebaseEventLogger.reportPauseButtonClickEvent(getApplicationContext());
            }
            mCloudConnector.setRobotSpeedCommand(mCurrentRobot,
                    new SpeedStateCommand(false));
        }
    }

    /**
     * Handle executive mode button clicked event
     */
    public synchronized void onExecutiveModeButtonClicked(final View view) {
        Object tag = view.getTag();
        final ExecutiveStateCommand.ExecutiveState mode
                = tag == null ? null : (ExecutiveStateCommand.ExecutiveState) tag;
        if (mCloudConnector == null) {
            return;
        }
        if (mode == null || mode == ExecutiveStateCommand.ExecutiveState.STOP) {
            if (mFirebaseEventLogger != null) {
                // Report "Executive Random Mode" button click events to Firebase Analytics.
                mFirebaseEventLogger.reportExecutiveModeRandomButtonClickEvent(getApplicationContext());
            }
            mCloudConnector.setRobotExecutiveCommand(mCurrentRobot,
                    new ExecutiveStateCommand(ExecutiveStateCommand.ExecutiveState.RANDOM_DRIVER));
        } else {
            if (mFirebaseEventLogger != null) {
                // Report "Executive Stop Mode" button click events to Firebase Analytics.
                mFirebaseEventLogger.reportExecutiveModeStopButtonClickEvent(getApplicationContext());
            }
            mCloudConnector.setRobotExecutiveCommand(mCurrentRobot,
                    new ExecutiveStateCommand(ExecutiveStateCommand.ExecutiveState.STOP));
        }
    }

    /**
     * Handle Hide/Show button click event
     */
    public void onHideShowDebugButtonClicked(View view) {
        if (mFirebaseEventLogger != null) {
            // Report "Log" button click events to Firebase Analytics.
            mFirebaseEventLogger.reportLogButtonClickEvent(getApplicationContext());
        }
        final int show = (Integer) view.getTag();
        if (show == 0) {
            setShowDebugStatus(true);
        } else {
            setShowDebugStatus(false);
        }
    }

    /**
     * Handle Hide/Show button click event
     */
    public void onHideShowPOIsButtonClicked(View view) {
        if (mFirebaseEventLogger != null) {
            // Report "POIs" button click events to Firebase Analytics.
            mFirebaseEventLogger.reportPOIsButtonClickEvent(getApplicationContext());
        }
        final int show = (Integer) view.getTag();
        if (show == 0) {
            setShowPOIList(true);
        } else {
            setShowPOIList(false);
        }
    }

    /**
     * Handle Hide/Show button click event
     */
    public void onHideShowTeleopButtonClicked(View view) {
        if (mFirebaseEventLogger != null) {
            // Report "Teleop" button click events to Firebase Analytics.
            mFirebaseEventLogger.reportTeleopButtonClickEvent(getApplicationContext());
        }
        final int show = (Integer) view.getTag();
        if (show == 0) {
            setShowTeleop(true);
        } else {
            setShowTeleop(false);
        }
    }

    /**
     * Handle Hide/Show button click event
     */
    public void onHideShowSoundsButtonClicked(View view) {
        if (mFirebaseEventLogger != null) {
            // Report "Sounds" button click events to Firebase Analytics.
            mFirebaseEventLogger.reportSoundsButtonClickEvent(getApplicationContext());
        }
        final int show = (Integer) view.getTag();
        if (show == 0) {
            setShowSoundList(true);
        } else {
            setShowSoundList(false);
        }
    }

    /**
     * Handle Hide/Show button click event
     */
    public void onHideShowAnimationsButtonClicked(View view) {
        if (mFirebaseEventLogger != null) {
            // Report "Animations" button click events to Firebase Analytics.
            mFirebaseEventLogger.reportAnimationsButtonClickEvent(getApplicationContext());
        }
        final int show = (Integer) view.getTag();
        if (show == 0) {
            setShowAnimList(true);
        } else {
            setShowAnimList(false);
        }
    }

    /**
     * Handle Hide/Show button click event
     */
    public void onHideShowVideoButtonClicked(View view) {
        if (mFirebaseEventLogger != null) {
            // Report "Video" button click events to Firebase Analytics.
            mFirebaseEventLogger.reportVideoButtonClickEvent(getApplicationContext());
        }
        final int show = (Integer) view.getTag();
        if (show == 0) {
            setShowVideoView(true);
        } else {
            setShowVideoView(false);
        }
    }

    public void setShowDebugStatus(boolean status) {
        if (status) {
            mButtonShowDebug.setTag(1);
            mStatusTextViewScroll.setVisibility(View.VISIBLE);
            mButtonShowDebug.setPressed(true);
        } else {
            mButtonShowDebug.setTag(0);
            mStatusTextViewScroll.setVisibility(View.GONE);
            mButtonShowDebug.setPressed(false);
        }
    }

    public void setShowPOIList(boolean status) {
        if (status) {
            mButtonShowPOIs.setTag(1);
            mListViewPOIs.setVisibility(View.VISIBLE);
            mFloorplan.drawAllPOIs(true);
            mButtonShowPOIs.setPressed(true);
        } else {
            mButtonShowPOIs.setTag(0);
            mListViewPOIs.setVisibility(View.GONE);
            mFloorplan.drawAllPOIs(false);
            mButtonShowPOIs.setPressed(false);
        }
    }

    public void setShowAnimList(boolean status) {
        if (status) {
            mButtonShowAnims.setTag(1);
            mListViewAnims.setVisibility(View.VISIBLE);
            mButtonShowAnims.setPressed(true);
        } else {
            mButtonShowAnims.setTag(0);
            mListViewAnims.setVisibility(View.GONE);
            mButtonShowAnims.setPressed(false);
        }
    }

    public void setShowVideoView(boolean status) {
        if (status && mActivityActive) {
            mButtonShowVideo.setTag(1);
            mWebRTCView.setVisibility(View.VISIBLE);
            mButtonShowVideo.setPressed(true);
        } else {
            mButtonShowVideo.setTag(0);
            mWebRTCView.setVisibility(View.GONE);
            mButtonShowVideo.setPressed(false);
        }
    }

    public void setShowSoundList(boolean status) {
        if (status) {
            mButtonShowSounds.setTag(1);
            mListViewSounds.setVisibility(View.VISIBLE);
            mButtonShowSounds.setPressed(true);
        } else {
            mButtonShowSounds.setTag(0);
            mListViewSounds.setVisibility(View.GONE);
            mButtonShowSounds.setPressed(false);
        }
    }

    public void setShowTeleop(boolean status) {
        if (status) {
            mButtonShowTeleop.setTag(1);
            mTeleopView.setVisibility(View.VISIBLE);
            mButtonShowTeleop.setPressed(true);
        } else {
            mButtonShowTeleop.setTag(0);
            mTeleopView.setVisibility(View.GONE);
            mButtonShowTeleop.setPressed(false);
        }
    }

    public void setMappingView(boolean status) {
        if (status) {
            mButtonSaveMap.setVisibility(View.VISIBLE);
            mButtonRunning.setVisibility(View.GONE);
            setShowTeleop(true);
            mFloorplan.setVisibility(View.GONE);
            mMapping = true;
        } else {
            mButtonSaveMap.setVisibility(View.GONE);
            mButtonRunning.setVisibility(View.VISIBLE);
            mFloorplan.setVisibility(View.VISIBLE);
            mMapping = false;
            setShowTeleop(false);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_map_label, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.DriveGoal:
                if (mFirebaseEventLogger != null) {
                    // Report "Add Drive Goal" events to Firebase Analytics.
                    mFirebaseEventLogger.reportAddDriveGoalEvent(getApplicationContext());
                }
                // Draw marker and send goal to Firebase.
                mFloorplan.addDriveGoal();
                return true;
            case R.id.PointOfInterest:
                if (mFirebaseEventLogger != null) {
                    // Report an "Add POI Option Selected" to Firebase Analytics.
                    mFirebaseEventLogger.reportAddPOIRequest(this);
                }
                // Open a dialog to enter the name of the point of interest.
                final EditText input = new EditText(MainActivity.this);
                final AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Enter Point of Interest Name")
                        .setView(input)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // Intentionally left blank due to this method's automatic dismiss()
                                // call for the AlertDialog. onClick method overridden below has
                                // more control on the dialog's dismissal behavior.
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int i) {
                                mFloorplan.drawSingleMarker(false);
                                dialog.cancel();
                            }
                        })
                        .create();

                dialog.show();
                // Manually override the dialog's positive button onClickListener.
                // This onClick method prevents the dialog from automatically closing when the user
                // clicks the "OK" button without setting a valid POI name.
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                final String poiName = input.getText().toString();

                                // Check if poi name is null or empty
                                if (!TextUtils.isEmpty(poiName)) {
                                    if (mFirebaseEventLogger != null) {
                                        // Report "POI Added" events to Firebase Analytics.
                                        mFirebaseEventLogger.reportPOIAddedEvent(
                                                getApplicationContext());
                                    }
                                    // Draw marker and save POI in the cloud.
                                    addLogEntry(LOG_TAG_APP, "POI LABEL: " + poiName);
                                    mFloorplan.addPOI(poiName);
                                    dialog.dismiss();
                                } else {
                                    // POI Name is null or empty
                                    input.setError(
                                            getString(R.string.error_poi_name_cannot_be_empty));
                                }
                            }
                        });
                return true;
            case R.id.patrolDriverGoal:
                if (mFirebaseEventLogger != null) {
                    // Report "Add Patrol Goal Option Selected" event to Firebase Analytics.
                    mFirebaseEventLogger.reportAddPatrolGoalRequest(this);
                }
                android.app.AlertDialog.Builder timePatrolBuilder =
                        new android.app.AlertDialog.Builder(
                                MainActivity.this);
                final EditText timePatrolInput = new EditText(MainActivity.this);
                timePatrolInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                timePatrolInput.setHint(String.valueOf(PATROL_GOAL_DEFAULT_DURATION_SEC));
                timePatrolBuilder.setTitle("Time to patrol around point [s]:")
                        .setView(timePatrolInput);

                timePatrolBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mFirebaseEventLogger != null) {
                            // Report "Patrol Goal Added" events to Firebase Analytics.
                            mFirebaseEventLogger.reportPatrolGoalAddedEvent(getApplicationContext());
                        }
                        final String time = timePatrolInput.getText().toString();
                        if (!time.isEmpty()) {
                            // Draw marker and send patrolDriver goal to Firebase.
                            mFloorplan.addPatrolDriverGoal(Long.valueOf(time));
                        } else {
                            mFloorplan.addPatrolDriverGoal(PATROL_GOAL_DEFAULT_DURATION_SEC);
                        }
                    }
                });

                timePatrolBuilder.setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });

                timePatrolBuilder.show();
                return true;
            case R.id.SpotClean:
                if (mFirebaseEventLogger != null) {
                    // Report "Add Spot Clean Option Selected" event to Firebase Analytics.
                    mFirebaseEventLogger.reportAddSpotCleanRequest(this);
                }
                android.app.AlertDialog.Builder timeBuilder = new android.app.AlertDialog.Builder(
                        MainActivity.this);
                final EditText timeInput = new EditText(MainActivity.this);
                timeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
                timeInput.setHint(String.valueOf(CLEAN_GOAL_DEFAULT_DURATION_SEC));
                timeBuilder.setTitle("Time to perform cleaning [s]:").setView(timeInput);

                timeBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mFirebaseEventLogger != null) {
                            // Report "Add Spot Clean Goal" event to Firebase Analytics.
                            mFirebaseEventLogger.reportSpotCleanGoalAddedEvent(getApplicationContext());
                        }
                        final String time = timeInput.getText().toString();
                        if (!time.isEmpty()) {
                            // Draw marker and save POI in Firebase.
                            mFloorplan.addCleanGoal(Long.valueOf(time));
                        } else {
                            mFloorplan.addCleanGoal(CLEAN_GOAL_DEFAULT_DURATION_SEC);
                        }
                    }
                });

                timeBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                timeBuilder.show();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    public void showContextMenu() {
        // Erase previous markers.
        mFloorplan.drawSingleMarker(false);
        openContextMenu(findViewById(R.id.floorplan));
    }

    /**
     * Handles click on a on a ListView. Depending on the view it either:
     * - In the case of a POI, it highlights selected POI both on the list and on the map.
     * - In the case of a sound, it feeds that SoundInfo object to the currently selected robot.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mListViewPOIs) {
            TextView textViewItem = view.findViewById(R.id.textViewItem);

            // get the clicked item name
            String listItemText = textViewItem.getText().toString();

            // Draw all POIs
            mFloorplan.drawAllPOIs(true);

            // Add a new listener for the current robot.
            mCloudHelper.addSinglePOIListener(
                    new CloudHelper.ResultListener<PointOfInterest>() {
                        @Override
                        public void onResult(final PointOfInterest result) {
                            // Highlight single POI
                            mFloorplan.drawDrivePOIGoal(result);
                        }
                    }, mKeyedPoi.get(listItemText).uuid, mCurrentRobot.map);
        } else if (parent == mListViewSounds) {
            Object adapter = parent.getAdapter();
            Robot r = mCurrentRobot;
            if (adapter != null && adapter instanceof ArrayAdapter && r != null) {
                ArrayAdapter arrayAdapter = (ArrayAdapter) adapter;
                if (arrayAdapter.getCount() > position) {
                    Object item = parent.getAdapter().getItem(position);
                    if (item instanceof SoundInfo) {
                        mCloudHelper.setPlayingSound((SoundInfo) item, r);
                    } else {
                        Log.e(TAG, "Sound array item not instance of SoundInfo");
                    }
                } else {
                    Log.d(TAG, "Ignore sound over the adapter size");
                }
            }
        } else if (parent == mListViewAnims) {
            Object adapter = parent.getAdapter();
            Robot r = mCurrentRobot;
            if (adapter != null && adapter instanceof ArrayAdapter && r != null) {
                ArrayAdapter arrayAdapter = (ArrayAdapter) adapter;
                if (arrayAdapter.getCount() > position) {
                    Object item = parent.getAdapter().getItem(position);
                    if (item instanceof AnimationInfo) {
                        mCloudHelper.sendPlannerGoalToRobot(new AnimationGoal(
                                (AnimationInfo) item), mCurrentRobot);
                    } else {
                        Log.e(TAG, "Sound array item not instance of AnimationInfo");
                    }
                } else {
                    Log.d(TAG, "Ignore animation over the adapter size");
                }
            }
        }
    }

    /**
     * Handles long click on a POI from the displayed list. The long clicked POI will be sent as
     * a goal to Firebase.
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (mFirebaseEventLogger != null) {
            // Report "Drive To POI" event to Firebase Analytics.
            mFirebaseEventLogger.reportDriveToPOIEvent(this);
        }

        TextView textViewItem = view.findViewById(R.id.textViewItem);

        // Get the clicked item name
        String listItemText = textViewItem.getText().toString();

        // Erase all POIs from the map
        mFloorplan.drawAllPOIs(false);

        // Add a new listener for the current robot.
        mCloudHelper.addSinglePOIListener(
                new CloudHelper.ResultListener<PointOfInterest>() {
                    @Override
                    public void onResult(final PointOfInterest result) {
                        // Draw only the goal on the map.
                        mFloorplan.drawDrivePOIGoal(result);
                    }
                }, mKeyedPoi.get(listItemText).uuid, mCurrentRobot.map);

        // Send the POI name to Firebase
        mCloudHelper.sendPlannerGoalToRobot(new DrivePOIGoal(mKeyedPoi.get(listItemText).uuid),
                mCurrentRobot);

        // Tell the user that we are driving to the selected POI.
        Toast.makeText(this, "Going to POI: " + listItemText, Toast.LENGTH_SHORT).show();

        return true;
    }

    /**
     * Draw or not active goals on the map according to the robot_preferences selected.
     *
     * @param preference for showing active goals.
     * @param newValue   for the preference checkbox.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(getString(R.string.pref_key_show_active_goals))) {
            mFloorplan.drawActiveGoals((boolean) newValue);
        } else if (preference.getKey().equals(getString(R.string.pref_key_show_original_path))) {
            mFloorplan.drawOriginalPath((boolean) newValue);
        }
        return true;
    }

    /**
     * Reset active goals preference to unchecked.
     */
    public void resetActiveGoalsPreference() {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putBoolean(getString(R.string.pref_key_show_active_goals), true);
        editor.putBoolean(getString(R.string.pref_key_show_original_path), false);
        editor.commit();
    }

    /**
     * Deletes a POI given its name.
     *
     * @param name of POI to delete.
     */
    public void deletePOI(String name) {
        if (mFirebaseEventLogger != null) {
            // Report "Delete POI" events to Firebase Analytics.
            mFirebaseEventLogger.reportDeletePOIEvent(this);
        }
        // Get POI uuid, previously stored in a map.
        String ObjectUuid = mKeyedPoi.get(name).uuid;
        // If POI to delete is the last one, then empty the list.
        if (mPoiKeys.size() == 1) {
            mPoiKeys.clear();
            mKeyedPoi.clear();
            mPoiListAdapter.notifyDataSetChanged();
        }
        // Let the cloud helper handle the POI deletion in firebase.
        mCloudHelper.deletePOI(ObjectUuid, mCurrentRobot.map);
    }

    /**
     * Patrols around a POI given its name.
     *
     * @param name of POI to patrol.
     */
    public void patrolPOI(final String name) {
        // Get POI uuid, previously stored in a map.
        final String ObjectUuid = mKeyedPoi.get(name).uuid;

        android.app.AlertDialog.Builder timePatrolBuilder = new android.app.AlertDialog.Builder(
                MainActivity.this);
        final EditText timePatrolInput = new EditText(MainActivity.this);
        timePatrolInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        timePatrolInput.setHint(String.valueOf("100"));
        timePatrolBuilder.setTitle("Time to patrol around POI:").setView(timePatrolInput);

        timePatrolBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mFirebaseEventLogger != null) {
                    // Report "Patrol POI" event to Firebase Analytics.
                    mFirebaseEventLogger.reportPatrolPOIEvent(getApplicationContext());
                }
                final String time = timePatrolInput.getText().toString();
                if (!time.isEmpty()) {
                    // Let the cloud helper handle the POI patrol in Firebase.
                    mCloudHelper.sendPlannerGoalToRobot(
                            new PatrolDriverPOIGoal(ObjectUuid, Long.valueOf(time)),
                            mCurrentRobot);
                } else {
                    mCloudHelper.sendPlannerGoalToRobot(
                            new PatrolDriverPOIGoal(ObjectUuid, PATROL_GOAL_DEFAULT_DURATION_SEC),
                            mCurrentRobot);
                }
                Toast.makeText(MainActivity.this, "Patrolling POI: " + name, Toast.LENGTH_SHORT)
                        .show();
            }
        });

        timePatrolBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        timePatrolBuilder.show();
    }

    /**
     * Handler launched after Google sign in
     *
     * @param requestCode RC_SIGN_IN
     * @param resultCode  resulted state of the sign in process
     * @param data        user credential
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                if (mFirebaseEventLogger != null) {
                    // Report "User Sign In" event to Firebase Analytics.
                    mFirebaseEventLogger.reportSignInEvent(this);
                }
                // Sign-in succeeded, set up the UI
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                // Sign in was canceled by the user, finish the activity
                Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * Handle events when back button on phone is pressed.
     */
    @Override
    public void onBackPressed() {
        if (mFirebaseEventLogger != null) {
            // Report "Phone Back Button" click event to Firebase Analytics.
            mFirebaseEventLogger.reportPhoneBackButtonClickEvent(this);
        }

        // Pop most recent FragmentManager transaction.
        // If no transaction exists, the typical "back pressed" behavior is initiated.
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStackImmediate();

            if (fragmentManager.getBackStackEntryCount() == 0) {
                // Hide fragment container.
                mFragmentContainer.setVisibility(View.GONE);

                // Set title of MainActivity back to the app name.
                setTitle(R.string.app_name);

                showSystemUI();
                disableDisplayAlwaysOnMode();
            }
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Show action bar and system UI.
     *
     * Must be done manually in this activity instead of PoiControllerFragment as the connection
     * between this activity and the fragment is lost when the back button is pressed, which
     * would result in a NullPointerException if called in the fragment.
     */
    private void showSystemUI() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.show();
        } else {
            Log.wtf(TAG, "Cannot access action bar");
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    /**
     * Re-enables screen timeout by clearing the flag for "Display Always On" mode.
     */
    private void disableDisplayAlwaysOnMode() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void logLongPressOnFloorplan() {
        if (mFirebaseEventLogger != null) {
            // Report "Floorplan Long Press" event to Firebase Analytics.
            mFirebaseEventLogger.reportFloorplanLongPressEvent(this);
        }
    }
}
