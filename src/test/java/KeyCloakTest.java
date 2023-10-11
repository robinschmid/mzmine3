/*
 * Copyright (c) 2004-2023 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.keycloak.OAuthErrorException;
import org.keycloak.adapters.ServerRequest.HttpFailure;
import org.keycloak.adapters.installed.KeycloakInstalled;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessToken;

public class KeyCloakTest {

  @Test
  public void testAuth()
      throws OAuthErrorException, HttpFailure, VerificationException, IOException, URISyntaxException, InterruptedException {
//    System.setProperty("Djava.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // need this: -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager

    // reads the configuration from classpath: META-INF/keycloak.json
    KeycloakInstalled keycloak = new KeycloakInstalled(
        KeyCloakTest.class.getClassLoader().getResourceAsStream("keycloak.json"));
    keycloak.setLocale(Locale.ENGLISH);

    AccessToken token = keycloak.getToken();

// opens desktop browser
//    keycloak.loginManual();
    if (token == null) {
      keycloak.loginDesktop();
      token = keycloak.getToken();
    }

// use token to send backend request

// ensure token is valid for at least 30 seconds
    long minValidity = 30L;
    String tokenString = keycloak.getTokenString(minValidity, TimeUnit.SECONDS);

    System.out.println("Logged in...");
    System.out.println("Token: " + token.getSubject());
    System.out.println("Scope: " + token.getScope());
    System.out.println("Birth: " + token.getBirthdate());
    System.out.println("Issuer: " + token.getIssuer());
    System.out.println("SessionState: " + token.getSessionState());
    System.out.println("Authorize: " + token.getAuthorization());
    System.out.println(
        "Access: " + token.getResourceAccess().entrySet().stream().map(Objects::toString)
            .collect(Collectors.joining(", ")));
    System.out.println("Auth time: " + token.getAuth_time());
    System.out.println("Exp: " + token.getExp());
    System.out.println("Update: " + token.getUpdatedAt());
    System.out.println("Iat: " + token.getIat());
    System.out.println("Nbf: " + token.getNbf());
    System.out.println("Username: " + token.getPreferredUsername());
    try {
      System.out.println("AccessToken: " + keycloak.getTokenString());
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    // when you want to logout the user.
//    keycloak.logout();
  }

}
