package cc.brainbook.android.multithreaddownload.enumeration;

import java.util.HashMap;
import java.util.Map;

///\com\amazonaws\mobileconnectors\s3\transferutility\DownloadState.java
/**
 * The current state of a transfer.
 *
 * A transfer is initially in NEW state when added.
 * It will turn into STARTED once it starts.
 * Customers can pause or cancel the transfer when needed and turns it into PAUSED or STOPPED state respectively.
 * Finally the transfer will either succeed as COMPLETED or fail as FAILED.
 * WAITING_FOR_NETWORK state may kick in for an active transfer when network is lost.
 * The other enum values are internal use only.
 */
public enum DownloadState {
    /**
     * This state represents a transfer that has been queued, but has not yet
     * initialized or started
     */
    NEW,

    /**
     * This state represents a transfer that has been queued, but has not yet
     * initialized
     */
    INITIALIZED,

    /**
     * This state represents a transfer that is currently uploading or
     * downloading data
     */
    STARTED,

    /**
     * This state represents a transfer that is paused
     */
    PAUSED,

    /**
     * This state represents a transfer that is completed
     */
    COMPLETED,

    /**
     * This state represents a transfer that is stopped (canceled)
     */
    STOPPED,

    /**
     * This state represents a transfer that has failed
     */
    FAILED,

    /**
     * This state represents a transfer that is currently on hold, waiting for
     * the network to become available
     */
    WAITING_FOR_NETWORK,

    /**
     * This is an internal value used to detect if the current transfer is in an
     * unknown state
     */
    UNKNOWN;

    private static final Map<String, DownloadState> MAP;
    static {
        MAP = new HashMap<>();
        for (final DownloadState state : DownloadState.values()) {
            MAP.put(state.toString(), state);
        }
    }

    /**
     * Returns the transfer state from string
     *
     * @param stateAsString state of the transfer represented as string.
     * @return the {@link DownloadState}
     */
    public static DownloadState getState(String stateAsString) {
        if (MAP.containsKey(stateAsString)) {
            return MAP.get(stateAsString);
        }

        return UNKNOWN;
    }
}

