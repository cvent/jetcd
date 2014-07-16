package com.cvent.etcd;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.util.concurrent.ListenableFuture;

public class SmokeTest {

    String prefix;
    EtcdClient client;

    @Before
    public void initialize() {
        this.prefix = "/unittest-" + UUID.randomUUID().toString();
        this.client = new EtcdClient(URI.create("http://127.0.0.1:4001/"));
    }

    @Test
    public void setAndGet() throws Exception {
        String key = prefix + "/message";

        EtcdResult result;

        result = this.client.set(key, "hello");
        Assert.assertEquals("set", result.getAction());
        Assert.assertEquals("hello", result.getNode().getValue());
        Assert.assertNull(result.getPrevNode());

        result = this.client.get(key);
        Assert.assertEquals("get", result.getAction());
        Assert.assertEquals("hello", result.getNode().getValue());
        Assert.assertNull(result.getPrevNode());

        result = this.client.set(key, "world");
        Assert.assertEquals("set", result.getAction());
        Assert.assertEquals("world", result.getNode().getValue());
        Assert.assertNotNull(result.getPrevNode());
        Assert.assertEquals("hello", result.getPrevNode().getValue());

        result = this.client.get(key);
        Assert.assertEquals("get", result.getAction());
        Assert.assertEquals("world", result.getNode().getValue());
        Assert.assertNull(result.getPrevNode());
    }

    @Test
    public void getNonExistentKey() throws Exception {
        String key = prefix + "/doesnotexist";

        EtcdResult result;

        result = this.client.get(key);
        Assert.assertNull(result);
    }

    @Test
    public void testDelete() throws Exception {
        String key = prefix + "/testDelete";

        EtcdResult result;

        this.client.set(key, "hello");

        result = this.client.get(key);
        Assert.assertEquals("hello", result.getNode().getValue());

        result = this.client.delete(key);
        Assert.assertEquals("delete", result.getAction());
        Assert.assertEquals(null, result.getNode().getValue());
        Assert.assertNotNull(result.getPrevNode());
        Assert.assertEquals("hello", result.getPrevNode().getValue());

        result = this.client.get(key);
        Assert.assertNull(result);
    }

    @Test
    public void deleteNonExistentKey() throws Exception {
        String key = prefix + "/doesnotexist";

        try {
            /*EtcdResult result =*/ this.client.delete(key);
            Assert.fail();
        } catch (EtcdClientException e) {
            Assert.assertTrue(e.isEtcdError(100));
        }
    }

    @Test
    public void testTtl() throws Exception {
        String key = prefix + "/ttl";

        EtcdResult result;

        result = this.client.set(key, "hello", 2);
        Assert.assertNotNull(result.getNode().getExpiration());
        Assert.assertTrue(result.getNode().getTtl() == 2 || result.getNode().getTtl() == 1);

        result = this.client.get(key);
        Assert.assertEquals("hello", result.getNode().getValue());

        // TTL was redefined to mean TTL + 0.5s (Issue #306)
        Thread.sleep(3000);

        result = this.client.get(key);
        Assert.assertNull(result);
    }

    @Test
    public void testCAS() throws Exception {
        String key = prefix + "/cas";

        EtcdResult result;

        this.client.set(key, "hello");
        result = this.client.get(key);
        Assert.assertEquals("hello", result.getNode().getValue());

        result = this.client.cas(key, "world", "world");
        Assert.assertEquals(true, result.isError());
        result = this.client.get(key);
        Assert.assertEquals("hello", result.getNode().getValue());

        result = this.client.cas(key, "hello", "world");
        Assert.assertEquals(false, result.isError());
        result = this.client.get(key);
        Assert.assertEquals("world", result.getNode().getValue());
    }

    @Test
    public void testWatchPrefix() throws Exception {
        String key = prefix + "/watch";

        EtcdResult result = this.client.set(key + "/f2", "f2");
        Assert.assertTrue(!result.isError());
        Assert.assertNotNull(result.getNode());
        Assert.assertEquals("f2", result.getNode().getValue());

        ListenableFuture<EtcdResult> watchFuture = this.client.watch(key,
                result.getNode().getModifiedIndex() + 1,
                true);
        try {
            EtcdResult watchResult = watchFuture
                    .get(100, TimeUnit.MILLISECONDS);
            Assert.fail("Subtree watch fired unexpectedly: " + watchResult);
        } catch (TimeoutException e) {
            // Expected
        }

        Assert.assertFalse(watchFuture.isDone());

        result = this.client.set(key + "/f1", "f1");
        Assert.assertTrue(!result.isError());
        Assert.assertNotNull(result.getNode());
        Assert.assertEquals("f1", result.getNode().getValue());

        EtcdResult watchResult = watchFuture.get(100, TimeUnit.MILLISECONDS);

        Assert.assertNotNull(watchResult);
        Assert.assertTrue(!watchResult.isError());
        Assert.assertNotNull(watchResult.getNode());

        {
            Assert.assertEquals(key + "/f1", watchResult.getNode().getKey());
            Assert.assertEquals("f1", watchResult.getNode().getValue());
            Assert.assertEquals("set", watchResult.getAction());
            Assert.assertNull(result.getPrevNode());
            Assert.assertEquals(result.getNode().getModifiedIndex(),
                    watchResult.getNode().getModifiedIndex());
        }
    }

    @Test
    public void testList() throws Exception {
        String key = prefix + "/dir";

        EtcdResult result;

        result = this.client.set(key + "/f1", "f1");
        Assert.assertEquals("f1", result.getNode().getValue());
        result = this.client.set(key + "/f2", "f2");
        Assert.assertEquals("f2", result.getNode().getValue());
        result = this.client.set(key + "/f3", "f3");
        Assert.assertEquals("f3", result.getNode().getValue());
        result = this.client.set(key + "/subdir1/f", "f");
        Assert.assertEquals("f", result.getNode().getValue());

        EtcdResult listing = this.client.listChildren(key);
        Assert.assertEquals(4, listing.getNode().getNodes().size());
        Assert.assertEquals("get", listing.getAction());

        {
            EtcdNode child = listing.getNode().getNodes().get(0);
            Assert.assertEquals(key + "/f1", child.getKey());
            Assert.assertEquals("f1", child.getValue());
            Assert.assertEquals(false, child.isDir());
        }
        {
            EtcdNode child = listing.getNode().getNodes().get(1);
            Assert.assertEquals(key + "/f2", child.getKey());
            Assert.assertEquals("f2", child.getValue());
            Assert.assertEquals(false, child.isDir());
        }
        {
            EtcdNode child = listing.getNode().getNodes().get(2);
            Assert.assertEquals(key + "/f3", child.getKey());
            Assert.assertEquals("f3", child.getValue());
            Assert.assertEquals(false, child.isDir());
        }
        {
            EtcdNode child = listing.getNode().getNodes().get(3);
            Assert.assertEquals(key + "/subdir1", child.getKey());
            Assert.assertEquals(null, child.getValue());
            Assert.assertEquals(true, child.isDir());
        }
    }

    @Test
    public void testGetVersion() throws Exception {
        String version = this.client.getVersion();
        Assert.assertTrue(version.startsWith("etcd 0."));
    }

}
