package ai.cellbots.robotapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.atap.tangoservice.Tango;
import com.google.common.base.Joiner;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.cellbots.common.AboutActivity;
import ai.cellbots.common.CloudConnector;
import ai.cellbots.common.CloudHelper;
import ai.cellbots.common.NetworkUtil;
import ai.cellbots.common.World;
import ai.cellbots.common.cloud.CloudPath;
import ai.cellbots.common.cloud.DataFactory;
import ai.cellbots.common.cloud.RTCDatabaseChannel;
import ai.cellbots.common.data.DrivePOIGoal;
import ai.cellbots.common.data.ExecutiveStateCommand;
import ai.cellbots.common.data.PointOfInterest;
import ai.cellbots.common.data.Robot;
import ai.cellbots.common.data.RobotPreferences;
import ai.cellbots.common.data.SpeedStateCommand;
import ai.cellbots.common.poicontroller.PoiControllerFragment;
import ai.cellbots.common.settings.RobotPreferencesFragment;
import ai.cellbots.common.teleop.VirtualJoystickView;
import ai.cellbots.common.utils.PointOfInterestValidator;
import ai.cellbots.common.voice.OnVoiceDetectedListener;
import ai.cellbots.common.voice.VoiceCommands;
import ai.cellbots.robot.manager.RobotManager;
import ai.cellbots.robot.manager.RobotManagerWrapper;
import ai.cellbots.robot.slam.WorldManager;
import ai.cellbots.robot.state.RobotSessionGlobals;
import ai.cellbots.robotlib.FirebaseAnalyticsEvents;
import ai.cellbots.robotlib.RobotRunner;
import ai.cellbots.tangocommon.CloudWorldManager;
import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * MainActivity of the RobotApp Android app. Contains all UI components necessary for controlling
 * the Cellbots robot and logging the robot's features (like current map, ip address, etc.).
 *
 * TODO (playerthree): Add more to this javadoc.
 */
@SuppressLint("GoogleAppIndexingApiWarning")
public class MainActivity extends AppCompatActivity implements RobotRunner.Monitor,
        RobotManagerWrapper.Listener, PoiControllerFragment.OnPoiControllerResultListener,
        OnVoiceDetectedListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String TELEOP_TAG = TAG + "Teleop";

    // Request Codes
    private static final int REQUEST_TANGO_PERMISSION_CODE = 42;
    private static final int REQUEST_SIGN_IN_CODE = 1;

    // FragmentManager Transactions
    private static final String TRANSACTION_ADD_POI_CONTROLLER_FRAGMENT =
            "TRANSACTION_ADD_POI_CONTROLLER_FRAGMENT";

    // Fragment Tags
    private static final String FRAGMENT_TAG_POI_CONTROLLER = "FRAGMENT_TAG_POI_CONTROLLER";

    // Authorization requests flags, service won't start without them
    private final AtomicBoolean mTangoAdfPermission = new AtomicBoolean(false);

    @BindView(R.id.status_text) TextView mStatusTextView;
    @BindView(R.id.adf_list) ListView mAdfList;
    @BindView(R.id.logout_main) Button mLogOutButton;
    @BindView(R.id.start_mapping) Button mStartMappingButton;
    @BindView(R.id.save_map) Button mSaveMapButton;
    @BindView(R.id.start_service) Button mStartServiceButton;
    @BindView(R.id.stop_service) Button mStopServiceButton;
    @BindView(R.id.stop_operation) Button mStopOperationButton;
    @BindView(R.id.calibrate) Button mCalibrateButton;
    @BindView(R.id.start_vps) Button mStartVpsButton;
    @BindView(R.id.button_open_poi_controller) Button mOpenPoiControllerButton;
    @BindView(R.id.nudge_robot) Button mNudgeRobotButton;
    @BindView(R.id.teleop_joystick_view) VirtualJoystickView mTeleopJoystickView;
    @BindView(R.id.cloud_sync) View mCloudSyncView;
    @BindView(R.id.saving_world) View mSavingWorldView;
    @BindView(R.id.fragment_container) FrameLayout mFragmentContainer;

    private Thread mSpinnerUpdateStatusVisualization = null;
    private boolean mNotPaused = false;
    private String mStatusTextRaw = "";
    private String mIpAddress = "";
    private int mSpinDots = 0;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    private RobotPreferencesFragment mSettings;

    private final CloudWorldManager.PromptLink mWorldPromptLink
            = new CloudWorldManager.PromptLink(this);

    // Helper class for reading and writing data in the cloud.
    private final CloudHelper mCloudHelper = CloudHelper.getInstance();

    // CloudHelper Listeners
    // Retrieves list of POIs for the current map.
    private ValueEventListener mPOIListener = null;
    // Listens for changes in robot's metadata on the cloud.
    private CloudHelper.RobotListener mRobotListener = null;

    // Helper class for checking and requesting the required Android Manifest permissions.
    private final RobotAppPermissionsHelper mPermissionsHelper = new RobotAppPermissionsHelper(this);

    // A HashMap collection consisting of "POI name" as key and "POI" as value.
    private final Map<String, PointOfInterest> mNameAndPoiMap = new HashMap<>();

    // Current robot operating in the RobotApp.
    // Properties such as uuid and world are updated in processRobotManagerWrapper().
    private Robot mCurrentRobot;

    // Class for controlling the speed and action of the robot when a command is given
    // in the cloud.
    private CloudConnector mCloudConnector = null;

    // Class for processing voice commands and sending them to the cloud.
    private VoiceCommands mVoiceCommands;

    /**
     * Android onCreate.
     *
     * @param savedInstanceState From Android, we ignore it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        // Initialize Firebase components.
        mFirebaseAuth = FirebaseAuth.getInstance();

        final Toolbar mainToolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(mainToolbar);

        mCurrentRobot = new Robot();
        mSettings = new SettingsFragment();

        mSettings.setPreferencesListener(
                new RobotPreferencesFragment.PreferenceKeyChangeListener<String>() {
                    @Override
                    public void onChange(final String key) {
                        // If a key is being updated synchronize with robot
                        updateRobotConfigFromKey(key);
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

        addRobotPreferencesFromDBFleetListener();

        mStartMappingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getApplicationContext() != null) {
                    // Report "Start Mapping" button click events to Firebase Analytics.
                    FirebaseAnalyticsEvents.getInstance().reportStartMappingButtonClickEvent(
                            getApplicationContext());
                }
                final RobotRunner runner = RobotService.getRobotRunner();
                RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
                if (runner == null && wrapper == null) {
                    return;
                }
                mAdfList.setVisibility(View.GONE);
                mStartMappingButton.setVisibility(View.GONE);
                mStartVpsButton.setVisibility(View.GONE);
                mOpenPoiControllerButton.setVisibility(View.GONE);
                mTeleopJoystickView.setVisibility(View.GONE);
                mNudgeRobotButton.setVisibility(View.GONE);
                if (runner != null) {
                    runner.startMapping();
                }
                if (wrapper != null) {
                    wrapper.startMapping();
                }
            }
        });

        mStopOperationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getApplicationContext() != null) {
                    // Report "Stop Operation" button click events to Firebase Analytics.
                    FirebaseAnalyticsEvents.getInstance().reportStopOperationButtonClickEvent(
                            getApplicationContext());
                }
                final RobotRunner runner = RobotService.getRobotRunner();
                if (runner != null) {
                    runner.stopOperations();
                }
                RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
                if (wrapper != null) {
                    wrapper.stopOperation();
                }
            }
        });

        mSaveMapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getApplicationContext() != null) {
                    // Report "Save Map" button click events to Firebase Analytics.
                    FirebaseAnalyticsEvents.getInstance().reportSaveMapButtonClickEvent(
                            getApplicationContext());
                }
                final RobotRunner runner = RobotService.getRobotRunner();
                final RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
                if (runner != null || wrapper != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                    final EditText input = new EditText(MainActivity.this);
                    input.setInputType(InputType.TYPE_CLASS_TEXT);
                    builder.setTitle("Enter Map Name").setView(input);

                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final RobotRunner runner = RobotService.getRobotRunner();
                            final String text = input.getText().toString();
                            if (runner != null && !text.isEmpty()) {
                                Log.i(TAG, "Start saving ADF");
                                mSaveMapButton.setVisibility(View.GONE);
                                runner.saveADF(text);
                            } else {
                                RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
                                if (wrapper != null && !text.isEmpty()) {
                                    mSaveMapButton.setVisibility(View.GONE);
                                    Log.i(TAG, "Start saving Map");
                                    wrapper.saveMap(text);
                                }
                            }
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            mSaveMapButton.setVisibility(View.VISIBLE);
                        }
                    });
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            mSaveMapButton.setVisibility(View.VISIBLE);
                        }
                    });

                    builder.show();
                    mSaveMapButton.setVisibility(View.GONE);
                }
            }
        });

        mOpenPoiControllerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Start POI controller with POI list.
                List<PointOfInterest> poiList = new ArrayList<>(mNameAndPoiMap.values());
                startPoiControllerFragment(poiList);
            }
        });

        mNudgeRobotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "NudgeRobot button clicked");
                final RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
                if (wrapper != null) {
                    wrapper.nudgeRobot();
                }
            } });

        mCalibrateButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getApplicationContext() != null) {
                            // Report "Calibrate" button click events to Firebase Analytics.
                            FirebaseAnalyticsEvents.getInstance().reportCalibrateButtonClickEvent(
                                    getApplicationContext());
                        }
                        final RobotRunner runner = RobotService.getRobotRunner();
                        if (runner == null) {
                            return;
                        }
                        mCalibrateButton.setEnabled(false);
                        runner.calibrate();
                    }
                });

        mAdfList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> listView, View rowView, int index, long id) {
                if (getApplicationContext() != null) {
                    // Report "ADF List Item" click events to Firebase Analytics.
                    FirebaseAnalyticsEvents.getInstance().reportSelectMapButtonClickEvent(
                            getApplicationContext());
                }
                Adapter a = listView.getAdapter();
                if (a != null) {
                    if (index < a.getCount() && a.getItem(index) instanceof World) {
                        final World w = (World) a.getItem(index);
                        final RobotRunner runner = RobotService.getRobotRunner();
                        if (runner != null) {
                            mAdfList.setVisibility(View.GONE);
                            mStartMappingButton.setVisibility(View.GONE);
                            mStartVpsButton.setVisibility(View.GONE);
                            runner.startOperation(w);
                        }
                        final RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
                        if (wrapper != null) {
                            mAdfList.setVisibility(View.GONE);
                            mStartMappingButton.setVisibility(View.GONE);
                            mStartVpsButton.setVisibility(View.GONE);
                            wrapper.startOperation(w);
                        }
                    }
                }
            }

        });

        mAdfList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> listView, View rowView, int index, long
                    id) {
                Adapter a = listView.getAdapter();
                if (a != null) {
                    if (index < a.getCount()) {
                        @SuppressWarnings("CastToConcreteClass") final World w = (World) a
                                .getItem(index);

                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface
                                .OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        final RobotRunner runner = RobotService.getRobotRunner();
                                        if (runner != null) {
                                            runner.removeWorld(w);
                                        }
                                        RobotManagerWrapper managerWrapper =
                                                RobotService.getRobotManagerWrapper();
                                        if (managerWrapper != null) {
                                            managerWrapper.removeWorld(w);
                                        }
                                        break;

                                    case DialogInterface.BUTTON_NEGATIVE:
                                        break;

                                    default:
                                        break;
                                }
                            }
                        };

                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setMessage("Are you sure you want to delete '" + w.getName() + "'?")
                                .setPositiveButton("Yes", dialogClickListener)
                                .setNegativeButton("No", dialogClickListener).show();
                        return true;
                    }
                }
                return false;
            }
        });


        mStartServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getApplicationContext() != null) {
                    // Report "Start Service" button click events to Firebase Analytics.
                    FirebaseAnalyticsEvents.getInstance().reportStartServiceButtonClickEvent(
                            getApplicationContext());
                }
                RobotService.startService(MainActivity.this);
                resetGUIState(false);
            }
        });

        mStopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getApplicationContext() != null) {
                    // Report "Stop Service" button click events to Firebase Analytics.
                    FirebaseAnalyticsEvents.getInstance().reportStopServiceButtonClickEvent(
                            getApplicationContext());
                }
                RobotService.stopService(MainActivity.this);
                resetGUIState(false);
            }
        });

        mStartVpsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final RobotRunner runner = RobotService.getRobotRunner();
                if (runner != null) {
                    mAdfList.setVisibility(View.GONE);
                    mStartMappingButton.setVisibility(View.GONE);
                    mStartVpsButton.setVisibility(View.GONE);
                    runner.startOperation(World.VPS_WORLD);
                }
                final RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
                if (wrapper != null) {
                    mAdfList.setVisibility(View.GONE);
                    mStartMappingButton.setVisibility(View.GONE);
                    mStartVpsButton.setVisibility(View.GONE);
                    wrapper.startOperation(World.VPS_WORLD);
                }
            }
        });

        mLogOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getApplicationContext() != null) {
                    // Report "Sign Out" button click events to Firebase Analytics.
                    FirebaseAnalyticsEvents.getInstance().reportSignOutButtonClickEvent(
                            getApplicationContext());
                }
                AuthUI.getInstance().signOut(MainActivity.this);
            }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                checkSignIn();
            }
        };

        // Get IP addresses.
        Joiner joiner = Joiner.on(", ").skipNulls();
        mIpAddress = joiner.join(NetworkUtil.getIPv4List());

        mFirebaseAuth.addAuthStateListener(mAuthStateListener);

        RobotService.addMonitor(this);
        RobotService.setPromptLink(mWorldPromptLink);

        mCloudConnector = new CloudConnector(this, new CloudConnector.Listener() {
            @Override
            public void onExecutiveCommand(ExecutiveStateCommand command, boolean sync) {
                // Nothing to do.
            }

            @Override
            public void onSpeedCommand(SpeedStateCommand command, boolean sync) {
                // Nothing to do.
            }
        });

        mVoiceCommands = new VoiceCommands(this);
        mVoiceCommands.setOnVoiceDetectedListener(this);
    }

    /**
     * Gets the Firebase user.
     *
     * @return FirebaseUser object. Null if not connected to firebase.
     */
    private FirebaseUser getFirebaseUser() {
        if (mFirebaseAuth == null) {
            Log.w(TAG, "Could not get user from cloud. Not connected yet.");
            return null;
        }
        return mFirebaseAuth.getCurrentUser();
    }

    /**
     * Gets user's email address.
     *
     * @return Email address string. Null if not signed in yet.
     */
    private String getUserEmail() {
        FirebaseUser user = getFirebaseUser();
        return user == null ? "" : user.getEmail();
    }

    /**
     * Attempts to sign-in if possible.
     */
    private void checkSignIn() {
        if (!hasAllPermissions()) {
            return;
        }
        FirebaseUser user = getFirebaseUser();
        if (user == null) {
            // User is signed out
            AuthUI.IdpConfig identityProviderConfig =
                    new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build();
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setIsSmartLockEnabled(false)
                            .setAvailableProviders(
                                    Collections.singletonList(identityProviderConfig))
                            .build(),
                    REQUEST_SIGN_IN_CODE);
        }
    }

    /**
     * Called when the activity is started back up.
     */
    @Override
    protected void onResume() {
        super.onResume();
        checkAndRequestPermissions();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);

        mNotPaused = true;
        mSpinnerUpdateStatusVisualization = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mNotPaused) {
                    mSpinDots = (mSpinDots + 1) % 5;
                    updateStatusText(null);
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
        mSpinnerUpdateStatusVisualization.start();

        resetGUIState(true);
        RobotService.addMonitor(this);
        RobotService.addListener(this);
        RobotService.setPromptLink(mWorldPromptLink);

        // Every time permissions are requested the application goes to onPause. We check if they
        // were set properly here.
        if (hasAllPermissions() && getFirebaseUser() != null) {
            RobotService.startService(this);
        }

        mTeleopJoystickView.startTimer();
    }

    /**
     * Returns true if we have permission to access the relevant items.
     *
     * @return True if we have permissions
     */
    private boolean hasAllPermissions() {
        return mTangoAdfPermission.get() && mPermissionsHelper.hasAllPermissions();
    }

    /**
     * Called when the activity is stopped.
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        if (mNotPaused || (mSpinnerUpdateStatusVisualization != null)) {
            mNotPaused = false;
            try {
                mSpinnerUpdateStatusVisualization.join();
            } catch (InterruptedException e) {
                // Do nothing
            }
            mSpinnerUpdateStatusVisualization = null;
        }

        mTeleopJoystickView.stopTimer();
        RobotService.removeMonitor(this);
        RobotService.removeListener(this);
        RobotService.clearPromptLink(mWorldPromptLink);
    }

    /**
     * Handles ActionBar actions.
     * See /res/menu/main.xml for declared actions.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                // Show About activity
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.action_preferences:
                // Show robot_preferences fragment
                getFragmentManager().beginTransaction().
                        replace(android.R.id.content, mSettings).
                        addToBackStack(null).commit();

                RobotRunner runner = RobotService.getRobotRunner();
                if (runner != null && runner.getRobotConnected()) {
                    // Update current value as default
                    int currentSeekBarVelocity = (int) (runner.getMaxSpeed()
                            * 100 / runner.getMaxAllowedSpeed());
                    mSettings.setSeekBarVelocity(currentSeekBarVelocity);
                }

                // Enable the following robot_preferences only if robot is connected.
                mSettings.enableSmootherPreferences(runner != null && runner.getRobotConnected());
                mSettings.enableInflationPreferences(runner != null && runner.getRobotConnected());
                mSettings.enableMaxVelPreference(runner != null && runner.getRobotConnected());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Saves the current settings as fleet settings on the DB when the button is pressed
     *
     * @param v Current view
     */
    @SuppressWarnings("unused")
    public void onSaveFleetSettingsClick(View v) {
        if (getApplicationContext() != null) {
            // Report "Save As Fleet" button click events to Firebase Analytics.
            FirebaseAnalyticsEvents.getInstance().reportSaveAsFleetButtonClickEvent(
                    getApplicationContext());
        }
        CloudHelper.getInstance().setRobotFleetPreferences(mSettings.getRobotPreferencesData(this),
                new CloudHelper.ResultListener<Boolean>() {
                    @Override
                    public void onResult(final Boolean result) {
                        String resultString = "Settings have been saved successfully.";
                        if (!result) {
                            resultString = "Settings haven't been saved.";
                        }
                        Toast.makeText(MainActivity.this, resultString, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Adds listener for settings changes on DB
     */
    private void addRobotPreferencesFromDBFleetListener() {
        // Add a new listener for the current robot.
        CloudHelper.getInstance().addRobotFleetPreferencesListener(
                new CloudHelper.ResultListener<RobotPreferences>() {
                    @Override
                    public void onResult(final RobotPreferences result) {
                        if (result != null) {
                            updateRobotConfigFromSettings(result);
                        }
                    }
                });
    }

    /**
     * Updates settings from the data from Firebase.
     */
    public void updateRobotPreferencesFromDBFleet() {
        CloudHelper.getInstance().getRobotFleetPreferences(
                new CloudHelper.ResultListener<RobotPreferences>() {
                    @Override
                    public void onResult(final RobotPreferences result) {
                        if (result != null) {
                            updateRobotConfigFromSettings(result);
                        }
                    }
                });
    }

    /**
     * Updates preferences in memory of the robot setting shared preferences and the settings on
     * robot runner
     *
     * @param preferences preferences that are going to be used to update robot preferences
     */
    public void updateRobotConfigFromSettings(RobotPreferences preferences) {
        // Update shared preferences
        if (mSettings != null) {
            mSettings.setRobotPreferencesData(this, preferences);
        } else {
            return;
        }
        // If the robot runner is running update its preferences
        RobotRunner runner = RobotService.getRobotRunner();
        if (runner != null) {
            runner.setRobotPreferences(preferences);
            if (runner.getRobotConnected()) {
                runner.updatePreferences();
            }
        }
        Log.v(TAG, "Preferences were updated from database");
    }

    /**
     * Updates only one setting on the robot robot runner, the setting is identified with the
     * preference key
     *
     * @param key key setting of the robot runner
     */
    private void updateRobotConfigFromKey(String key) {
        RobotRunner runner = RobotService.getRobotRunner();
        if (runner != null && runner.getRobotConnected()) {
            if (key.equals(getString(R.string.seekbar_max_vel_key_preference))) {
                // Updates the maximum linear velocity of the robot
                runner.updateMaxVelParam();
            } else if (key.equals(
                    getString(R.string.seekbar_occ_grid_key_preference))) {
                // Updates the inflation parameter for the Occupancy Grid
                runner.updateInflationParam();
            } else if (key.equals(getString(R.string.pref_smoother_deviation)) ||
                    key.equals(getString(R.string.pref_smoother_smoothness))) {
                // Updates smoother params
                runner.updateSmootherParams();
            }

        } else {
            Log.e(TAG, "Tried configure null robot");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Repopulates the ADFs from the tango.
     */
    private void repopulateAdfList() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RobotRunner runner = RobotService.getRobotRunner();
                RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
                World[] worlds = null;
                if (runner != null) {
                    worlds = runner.listWorldInfo();
                } else if (wrapper != null) {
                    worlds = wrapper.getWorlds();
                }
                if (worlds != null) {
                    ArrayAdapter<World> itemsAdapter =
                            new ArrayAdapter<>(MainActivity.this,
                                    android.R.layout.simple_list_item_1, worlds);
                    mAdfList.setAdapter(itemsAdapter);
                    mAdfList.setVisibility(View.VISIBLE);
                } else {
                    mAdfList.setVisibility(View.GONE);
                }
            }
        });
    }

    /**
     * Displays toast on UI thread.
     *
     * @param err The string to be shown
     */
    private void showsToastAndFinishOnUiThread(final String err) {
        Log.e(TAG, "Report error: " + err);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, err, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    /**
     * Called when the RobotRunner has an error.
     *
     * @param error The RobotRunner error.
     */
    @Override
    public void reportRobotRunnerError(RobotRunner.Error error) {
        if (error == RobotRunner.Error.exception_out_of_date) {
            showsToastAndFinishOnUiThread(getString(R.string.exception_out_of_date));
        } else if (error == RobotRunner.Error.exception_tango_error) {
            showsToastAndFinishOnUiThread(getString(R.string.exception_tango_error));
        } else if (error == RobotRunner.Error.exception_tango_invalid) {
            showsToastAndFinishOnUiThread(getString(R.string.exception_tango_invalid));
        } else if (error == RobotRunner.Error.robot_driver_invalid) {
            showsToastAndFinishOnUiThread(getString(R.string.robot_driver_invalid));
        } else if (error == RobotRunner.Error.robot_driver_error) {
            showsToastAndFinishOnUiThread(getString(R.string.robot_driver_error));
        } else if (error == RobotRunner.Error.ros_startup_error) {
            showsToastAndFinishOnUiThread(getString(R.string.ros_startup_error));
        } else {
            showsToastAndFinishOnUiThread(error.toString());
        }
    }

    /**
     * Updates the state of the service stop/start buttons.
     */
    private void serviceButtonsUpdate() {
        if (RobotService.getLockout()) {
            mStartServiceButton.setVisibility(View.GONE);
            mStopServiceButton.setVisibility(View.GONE);
            findViewById(R.id.logout_main).setVisibility(View.GONE);
        } else if (RobotService.getInstance() != null) {
            mStartServiceButton.setVisibility(View.GONE);
            mStopServiceButton.setVisibility(View.VISIBLE);
            findViewById(R.id.logout_main).setVisibility(View.GONE);
        } else {
            findViewById(R.id.logout_main).setVisibility(View.VISIBLE);
            mStartServiceButton.setVisibility(View.VISIBLE);
            mStopServiceButton.setVisibility(View.GONE);
        }
    }

    /**
     * Resets the state of the GUI.
     *
     * @param isLoading True if we are loading the app up at start.
     */
    private void resetGUIState(boolean isLoading) {
        serviceButtonsUpdate();
        mSaveMapButton.setVisibility(View.GONE);
        mStartMappingButton.setVisibility(View.GONE);
        mCalibrateButton.setVisibility(View.GONE);
        mStopOperationButton.setVisibility(View.GONE);
        mAdfList.setVisibility(View.GONE);
        mStartVpsButton.setVisibility(View.GONE);
        mCloudSyncView.setVisibility(View.GONE);
        mSavingWorldView.setVisibility(View.GONE);
        mOpenPoiControllerButton.setVisibility(View.GONE);
        mNudgeRobotButton.setVisibility(View.GONE);
        mTeleopJoystickView.setVisibility(View.GONE);
        if (isLoading) {
            updateStatusText(getString(R.string.status_text_initial_value));
        } else {
            updateStatusText(getString(R.string.status_text_robot_disabled));
        }
    }

    /**
     * Updates the status text with the spinner
     *
     * @param newText The new text
     */
    private synchronized void updateStatusText(String newText) {
        if (newText != null) {
            mStatusTextRaw = newText;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(mStatusTextRaw);
        sb.append(" (");
        for (int i = 0; i < mSpinDots; i++) {
            sb.append('.');
        }
        sb.append(')');
        final String nextText = sb.toString();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusTextView.setText(nextText);
            }
        });
    }

    /**
     * Called by the robot runner whenever there is a new state.
     */
    @Override
    public void onRobotRunnerState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                serviceButtonsUpdate();
                processRobotRunner();
                processRobotManagerWrapper();
            }
        });
    }

    /**
     * Process the RobotManagerWrapper state.
     */
    @MainThread
    private void processRobotManagerWrapper() {
        final RobotManagerWrapper wrapper = RobotService.getRobotManagerWrapper();
        if (wrapper == null) {
            return;
        }
        mCurrentRobot.uuid = wrapper.getRobotUuid();
        RobotSessionGlobals sessionGlobals = wrapper.getSession();
        StringBuilder statusText = new StringBuilder();
        RobotManager.State robotState = wrapper.getRobotState();
        WorldManager.State worldState = wrapper.getWorldManagerState();

        statusText.append("State: ");
        statusText.append(robotState);
        statusText.append(" ");
        statusText.append(worldState);
        statusText.append("\nUser: ");
        statusText.append(getUserEmail());
        statusText.append("\nLast Robot: ");
        statusText.append(wrapper.getRobotUuid());
        statusText.append("\nAddress: ");
        statusText.append(mIpAddress);

        statusText.append("\nWorld: ");
        if (mTangoAdfPermission.get()) {
            if (sessionGlobals != null && sessionGlobals.getWorld() != null) {
                World world = sessionGlobals.getWorld();
                mCurrentRobot.map = world.getUuid();
                statusText.append(world.getName());
            } else {
                mCurrentRobot.map = null;
                statusText.append("NULL");
            }
        } else {
            statusText.append("No Adf permissions");
        }

        statusText.append("\nLocalized: ");
        statusText.append(wrapper.isLocalized());

        // TODO (playerthree) clean this up and add comments...
        mCalibrateButton.setVisibility(View.GONE);
        if (robotState == null || worldState != null) {
            mSavingWorldView.setVisibility(View.GONE);
            mSavingWorldView.setVisibility(View.GONE);
            mStopOperationButton.setVisibility(View.GONE);
            mSaveMapButton.setVisibility(View.GONE);
            mNudgeRobotButton.setVisibility(View.GONE);
            if (worldState == WorldManager.State.LOCKED) {
                mStartMappingButton.setVisibility(View.GONE);
                mAdfList.setVisibility(View.GONE);
                mStartVpsButton.setVisibility(View.GONE);
                mCloudSyncView.setVisibility(View.VISIBLE);
            } else {
                mCloudSyncView.setVisibility(View.GONE);
                mStartMappingButton.setVisibility(View.VISIBLE);
                mAdfList.setVisibility(View.VISIBLE);
                mStartVpsButton.setVisibility(View.VISIBLE);
                mNudgeRobotButton.setVisibility(View.VISIBLE);
                repopulateAdfList();
            }
        } else {
            mSavingWorldView.setVisibility(View.GONE);
            mCloudSyncView.setVisibility(View.GONE);
            mStartMappingButton.setVisibility(View.GONE);
            mAdfList.setVisibility(View.GONE);
            mStartVpsButton.setVisibility(View.GONE);
            mNudgeRobotButton.setVisibility(View.GONE);
            if (robotState == RobotManager.State.MAPPING
                    || robotState == RobotManager.State.NAVIGATING
                    || robotState == RobotManager.State.SAVED_MAP) {
                mStopOperationButton.setVisibility(View.VISIBLE);
                mNudgeRobotButton.setVisibility(View.VISIBLE);
                if (wrapper.canSaveWorld()) {
                    mSaveMapButton.setVisibility(View.VISIBLE);
                } else {
                    mSaveMapButton.setVisibility(View.GONE);
                }
            } else {
                mSaveMapButton.setVisibility(View.GONE);
                mStopOperationButton.setVisibility(View.GONE);
                mNudgeRobotButton.setVisibility(View.GONE);
            }
            mSavingWorldView.setVisibility(robotState == RobotManager.State.SAVING_MAP
                    ? View.VISIBLE : View.GONE);
        }
        // Show "Open POI Controller" button and teleop joystick ONLY if:
        //      1) Current robot's uuid is non-null.
        //      2) Current robot's map is non-null.
        //      3) Robot Manager is in the NAVIGATING state.
        if (mCurrentRobot.uuid != null
                && mCurrentRobot.map != null
                && robotState == RobotManager.State.NAVIGATING) {
            // Retrieve changes in the current robot on the cloud.
            watchRobot();
            // Update the collection of POIs.
            updatePoiMap();

            // Show POI Controller button and teleop joystick view.
            mOpenPoiControllerButton.setVisibility(View.VISIBLE);
            mTeleopJoystickView.setVisibility(View.VISIBLE);
        } else {
            // Hide POI Controller button and teleop joystick view.
            mOpenPoiControllerButton.setVisibility(View.GONE);
            mTeleopJoystickView.setVisibility(View.GONE);
        }

        updateStatusText(statusText.toString());

        // Update cloud connector and teleop virtual joystick with the current updated robot.
        mCloudConnector.updateWithRobot(mCloudHelper.getCurrentUserid(), mCurrentRobot);
        mTeleopJoystickView.setRobot(mCurrentRobot);
    }

    /**
     * Process the RobotRunner state.
     */
    @MainThread
    private void processRobotRunner() {
        final RobotRunner runner = RobotService.getRobotRunner();
        if (runner == null) {
            return;
        }
        final RobotRunner.State state = runner.getState();
        if (state != RobotRunner.State.SHUTDOWN && !RobotService.getLockout()) {
            StringBuilder statusText = new StringBuilder();

            statusText.append("State: ");
            statusText.append(state);
            statusText.append("\nAddress: ");
            statusText.append(mIpAddress);
            statusText.append("\nRobot Connected: ");
            statusText.append(runner.getRobotConnected());
            statusText.append("\nRobot UUID: ");
            statusText.append(runner.getRobotUuid());
            statusText.append("\nRobot Version: ");
            statusText.append(runner.getRobotVersion());
            statusText.append("\nSounds Synchronized: ");
            statusText.append(runner.getSoundsSync());
            statusText.append("\nLocalized: ");
            statusText.append(runner.getLocalized());
            statusText.append("\nAction1: ");
            statusText.append(runner.getAction1());
            statusText.append(" Action2: ");
            statusText.append(runner.getAction2());
            statusText.append("\nBumper State: ");
            statusText.append(runner.getBumperManeuver());
            statusText.append("\nPoint Cloud/OctoMap Blocked: ");
            statusText.append(runner.isRobotBlocked());
            String driverState = runner.getDriverState();
            if (driverState != null && !driverState.trim().equals("")) {
                statusText.append("\n");
                statusText.append(driverState);
            }
            statusText.append("\nWorld: ");

            if (mTangoAdfPermission.get()) {
                World world = runner.getWorld();
                if (world != null) {
                    statusText.append(world.getName());
                } else {
                    statusText.append("NULL");
                }
            } else {
                statusText.append("No Adf permissions");
            }

            updateStatusText(statusText.toString());

            if (runner.canSaveWorld()) {
                mSaveMapButton.setVisibility(View.VISIBLE);
            } else {
                mSaveMapButton.setVisibility(View.GONE);
            }
            if (state == RobotRunner.State.CALIBRATION
                    || state == RobotRunner.State.RUNNING_MAPPING
                    || state == RobotRunner.State.RUNNING_NAVIGATION) {
                mStopOperationButton.setVisibility(View.VISIBLE);
            } else {
                mStopOperationButton.setVisibility(View.GONE);
            }
            if (state == RobotRunner.State.WAIT_FOR_COMMAND) {
                if (runner.getRobotConnected()) {
                    // If the robot is connected, show and enable calibration button.
                    mCalibrateButton.setVisibility(View.VISIBLE);
                    mCalibrateButton.setEnabled(true);
                } else {
                    // If the robot is not connected, hide calibration button.
                    mCalibrateButton.setVisibility(View.GONE);
                }
                mStartMappingButton.setVisibility(View.VISIBLE);
                mAdfList.setVisibility(View.VISIBLE);
                mStartVpsButton.setVisibility(View.VISIBLE);
                if (mTangoAdfPermission.get()) {
                    repopulateAdfList();
                }
            } else {
                mCalibrateButton.setVisibility(View.GONE);
                mStartMappingButton.setVisibility(View.GONE);
                mAdfList.setVisibility(View.GONE);
                mStartVpsButton.setVisibility(View.GONE);
            }

            mCloudSyncView.setVisibility(runner.isInCloudWorldTransaction()
                    ? View.VISIBLE : View.GONE);
            mSavingWorldView.setVisibility(runner.isSavingWorld()
                    ? View.VISIBLE : View.GONE);
        } else {
            resetGUIState(false);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {
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
        } else if (requestCode == REQUEST_TANGO_PERMISSION_CODE) {
            if (resultCode != RESULT_OK) {
                showsToastAndFinishOnUiThread(getString(R.string.tango_permissions_error));
            } else {
                mTangoAdfPermission.set(true);
            }
        } else {
            Log.i(TAG, "Got activity result: request code: " + requestCode
                    + " result code: " + resultCode + " "
                    + ((data == null) ? "NULL" : data.toString()));
        }
        // Login activity
        if (requestCode == REQUEST_SIGN_IN_CODE) {
            if (resultCode == RESULT_OK) {
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
     * Checks to see if we have the necessary permissions for this app; ask for them if we don't.
     */
    private void checkAndRequestPermissions() {
        if (!mPermissionsHelper.hasWriteExternalStoragePermission()) {
            mPermissionsHelper.requestWriteExternalStoragePermission();
            return;
        }
        if (!mPermissionsHelper.hasCameraPermission()) {
            mPermissionsHelper.requestCameraPermission();
            return;
        }
        if (!mPermissionsHelper.hasLocationPermission()) {
            mPermissionsHelper.requestLocationPermission();
            return;
        }
        if (!mPermissionsHelper.hasRecordAudioPermission()) {
            mPermissionsHelper.requestRecordAudioPermission();
            return;
        }
        if (!mTangoAdfPermission.get()) {
            startActivityForResult(
                    Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE),
                    REQUEST_TANGO_PERMISSION_CODE);
        }
    }

    /**
     * Result for requesting camera permission.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] results) {
        if (results.length > 0) {
            // Iterate through all permissions results and check if any permission was denied.
            // App should only proceed if all permissions are granted.
            for (int result : results) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showsToastAndFinishOnUiThread("All permissions must be granted to use " +
                            "the Cellbots Robot app.");
                    return;
                }
            }
        }
    }

    /**
     * Called when the state of the RobotManagerWrapper is updated.
     */
    @Override
    public void onStateUpdate() {
        onRobotRunnerState();
    }

    /**
     * Removes any active robot listeners, then initializes a new robot listener for changes
     * in the robot's metadata in the cloud.
     */
    private void watchRobot() {
        // Remove the old robot listeners, if they exist. There can be multiple robot listeners.
        if (mRobotListener != null) {
            mCloudHelper.removeRobotListener(mRobotListener);
        }

        // Add a new listener for the current robot.
        mRobotListener = mCloudHelper.watchRobot(new CloudHelper.ResultListener<Robot>() {
            @Override
            public void onResult(Robot result) {
                if (mCurrentRobot.uuid == null || !mCurrentRobot.uuid.equals(result.uuid)) {
                    Log.w(TAG, "The uuids of the current robot and cloud robot do not match. "
                            + "Cannot keep track of robot's metadata changes on the cloud.");
                    return;
                }

                // Update teleop with robot's tf.
                if (result.tf != null) {
                    final ai.cellbots.common.data.Transform position = result.tf;
                    mCurrentRobot.tf = position;

                    Log.i(TELEOP_TAG, "Updated current robot's position to: " + position);
                    mTeleopJoystickView.onRobotPositionUpdate(position);
                }
            }
        }, mCurrentRobot);
    }

    /**
     * Updates map of POIs (mNameAndPoiMap) using the list of POIs stored in the cloud.
     */
    private void updatePoiMap() {
        // First, remove the listener if the user switched robots.
        if (mPOIListener != null) {
            mCloudHelper.removePOIListener(mPOIListener, mCurrentRobot);
        }

        // Add a new listener for the current robot.
        mPOIListener = mCloudHelper.addPOIListener(
            new CloudHelper.ListResultsListener<PointOfInterest>() {
                @Override
                public void onResult(final List<PointOfInterest> result) {
                    // Update map of POIs.
                    mNameAndPoiMap.clear();
                    List<PointOfInterest> validPOIs = new ArrayList<>();
                    for (PointOfInterest poi : result) {
                        boolean isValid = PointOfInterestValidator.areAllFieldsValid(poi);
                        if (isValid) {
                            Log.i(TAG, "All fields for POI " + poi.uuid + " were valid. " +
                                    "Adding to POI list for rendering.");
                            validPOIs.add(poi);
                        } else {
                            Log.w(TAG, "The POI " + poi.uuid + " contains some invalid fields. "
                                    + "Rejected!");
                        }
                    }

                    // Update mNameAndPoiMap with the list of valid POIs.
                    for (int i = 0; i < validPOIs.size(); ++i) {
                        mNameAndPoiMap.put(validPOIs.get(i).variables.name, validPOIs.get(i));
                    }

                    // Update list of POIs in POI controller.
                    updatePoiControllerFragmentPoiList(validPOIs);
                }
            }, mCurrentRobot);
    }

    /**
     * Updates list of POIs in PoiControllerFragment.
     *
     * @param validPOIs The list of newly updated, valid POIs from the cloud.
     */
    private void updatePoiControllerFragmentPoiList(List<PointOfInterest> validPOIs) {
        Log.i(TAG, "Updating POI list in POI controller.");
        PoiControllerFragment poiControllerFragment =
                (PoiControllerFragment) getSupportFragmentManager()
                        .findFragmentByTag(FRAGMENT_TAG_POI_CONTROLLER);

        if (poiControllerFragment != null) {
            poiControllerFragment.updatePoiList(validPOIs);
        }
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
     * Upon receiving the click result from PoiControllerFragment, creates a new DrivePOIGoal
     * and sends the goal to the cloud.
     *
     * @param poi The POI of the clicked button in the POI controller.
     */
    @Override
    public void onPoiControllerResult(PointOfInterest poi) {
        Toast.makeText(
                this,
                "Sending robot to POI: " + poi.variables.name,
                Toast.LENGTH_SHORT
        ).show();
        DrivePOIGoal drivePOIGoal = new DrivePOIGoal(poi.uuid);
        mCloudHelper.sendPlannerGoalToRobot(drivePOIGoal, mCurrentRobot);
    }

    /**
     * On mic button click in POI controller, starts voice command recognition.
     */
    @Override
    public void onMicButtonClick() {
        mVoiceCommands.startVoiceRecognition(mCurrentRobot, mNameAndPoiMap, new ArrayList<String>());
    }

    /**
     * Initiates the robot action provided by the voice command.
     *
     * @param actionDetected The detected action for controlling the robot.
     */
    @Override
    public void onVoiceActionDetected(VoiceCommands.VoiceAction actionDetected) {
        switch (actionDetected) {
            case START:
                if (mCloudConnector != null) {
                    mCloudConnector.setRobotSpeedCommand(
                            mCurrentRobot,
                            new SpeedStateCommand(true)
                    );
                }
                break;
            case STOP:
                if (mCloudConnector != null) {
                    mCloudConnector.setRobotSpeedCommand(
                            mCurrentRobot,
                            new SpeedStateCommand(false)
                    );
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

    /**
     * Handles events that occur when the back button is pressed.
     */
    @Override
    public void onBackPressed() {
        // Pop most recent FragmentManager transaction.
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStackImmediate();

            if (fragmentManager.getBackStackEntryCount() == 0) {
                // Hide fragment container.
                mFragmentContainer.setVisibility(View.GONE);

                // Re-display the action bar and system UI.
                showSystemUI();

                // Disable "Display Always On" mode. This re-enables screen timeout.
                disableDisplayAlwaysOnMode();

                // Set orientation back to PORTRAIT if not already set to it.
                if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }
        } else {
            // If no fragment transaction exists in the back stack, the default
            // "back button pressed" behavior is initiated.
            super.onBackPressed();
        }
    }

    /**
     * Shows action bar and system UI.
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
}
