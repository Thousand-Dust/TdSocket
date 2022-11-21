package org.td.socket;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Thousand Dust
 */
public class Utils {

    public static void close(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
