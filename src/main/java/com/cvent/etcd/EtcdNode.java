package com.cvent.etcd;

import java.util.List;

/**
 * The object representing an Etcd node.  See here for more info: 
 * http://coreos.com/docs/distributed-configuration/etcd-api/
 * 
 * @author bryan
 */
public class EtcdNode {

    private String key;
    private long createdIndex;
    private long modifiedIndex;
    private String value;

    // For TTL keys
    private String expiration;
    private Integer ttl;

    // For listings
    private boolean dir;
    private List<EtcdNode> nodes;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getCreatedIndex() {
        return createdIndex;
    }

    public void setCreatedIndex(long createdIndex) {
        this.createdIndex = createdIndex;
    }

    public long getModifiedIndex() {
        return modifiedIndex;
    }

    public void setModifiedIndex(long modifiedIndex) {
        this.modifiedIndex = modifiedIndex;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getExpiration() {
        return expiration;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public boolean isDir() {
        return dir;
    }

    public void setDir(boolean dir) {
        this.dir = dir;
    }

    public List<EtcdNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<EtcdNode> nodes) {
        this.nodes = nodes;
    }
    
    
}
