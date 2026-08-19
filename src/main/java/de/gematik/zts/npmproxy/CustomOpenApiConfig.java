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

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
    name = "BearerAuthentication",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer")
public class CustomOpenApiConfig {

  // To define a tag order
  private static final String[] TAG_FRAGS_IN_ORDER = {"Token-API", "Package-API", "Feeds-API"};

  @Value("${proxy.api-docs.server-url:http://localhost:8080}")
  private List<String> serverUrls;

  @Value("${proxy.api-docs.title:ZTS - FHIR Package API}")
  private String title;

  @Value("${proxy.api-docs.version:1.0.0}")
  private String version;

  // To sort tags in the order defined above
  private static int getTagPriority(Tag tag) {
    for (int i = 0; i < TAG_FRAGS_IN_ORDER.length; i++) {
      var frag = TAG_FRAGS_IN_ORDER[i];
      if (StringUtils.contains(tag.getName(), frag)) return i;
    }
    // All the others will go in lexicographical order
    return Integer.MAX_VALUE;
  }

  @Bean
  public OpenApiCustomizer customizeOpenApi() {

    return openApi -> {
      // sort the tags in the desired order
      List<Tag> tags = openApi.getTags();
      if (tags != null) {
        tags.sort(
            Comparator.comparingInt(CustomOpenApiConfig::getTagPriority)
                // Last resort for tags of least/same priority: lexicographically
                .thenComparing(Tag::getName, Comparator.naturalOrder()));
        openApi.setTags(tags);
      }
      // remove the operationId from the generated OpenAPI
      var paths = openApi.getPaths();
      if (paths != null) {
        paths
            .values()
            .forEach(
                pathItem ->
                    pathItem.readOperations().forEach(operation -> operation.setOperationId(null)));
      }

      var appVersion =
          getClass().getPackage().getImplementationVersion() != null
              ? getClass().getPackage().getImplementationVersion()
              : version;
      // set customized server URLs and API info
      openApi
          .servers(serverUrls.stream().map(serverUrl -> new Server().url(serverUrl)).toList())
          .info(new Info().title(title).version(appVersion));
    };
  }

  @Bean
  public GroupedOpenApi feedsOpenApi() {
    String[] paths = {"/feeds/**"};
    return GroupedOpenApi.builder()
        .group("feeds")
        .addOpenApiCustomizer(customizeOpenApi())
        .pathsToMatch(paths)
        .build();
  }

  @Bean
  public GroupedOpenApi packagesOpenApi() {
    String[] paths = {"/api/**", "/packages/**"};
    return GroupedOpenApi.builder()
        .group("packages")
        .pathsToMatch(paths)
        .addOpenApiCustomizer(customizeOpenApi())
        .build();
  }
}
