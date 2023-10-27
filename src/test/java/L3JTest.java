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

import static io.github.mzmine.users.UserActiveService.COMMUNITY;
import static io.github.mzmine.users.UserActiveService.PRO_WORKSPACES;
import static io.github.mzmine.users.UserActiveService.SPECTRAL_LIBRARIES;
import static io.github.mzmine.users.UserActiveService.WEBSERVICES;

import io.github.mzmine.users.UserActiveService;
import io.github.mzmine.users.UserFeatures;
import io.github.mzmine.users.UserType;
import io.github.mzmine.util.files.FileAndPathUtil;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax0.license3j.Feature.Create;
import javax0.license3j.License;
import javax0.license3j.crypto.LicenseKeyPair;
import javax0.license3j.hardware.Network.Interface.Selector;
import javax0.license3j.hardware.UUIDCalculator;
import javax0.license3j.io.IOFormat;
import javax0.license3j.io.KeyPairReader;
import javax0.license3j.io.KeyPairWriter;
import javax0.license3j.io.LicenseReader;
import javax0.license3j.io.LicenseWriter;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

public class L3JTest {

  private static final Logger logger = Logger.getLogger(L3JTest.class.getName());
  private static final String ALGORITHM = "RSA";
  private static final String DIGEST = "SHA-512";
  private static final int SIZE = 2048;
  private static final IOFormat FORMAT = IOFormat.BINARY;
  private static final String CONFIRM = "confirm";
  private static final String TEXT = "TEXT";
  private static final String BINARY = "BINARY";
  private static final String BASE_64 = "BASE64";
  File path = new File("D:\\tmp\\license3j\\");
  File file = new File(path, "license.bin");
  File clientLicenseFile = new File(path, "CLIENT_license.bin");
  File PUBLIC_KEY_FILE_TEST = new File(path, "public_test.key");
  File PRIVATE_KEY_FILE_TEST = new File(path, "private_test.key");
  File PUBLIC_KEY_FILE = new File(path, "public.key");
  File PRIVATE_KEY_FILE = new File(path, "private.key");

  @Test
  public void testCreateLicenses()
      throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
    LicenseKeyPair keyPair = load(PRIVATE_KEY_FILE, PUBLIC_KEY_FILE, IOFormat.BINARY);

    digestPublicKey(keyPair);

    var users = List.of(User.create("Robin Schmid", 30, 120, UserType.PRO,
            List.of(PRO_WORKSPACES, SPECTRAL_LIBRARIES, WEBSERVICES)),
        User.create("Steffen", 0, 1000, UserType.UNVALIDATED, List.of()),
        User.create("Tomas", 300, -10, UserType.NON_PROFIT, List.of(COMMUNITY)),
        User.create("Tito Teito", 100, -15, UserType.PRO, List.of(PRO_WORKSPACES)),
        User.create("Corinna Brungs", 100, -100, UserType.PRO,
            List.of(PRO_WORKSPACES, WEBSERVICES)));

    Set<String> files = new HashSet<>();

    for (final User user : users) {
      var license = new License();
      UUIDCalculator uuidCalculator = new UUIDCalculator(new Selector());
      UUID uuid = uuidCalculator.getMachineId(null, true, true, true);
      System.out.println("UUID " + uuid);
      license.add(Create.uuidFeature("uuid", uuid));
      license.add(Create.uuidFeature(UserFeatures.UUID.toString(), uuid));
      license.add(
          Create.dateFeature(UserFeatures.ACTIVATION_DATE.toString(), user.activationDate()));
      license.add(
          Create.dateFeature(UserFeatures.ACTIVE_UNTIL_DATE.toString(), user.activeUntilDate()));
      license.add(Create.stringFeature(UserFeatures.NICKNAME.toString(), user.nickname()));
      license.add(Create.stringFeature(UserFeatures.USER_TYPE.toString(), user.userType().name()));

      // all services
      String services = user.services().stream().map(Enum::name).collect(Collectors.joining(","));
      license.add(Create.stringFeature(UserFeatures.SERVICES.toString(), services));

      license.sign(keyPair.getPair().getPrivate(), DIGEST);
      System.out.println("License signed " + license.toString());
      validate(license, keyPair);

      String basename = FileAndPathUtil.safePathEncode(user.nickname().toLowerCase())
          .replaceAll(" ", "_");
      String name = basename;
      int counter = 1;
      while (files.contains(name)) {
        name = basename + "_" + counter;
        counter++;
      }
      files.add(name);
      saveLicenseFile(license, FileAndPathUtil.getRealFilePath(path, name, "mzuser"));
    }
  }

  @Test
  public void testL3JKeys()
      throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
    // generate
    var keyPair = LicenseKeyPair.Create.from(ALGORITHM, SIZE);
    try (final var writer = new KeyPairWriter(PRIVATE_KEY_FILE_TEST, PUBLIC_KEY_FILE_TEST)) {
      writer.write(keyPair, IOFormat.BINARY);
    }
    // print public to screen
    // copy into software
    digestPublicKey(keyPair);
  }

  @Test
  public void testReadLicense() {
//    testLoadPrivateKey();
//// public key changes - license invalid
////    keyPair = LicenseKeyPair.Create.from(ALGORITHM, SIZE);
//    var readLicense = readLicenseFile(keyPair, file);
//    readLicense.add(Create.stringFeature("clientside", "client"));
//    saveLicenseFile(readLicense, clientLicenseFile);
//
//    logger.fine("Should fail");
//    var readLicenseUnsigned = readLicenseFile(keyPair, clientLicenseFile);
//    Assertions.assertFalse(readLicenseUnsigned.isOK(keyPair.getPair().getPublic()));
  }

  private License readLicenseFile(final LicenseKeyPair keyPair, File file) throws IOException {
    try (final var reader = new LicenseReader(file)) {
      License readLicense = reader.read(FORMAT);
      validate(readLicense, keyPair);

      System.out.println("Read license: \n" + readLicense);
      return readLicense;
    }
  }

  private void saveLicenseFile(final License license, File file) throws IOException {
    try (LicenseWriter writer = new LicenseWriter(file)) {
      writer.write(license, FORMAT);
    }
  }

  public void validate(License license, LicenseKeyPair keyPair) {
    if (license.isOK(keyPair.getPair().getPublic())) {
      System.out.println("License is properly signed.");
    } else {
      System.out.println("License is not signed properly.");
    }
  }

  @Test
  public void testLoadPrivateKey() {
    var format = IOFormat.BINARY;
    LicenseKeyPair keyPair = null;

    try (final var reader = new KeyPairReader(PRIVATE_KEY_FILE)) {
      keyPair = merge(keyPair, reader.readPrivate(format));
      final var keyPath = PRIVATE_KEY_FILE.getAbsolutePath();
      System.out.println("Public key loaded from" + keyPath);
      System.out.println("Public key" + keyPair.getPrivate().toString());

    } catch (Exception e) {
      System.out.println("An exception occurred loading the keys: " + e);
      e.printStackTrace();
    }
  }

  @Nullable
  private LicenseKeyPair load(File privateKey, File publicKey, IOFormat format) {
    LicenseKeyPair keyPair = null;

    try (final var reader = new KeyPairReader(publicKey)) {
      keyPair = merge(keyPair, reader.readPublic(format));
      try (final var readerPrivate = new KeyPairReader(privateKey)) {
        keyPair = merge(keyPair, readerPrivate.readPrivate(format));
      }
    } catch (Exception e) {
      logger.warning("Error loading keys");
    }
    return keyPair;
  }

  private LicenseKeyPair merge(LicenseKeyPair oldKp, LicenseKeyPair newKp) {
    if (oldKp == null) {
      return newKp;
    }
    final var cipher = oldKp.cipher();
    if (newKp.getPair().getPublic() != null) {
      return LicenseKeyPair.Create.from(newKp.getPair().getPublic(), oldKp.getPair().getPrivate(),
          cipher);
    }
    if (newKp.getPair().getPrivate() != null) {
      return LicenseKeyPair.Create.from(oldKp.getPair().getPublic(), newKp.getPair().getPrivate(),
          cipher);
    }
    return oldKp;
  }

  private void digestPublicKey(LicenseKeyPair keyPair) {
    try {
      assert keyPair != null;

      final var key = keyPair.getPublic();
      final var md = MessageDigest.getInstance(DIGEST);
      final var calculatedDigest = md.digest(key);
      final var javaCode = new StringBuilder("--KEY DIGEST START\nbyte [] digest = new byte[] {\n");
      for (int i = 0; i < calculatedDigest.length; i++) {
        int intVal = ((int) calculatedDigest[i]) & 0xff;
        javaCode.append(String.format("(byte)0x%02X, ", intVal));
        if (i % 8 == 0) {
          javaCode.append("\n");
        }
      }
      javaCode.append("\n};\n---KEY DIGEST END\n");

      javaCode.append("--KEY START\nbyte [] key = new byte[] {\n");
      for (int i = 0; i < key.length; i++) {
        int intVal = ((int) key[i]) & 0xff;
        javaCode.append(String.format("(byte)0x%02X, ", intVal));
        if (i % 8 == 0) {
          javaCode.append("\n");
        }
      }
      javaCode.append("\n};\n---KEY END\n");

      logger.info("\n" + javaCode.toString());
    } catch (NoSuchAlgorithmException e) {
      logger.log(Level.WARNING, "Cannot print keys", e);
    }
  }

  public record User(String nickname, Date activationDate, Date activeUntilDate, UserType userType,
                     List<UserActiveService> services) {

    public static User create(String nickname, int doneDays, int comingDays, UserType userType,
        List<UserActiveService> services) {
      return new User(nickname, //
          Date.from(Instant.now().minus(doneDays, ChronoUnit.DAYS)), //
          Date.from(Instant.now().plus(comingDays, ChronoUnit.DAYS)), //
          userType, services);
    }

  }

}
