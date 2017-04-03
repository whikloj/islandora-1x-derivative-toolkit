package ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever;

/**
 * Exception for issues connecting to remote Islandora instance
 * 
 * @author whikloj
 * @since 2017-03-16
 */
public class ClientAccessException extends RuntimeException {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public ClientAccessException(final String msg) {
        super(msg);
    }

    public ClientAccessException(final String msg, final Throwable rootCause) {
        super(msg, rootCause);
    }
}
