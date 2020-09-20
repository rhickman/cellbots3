package ai.cellbots.robot.navigation;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.cellbots.robot.costmap.CostMapPose;

/**
 * Path class. The coordinates are represented by CostMapPose.
 */
// TODO: (FlorGrosso) This class gives the abstraction needed to working with different paths, made
// TODO: as a list of CostMapPose elements, which could be grid cells, transforms, or any other type
public class Path {
    private static final String TAG = Path.class.getSimpleName();
    private final ArrayList<CostMapPose> mPath;

    /**
     * Class constructor, no input arguments.
     */
    public Path() {
        mPath = new ArrayList<>();
    }

    /**
     * Class constructor. Makes a path with the origin.
     *
     * @param origin The start point of the map.
     */
    public Path(CostMapPose origin) {
        mPath = new ArrayList<>();
        mPath.add(origin);
    }

    /**
     * Class constructor. Makes a path with multiple elements.
     *
     * @param poses An array of CostMapPose objects.
     */
    public Path(CostMapPose[] poses) {
        mPath = new ArrayList<>();
        Collections.addAll(mPath, poses);
    }
    /**
     * Class constructor. Makes a path with multiple elements.
     *
     * @param poses An array of CostMapPose objects.
     */
    public Path(List<CostMapPose> poses) {
        mPath = new ArrayList<>(poses);
    }

    /**
     * Add an element to the path's tail.
     *
     * @param tail A CostMapPose to add.
     */
    public void addElementToTail(CostMapPose tail) {
        mPath.add(tail);
    }

    /**
     * Get an element from the path.
     *
     * @param index The index of the element to get from the path.
     * @return CostMapPose stored under the indicated index.
     */
    public CostMapPose get(int index) {
        if(index < 0  || index > mPath.size()) {
            Log.w(TAG, "get: index out of bounds");
            return null;
        }
        return mPath.get(index);
    }

    /**
     * Get the size of the path.
     *
     * @return number of elements in the path.
     */
    public int size() {
        return mPath.size();
    }

    /**
     * Get the last element of the path.
     *
     * @return last element.
     */
    public CostMapPose last() {
        return mPath.get(mPath.size() - 1);
    }

    /**
     * Remove from a specified path all the elements that are also contained within this path.
     *
     * @param originList to remove elements from.
     */
    @SuppressWarnings("unused")
    public void removeFrom(ArrayList<CostMapPose> originList) {
        originList.removeAll(mPath);
    }

    /**
     * Gets the path as a list.
     *
     * @return The path.
     */
    public List<CostMapPose> asList() {
        return new ArrayList<>(mPath);
    }

    /**
     * Remove the first occurrence of the specified CostMapPose from this path.
     *
     * @param element to be removed.
     */
    @SuppressWarnings("unused")
    public void removeElement(CostMapPose element) {
        mPath.remove(element);
    }

    /**
     * Copies the path.
     *
     * @return The copy of the path.
     */
    public Path copy() {
        Path pathCopy = new Path();
        for (CostMapPose element : mPath) {
            pathCopy.addElementToTail(element.copy());
        }
        return pathCopy;
    }

    /**
     * Flip a path.
     */
    public void reverse() {
        Collections.reverse(mPath);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Path)) {
            return false;
        }

        Path path = (Path) obj;
        return mPath.equals(path.mPath);
    }

    @Override
    public String toString() {
        StringBuilder pathString = new StringBuilder();
        for (int i = 0; i < mPath.size(); i++) {
            pathString.append(mPath.get(i).toString());
            pathString.append(" ");
        }
        return pathString.toString();
    }

    /**
     * Checks if the path is valid. This condition is true, when:
     * 1) Path is not null
     * 2) Path has, at least, two elements
     *
     * @return True if the mPath is valid
     */
    public boolean isValid() {
        return (mPath != null) && (mPath.size() > 1);
    }
}