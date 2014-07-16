package com.cvent.etcd;

/**
 * An enum representing all of the etc status codes taken from here:
 * https://github.com/coreos/etcd/blob/master/Documentation/errorcode.md
 *
 * @author bryan
 */
public enum EtcdStatusCode {

    EcodeKeyNotFound(100),
    EcodeTestFailed(101),
    EcodeNotFile(102),
    EcodeNoMorePeer(103),
    EcodeNotDir(104),
    EcodeNodeExist(105),
    EcodeKeyIsPreserved(106),
    EcodeRootROnly(107),
    EcodeValueRequired(200),
    EcodePrevValueRequired(201),
    EcodeTTLNaN(202),
    EcodeIndexNaN(203),
    EcodeRaftInternal(300),
    EcodeLeaderElect(301),
    EcodeWatcherCleared(400),
    EcodeEventIndexCleared(401);

    private final int statusCode;

    private EtcdStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public int value() {
        return this.statusCode;
    }
}
