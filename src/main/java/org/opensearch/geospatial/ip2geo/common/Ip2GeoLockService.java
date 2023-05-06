/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.ip2geo.common;

import static org.opensearch.geospatial.ip2geo.jobscheduler.DatasourceExtension.JOB_INDEX_NAME;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.jobscheduler.spi.LockModel;
import org.opensearch.jobscheduler.spi.utils.LockService;

/**
 * A wrapper of job scheduler's lock service for datasource
 */
public class Ip2GeoLockService {
    private final ClusterService clusterService;
    private final Client client;
    private final LockService lockService;

    /**
     * Constructor
     *
     * @param clusterService the cluster service
     * @param client the client
     */
    @Inject
    public Ip2GeoLockService(final ClusterService clusterService, final Client client) {
        this.clusterService = clusterService;
        this.client = client;
        this.lockService = new LockService(client, clusterService);
    }

    /**
     * Wrapper method of LockService#acquireLockWithId
     *
     * Datasource use its name as doc id in job scheduler. Therefore, we can use datasource name to acquire
     * a lock on a datasource.
     *
     * @param datasourceName datasourceName to acquire lock on
     * @param lockDurationSeconds the lock duration in seconds
     * @param listener the listener
     */
    public void acquireLock(final String datasourceName, final Long lockDurationSeconds, final ActionListener<LockModel> listener) {
        lockService.acquireLockWithId(JOB_INDEX_NAME, lockDurationSeconds, datasourceName, listener);
    }

    /**
     * Wrapper method of LockService#release
     *
     * @param lockModel the lock model
     * @param listener the listener
     */
    public void releaseLock(final LockModel lockModel, final ActionListener<Boolean> listener) {
        lockService.release(lockModel, listener);
    }

    /**
     * Synchronous method of LockService#renewLock
     *
     * @param lockModel lock to renew
     * @param timeout timeout in milliseconds precise
     * @return renewed lock if renew succeed and null otherwise
     */
    public LockModel renewLock(final LockModel lockModel, final TimeValue timeout) {
        AtomicReference<LockModel> lockReference = new AtomicReference();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        lockService.renewLock(lockModel, new ActionListener<>() {
            @Override
            public void onResponse(final LockModel lockModel) {
                lockReference.set(lockModel);
                countDownLatch.countDown();
            }

            @Override
            public void onFailure(final Exception e) {
                lockReference.set(null);
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await(timeout.getMillis(), TimeUnit.MILLISECONDS);
            return lockReference.get();
        } catch (InterruptedException e) {
            return null;
        }
    }
}