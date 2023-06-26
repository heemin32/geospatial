/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.ip2geo.cache;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;

import lombok.SneakyThrows;

import org.junit.Before;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.geospatial.GeospatialTestHelper;
import org.opensearch.geospatial.ip2geo.Ip2GeoTestCase;
import org.opensearch.geospatial.ip2geo.common.DatasourceState;
import org.opensearch.geospatial.ip2geo.jobscheduler.Datasource;
import org.opensearch.index.engine.Engine;
import org.opensearch.index.shard.ShardId;

public class Ip2GeoCacheTests extends Ip2GeoTestCase {
    private Ip2GeoCache ip2GeoCache;

    @Before
    public void init() {
        ip2GeoCache = new Ip2GeoCache(datasourceDao);
    }

    public void testGetIndexName_whenCalled_thenReturnIndexName() {
        Datasource datasource = randomDatasource();
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList(datasource));

        // Run
        String indexName = ip2GeoCache.getIndexName(datasource.getName());

        // Verify
        assertEquals(datasource.currentIndexName(), indexName);
    }

    public void testIsExpired_whenExpired_thenReturnTrue() {
        Datasource datasource = randomDatasource();
        datasource.getUpdateStats().setLastSucceededAt(Instant.MIN);
        datasource.getUpdateStats().setLastSkippedAt(null);
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList(datasource));

        // Run
        boolean isExpired = ip2GeoCache.isExpired(datasource.getName());

        // Verify
        assertTrue(isExpired);
    }

    public void testIsExpired_whenNotExpired_thenReturnFalse() {
        Datasource datasource = randomDatasource();
        datasource.getUpdateStats().setLastSucceededAt(Instant.now());
        datasource.getUpdateStats().setLastSkippedAt(null);
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList(datasource));

        // Run
        boolean isExpired = ip2GeoCache.isExpired(datasource.getName());

        // Verify
        assertFalse(isExpired);
    }

    public void testHas_whenHasDatasource_thenReturnTrue() {
        Datasource datasource = randomDatasource();
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList(datasource));

        // Run
        boolean hasDatasource = ip2GeoCache.has(datasource.getName());

        // Verify
        assertTrue(hasDatasource);
    }

    public void testHas_whenNoDatasource_thenReturnFalse() {
        Datasource datasource = randomDatasource();
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList(datasource));

        String datasourceName = GeospatialTestHelper.randomLowerCaseString();
        // Run
        boolean hasDatasource = ip2GeoCache.has(datasourceName);

        // Verify
        assertFalse(hasDatasource);
    }

    public void testGetState_whenCalled_thenReturnState() {
        Datasource datasource = randomDatasource();
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList(datasource));

        // Run
        DatasourceState state = ip2GeoCache.getState(datasource.getName());

        // Verify
        assertEquals(datasource.getState(), state);
    }

    @SneakyThrows
    public void testPostIndex_whenFailed_thenNoUpdate() {
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList());
        Datasource datasource = randomDatasource();

        ShardId shardId = mock(ShardId.class);
        Engine.Index index = mock(Engine.Index.class);
        BytesReference bytesReference = BytesReference.bytes(datasource.toXContent(XContentFactory.jsonBuilder(), null));
        when(index.source()).thenReturn(bytesReference);
        Engine.IndexResult result = mock(Engine.IndexResult.class);
        when(result.getResultType()).thenReturn(Engine.Result.Type.FAILURE);

        // Run
        ip2GeoCache.postIndex(shardId, index, result);

        // Verify
        assertFalse(ip2GeoCache.has(datasource.getName()));
        assertTrue(ip2GeoCache.isExpired(datasource.getName()));
        assertNull(ip2GeoCache.getIndexName(datasource.getName()));
        assertNull(ip2GeoCache.getState(datasource.getName()));
    }

    @SneakyThrows
    public void testPostIndex_whenSucceed_thenUpdate() {
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList());
        Datasource datasource = randomDatasource();

        ShardId shardId = mock(ShardId.class);
        Engine.Index index = mock(Engine.Index.class);
        BytesReference bytesReference = BytesReference.bytes(datasource.toXContent(XContentFactory.jsonBuilder(), null));
        when(index.source()).thenReturn(bytesReference);
        Engine.IndexResult result = mock(Engine.IndexResult.class);
        when(result.getResultType()).thenReturn(Engine.Result.Type.SUCCESS);

        // Run
        ip2GeoCache.postIndex(shardId, index, result);

        // Verify
        assertTrue(ip2GeoCache.has(datasource.getName()));
        assertFalse(ip2GeoCache.isExpired(datasource.getName()));
        assertEquals(datasource.currentIndexName(), ip2GeoCache.getIndexName(datasource.getName()));
        assertEquals(datasource.getState(), ip2GeoCache.getState(datasource.getName()));
    }

    public void testPostDelete_whenFailed_thenNoUpdate() {
        Datasource datasource = randomDatasource();
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList(datasource));

        ShardId shardId = mock(ShardId.class);
        Engine.Delete index = mock(Engine.Delete.class);
        Engine.DeleteResult result = mock(Engine.DeleteResult.class);
        when(result.getResultType()).thenReturn(Engine.Result.Type.FAILURE);

        // Run
        ip2GeoCache.postDelete(shardId, index, result);

        // Verify
        assertTrue(ip2GeoCache.has(datasource.getName()));
    }

    public void testPostDelete_whenSucceed_thenUpdate() {
        Datasource datasource = randomDatasource();
        when(datasourceDao.getAllDatasources()).thenReturn(Arrays.asList(datasource));

        ShardId shardId = mock(ShardId.class);
        Engine.Delete index = mock(Engine.Delete.class);
        when(index.id()).thenReturn(datasource.getName());
        Engine.DeleteResult result = mock(Engine.DeleteResult.class);
        when(result.getResultType()).thenReturn(Engine.Result.Type.SUCCESS);

        // Run
        ip2GeoCache.postDelete(shardId, index, result);

        // Verify
        assertFalse(ip2GeoCache.has(datasource.getName()));
    }
}
