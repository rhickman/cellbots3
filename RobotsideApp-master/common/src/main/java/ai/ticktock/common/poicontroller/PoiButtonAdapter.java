package ai.cellbots.common.poicontroller;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.List;

import ai.cellbots.common.R;
import ai.cellbots.common.data.PointOfInterest;

/**
 * A RecyclerView.Adapter class that binds a list of PointOfInterest objects to a grid of buttons in
 * the RecyclerView in PoiControllerActivity.
 *
 * Clicking on a point of interest's button will create a goal and send it to the cloud where it
 * will be issued to the robot as a command.
 *
 * TODO (playerthree): Set height of poi buttons to dynamically scale according to the phone's display.
 *              Using 155dp for button height is fine for displaying 6 buttons on the Phab 2 phone,
 *              but it won't work for another phone like the Asus Zenfone.
 *
 * TODO (playerthree): Change up the color of the buttons and add rounded corners. Make it look pretty.
 */
public class PoiButtonAdapter extends RecyclerView.Adapter<PoiButtonAdapter.PoiButtonViewHolder> {

    private final Context mContext;

    @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
    private List<PointOfInterest> mPoiList; // Must be non-final for notifyDataSetChanged() to work.

    private OnPoiButtonClickListener mListener;

    /**
     * Listener that, on button click, sends the resulting button's POI to the calling class.
     */
    public interface OnPoiButtonClickListener {
        /**
         * Sends resulting POI to calling class.
         *
         * @param poi The clicked button's POI.
         */
        void onPoiButtonClickResult(PointOfInterest poi);
    }

    /**
     * Sets listener for OnPoiButtonClickListener.
     *
     * @param listener The listener.
     */
    void setOnPoiButtonClickListener(OnPoiButtonClickListener listener) {
        mListener = listener;
    }

    /**
     * Constructor for PoiButtonAdapter.
     *
     * @param context The context, or parent class.
     * @param poiList The list of POIs to create viewholders for.
     */
    PoiButtonAdapter(Context context, List<PointOfInterest> poiList) {
        mContext = context;
        // Collection of objects MUST be assigned like this, or else notifyDataSetChanged() cannot
        // update the data in this adapter.
        //noinspection AssignmentToCollectionOrArrayFieldFromParameter
        mPoiList = poiList;
    }

    @Override
    public PoiButtonViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.list_item_poi, parent, false);
        return new PoiButtonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(PoiButtonViewHolder holder, int position) {
        PointOfInterest poi = mPoiList.get(position);
        holder.bindPoi(poi);
    }

    @Override
    public int getItemCount() {
        return mPoiList == null ? 0 : mPoiList.size();
    }

    /**
     * A ViewHolder class for the POI buttons in POI controller.
     */
    final class PoiButtonViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final Button mPoiButton;

        private PointOfInterest mPoi;

        /**
         * Constructor for the POI button viewholder.
         *
         * @param itemView The item's view.
         */
        private PoiButtonViewHolder(View itemView) {
            super(itemView);
            mPoiButton = itemView.findViewById(R.id.poi_button);
            mPoiButton.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            // Create goal and send to firebase
            if (mListener != null) {
                mListener.onPoiButtonClickResult(mPoi);
            }
        }

        /**
         * Binds data from POI to the POI button.
         *
         * @param poi The POI containing the necessary button data.
         */
        private void bindPoi(PointOfInterest poi) {
            mPoi = poi;
            mPoiButton.setText(mPoi.variables.name);
        }
    }
}
