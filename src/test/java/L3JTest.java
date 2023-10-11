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

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax0.license3j.Feature;
import javax0.license3j.License;
import javax0.license3j.crypto.LicenseKeyPair;
import javax0.license3j.hardware.Network.Interface.Selector;
import javax0.license3j.hardware.UUIDCalculator;
import javax0.license3j.io.IOFormat;
import javax0.license3j.io.KeyPairReader;
import javax0.license3j.io.KeyPairWriter;
import javax0.license3j.io.LicenseReader;
import javax0.license3j.io.LicenseWriter;
import org.junit.jupiter.api.Assertions;
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
  File PUBLIC_KEY_FILE = new File(path, "public.key");
  File clientLicenseFile = new File(path, "CLIENT_license.bin");
  File PRIVATE_KEY_FILE = new File(path, "private.key");

  @Test
  public void testL3J()
      throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {

    // generate
    var keyPair = LicenseKeyPair.Create.from(ALGORITHM, SIZE);
    try (final var writer = new KeyPairWriter(PRIVATE_KEY_FILE, PUBLIC_KEY_FILE)) {
      writer.write(keyPair, IOFormat.BASE64);
    }

    var license = new License();
    UUIDCalculator uuidCalculator = new UUIDCalculator(new Selector());
    UUID uuid = uuidCalculator.getMachineId(null, true, true, true);
    System.out.println("UUID " + uuid);
    license.add(Feature.Create.uuidFeature("uuid", uuid));
    license.add(Feature.Create.dateFeature("date_activated", Date.from(Instant.now())));
    license.add(Feature.Create.dateFeature("date_expiration",
        Date.from(Instant.now().plus(30, ChronoUnit.DAYS))));
    license.add(Feature.Create.stringFeature("entity", "full persons name"));

    System.out.println("License " + license.toString());

    license.sign(keyPair.getPair().getPrivate(), DIGEST);

    System.out.println("License signed " + license.toString());

    validate(license, keyPair);

    saveLicenseFile(license, file);

// public key changes - license invalid
//    keyPair = LicenseKeyPair.Create.from(ALGORITHM, SIZE);
    var readLicense = readLicenseFile(keyPair, file);
    readLicense.add(Feature.Create.stringFeature("clientside", "client"));
    saveLicenseFile(readLicense, clientLicenseFile);

    logger.fine("Should fail");
    var readLicenseUnsigned = readLicenseFile(keyPair, clientLicenseFile);
    Assertions.assertFalse(readLicenseUnsigned.isOK(keyPair.getPair().getPublic()));
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
  public void testSaveLoadPrivateKey() {
    var format = IOFormat.BINARY;
    LicenseKeyPair keyPair = null;

    try (final var reader = new KeyPairReader(PRIVATE_KEY_FILE)) {
      keyPair = merge(keyPair, reader.readPublic(format));
      final var keyPath = PRIVATE_KEY_FILE.getAbsolutePath();
      System.out.println("Public key loaded from" + keyPath);
      System.out.println("Public key" + keyPair.getPublic().toString());

    } catch (Exception e) {
      System.out.println("An exception occurred loading the keys: " + e);
      e.printStackTrace();
    }
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

}
