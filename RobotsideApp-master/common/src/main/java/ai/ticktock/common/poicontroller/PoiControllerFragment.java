package ai.cellbots.common.poicontroller;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ai.cellbots.common.R;
import ai.cellbots.common.data.PointOfInterest;

/**
 * A fragment class that displays the current ADF's list of points of interest in a 2-column grid.
 * Clicking on a point of interest's button will create a goal and send it to the cloud where it
 * will be issued to the robot as a command.
 */
public class PoiControllerFragment extends Fragment implements PoiButtonAdapter.OnPoiButtonClickListener,
        View.OnClickListener {

    private static final String TAG = PoiControllerFragment.class.getSimpleName();

    // Fragment Argument Tags.
    private static final String ARGUMENT_POI_LIST = "ARGUMENT_POI_LIST";

    // Number of columns the recycler view's grid layout should have.
    private static final int GRID_SPAN_COUNT = 2;

    private OnPoiControllerResultListener mCallback;

    /**
     * Listener for POI controller results and events.
     */
    public interface OnPoiControllerResultListener {
        /**
         * Sends resulting POI to calling class.
         *
         * @param poi The clicked button's POI.
         */
        void onPoiControllerResult(PointOfInterest poi);
        /**
         * Event triggered when the microphone button in POI controller is clicked.
         */
        void onMicButtonClick();
    }

    private RecyclerView mPoiButtonsRecyclerView;
    private FloatingActionButton mMicButton;

    private PoiButtonAdapter mPoiButtonAdapter;

    private final List<PointOfInterest> mPoiList = new ArrayList<>();

    /**
     * Static method for initializing a new instance of PoiControllerFragment.
     *
     * @param poiList The list of POIs to pass into PoiControllerFragment.
     * @return A PoiControllerFragment object.
     */
    public static PoiControllerFragment newInstance(List<PointOfInterest> poiList) {
        Bundle args = new Bundle();
        args.putParcelable(ARGUMENT_POI_LIST, Parcels.wrap(poiList));

        PoiControllerFragment fragment = new PoiControllerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // This makes sure that the context has implemented the callback interface.
        // If not, it throws an exception.
        try {
            mCallback = (OnPoiControllerResultListener) context;
        } catch (ClassCastException e) {
            //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
            throw new ClassCastException(context
                    + " must implement OnPoiControllerResultListener.");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        if (getArguments() != null) {
            Parcelable poiListParcel = getArguments().getParcelable(ARGUMENT_POI_LIST);
            List<PointOfInterest> poiList = Parcels.unwrap(poiListParcel);

            if (poiList != null && !poiList.isEmpty()) {
                alphabeticallySortPoiList(poiList);
                mPoiList.addAll(poiList);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_poi_controller, container, false);

        mPoiButtonsRecyclerView = view.findViewById(R.id.recycler_view_poi_list);
        mPoiButtonsRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), GRID_SPAN_COUNT));

        mMicButton = view.findViewById(R.id.fab_mic);
        mMicButton.setOnClickListener(this);

        mPoiButtonAdapter = new PoiButtonAdapter(getContext(), mPoiList);
        mPoiButtonAdapter.setOnPoiButtonClickListener(this);
        mPoiButtonsRecyclerView.setAdapter(mPoiButtonAdapter);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (isAdded()) {
            hideSystemUI();
            // Set this fragment's orientation to LANDSCAPE only.
            setOrientationToLandscape();
            enableDisplayAlwaysOnMode();
        }
    }

    /**
     * Receives POI from ViewHolder click events in PoiButtonAdapter.
     * The POI is then sent from this class up to MainActivity for processing and sending
     * to firebase as a new DrivePOI goal.
     *
     * @param poi POI that was clicked in PoiController.
     */
    @Override
    public void onPoiButtonClickResult(PointOfInterest poi) {
        mCallback.onPoiControllerResult(poi);
    }

    @Override
    public void onClick(View v) {
        // If-else statements are required here as resource IDs cannot be used in
        // a switch statement.
        if (v.getId() == R.id.fab_mic) {
            mCallback.onMicButtonClick();
        }
    }

    /**
     * Hides navigation, status, and action bars, then enables immersive full-screen mode.
     *
     * In immersive full-screen mode, the PoiController's contents stretch all the way to
     * full screen, and swipes for showing status or navigation bars are disabled.
     */
    private void hideSystemUI() {
        // Hide action bar.
        //noinspection ConstantConditions
        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();

        // Hide navigation and status bars, then resize content to enable
        // immersive full screen mode.
        getActivity().getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION  // Hide navigation bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN  // Hide status bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    /**
     * Sets the screen orientation to landscape.
     */
    private void setOrientationToLandscape() {
        if (getActivity() != null
                && getActivity().getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            Log.i(TAG, "Setting screen orientation to LANDSCAPE.");
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            Log.wtf(TAG, "Hosting activity is null. Cannot change orientation.");
        }
    }

    /**
     * Disables screen timeout by adding the flag for "Display Always On" mode.
     */
    private void enableDisplayAlwaysOnMode() {
        if (getActivity() != null && getActivity().getWindow() != null) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            Log.wtf(TAG, "Hosting activity is null. Cannot enable \"display always on\" mode.");
        }
    }

    /**
     * Sorts POI list alphabetically by name.
     *
     * @param poiList List of PointOfInterest objects sorted alphabetically.
     */
    private void alphabeticallySortPoiList(List<PointOfInterest> poiList) {
        try {
            Collections.sort(poiList, new Comparator<PointOfInterest>() {
                @Override
                public int compare(PointOfInterest thisPoi, PointOfInterest thatPoi) {
                    String thisName = thisPoi.variables.name.toLowerCase();
                    String thatName = thatPoi.variables.name.toLowerCase();
                    return thisName.compareTo(thatName);
                }
            });
        } catch (Exception exception) {
            Log.e(TAG, "Error while trying to sort poi list: " + exception);
        }
    }

    /**
     * Updates POI list in this fragment and the RecyclerView's adapter.
     * Data is passed in from MainActivity's updatePOIList() method.
     *
     * @param newPoiList List of new POIs from Firebase.
     */
    public void updatePoiList(List<PointOfInterest> newPoiList) {
        Log.i(TAG, "Updating POI list.");
        alphabeticallySortPoiList(newPoiList);
        mPoiList.clear();
        mPoiList.addAll(newPoiList);
        mPoiButtonAdapter.notifyDataSetChanged();
        Log.i(TAG, "POI List in PoiButtonAdapter has been updated.");
    }
}
