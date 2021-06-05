/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 *
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.pgp.core;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentMatchers;
import com.openexchange.exception.OXException;
import com.openexchange.pgp.core.packethandling.AddRecipientPacketProcessorHandler;
import com.openexchange.pgp.core.packethandling.PacketProcessor;
import com.openexchange.pgp.core.packethandling.RelaceAllRecipientsPacketProcessorHandler;
import com.openexchange.pgp.core.packethandling.RemoveRecipientPacketProcessorHandler;

/**
 * {@link ModifyRecipientTest}
 *
 * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
 * @since v7.8.4
 */
@RunWith(value = Parameterized.class)
public class ModifyRecipientTest extends AbstractPGPTest {

    private final boolean armored;
    private PGPKeyRetrievalStrategy keyRetrievalStrategy;
    private Identity identity;
    private Identity identity2;
    private Identity identity3;

    private static final int TEST_DATA_LENGTH = 4096;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Defines injection of constructor parameters
     *
     * @return An iterable of Arrays which can be injected into the constructor when running the tests
     */
    @Parameters(name = "{index} - Ascii-armored: {0}")
    public static Iterable<?> parameters() {
        return Arrays.asList(new Object[][] {
            { Boolean.TRUE /* Runs the tests in ASCII-Armored mode */},
            { Boolean.FALSE /* Runs the tests in Binary-Mode */}
        });

    }

    /**
     * Initializes a new {@link ModifyRecipientTest}.
     *
     * @param armored Whether to operate in ASCII-Armored mode or Binary mode
     */
    public ModifyRecipientTest(boolean armored) {
        this.armored = armored;
    }

    @Before
    public void setup() throws Exception {
        //Setting up a keypair for the first identity
        PGPKeyRingGenerator keyGenerator = createPGPKeyPairGenerator();
        identity = new Identity(TEST_IDENTITY_NAME, getPublicKeyFromGenerator(keyGenerator), getSecretKeyFromGenerator(keyGenerator), TEST_IDENTITY_PASSWORD);

        //Setup an additional key pair for the 2nd identity
        final String TEST_IDENTITY_2 = "user2";
        final char[] TEST_PASSWORD_2 = "secret".toCharArray();
        PGPKeyRingGenerator keyGenerator2 = createPGPKeyPairGenerator(TEST_IDENTITY_2, TEST_PASSWORD_2);
        identity2 = new Identity(TEST_IDENTITY_2, getPublicKeyFromGenerator(keyGenerator2), getSecretKeyFromGenerator(keyGenerator2), TEST_PASSWORD_2);
        //Setup user for 3rd identity
        final String TEST_IDENTITY_3 = "user3";
        final char[] TEST_PASSWORD_3 = "secret".toCharArray();
        PGPKeyRingGenerator keyGenerator3 = createPGPKeyPairGenerator(TEST_IDENTITY_3, TEST_PASSWORD_3);
        identity3 = new Identity(TEST_IDENTITY_3, getPublicKeyFromGenerator(keyGenerator3), getSecretKeyFromGenerator(keyGenerator3), TEST_PASSWORD_3);
        //Setting up a strategy for key retrieving, this is used when decrypting data
        keyRetrievalStrategy = mock(PGPKeyRetrievalStrategy.class);
        when(keyRetrievalStrategy.getSecretKey(ArgumentMatchers.eq(identity.getSecretKey().getKeyID()), ArgumentMatchers.eq(identity.getIdentity()), ArgumentMatchers.eq(identity.getPassword()))).thenReturn(decodePrivateKey(identity.getSecretKey(), identity.getPassword()));
        when(keyRetrievalStrategy.getSecretKey(ArgumentMatchers.eq(identity2.getSecretKey().getKeyID()), ArgumentMatchers.eq(identity2.getIdentity()), ArgumentMatchers.eq(identity2.getPassword()))).thenReturn(decodePrivateKey(identity2.getSecretKey(), identity2.getPassword()));
        when(keyRetrievalStrategy.getSecretKey(ArgumentMatchers.eq(identity3.getSecretKey().getKeyID()), ArgumentMatchers.eq(identity3.getIdentity()), ArgumentMatchers.eq(identity3.getPassword()))).thenReturn(decodePrivateKey(identity3.getSecretKey(), identity3.getPassword()));
    }


    /**
     * final String TEST_IDENTITY_2 = "user2";
     * final char[] TEST_PASSWORD_2 = "secret".toCharArray();
     * A helper method for getting all key IDs from a group of identities
     *
     * @param identities The identities to get the key IDs for
     * @return A set of Key Ids for the given identities
     */
    private long[] getPublicKeyIdsFor(List<Identity> identities) {
        if (identities != null) {
            long[] ret = new long[identities.size()];
            for (int i = 0; i < identities.size(); i++) {
                ret[i] = identities.get(i).getPublicKey().getKeyID();
            }
            return ret;
        }
        return null;
    }

    /**
     * Checks if a string represents an ASCII-Armored PGP block
     *
     * @param data The data to check
     * @return true, if the string represents an ASCII-Armored PGP block, false otherwise
     */
    private boolean isAsciiArmored(String data) {
        final Pattern pattern = Pattern.compile("^(-----BEGIN PGP MESSAGE-----)(.*)(-----END PGP MESSAGE-----)$", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(data);
        return matcher.find();
    }

    /**
     * Encrypts the given data and removes a group of identities from the resulting PGP data before trying to decrypt the data again
     *
     * @param data The data to encrypt
     * @param encryptFor The recipients to encrypt the data for
     * @param remove The recipients which should be removed from the PGP data after encryption has been finished
     * @param decryptFor The recipients for which the data should be decrypted
     * @throws Exception
     */
    private void encryptThenRemoveThenDecrypt(byte[] data, List<Identity> encryptFor, List<Identity> remove, List<Identity> decryptFor) throws Exception {
        //Test data
        InputStream plainTextData = new ByteArrayInputStream(data);

        //Encrypting the data
        ByteArrayOutputStream encryptedData = new ByteArrayOutputStream();
        new PGPEncrypter().encrypt(plainTextData, encryptedData, this.armored, getPublicKeysFor(encryptFor));

        //Removing the 2nd recipient from the encrypted data
        ByteArrayOutputStream modifiedEncryptedData = new ByteArrayOutputStream();
        PacketProcessor processor = new PacketProcessor();
        processor.process(new ByteArrayInputStream(encryptedData.toByteArray()),
            modifiedEncryptedData,
            new RemoveRecipientPacketProcessorHandler(getPublicKeyIdsFor(remove)),
            this.armored);

        if (this.armored) {
            Assert.assertTrue("The modified encrypted data should be ASCII-Armored",
                isAsciiArmored(new String(modifiedEncryptedData.toByteArray(), StandardCharsets.UTF_8)));
        }

        //Decrypting the data for each recipient
        for (Identity encryptingIdentity : decryptFor) {
            ByteArrayOutputStream decryptedData = new ByteArrayOutputStream();
                PGPDecryptionResult result = new PGPDecrypter(keyRetrievalStrategy).decrypt(
                    new ByteArrayInputStream(modifiedEncryptedData.toByteArray()),
                    decryptedData,
                    encryptingIdentity.getIdentity(),
                    encryptingIdentity.getPassword());
            List<PGPSignatureVerificationResult> verifyResults = result.getSignatureVerificationResults();
            Assert.assertTrue("Verification results should be empty for non signed data", verifyResults.isEmpty());
            Assert.assertArrayEquals("Decrypted data should be equals to plaintext data", decryptedData.toByteArray(), data);
        }
    }

    private void encryptThenAddThenDecrypt(byte[] data, List<Identity> encryptFor, List<Identity> add, List<Identity> decryptFor) throws Exception {
        ByteArrayInputStream plainTextData = new ByteArrayInputStream(data);

        //Encrypting the data
        ByteArrayOutputStream encryptedData = new ByteArrayOutputStream();
        new PGPEncrypter().encrypt(plainTextData, encryptedData, this.armored, getPublicKeysFor(encryptFor));

        //Adding a new recipient to the encrypted data
        ByteArrayOutputStream modifiedEncryptedData = new ByteArrayOutputStream();
        PacketProcessor processor = new PacketProcessor();
        Identity adder = encryptFor.iterator().next();
        processor.process(new ByteArrayInputStream(encryptedData.toByteArray()),
            modifiedEncryptedData,
            new AddRecipientPacketProcessorHandler(decodePrivateKey(adder.getSecretKey(), adder.getPassword()), getPublicKeysFor(add)),
            this.armored);

        if (this.armored) {
            Assert.assertTrue("The modified encrypted data should be ASCII-Armored",
                isAsciiArmored(new String(modifiedEncryptedData.toByteArray(), StandardCharsets.UTF_8)));
        }

        //Decrypting the data for each recipient
        for (Identity encryptingIdentity : decryptFor) {
            ByteArrayOutputStream decryptedData = new ByteArrayOutputStream();
                PGPDecryptionResult result = new PGPDecrypter(keyRetrievalStrategy).decrypt(
                    new ByteArrayInputStream(modifiedEncryptedData.toByteArray()),
                    decryptedData,
                    encryptingIdentity.getIdentity(),
                    encryptingIdentity.getPassword());
            List<PGPSignatureVerificationResult> verifyResults = result.getSignatureVerificationResults();

            Assert.assertTrue("Verification results should be empty for non signed data", verifyResults.isEmpty());
            Assert.assertArrayEquals("Decrypted data should be equals to plaintext data", decryptedData.toByteArray(), data);
        }
    }

    private void encryptThenReplaceThenDecrypt(byte[] data, List<Identity> encryptFor, List<Identity> replace, List<Identity> decryptFor, List<Identity> verifyFail) throws Exception {
        ByteArrayInputStream plainTextData = new ByteArrayInputStream(data);

        //Encrypting the data
        ByteArrayOutputStream encryptedData = new ByteArrayOutputStream();
        new PGPEncrypter().encrypt(plainTextData, encryptedData, this.armored, getPublicKeysFor(encryptFor));

        //Adding a new recipient to the encrypted data
        ByteArrayOutputStream modifiedEncryptedData = new ByteArrayOutputStream();
        PacketProcessor processor = new PacketProcessor();
        Identity adder = encryptFor.iterator().next();
        processor.process(new ByteArrayInputStream(encryptedData.toByteArray()),
            modifiedEncryptedData,
            new RelaceAllRecipientsPacketProcessorHandler(decodePrivateKey(adder.getSecretKey(), adder.getPassword()), getPublicKeysFor(replace)),
            this.armored);

        if (this.armored) {
            Assert.assertTrue("The modified encrypted data should be ASCII-Armored",
                isAsciiArmored(new String(modifiedEncryptedData.toByteArray(), StandardCharsets.UTF_8)));
        }

        //Decrypting the data for each recipient
        for (Identity encryptingIdentity : decryptFor) {
            ByteArrayOutputStream decryptedData = new ByteArrayOutputStream();
                PGPDecryptionResult result = new PGPDecrypter(keyRetrievalStrategy).decrypt(
                    new ByteArrayInputStream(modifiedEncryptedData.toByteArray()),
                    decryptedData,
                    encryptingIdentity.getIdentity(),
                    encryptingIdentity.getPassword());
            List<PGPSignatureVerificationResult> verifyResults = result.getSignatureVerificationResults();

            Assert.assertTrue("Verification results should be empty for non signed data", verifyResults.isEmpty());
            Assert.assertArrayEquals("Decrypted data should be equals to plaintext data", decryptedData.toByteArray(), data);
        }

        //Make sure removed
        for (Identity encryptingIdentity : verifyFail) {
            ByteArrayOutputStream decryptedData = new ByteArrayOutputStream();
            boolean failed = false;
            try {
                PGPDecryptionResult result = new PGPDecrypter(keyRetrievalStrategy).decrypt(
                    new ByteArrayInputStream(modifiedEncryptedData.toByteArray()),
                    decryptedData,
                    encryptingIdentity.getIdentity(),
                    encryptingIdentity.getPassword());
                result.getSignatureVerificationResults();
            } catch (Exception ex) {
                failed = true;
            }

            Assert.assertTrue("Decryption should fail for removed user", failed);
        }
    }

    /**
     * Test that replacing all Recipients is still decryptable with inteded recipients
     * @throws Exception
     */
    @Test
    public void testReplacingRecipientsShouldBeDecryptable() throws Exception {
        encryptThenReplaceThenDecrypt(generateTestData(TEST_DATA_LENGTH), Arrays.asList(identity, identity2), Arrays.asList(identity, identity3), Arrays.asList(identity, identity3), Arrays.asList(identity2));
    }
    /**
     * Test that removing a recipient from a PGP message does not affect the possibility for other recipients to decrypt the message
     *
     * @throws Exception
     */
    @Test
    public void testRemovingRecipientShouldNotAffectDecryptionForOtherRecipient() throws Exception {
        encryptThenRemoveThenDecrypt(generateTestData(TEST_DATA_LENGTH), Arrays.asList(identity, identity2), Arrays.asList(identity2), Arrays.asList(identity));
    }

    /**
     * Tests that not removing a recipient from a PGP message does not affect the message at all
     *
     * @throws Exception
     */
    @Test
    public void testNotRemovingAnyRecipientsShouldNotAffectDecryption() throws Exception {
        encryptThenRemoveThenDecrypt(generateTestData(TEST_DATA_LENGTH), Arrays.asList(identity, identity2), null, Arrays.asList(identity, identity2));
    }

    /**
     * Test that a recipient which has been removed from a PGP message is not able to decrypt the message
     *
     * @throws Exception
     */
    @Test
    public void testRemovingRecipientShouldResultInAnErrorWhenDecrypting() throws Exception {
        thrown.expect(OXException.class);
        thrown.expectMessage("The private key for the identity" /* expected error message substring */);
        encryptThenRemoveThenDecrypt(generateTestData(TEST_DATA_LENGTH), Arrays.asList(identity, identity2), Arrays.asList(identity), Arrays.asList(identity));
    }

    /**
     * Tests adding a recipient to a PGP message
     *
     * @throws Exception
     */
    @Test
    public void testAddedRecipientShouldBeAbleToDecrypt() throws Exception {
        encryptThenAddThenDecrypt(generateTestData(TEST_DATA_LENGTH), Arrays.asList(identity), Arrays.asList(identity2), Arrays.asList(identity, identity2));
    }
}
