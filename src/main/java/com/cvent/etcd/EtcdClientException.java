package com.cvent.etcd;

import java.io.IOException;

/**
 * Exception used for remote etcd api calls
 * 
 * @author bryan
 */
public class EtcdClientException extends IOException {
    private static final long serialVersionUID = 1L;

    private final Integer httpStatusCode;

    private final EtcdResult result;

    public EtcdClientException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = null;
        this.result = null;
    }

    public EtcdClientException(String message, int httpStatusCode) {
        super(message + "(" + httpStatusCode + ")");
        this.httpStatusCode = httpStatusCode;
        this.result = null;
    }

    public EtcdClientException(String message, EtcdResult result) {
        super(message);
        this.httpStatusCode = null;
        this.result = result;
    }
    
    public int getHttpStatusCode() {
      return httpStatusCode;
    }

    public boolean isHttpError(int statusCode) {
        return (this.httpStatusCode != null && statusCode == this.httpStatusCode);
    }

    public boolean isEtcdError(int etcdCode) {
        return (this.result != null && this.result.getErrorCode() != null && etcdCode == this.result.getErrorCode());
    }
    
    public EtcdResult getEtcdResult() {
        return result;
    }
}
