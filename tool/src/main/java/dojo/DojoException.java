package dojo;

/** 可以直接给用户看的错误：消息本身就是提示，不带栈。 */
public class DojoException extends RuntimeException {

    public DojoException(String message) {
        super(message);
    }

    public DojoException(String message, Throwable cause) {
        super(message, cause);
    }
}
