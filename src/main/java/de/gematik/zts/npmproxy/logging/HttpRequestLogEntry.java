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

package de.gematik.zts.npmproxy.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Log Entry für HTTP Requests
 * (gem. <a href="https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#httprequest">...</a>)
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpRequestLogEntry {
    private String requestMethod;
    private String requestUrl;
    private String requestSize;
    private int status;
    private String responseSize;
    private String userAgent;
    private String remoteIp;
    private String serverIp;
    private String referer;
    private String latency;
    private Boolean cacheLookup;
    private Boolean cacheHit;
    private Boolean cacheValidatedWithOriginServer;
    private String cacheFillBytes;
    private String protocol;
}
