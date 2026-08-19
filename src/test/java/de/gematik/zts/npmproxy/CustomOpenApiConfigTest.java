/*
 * Copyright (Change Date see Readme), gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes
 * by gematik, find details in the "Readme" file.
 */

package de.gematik.zts.npmproxy;

import static org.junit.jupiter.api.Assertions.*;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = CustomOpenApiConfig.class)
@TestPropertySource(
    properties = {
      "proxy.api-docs.server-url=http://localhost:8080,http://example.com",
      "proxy.api-docs.title=Test API",
      "proxy.api-docs.version=2.0.0"
    })
class CustomOpenApiConfigTest {

  @Autowired private OpenApiCustomizer openApiCustomizer;

  @Test
  void testCustomOpenAPI() {
    OpenAPI openAPI = new OpenAPI();

    // Check openAPI Info
    Info info = openAPI.getInfo();
    assertNull(info, "Info should initially be null");

    // Apply customizer
    openApiCustomizer.customise(openAPI);

    // After applying the customizer, check Info
    info = openAPI.getInfo();
    assertNotNull(info, "Info should not be null");
    assertEquals("Test API", info.getTitle(), "Title does not match");
    assertEquals("2.0.0", info.getVersion(), "Version does not match");

    // Check Servers
    List<Server> servers = openAPI.getServers();
    assertNotNull(servers, "Servers should not be null");
    assertEquals(2, servers.size(), "Number of servers do not match");
    assertEquals("http://localhost:8080", servers.get(0).getUrl(), "First server does not match");
    assertEquals("http://example.com", servers.get(1).getUrl(), "Second server does not match");
  }

  @Test
  void testTagSorting() {
    // Create a mock OpenAPI object with unsorted tags
    OpenAPI mockOpenAPI = new OpenAPI();
    List<Tag> tags =
        new ArrayList<>(
            List.of(
                new Tag().name("Feeds-API"),
                new Tag().name("Token-API"),
                new Tag().name("Package-API")));

    mockOpenAPI.setTags(tags);

    // Apply the customizer
    openApiCustomizer.customise(mockOpenAPI);

    // Check if the tags are sorted correctly
    List<Tag> sortedTags = mockOpenAPI.getTags();
    assertEquals(3, sortedTags.size(), "Tag count should remain the same");
    assertEquals("Token-API", sortedTags.get(0).getName(), "First tag should be Token-API");
    assertEquals("Package-API", sortedTags.get(1).getName(), "Second tag should be Package-API");
    assertEquals("Feeds-API", sortedTags.get(2).getName(), "Third tag should be Feeds-API");
  }

  @Test
  void testRemoveOperationIdCustomizer() {
    // Create a mock OpenAPI object with a path and operation
    OpenAPI mockOpenAPI = new OpenAPI();
    Paths paths = new Paths();
    PathItem pathItem = new PathItem();

    Operation operation = new Operation();
    operation.setOperationId("testOperationId");
    pathItem.setGet(operation);

    paths.addPathItem("/test", pathItem);
    mockOpenAPI.setPaths(paths);

    // Before applying the customizer, operationId should be set
    assertEquals("testOperationId", operation.getOperationId(), "operationId should be set");

    // apply the customizer
    openApiCustomizer.customise(mockOpenAPI);

    // After applying the customizer, operationId should be null
    assertNull(operation.getOperationId(), "operationId should be null after applying customizer");
  }
}
