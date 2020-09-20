package ai.cellbots.common;

import android.test.suitebuilder.annotation.SmallTest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests functions in FileUtil.
 */
@SmallTest
public class FileUtilTest {
    // Tests that the multiple paths are joined correctly.
    @Test
    public void testJoinPath() {
        String path1 = "/my/own";
        String path2 = "dir2";
        String path3 = "/dir3";
        String path4 = "/dir4";
        assertEquals("/my/own/dir2", FileUtil.joinPath(path1, path2));
        assertEquals("/my/own/dir2/dir3", FileUtil.joinPath(path1, path2, path3));
        assertEquals("/my/own/dir2/dir3/dir4",
                FileUtil.joinPath(path1, path2, path3, path4));
    }
}
