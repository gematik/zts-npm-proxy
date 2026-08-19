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

package de.gematik.zts.npmproxy.security;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String token;
    private final UserDetails principal;

    JwtAuthenticationToken(String token, UserDetails principal) {
        super(principal.getAuthorities());
        this.token = token;
        this.principal = principal;
    }

    Authentication withAuthenticated(boolean isAuthenticated) {
        JwtAuthenticationToken cloned = new JwtAuthenticationToken(token, principal);
        cloned.setAuthenticated(isAuthenticated);
        return cloned;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JwtAuthenticationToken test)) {
            return false;
        }
        if (this.getToken() == null && test.getToken() != null) {
            return false;
        }
        if (this.getToken() != null && !this.getToken().equals(test.getToken())) {
            return false;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        int code = super.hashCode();
        if (this.getToken() != null) {
            code ^= this.getToken().hashCode();
        }
        return code;
    }
}
