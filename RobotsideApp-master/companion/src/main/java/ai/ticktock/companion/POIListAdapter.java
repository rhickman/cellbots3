package ai.cellbots.companion;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkArgument;

public class POIListAdapter extends ArrayAdapter<String> {
    private Context mContext;
    private int mViewResourceId;
    private ArrayList<String> mListItems = new ArrayList<>();
    private MainActivity mListener;

    /**
     * Class constructor
     */
    public POIListAdapter(Context context, int viewResourceId, ArrayList<String> items) {
        super(context, viewResourceId, items);
        mContext = context;
        mListItems.clear();
        mListItems.addAll(items);
        mViewResourceId = viewResourceId;
    }

    public void setListener(MainActivity l) {
        mListener = l;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        // Only inflate the view if it is null, allowing to recycle an old one.
        if (convertView == null) {
            LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
            convertView = inflater.inflate(mViewResourceId, parent, false);
        }

        // Sanity check for position
        checkArgument(position >= 0, "Position should not be negative, ", position);
        checkArgument(position < getCount(), "Position should be less than the POI count, ", position);

        // Fill view with items
        if (getCount() > 0) {
            fillView(convertView, getItem(position));
        }

        return convertView;
    }

    /**
     * This method sets the items on the list.
     */
    private void fillView(View view, String item) {
        final TextView name = view.findViewById(R.id.textViewItem);
        name.setText(item);

        ImageButton patrolButton = view.findViewById(R.id.patrolButton);
        // Handles clicks on patrol button.
        patrolButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle the patrol POI task to the main activity.
                mListener.patrolPOI(name.getText().toString());
            }
        });

        ImageButton deleteButton = view.findViewById(R.id.deleteButton);
        // Handles clicks on delete button. First confirm that the user really wants to delete a POI
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                        getContext());

                builder.setTitle("Are you sure you want to delete this POI?");

                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Handle the delete task to the main activity.
                        mListener.deletePOI(name.getText().toString());
                    }
                });
                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
            }
        });
    }
}


