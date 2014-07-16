package com.cvent.etcd;

/**
 * The result of an etcd operation: http://coreos.com/docs/distributed-configuration/etcd-api/
 * 
 * @author bryan
 */
public class EtcdResult {

    // General values
    private String action;
    private EtcdNode node;
    private EtcdNode prevNode;

    // For errors
    private Integer errorCode;
    private String message;
    private String cause;
    private int index;

    public boolean isError() {
        return errorCode != null;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public EtcdNode getNode() {
        return node;
    }

    public void setNode(EtcdNode node) {
        this.node = node;
    }

    public EtcdNode getPrevNode() {
        return prevNode;
    }

    public void setPrevNode(EtcdNode prevNode) {
        this.prevNode = prevNode;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
    
    
}
