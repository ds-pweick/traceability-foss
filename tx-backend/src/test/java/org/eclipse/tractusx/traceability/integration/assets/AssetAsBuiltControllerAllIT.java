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
package org.eclipse.tractusx.traceability.integration.assets;


import io.restassured.http.ContentType;
import org.eclipse.tractusx.traceability.integration.IntegrationTestSpecification;
import org.eclipse.tractusx.traceability.integration.common.support.AssetsSupport;
import org.eclipse.tractusx.traceability.integration.common.support.BpnSupport;
import org.hamcrest.Matchers;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.eclipse.tractusx.traceability.common.security.JwtRole.ADMIN;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class AssetAsBuiltControllerAllIT extends IntegrationTestSpecification {

    @Autowired
    BpnSupport bpnSupport;

    @Autowired
    AssetsSupport assetsSupport;

    private static Stream<Arguments> owners() {
        return Stream.of(
                arguments("OWN", 1),
                arguments("CUSTOMER", 0),
                arguments("SUPPLIER", 12),
                arguments("UNKNOWN", 0));
    }

    @Test
    void shouldReturnAssetsWithManufacturerName() throws JoseException {
        //GIVEN
        bpnSupport.cachedBpnsForDefaultAssets();
        assetsSupport.defaultAssetsStored();

        //THEN
        given()
                .header(oAuth2Support.jwtAuthorization(ADMIN))
                .contentType(ContentType.JSON)
                .log().all()
                .when()
                .get("/api/assets/as-built")
                .then()
                .log().all()
                .statusCode(200)
                .body("content.manufacturerName", everyItem(not(equalTo(assetsSupport.emptyText()))));
    }

    // Deprecated please remove once controller has been removed
    @Test
    void shoulReturnSupplierAssets() throws JoseException {
        //GIVEN
        assetsSupport.defaultAssetsStored();

        //THEN
        given()
                .header(oAuth2Support.jwtAuthorization(ADMIN))
                .contentType(ContentType.JSON)
                .queryParam("owner", "SUPPLIER")
                .when()
                .get("/api/assets/as-built")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(12));
    }

    // Deprecated please remove once controller has been removed
    @Test
    void shouldReturnOwnAssets() throws JoseException {
        //GIVEN
        assetsSupport.defaultAssetsStored();

        //THEN
        given()
                .header(oAuth2Support.jwtAuthorization(ADMIN))
                .contentType(ContentType.JSON)
                .queryParam("owner", "OWN")
                .when()
                .get("/api/assets/as-built")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(1));
    }

    @Test
    void shouldReturnAllAssets() throws JoseException {
        //GIVEN
        assetsSupport.defaultAssetsStored();

        //THEN
        given()
                .header(oAuth2Support.jwtAuthorization(ADMIN))
                .contentType(ContentType.JSON)
                .when()
                .get("/api/assets/as-built")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(13))
                .body("content[0]", hasEntry("id", "urn:uuid:d387fa8e-603c-42bd-98c3-4d87fef8d2bb"))
                .body("content[0]", hasEntry("idShort", "vehicle_hybrid.asm"))
                .body("content[0]", hasEntry("semanticModelId", "OMA-TGFAYUHXFLHHUQQMPLTE"))
                .body("content[0]", hasEntry("manufacturerId", "BPNL00000003AYRE"))
                .body("content[0]", hasEntry("manufacturerName", "Manufacturer Name 1"))
                .body("content[0]", hasEntry("underInvestigation", false))
                .body("content[0]", hasEntry("qualityType", "Ok"))
                .body("content[0]", hasEntry("van", "OMA-TGFAYUHXFLHHUQQMPLTE"))
                .body("content[0].semanticModel", hasEntry("manufacturingCountry", "DEU"))
                .body("content[0].semanticModel", hasEntry("manufacturingDate", "2014-11-18T08:23:55Z"))
                .body("content[0].semanticModel", hasEntry("nameAtManufacturer", "Vehicle Hybrid"))
                .body("content[0].semanticModel", hasEntry("manufacturerPartId", assetsSupport.emptyText()))
                .body("content[0].semanticModel", hasEntry("nameAtCustomer", assetsSupport.emptyText()))
                .body("content[0].semanticModel", hasEntry("customerPartId", assetsSupport.emptyText()));

    }

    @ParameterizedTest
    @MethodSource("owners")
    void shouldReturnAssetsByOwnerFiltering(String ownerValue, int totalItemsValue) throws JoseException {
        //GIVEN
        assetsSupport.defaultAssetsStored();

        //THEN
        given()
                .header(oAuth2Support.jwtAuthorization(ADMIN))
                .contentType(ContentType.JSON)
                .queryParam("owner", ownerValue)
                .when()
                .get("/api/assets/as-built")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(totalItemsValue));
    }

    @Test
    void shouldGetPageOfAssets() throws JoseException {
        //GIVEN
        assetsSupport.defaultAssetsStored();

        //THEN
        given()
                .header(oAuth2Support.jwtAuthorization(ADMIN))
                .contentType(ContentType.JSON)
                .param("page", "2")
                .param("size", "2")
                .when()
                .get("/api/assets/as-built")
                .then()
                .statusCode(200)
                .body("page", Matchers.is(2))
                .body("pageSize", Matchers.is(2));
    }

}