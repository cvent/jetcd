package com.cvent.etcd;

import java.util.List;

/**
 * An object encapsulating an array of EtcdResult objects
 * 
 * @author bryan
 */
public class EtcdResultList {

    private List<EtcdResult> resultList;

    /**
     * Get the value of resultList
     *
     * @return the value of resultList
     */
    public List<EtcdResult> getResultList() {
        return resultList;
    }

    /**
     * Set the value of resultList
     *
     * @param resultList new value of resultList
     */
    public void setResultList(List<EtcdResult> resultList) {
        this.resultList = resultList;
    }

}
