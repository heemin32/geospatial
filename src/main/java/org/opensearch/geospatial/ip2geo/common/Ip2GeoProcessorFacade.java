/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.geospatial.ip2geo.common;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.opensearch.common.inject.Inject;
import org.opensearch.geospatial.ip2geo.processor.Ip2GeoProcessor;
import org.opensearch.ingest.IngestMetadata;
import org.opensearch.ingest.IngestService;

public class Ip2GeoProcessorFacade {
    private final IngestService ingestService;

    @Inject
    public Ip2GeoProcessorFacade(final IngestService ingestService) {
        this.ingestService = ingestService;
    }

    public List<Ip2GeoProcessor> getProcessors(final String datasourceName) {
        IngestMetadata ingestMetadata = ingestService.getClusterService().state().getMetadata().custom(IngestMetadata.TYPE);
        if (ingestMetadata == null) {
            return Collections.emptyList();
        }
        return ingestMetadata.getPipelines()
            .keySet()
            .stream()
            .flatMap(pipelineId -> ingestService.getProcessorsInPipeline(pipelineId, Ip2GeoProcessor.class).stream())
            .filter(ip2GeoProcessor -> ip2GeoProcessor.getDatasourceName().equals(datasourceName))
            .collect(Collectors.toList());
    }
}
