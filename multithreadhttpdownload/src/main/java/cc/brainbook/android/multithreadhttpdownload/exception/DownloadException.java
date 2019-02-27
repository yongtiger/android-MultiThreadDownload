package cc.brainbook.android.multithreadhttpdownload.exception;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class DownloadException extends RuntimeException {
    public static final int EXCEPTION_FILE_URL_NULL = 0;
    public static final int EXCEPTION_FILE_NAME_NULL = 1;
    public static final int EXCEPTION_FILE_NOT_FOUND = 2;
    public static final int EXCEPTION_MALFORMED_URL = 3;
    public static final int EXCEPTION_UNKNOWN_HOST = 4;
    public static final int EXCEPTION_IO_EXCEPTION = 5;
    public static final int EXCEPTION_PROTOCOL_EXCEPTION = 6;
    public static final int EXCEPTION_SAVE_PATH_MKDIR = 7;
    public static final int EXCEPTION_NO_INIT = 8;

    private int code;

    public DownloadException(@ExceptionType int code) {
        this.code = code;
    }

    public DownloadException(@ExceptionType int code, String message) {
        super(message);
        this.code = code;
    }

    public DownloadException(@ExceptionType int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DownloadException(@ExceptionType int code, Throwable cause) {
        super(cause);
        this.code = code;
    }


    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    /**
     * Download exception type.
     */
    @IntDef({EXCEPTION_FILE_URL_NULL,
            EXCEPTION_FILE_NAME_NULL,
            EXCEPTION_FILE_NOT_FOUND,
            EXCEPTION_MALFORMED_URL,
            EXCEPTION_UNKNOWN_HOST,
            EXCEPTION_IO_EXCEPTION,
            EXCEPTION_PROTOCOL_EXCEPTION,
            EXCEPTION_SAVE_PATH_MKDIR,
            EXCEPTION_NO_INIT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ExceptionType {}

}
