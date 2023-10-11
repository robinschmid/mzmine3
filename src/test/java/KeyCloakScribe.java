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

import com.github.scribejava.apis.KeycloakApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.AuthorizationUrlBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

public class KeyCloakScribe {

  public static final String baseUrl = "http://localhost:8080";
  public static final String realm = "user_realm";
  private static final String NETWORK_NAME = "Google";
  private static final String PROTECTED_RESOURCE_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
  final String callback = "http://localhost:8080/auth";

  @Test
  public void testScribePKCE() throws IOException, ExecutionException, InterruptedException {
    // Replace these with your client id and secret
    final String clientId = "mzmine";
    final String clientSecret = null;
    final String secretState = "secret" + new Random().nextInt(999_999);
    final OAuth20Service service = new ServiceBuilder(clientId)
//        .apiSecret(clientSecret)
        .defaultScope("profile") // replace with desired scope
        .callback(callback).build(KeycloakApi.instance(baseUrl, realm));

    final Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);

    System.out.println("=== " + NETWORK_NAME + "'s OAuth Workflow ===");
    System.out.println();

    // Obtain the Authorization URL
    System.out.println("Fetching the Authorization URL...");
    //pass access_type=offline to get refresh token
    //https://developers.google.com/identity/protocols/OAuth2WebServer#preparing-to-start-the-oauth-20-flow
    final Map<String, String> additionalParams = new HashMap<>();
    additionalParams.put("access_type", "offline");
    //force to reget refresh token (if user are asked not the first time)
    additionalParams.put("prompt", "consent");

    final AuthorizationUrlBuilder authorizationUrlBuilder = service.createAuthorizationUrlBuilder()
        .state(secretState).additionalParams(additionalParams).initPKCE();

    System.out.println("Got the Authorization URL!");
    System.out.println("Now go and authorize ScribeJava here:");
    System.out.println(authorizationUrlBuilder.build());
    System.out.println("And paste the authorization code here");
    System.out.print(">>");
    final String code = in.nextLine();
    System.out.println();

    System.out.println(
        "And paste the state from server here. We have set 'secretState'='" + secretState + "'.");
    System.out.print(">>");
    final String value = in.nextLine();
    if (secretState.equals(value)) {
      System.out.println("State value does match!");
    } else {
      System.out.println("Ooops, state value does not match!");
      System.out.println("Expected = " + secretState);
      System.out.println("Got      = " + value);
      System.out.println();
    }

    System.out.println("Trading the Authorization Code for an Access Token...");
    OAuth2AccessToken accessToken = service.getAccessToken(AccessTokenRequestParams.create(code)
        .pkceCodeVerifier(authorizationUrlBuilder.getPkce().getCodeVerifier()));
    System.out.println("Got the Access Token!");
    System.out.println("(The raw response looks like this: " + accessToken.getRawResponse() + "')");

    System.out.println("Refreshing the Access Token...");
    accessToken = service.refreshAccessToken(accessToken.getRefreshToken());
    System.out.println("Refreshed the Access Token!");
    System.out.println("(The raw response looks like this: " + accessToken.getRawResponse() + "')");
    System.out.println();

    // Now let's go and ask for a protected resource!
    System.out.println("Now we're going to access a protected resource...");
    while (true) {
      System.out.println(
          "Paste fieldnames to fetch (leave empty to get profile, 'exit' to stop example)");
      System.out.print(">>");
      final String query = in.nextLine();
      System.out.println();

      final String requestUrl;
      if ("exit".equals(query)) {
        break;
      } else if (query == null || query.isEmpty()) {
        requestUrl = PROTECTED_RESOURCE_URL;
      } else {
        requestUrl = PROTECTED_RESOURCE_URL + "?fields=" + query;
      }

      final OAuthRequest request = new OAuthRequest(Verb.GET, requestUrl);
      service.signRequest(accessToken, request);
      System.out.println();
      try (Response response = service.execute(request)) {
        System.out.println(response.getCode());
        System.out.println(response.getBody());
      }
      System.out.println();
    }
  }

  @Test
  public void testScribe() throws IOException, ExecutionException, InterruptedException {
    // Replace these with your own api key, secret, callback, base url and realm
    final String apiKey = "your_api_key";
    final String apiSecret = null;

    final String protectedResourceUrl =
        baseUrl + "/auth/realms/" + realm + "/protocol/openid-connect/userinfo";

    final OAuth20Service service = new ServiceBuilder(apiKey)
//        .apiSecret(apiSecret)
        .defaultScope("openid").callback(callback).build(KeycloakApi.instance(baseUrl, realm));
    final Scanner in = new Scanner(System.in);

    System.out.println("=== Keyloack's OAuth Workflow ===");
    System.out.println();

    // Obtain the Authorization URL
    System.out.println("Fetching the Authorization URL...");
    final String authorizationUrl = service.getAuthorizationUrl();
    System.out.println("Got the Authorization URL!");
    System.out.println("Now go and authorize ScribeJava here:");
    System.out.println(authorizationUrl);
    System.out.println("And paste the authorization code here");
    System.out.print(">>");
    final String code = in.nextLine();
    System.out.println();

    System.out.println("Trading the Authorization Code for an Access Token...");
    final OAuth2AccessToken accessToken = service.getAccessToken(code);
    System.out.println("Got the Access Token!");
    System.out.println("(The raw response looks like this: " + accessToken.getRawResponse() + "')");
    System.out.println();

    // Now let's go and ask for a protected resource!
    System.out.println("Now we're going to access a protected resource...");
    final OAuthRequest request = new OAuthRequest(Verb.GET, protectedResourceUrl);
    service.signRequest(accessToken, request);
    try (Response response = service.execute(request)) {
      System.out.println("Got it! Lets see what we found...");
      System.out.println();
      System.out.println(response.getCode());
      System.out.println(response.getBody());
    }
    System.out.println();
    System.out.println("Thats it man! Go and build something awesome with ScribeJava! :)");

  }
}
