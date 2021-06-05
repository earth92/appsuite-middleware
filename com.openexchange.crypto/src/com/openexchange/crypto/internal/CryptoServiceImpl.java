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

package com.openexchange.crypto.internal;

import static com.openexchange.crypto.CryptoErrorMessage.BadPassword;
import static com.openexchange.crypto.CryptoErrorMessage.NoSalt;
import static com.openexchange.crypto.CryptoErrorMessage.SecurityException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.openexchange.crypto.CryptoErrorMessage;
import com.openexchange.crypto.CryptoService;
import com.openexchange.crypto.EncryptedData;
import com.openexchange.exception.OXException;
import com.openexchange.java.Strings;
import de.rtner.security.auth.spi.PBKDF2Engine;
import de.rtner.security.auth.spi.PBKDF2Parameters;

/**
 * This Service provides Methods for encrypting and decrypting of Strings.
 *
 * Warning: Do not change the parameters (IV, salt, algorithms, ...) in productive environment,
 * because decryption of former encrypted date will be impossible.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 * @author <a href="mailto:martin.herfurth@open-xchange.org">Martin Herfurth</a>
 */
public class CryptoServiceImpl implements CryptoService {

    private static final Logger LOG = LoggerFactory.getLogger(CryptoServiceImpl.class);

    /**
     * Hash Algorithm for generating PBE-Keys.
     */
    private static final String KEY_ALGORITHM = "HMacSHA1";

    /**
     * Key Charset
     */
    private static final String CHARSET = "UTF-8";

    /**
     * Algorithm for creating random salt.
     */
    private static final String RANDOM_ALGORITHM = "SHA1PRNG";

    /**
     * Key length
     */
    private static final int KEY_LENGTH = 16;

    /**
     * The algorithm.
     */
    private static final String ALGORITHM = "AES";

    /**
     * The mode.
     */
    private static final String MODE = "CBC";

    /**
     * The padding.
     */
    private static final String PADDING = "PKCS5Padding";

    /**
     * The transformation following pattern <i>"algorithm/mode/padding"</i>.
     */
    private static final String CIPHER_TYPE = ALGORITHM + "/" + MODE + "/" + PADDING;

    /**
     * Initialization Vector
     */
    private final IvParameterSpec IV = new IvParameterSpec(new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });

    /**
     * Default Salt, if no salt is given or "used". But password encryption needs always encryption.
     */
    private final byte[] SALT = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f };

    @Override
    public String encrypt(final String data, final String password) throws OXException {
        return encrypt(data, password, false).getData();
    }

    @Override
    public String decrypt(final String encryptedData, final String password) throws OXException {
        return decrypt(new EncryptedData(encryptedData, null), password, false);
        // return decrypt(encryptedData, generateSecretKey(password));
    }

    @Override
    public EncryptedData encrypt(final String data, final String password, final boolean useSalt) throws OXException {
        if (data == null) {
            return null;
        }
        if (useSalt) {
            final byte[] salt = generateSalt();
            return new EncryptedData(encrypt(data, generateSecretKey(password, salt)), salt);
        }
        return new EncryptedData(encrypt(data, generateSecretKey(password, SALT)), null);
    }

    @Override
    public String decrypt(final EncryptedData data, final String password, final boolean useSalt) throws OXException {
        if (useSalt && data.getSalt() == null) {
            throw NoSalt.create();
        }
        if (Strings.isEmpty(data.getData())) {
            return data.getData();
        }
        return useSalt ? decrypt(data.getData(), generateSecretKey(password, data.getSalt())) : decrypt(data.getData(), generateSecretKey(password, SALT));
    }

    /**
     * Encrypts specified data with given key.
     *
     * @param data The data to encrypt
     * @param password The password
     * @return The encrypted data as Base64 encoded string
     * @throws OXException
     */
    @Override
    public String encrypt(final String data, final Key password) throws OXException {
        String retval = null;
        try {
            final Cipher cipher = Cipher.getInstance(CIPHER_TYPE);
            cipher.init(Cipher.ENCRYPT_MODE, password, IV);
            final byte[] outputBytes = cipher.doFinal(data.getBytes(com.openexchange.java.Charsets.UTF_8));
            /*-
             * It's safe to use "US-ASCII" to turn bytes into a Base64 encoded encrypted password string.
             * Taken from RFC 2045 Section 6.8. "Base64 Content-Transfer-Encoding":
             *
             * A 65-character subset of US-ASCII is used, enabling 6 bits to be
             * represented per printable character. (The extra 65th character, "=",
             * is used to signify a special processing function.)
             *
             * NOTE: This subset has the important property that it is represented
             * identically in all versions of ISO 646, including US-ASCII, and all
             * characters in the subset are also represented identically in all
             * versions of EBCDIC. Other popular encodings, such as the encoding
             * used by the uuencode utility, Macintosh binhex 4.0 [RFC-1741], and
             * the base85 encoding specified as part of Level 2 PostScript, do not
             * share these properties, and thus do not fulfill the portability
             * requirements a binary transport encoding for mail must meet.
             *
             */
            retval = java.util.Base64.getEncoder().encodeToString(outputBytes);
        } catch (GeneralSecurityException e) {
            throw SecurityException.create(e);
        }

        return retval;
    }

    /**
     * Decrypts specified encrypted data with given Key.
     *
     * @param encryptedData The Base64 encoded encrypted data
     * @param key The Key
     * @return The decrypted data
     * @throws OXException
     */
    @Override
    public String decrypt(final String encryptedData, final Key key) throws OXException {
        Cipher cipher;
        final byte encrypted[];
        try {
            /*-
             * It's safe to use "US-ASCII" to turn Base64 encoded encrypted password string into bytes.
             * Taken from RFC 2045 Section 6.8. "Base64 Content-Transfer-Encoding":
             *
             * A 65-character subset of US-ASCII is used, enabling 6 bits to be
             * represented per printable character. (The extra 65th character, "=",
             * is used to signify a special processing function.)
             *
             * NOTE: This subset has the important property that it is represented
             * identically in all versions of ISO 646, including US-ASCII, and all
             * characters in the subset are also represented identically in all
             * versions of EBCDIC. Other popular encodings, such as the encoding
             * used by the uuencode utility, Macintosh binhex 4.0 [RFC-1741], and
             * the base85 encoding specified as part of Level 2 PostScript, do not
             * share these properties, and thus do not fulfill the portability
             * requirements a binary transport encoding for mail must meet.
             *
             */
            if (encryptedData.getBytes(StandardCharsets.US_ASCII).length < 2) {
                LOG.debug("Data is too short to be decrypted");
                throw SecurityException.create();
            }
            encrypted = java.util.Base64.getDecoder().decode(encryptedData);

            cipher = Cipher.getInstance(CIPHER_TYPE);
            cipher.init(Cipher.DECRYPT_MODE, key, IV);
        } catch (GeneralSecurityException e) {
            throw SecurityException.create(e);
        } catch (IllegalArgumentException e) {
            // No valid base64 scheme
            throw SecurityException.create(e);
        }

        try {
            final byte[] outputBytes = cipher.doFinal(encrypted);
            return new String(outputBytes, com.openexchange.java.Charsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw BadPassword.create(e);
        }
    }

    @Override
    public CipherInputStream encryptingStreamFor(InputStream in, Key key) throws OXException {
        if (null == in) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TYPE);
            cipher.init(Cipher.ENCRYPT_MODE, key, IV);
            return new CipherInputStream(in, cipher);
        } catch (GeneralSecurityException e) {
            throw SecurityException.create(e);
        }
    }

    @Override
    public CipherInputStream decryptingStreamFor(InputStream in, Key key) throws OXException {
        if (null == in) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TYPE);
            cipher.init(Cipher.DECRYPT_MODE, key, IV);
            return new CipherInputStream(in, cipher);
        } catch (GeneralSecurityException e) {
            throw SecurityException.create(e);
        }
    }

    /**
     * Generates a secret key from specified password string.
     *
     * @param password The password string
     * @return A secret key generated from specified password string
     * @throws OXException
     */
    private SecretKey generateSecretKey(final String password, final byte[] salt) throws OXException {
        if (Strings.isEmpty(password)) {
            throw CryptoErrorMessage.EmptyPassword.create();
        }
        try {
            final PBKDF2Parameters params = new PBKDF2Parameters(KEY_ALGORITHM, CHARSET, salt, 1000);
            final PBKDF2Engine engine = new PBKDF2Engine(params);
            final byte[] key = engine.deriveKey(password, KEY_LENGTH);
            final SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);
            return secretKey;
        } catch (RuntimeException e) {
            throw CryptoErrorMessage.SecurityException.create(e, e.getMessage());
        }
    }

    private byte[] generateSalt() throws OXException {
        byte[] salt = null;
        try {
            final SecureRandom rand = SecureRandom.getInstance(RANDOM_ALGORITHM);
            salt = new byte[16];
            rand.nextBytes(salt);
        } catch (GeneralSecurityException e) {
            throw SecurityException.create(e);
        }
        return salt;
    }

}
