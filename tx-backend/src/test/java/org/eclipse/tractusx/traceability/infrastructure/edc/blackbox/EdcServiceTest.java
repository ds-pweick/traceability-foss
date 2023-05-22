/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/
package org.eclipse.tractusx.traceability.infrastructure.edc.blackbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.catalog.Catalog;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.notification.ContractNegotiationDto;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.notification.TransferId;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.offer.ContractOffer;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.policy.AtomicConstraint;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.policy.LiteralExpression;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.policy.Operator;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.policy.Permission;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.policy.Policy;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.transfer.DataAddress;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.transfer.TransferRequestDto;
import org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.transfer.TransferType;
import org.eclipse.tractusx.traceability.infrastructure.edc.properties.EdcProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.EdcService.JSON;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset.Builder;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset.PROPERTY_CONTENT_TYPE;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset.PROPERTY_DESCRIPTION;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset.PROPERTY_ID;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset.PROPERTY_NAME;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset.PROPERTY_NOTIFICATION_METHOD;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset.PROPERTY_NOTIFICATION_TYPE;
import static org.eclipse.tractusx.traceability.infrastructure.edc.blackbox.asset.Asset.PROPERTY_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EdcServiceTest {

    @Mock
    private HttpCallService httpCallService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private EdcProperties edcProperties;

    @InjectMocks
    private EdcService edcService;

    private static final String CONSUMER_EDC_DATA_MANAGEMENT_URL = "http://consumer-edc-data-management.com";
    private static final String PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL = "http://provider-connector-control-plane-ids.com";

    @Captor
    private ArgumentCaptor<Request> requestCaptor;

    @Test
    void givenEmptyCatalogContractOffers_whenFindNotificationContractOffer_thenThrowBadRequestException() throws IOException {
        // given
        Catalog catalog = Catalog.Builder.newInstance().id("234").contractOffers(emptyList()).build();

        Map<String, String> header = new HashMap<>();
        when(httpCallService.getCatalogFromProvider(CONSUMER_EDC_DATA_MANAGEMENT_URL, PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL, header)).thenReturn(catalog);

        // when
        assertThrows(BadRequestException.class, () -> edcService.findNotificationContractOffer(CONSUMER_EDC_DATA_MANAGEMENT_URL,
                PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL, header, true));
    }

    @Test
    void testFindNotificationContractOfferForInitialNotification() throws IOException {
        // given
        Map<String, Object> properties = new HashMap<>();
        properties.put(PROPERTY_ID, 123);
        properties.put(PROPERTY_NAME, "My Asset");
        properties.put(PROPERTY_DESCRIPTION, "This is a description of my asset.");
        properties.put(PROPERTY_VERSION, 1.0);
        properties.put(PROPERTY_CONTENT_TYPE, "image/jpeg");
        properties.put(PROPERTY_NOTIFICATION_TYPE, "qualityinvestigation");
        properties.put(PROPERTY_NOTIFICATION_METHOD, "receive");

        Permission permission = Permission.Builder.newInstance()
                .constraint(AtomicConstraint.Builder.newInstance()
                        .leftExpression(new LiteralExpression("idsc:PURPOSE"))
                        .rightExpression(new LiteralExpression("ID 3.0 Trace"))
                        .operator(Operator.EQ)
                        .build()
                )
                .build();
        Policy policy = Policy.Builder.newInstance().permission(permission).build();
        Asset asset = Builder.newInstance().properties(properties).build();
        ContractOffer expectedContractOffer = ContractOffer.Builder.newInstance().id("123").policy(policy).asset(asset).build();
        Catalog catalog = Catalog.Builder.newInstance().id("234").contractOffers(List.of(expectedContractOffer)).build();


        Map<String, String> header = new HashMap<>();
        when(httpCallService.getCatalogFromProvider(CONSUMER_EDC_DATA_MANAGEMENT_URL, PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL, header)).thenReturn(catalog);

        // when
        Optional<ContractOffer> contractOfferResult = edcService.findNotificationContractOffer(CONSUMER_EDC_DATA_MANAGEMENT_URL,
                PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL, header, true);

        // then
        assertTrue(contractOfferResult.isPresent());
        assertEquals(expectedContractOffer, contractOfferResult.get());
    }

    @Test
    void testFindNotificationContractOfferForUpdateNotification() throws IOException {
        // given
        Map<String, Object> properties = new HashMap<>();
        properties.put(PROPERTY_ID, 123);
        properties.put(PROPERTY_NAME, "My Asset");
        properties.put(PROPERTY_DESCRIPTION, "This is a description of my asset.");
        properties.put(PROPERTY_VERSION, 1.0);
        properties.put(PROPERTY_CONTENT_TYPE, "image/jpeg");
        properties.put(PROPERTY_NOTIFICATION_TYPE, "qualityinvestigation");
        properties.put(PROPERTY_NOTIFICATION_METHOD, "update");

        Permission permission = Permission.Builder.newInstance()
                .constraint(AtomicConstraint.Builder.newInstance()
                        .leftExpression(new LiteralExpression("idsc:PURPOSE"))
                        .rightExpression(new LiteralExpression("ID 3.0 Trace"))
                        .operator(Operator.EQ)
                        .build()
                )
                .build();
        Policy policy = Policy.Builder.newInstance().permission(permission).build();
        Asset asset = Builder.newInstance().properties(properties).build();
        ContractOffer expectedContractOffer = ContractOffer.Builder.newInstance().id("123").policy(policy).asset(asset).build();
        Catalog catalog = Catalog.Builder.newInstance().id("234").contractOffers(List.of(expectedContractOffer)).build();


        Map<String, String> header = new HashMap<>();
        when(httpCallService.getCatalogFromProvider(CONSUMER_EDC_DATA_MANAGEMENT_URL, PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL, header)).thenReturn(catalog);

        // when
        Optional<ContractOffer> contractOfferResult = edcService.findNotificationContractOffer(CONSUMER_EDC_DATA_MANAGEMENT_URL,
                PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL, header, false);

        // then
        assertTrue(contractOfferResult.isPresent());
        assertEquals(expectedContractOffer, contractOfferResult.get());
    }

    @Test
    void testFindNotificationContractOfferWrongNotificationType() throws IOException {
        // given
        Map<String, Object> properties = new HashMap<>();
        properties.put(PROPERTY_ID, 123);
        properties.put(PROPERTY_NAME, "My Asset");
        properties.put(PROPERTY_DESCRIPTION, "This is a description of my asset.");
        properties.put(PROPERTY_VERSION, 1.0);
        properties.put(PROPERTY_CONTENT_TYPE, "image/jpeg");
        properties.put(PROPERTY_NOTIFICATION_TYPE, "asfd");
        properties.put(PROPERTY_NOTIFICATION_METHOD, "receive");

        Policy policy = Policy.Builder.newInstance().build();
        Asset asset = Builder.newInstance().properties(properties).build();
        ContractOffer expectedContractOffer = ContractOffer.Builder.newInstance().id("123").policy(policy).asset(asset).build();
        Catalog catalog = Catalog.Builder.newInstance().id("234").contractOffers(List.of(expectedContractOffer)).build();


        Map<String, String> header = new HashMap<>();
        when(httpCallService.getCatalogFromProvider(CONSUMER_EDC_DATA_MANAGEMENT_URL,
                PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL, header)).thenReturn(catalog);

        // when
        Optional<ContractOffer> contractOfferResult =
                edcService.findNotificationContractOffer(CONSUMER_EDC_DATA_MANAGEMENT_URL,
                        PROVIDER_CONNECTOR_CONTROL_PLANE_IDS_URL, header, false);

        // then
        assertTrue(contractOfferResult.isEmpty());
    }

    @Test
    void givenAssetId_whenInitializeContractNegotiation_thenProcessCorrectly() throws IOException, InterruptedException {
        // given
        final String providerConnectorUrl = "http://provider";
        final String assetId = "assetId";
        final String offerId = "offerId";
        final Policy policy = null;
        final String consumerEdcUrl = "http://consumer";
        final Map<String, String> headers = emptyMap();
        final ContractNegotiationDto negotiation = ContractNegotiationDto.Builder.newInstance()
                .id("negotiationId")
                .state("CONFIRMED")
                .contractAgreementId("contractAgreementId")
                .build();
        final TransferId transferId = TransferId.Builder.newInstance()
                .id("transferId")
                .build();

        when(edcProperties.getIdsPath()).thenReturn("/path");
        when(objectMapper.writeValueAsString(any())).thenReturn("requestBody");
        when(httpCallService.sendRequest(
                any(),
                any())).thenReturn(transferId).thenReturn(negotiation);

        // when
        final String result = edcService.initializeContractNegotiation(
                providerConnectorUrl,
                assetId,
                offerId,
                policy,
                consumerEdcUrl,
                headers);

        // then
        assertThat(result).isEqualTo("contractAgreementId");

    }

    @Test
    void givenData_whenInitiateHttpProxyTransferProcess_thenProperRequestIsSent() throws IOException {
        // given
        final String agreementId = "agreementId";
        final String assetId = "assetId";
        final String consumerEdcDataManagementUrl = "http://consumerUrl";
        final String providerConnectorControlPlaneIDSUrl = "http://providerUrl";
        final Map<String, String> headers = emptyMap();

        var url = consumerEdcDataManagementUrl + edcProperties.getTransferPath();

        DataAddress dataDestination = DataAddress.Builder.newInstance().type("HttpProxy").build();
        TransferType transferType = TransferType.Builder.transferType().contentType("application/octet-stream").isFinite(true).build();

        TransferRequestDto transferRequest = TransferRequestDto.Builder.newInstance()
                .assetId(assetId).contractId(agreementId).connectorId("provider").connectorAddress(providerConnectorControlPlaneIDSUrl)
                .protocol("ids-multipart").dataDestination(dataDestination).managedResources(false).transferType(transferType)
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("request");

        var requestBody = RequestBody.create(objectMapper.writeValueAsString(transferRequest), JSON);
        var request = new Request.Builder().url(url).post(requestBody);

        headers.forEach(request::addHeader);

        // when
        edcService.initiateHttpProxyTransferProcess(agreementId, assetId, consumerEdcDataManagementUrl, providerConnectorControlPlaneIDSUrl, headers);

        // then
        verify(httpCallService).sendRequest(requestCaptor.capture(), any());

        final Request sentRequest = requestCaptor.getValue();

        assertThat(sentRequest).usingRecursiveComparison().isEqualTo(request.build());

    }

}
