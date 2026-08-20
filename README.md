<img align="right" width="250" height="47" src="https://raw.githubusercontent.com/gematik/gematik.github.io/master/Gematik_Logo_Flag_With_Background.png"/> <br/> 

# npm-proxy

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
       <ul>
        <li><a href="#release-notes">Release Notes</a></li>
        <li><a href="#contributions-and-acknowledgements">Contributions and Acknowledgements</a></li>
      </ul>
	</li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#how-the-service-works">How The Service Works</a></li>
        <li><a href="#service-configuration">Service Configuration</a></li>
      </ul>
    </li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#additional-notes">Additional Notes and Disclaimer from gematik GmbH</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

The **Zentraler Terminologieserver (ZTS)** provides an NPM-based API for downloading terminology packages.
This API is currently implemented via GitLab and can therefore be integrated very easily into terminology projects.
However, there are functional limitations regarding the implementation of use cases that are relevant for the ZTS:

Within the terminology server, content is provided for which users must accept different download conditions.
This includes, for example, ICD-10-GM and OPS. Access and permission control within a GitLab project, however, applies to
all packages available in the NPM registry. It is therefore not possible to restrict access control to specific packages
and/or implement user-specific access control.

For this reason, a proxy service is placed in front of the GitLab NPM API to address this shortcoming. The proxy service
essentially implements the following functions:

* **Issuing JSON Web Tokens** - The proxy service issues JWT tokens that can control access authorization to
  terminology packages. Claims within the token encode for which terminology packages the respective publisher download
  conditions have been accepted.
* **Monitoring the GitLab NPM registry** - The proxy service contacts the GitLab NPM registry at regular intervals and,
  if necessary, downloads new package versions into the local cache. This ensures that the latest package versions are
  available for retrieval.
* **Serving NPM packages** - The service acts as an NPM registry and answers corresponding client requests. It checks
  the prerequisites for a package download based on the information in the token.
* **Searching for NPM packages** - The service provides a search function (Catalog API) that enables users to search
  for NPM packages.
* **Dynamic feeds for packages** - The service provides a dynamic feed API that enables users to retrieve information
  about packages / subscribe to feeds for them.
* **Providing API documentation** - The service provides API documentation that enables users to understand and use the
  API functions. This documentation is generated automatically.

Note: The mechanism described here is not security for an NPM registry in the usual sense. Rather, it is intended to
transfer the mechanism previously implemented in the BfArM Download Center for “clicking through” download conditions to
the NPM registry. This is required to enable automated downloads that do not require direct interaction with a user, or
that limit such interaction to a one-time configuration.

### Release Notes

See [ReleaseNotes.md](./ReleaseNotes.md) for all information regarding the (newest) releases.

### Contributions and Acknowledgements

This open source project was developed in cooperation with the German Federal Institute for Drugs and Medical Devices (BfArM) on the basis of Section 355 (12-14) of the German Social Code Book V (SGB V).
As part of the projects implementation, the fbeta GmbH and Fraunhofer FOKUS were commissioned to provide software development services.

We would like to thank all parties involved for their constructive and trusted collaboration.

## Getting Started

### How The Service Works

Using the proxy service takes place in two steps, aligned with the two functions described above:

#### Step 1 - Issuing JWT tokens

Mediated by a website, users get the possibility to accept the various download conditions for the respective
terminology packages. For this, checkmarks can be set in corresponding checkboxes after the user has acknowledged the
conditions. The website sends a request to the token creation endpoint of the service. Based on the information in the
request, the service generates a signed token and returns it in a response.

**Request:** `POST https://terminologien.bfarm.de/packages/api/generate-token`

```json
{
  "packages": [
    "bfarm.terminologien.icd10gm",
    "bfarm.terminologien.ops"
  ]
}
```

**Response:** `200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi [...] JleHAiOjE4MDgwNDU2MjR9.E9e-0VLW5Z_ymgz8mjjUIW7i9pu0JiubqZTg0VO7Kic"
}
```

**Token contents:**

```json
{
  "alg": "HS256"
}
.
{
  "sub": "040ce947-4fb8-4c89-ab8d-61f3f1bba1c4",
  "packages": [
    "bfarm.terminologien.icd10gm",
    "bfarm.terminologien.ops"
  ],
  "iat": 1721645624,
  "exp": 1808045624
}
.
HMACSHA256(base64UrlEncode(header) + "." +
base64UrlEncode(payload), secret)
```

Within the token payload you will find the following fields:

* **sub** - Subject of the token; in this case a random UUID generated by the service
* **packages** - List of packages for which the download conditions have been accepted
* **iat** - Time when the token was issued
* **exp** - Time when the token expires

The token returned to the website must then be displayed so that it can be used persistently in the next step as part of
automated processes. It is also useful to provide a function that copies the token to the clipboard.

#### Step 2 - Using the token to retrieve terminology packages from the NPM registry

The token issued in the first step can now be used to load terminology packages from the NPM registry.
To do so, the user must configure their NPM client so that, for access to the terminology server registry, it uses the
issued JWT as a bearer token within the Authorization header.

.npmrc file:

```bash
registry=https://terminologien.bfarm.de/packages/
//terminologien.bfarm.de/packages/:_authToken=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi [...] JleHAiOjE4MDgwNDU2MjR9.E9e-0VLW5Z_ymgz8mjjUIW7i9pu0JiubqZTg0VO7Kic
```

The user can now inspect and/or download the terminology packages as usual via the NPM client. In each case, the proxy
service registry must be referenced:

Listing packages:

```bash
npm --registry https://terminologien.bfarm.de/packages view bfarm.terminologien.icd10gm
```

Installing packages:

```bash
npm --registry https://terminologien.bfarm.de/packages install bfarm.terminologien.icd10gm
```

### Service Configuration

Service configuration is done via various properties that can be specified either in a configuration file or via
environment variables:

* **proxy.hostname / PROXY_HOSTNAME** - Hostname of the NPM proxy server (e.g. 'https://terminologien.bfarm.de'). This
  name is embedded into the returned messages (e.g. into the URLs for downloading the .tar.gz files)
* **proxy.health-path / PROXY_HEALTH_PATH** - Path to the health endpoint (e.g. '/api/health'). This path is used for
  service configuration
* **proxy.npm-path / PROXY_NPM_PATH** - Path to the NPM proxy (e.g. '/packages'). This path is used for service
  configuration and is also embedded into returned messages (e.g. into the URLs for downloading the .tar.gz files)
* **proxy.package-cache-dir / PROXY_PACKAGE_CACHE_DIR** - Path to the local cache directory for downloaded and cached
  NPM packages
* **proxy.monitored-packages / PROXY_MONITORED_PACKAGES** - NPM packages provided by the service and monitored at regular
  intervals in the configured backend NPM registry. If packages are not listed here, they are also not initially loaded
  from the cache.
* **proxy.protected-packages / PROXY_PROTECTED_PACKAGES** - NPM packages that must be explicitly included in the token
  to be allowed to download them. I.e., these are packages for which the download conditions must be accepted. Only the
  packages contained in the list may be requested in the token request.

* **proxy.target-url / PROXY_TARGET_URL** - URL of the NPM registry (
  e.g. 'https://gitlab.fokus.fraunhofer.de/api/v4/projects/1234/packages/npm') from which the monitored NPM packages are
  loaded. For the NTS this will be the NPM registry built into GitLab.
* **proxy.backend-mode / PROXY_BACKEND_MODE** - Defines the authentication mechanism to use towards the NPM backend.
  Allowed values are: 'gitlab' and 'basicauth'. For 'gitlab' mode a corresponding GitLab token must be defined. For
  'basicauth' mode the username and password properties must be set.
* **proxy.gitlab-token / PROXY_GITLAB_TOKEN** - GitLab token for authenticating the service against the GitLab NPM
  registry. Note: the value is only relevant for backend mode 'gitlab'.
* **proxy.username / PROXY_USERNAME** - Username for authenticating the service against a BasicAuth-based NPM registry.
  Note: the value is only relevant for backend mode 'basicauth'
* **proxy.password / PROXY_PASSWORD** - Password for authenticating the service against a BasicAuth-based NPM registry.
  Note: the value is only relevant for backend mode 'basicauth'
* **proxy.update-interval-in-ms / PROXY_UPDATE_INTERVAL_IN_MS** - Interval in milliseconds at which the monitored NPM
  packages are loaded from the backend NPM registry. This is a period

## Contributing
If you want to contribute, please check our [CONTRIBUTING.md](./CONTRIBUTING.md). 

## License

Copyright 2026 gematik GmbH

Apache License, Version 2.0

See the [LICENSE](./LICENSE) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
  1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
  2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
  3. We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes. If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: ospo@gematik.de
3. Parts of this software and - in isolated cases - content such as text or images may have been developed using the support of AI tools. They are subject to the same reviews, tests, and security checks as any other contribution. The functionality of the software itself is not based on AI decisions.

## Contact
We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes.
This software is currently being tested to ensure its technical quality and legal compliance. Your feedback is highly valued.
If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: zts@gematik.de.
