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

/**
 * A sample application that demonstrates how the Google OAuth2 library can be used to authenticate
 * against a locally running Keycloak server with a registered public client where using <a
 * href="https://tools.ietf.org/html/rfc7636">PKCE</a> is required.
 *
 * <p>Please note that before running this sample application, a local Keycloak server must be
 * running and a PKCE enabled client must have been defined. Please see <code>
 * samples/keycloak-pkce-cmdline-sample/scripts/initialize-keycloak.sh</code> for further
 * information.
 *
 * @author Stefan Freyr Stefansson
 */
public class KeyCloakGoogleTest {

//  /**
//   * Global instance of the JSON factory.
//   */
//  static final JsonFactory JSON_FACTORY = new GsonFactory();
//  /**
//   * OAuth 2 scope.
//   */
//  private static final String SCOPE = "email";
//
//  /**
//   * Global instance of the HTTP transport.
//   */
//  private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
//  private static final String TOKEN_SERVER_URL = "http://localhost:8080/auth/realms/user_realm/protocol/openid-connect/token";
//  private static final String AUTHORIZATION_SERVER_URL = "http://localhost:8080/auth/realms/user_realm/protocol/openid-connect/auth";
//  /**
//   * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
//   * globally shared instance across your application.
//   */
//  private static DataStoreFactory DATA_STORE_FACTORY;
//
//  /**
//   * Authorizes the installed application to access user's protected data.
//   */
//  private static Credential authorize() throws Exception {
//    // set up authorization code flow
//    String clientId = "mzmine";
//    AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(
//        BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT, JSON_FACTORY,
//        new GenericUrl(TOKEN_SERVER_URL), new ClientParametersAuthentication(clientId, null),
//        clientId, AUTHORIZATION_SERVER_URL).setScopes(List.of(SCOPE)).enablePKCE()
//        .setDataStoreFactory(DATA_STORE_FACTORY).build();
//    // authorize
//    LocalServerReceiver receiver = new LocalServerReceiver.Builder().setHost("127.0.0.1").build();
//    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("robin");
//  }
//
//  @Test
//  public void testGoogle() {
//    try {
//      // FileDataStoreFactory
//      DATA_STORE_FACTORY = new MemoryDataStoreFactory();
//      final Credential credential = authorize();
//      System.out.println("Successfully obtained credential from Keycloak running on localhost.");
//      final String accessToken = credential.getAccessToken();
//      System.out.println("Retrieved an access token of length " + accessToken.length());
//      return;
//    } catch (IOException e) {
//      System.err.println(e.getMessage());
//    } catch (Throwable t) {
//      t.printStackTrace();
//    }
//    System.exit(1);
//  }
}
