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

package com.openexchange.pgp.keys.tools;

import static com.openexchange.java.Autoboxing.L;
import java.util.Date;
import java.util.Iterator;
import java.util.Objects;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.bcpg.sig.RevocationReasonTags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import com.openexchange.exception.OXException;
import com.openexchange.pgp.keys.common.ModifyingPGPPublicKeyRing;
import com.openexchange.pgp.keys.exceptions.PGPKeysExceptionCodes;

/**
 * {@link PGPUtil}
 *
 * @author <a href="mailto:ioannis.chouklis@open-xchange.com">Ioannis Chouklis</a>
 * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
 */
public final class PGPKeysUtil {

    /**
     * Convert byte array fingerprint into standard String format with spacing
     *
     * @param fingerprint
     * @return
     */
    public static String getFingerPrintInBlocks(byte[] fingerprint) {
        StringBuffer fpstring = new StringBuffer();
        for (int i = 0; i < fingerprint.length; ++i) {
            String hex = Integer.toHexString((char) fingerprint[i]);
            hex = hex.toUpperCase();
            while (hex.length() < 2) {
                hex = '0' + hex;
            }
            if (hex.length() > 2) {
                hex = hex.substring(hex.length() - 2);
            }
            fpstring.append(hex);
            if (i % 2 == 1) {
                fpstring.append(" ");
            }
        }
        return fpstring.toString().trim();
    }

    /**
     * Converts byte array fingerprint into standard HEX String format
     *
     * @param fingerprint The fingerprint to convert
     * @return The HEX fingerprint as String
     */
    public static String getFingerPrint(byte[] fingerprint) {
        StringBuffer fpstring = new StringBuffer();
        for (int i = 0; i < fingerprint.length; ++i) {
            String hex = Integer.toHexString((char) fingerprint[i]).toUpperCase();
            while (hex.length() < 2) {
                hex = '0' + hex;
            }
            if (hex.length() > 2) {
                hex = hex.substring(hex.length() - 2);
            }
            fpstring.append(hex);
        }
        return fpstring.toString().trim();
    }

    public static int getHashAlgorithmTags() {
        return HashAlgorithmTags.SHA256;
    }

    /**
     * Check if a public key is expired
     *
     * @param key
     * @return
     */
    public static boolean isExpired(PGPPublicKey key) {
        if (key == null) {
            return false;
        }
        if (key.getValidSeconds() == 0) {
            return false;
        }
        Date now = new Date();
        return key.getCreationTime().getTime() + (key.getValidSeconds() * 1000) - now.getTime() < 0;
    }

    /**
     * Check if all keys are expired
     */
    public static boolean checkAllExpired(PGPPublicKeyRing ring) {
        boolean allExpired = true;
        Iterator<PGPPublicKey> it = ring.getPublicKeys();
        while (it.hasNext()) {
            PGPPublicKey key = it.next();
            allExpired = allExpired && isExpired(key);
        }
        return (allExpired);
    }

    /**
     * Duplicates the specified secret key ring. This method can also be used to create a new secret key ring with a different password
     *
     * @param secretKeyRing The secret key ring to duplicate
     * @param decryptorPasswordHash The hashed password for the decryptor
     * @param encryptorPasswordHash The hashed password for the encryptor
     * @param symmetricKeyAlgorithmTag The symmetric key algorithm tag (see PGPEncryptedData.AES_256 and PGPEncryptedData.AES_128)
     * @return The duplicated {@link PGPSecretKeyRing}
     * @throws PGPException
     */
    public static PGPSecretKeyRing duplicateSecretKeyRing(PGPSecretKeyRing secretKeyRing, String decryptorPasswordHash, String encryptorPasswordHash, int symmetricKeyAlgorithmTag) throws PGPException {
        PGPDigestCalculator sha256Calc = new BcPGPDigestCalculatorProvider().get(getHashAlgorithmTags());
        PBESecretKeyDecryptor oldEncryptor = new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(decryptorPasswordHash.toCharArray());
        PBESecretKeyEncryptor newEncryptor = new BcPBESecretKeyEncryptorBuilder(symmetricKeyAlgorithmTag, sha256Calc, 0x60)
            .build(encryptorPasswordHash.toCharArray());

        return PGPSecretKeyRing.copyWithNewPassword(secretKeyRing, oldEncryptor, newEncryptor);
    }

    /**
     * Gets the flags of a given key
     *
     * @param key the key to get the flags for
     * @return the flags for the given key
     */
    public static int getKeyFlags(PGPPublicKey key) {
        Iterator<PGPSignature> signatures = key.getSignatures();
        while (signatures.hasNext()) {
            PGPSignature signature = signatures.next();
            PGPSignatureSubpacketVector packet = signature.getHashedSubPackets();
            if (packet != null) {
                return packet.getKeyFlags();
            }
        }
        return 0;
    }

    /**
     * Checks whether a key has the given flags or not
     *
     * @param key the key
     * @param flag the flag to check
     * @see https://tools.ietf.org/html/rfc4880#section-5.2.3.21
     * @return True if the key has the given flag
     */
    public static boolean keyHasFlag(PGPPublicKey key, int flag) {
        int existingFlags = getKeyFlags(key);
        return (existingFlags & flag) > 0;
    }

    /**
     * Checks whether a key is meant to be an encryption key.
     *
     * Note: This method checks if the preferred usage of the key is encrypting.
     * Use {@link org.bouncycastle.openpgp.PGPPublicKey#isEncryptionKey} for checking if a key is technical able to encrypt
     *
     * @param key the key to check
     * @return true, if the key is meant to be an encryption key, false otherwise
     */
    public static boolean isEncryptionKey(PGPPublicKey key) {
        //Check if the key has flags
        if (getKeyFlags(key) > 0) {
            //Check for encryption Flags
            return keyHasFlag(key, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);
        } else {
            //Fallback if flags do not exist (for older keys or if the key creation software did not create them)
            return key.isEncryptionKey();
        }
    }

    /**
     * Checks whether a key is meant to be an decryption key
     *
     * Note: This method checks if the key's public key preferred usage of is encrypting.
     * Use {@link org.bouncycastle.openpgp.PGPPublicKey#isEncryptionKey} for checking if a key is technical able to encrypt
     *
     * @param key The key to check
     * @return True, if the key is meant to be an decryption key, false otherwise
     */
    public static boolean isDecryptionKey(PGPSecretKey key) {
        if (isEncryptionKey(key.getPublicKey())) {
            if (!key.isPrivateKeyEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the ring's singing key
     *
     * @param keyRing The key ring
     * @return the ring's signing sub key or the master key, if no sub key is marked for singing
     */
    public static PGPSecretKey getSigningKey(PGPSecretKeyRing keyRing) {
        PGPSecretKey ret = getSigningSubKey(keyRing);
        if (ret == null) {
            //If no signing subkey was found we are using the master key
            ret = keyRing.getSecretKey();
        }
        return ret;
    }

    /**
     * Returns a ring's subkey which is meant to be used as signing key
     *
     * @param keyRing The ring to get the signing sub key for
     * @return the signing sub key, or null if no signing sub key was found
     */
    public static PGPSecretKey getSigningSubKey(PGPSecretKeyRing keyRing) {
        Iterator<PGPSecretKey> iter = keyRing.getSecretKeys();
        while (iter.hasNext()) {
            PGPSecretKey secretKey = iter.next();
            if (!secretKey.isMasterKey() /* only check sub keys */) {
                PGPPublicKey publicKey = secretKey.getPublicKey();
                boolean isSigningKey = keyHasFlag(publicKey, KeyFlags.SIGN_DATA);
                if (isSigningKey && !publicKey.hasRevocation()) {
                    return secretKey;
                }
            }
        }
        return null;
    }

    /**
     * Gets the first public key from the key ring which is meant to be an encrypting key.
     *
     * Note: This method checks if the preferred usage of a key is encrypting.
     * Use {@link org.bouncycastle.openpgp.PGPPublicKey#isEncryptionKey} for checking if a key is technical able to encrypt.
     *
     * @param keyRing The key ring used to search for a suitable encryption key.
     * @return The first key found in the key ring which is meant to be an encryption key, or null if no such key was found in the ring.
     */
    public static PGPPublicKey getEncryptionKey(PGPPublicKeyRing keyRing) {
        PGPPublicKey found = null;
        if (keyRing != null) {
            Iterator<PGPPublicKey> it = keyRing.getPublicKeys();
            while (it.hasNext()) {
                PGPPublicKey key = it.next();
                if (PGPKeysUtil.isEncryptionKey(key)) {
                    if (key.isMasterKey() && !isExpired(key) && !key.hasRevocation()) {  // If master key, we will use only if we don't have another encryption key
                        if (found == null) {
                            found = key;
                        }
                    } else {
                        if (!key.hasRevocation()) {
                            if (!isExpired(key)) {
                                return (key);
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    /**
     * Gets the first secret key from the key ring which is meant to be a decryption key.
     * <br>
     * A key is meant to be a decryption keyT if the corresponding public key is meant to be a encryption key and the private key's data is not empty.
     * <br>
     * <br>
     * Use {@link org.bouncycastle.openpgp.PGPPublicKey#isEncryptionKey} with a private key's public counterpart for checking if a key is technical able to decrypt.
     *
     * @param keyRing The key ring to search for a decryption key.
     * @return The fist key in the key ring which is meant to be a decryption key, or null if no such key was found.
     */
    public static PGPSecretKey getDecryptionKey(PGPSecretKeyRing keyRing) {
        if (keyRing != null) {
            Iterator<PGPSecretKey> it = keyRing.getSecretKeys();
            while (it.hasNext()) {
                PGPSecretKey secretKey = it.next();
                if (!secretKey.isPrivateKeyEmpty() && secretKey.getPublicKey() != null && isEncryptionKey(secretKey.getPublicKey())) {
                    return secretKey;
                }
            }
        }
        return null;
    }

    /**
     * Gets the master key from the given public key ring, or null if no master key was found.
     *
     * @param publicKeyRing The key ring.
     * @return The master key, or null if no master key was found.
     */
    public static PGPPublicKey getPublicMasterKey(PGPPublicKeyRing publicKeyRing) {
        for (PGPPublicKey publicKey : publicKeyRing) {
            if (publicKey.isMasterKey()) {
                return publicKey;
            }
        }
        return null;
    }

    /**
     * Adds a new User ID to a {@link PGPPublicKeyRing}
     *
     * @param publicKeyRing The public key ring to add the user ID to
     * @param privateKey The private key used for signing
     * @param userId The new user ID
     * @return The public key ring containing the new user ID
     * @throws PGPException
     */
    public static PGPPublicKeyRing addUID(PGPPublicKeyRing publicKeyRing, PGPPrivateKey privateKey, String userId) throws PGPException {
        PGPPublicKey publicMasterKey = publicKeyRing.getPublicKey();
        PGPPublicKey newPublicMasterKey = addUID(publicMasterKey, privateKey, userId);

        ModifyingPGPPublicKeyRing modifyingPGPPublicKeyRing = new ModifyingPGPPublicKeyRing(publicKeyRing);
        if (modifyingPGPPublicKeyRing.removePublicKey(publicMasterKey)) {
            modifyingPGPPublicKeyRing.addPublicKey(newPublicMasterKey);
            publicKeyRing = modifyingPGPPublicKeyRing.getRing();
            return publicKeyRing;
        }
        return null;
    }

    /**
     * Adds a new User ID to a {@link PGPSecretKeyRing}
     *
     * @param secretKeyRing The secret key ring to add the user ID to
     * @param privateKey The private key used to signing
     * @param userId The new user ID
     * @return The secret key ring containing the new user ID
     * @throws PGPException
     */
    public static PGPSecretKeyRing addUID(PGPSecretKeyRing secretKeyRing, PGPPrivateKey privateKey, String userId) throws PGPException {
        PGPSecretKey secretMasterKey = secretKeyRing.getSecretKey();
        PGPPublicKey modifiedPublicMasterKey = addUID(secretMasterKey.getPublicKey(), privateKey, userId);
        PGPSecretKey modifiedSecretMasterKey = PGPSecretKey.replacePublicKey(secretMasterKey, modifiedPublicMasterKey);
        return PGPSecretKeyRing.insertSecretKey(secretKeyRing, modifiedSecretMasterKey);
    }

    /**
     * Adds a new User ID to a {@link PGPPublicKey}
     *
     * @param publicKey The public key to add the user ID to
     * @param privateKey The private key used for singning
     * @param userId The new user ID
     * @return The public key containing the new user ID
     * @throws PGPException
     */
    public static PGPPublicKey addUID(PGPPublicKey publicKey, PGPPrivateKey privateKey, String userId) throws PGPException {
        PGPSignatureGenerator generator = new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(publicKey.getAlgorithm(), org.bouncycastle.openpgp.PGPUtil.SHA1));
        generator.init(PGPSignature.POSITIVE_CERTIFICATION, privateKey);
        PGPSignatureSubpacketGenerator signhashgen = createSignatureGeneratorFromPrior (publicKey, L(privateKey.getKeyID()));
        generator.setHashedSubpackets(signhashgen.generate());
        PGPSignature certification = generator.generateCertification(userId, publicKey);
        return PGPPublicKey.addCertification(publicKey, userId, certification);
    }


    /**
     * Checks if a public key contains the specified user ID
     *
     * @param publicKey The key
     * @param userId The user ID
     * @return true, if the key contains the given ID, false otherwise
     */
    public static boolean containsUID(PGPPublicKey publicKey, String userId) {
        userId = userId.toUpperCase();
        for(Iterator<String> ids = publicKey.getUserIDs(); ids.hasNext();) {
            String keyUserId = ids.next().toUpperCase();
            if (keyUserId.contains(userId) || userId.contains(keyUserId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a public key ring contains the specified user ID
     *
     * @param publicKey The key ring
     * @param userId The user ID
     * @return true, if the and of the ring's keys contain the ID, false otherwise
     */
    public static boolean containsUID(PGPPublicKeyRing publicKeyring, String userId) {
       for(Iterator<PGPPublicKey> keys = publicKeyring.getPublicKeys(); keys.hasNext();) {
          if (containsUID(keys.next(), userId)){
             return true;
          }
       }
       return false;
    }

    /**
     * Find either the primary UserId, or the most recent, and use those hashes for a new PGPSignatureSubpacketGenerator
     * @param publicKey
     * @param keyId
     * @return
     */
    private static PGPSignatureSubpacketGenerator createSignatureGeneratorFromPrior (PGPPublicKey publicKey, Long keyId) {
        PGPSignatureSubpacketGenerator gen = new PGPSignatureSubpacketGenerator();
        Iterator<byte[]> userIds = publicKey.getRawUserIDs();
        PGPSignatureSubpacketVector last = null;
        Date mostRecentSignatureDate = null;
        while (userIds.hasNext()) {
            byte[] rawId = userIds.next();
            Iterator<PGPSignature> sigs = publicKey.getSignaturesForID(rawId);
            if (sigs == null) {
                continue;
            }
            while (sigs.hasNext()) {
                PGPSignature sig = sigs.next();
                if (sig.isCertification() && sig.getKeyID() == keyId.longValue()) {
                    PGPSignatureSubpacketVector vectors = sig.getHashedSubPackets();
                    if (vectors != null) {
                        if (vectors.isPrimaryUserID()) {
                            return setHashes (gen, vectors);
                        }
                        Date signatureCreationTime = vectors.getSignatureCreationTime();
                        if (mostRecentSignatureDate == null || (signatureCreationTime != null && signatureCreationTime.getTime() >  mostRecentSignatureDate.getTime())) {
                            mostRecentSignatureDate = signatureCreationTime;
                            last = vectors;
                        }
                    }
                }
            }
        }
        if (last != null) {
            return setHashes(gen, last);
        }
        return defaultHashSettings (gen);
    }

    /**
     * Return a PGPSignatureSubpacketGenerator populated with vectors
     * @param gen
     * @param vectors
     * @return
     */
    private static PGPSignatureSubpacketGenerator setHashes (PGPSignatureSubpacketGenerator gen, PGPSignatureSubpacketVector vectors) {
        gen.setKeyFlags(false, vectors.getKeyFlags());
        if (vectors.getFeatures() != null) {
            byte[] features = vectors.getFeatures().getData();
            for (byte feature : features) {
                gen.setFeature(vectors.getFeatures().isCritical(), feature);
            }
        }
        if (vectors.getPreferredCompressionAlgorithms() != null) {
            gen.setPreferredCompressionAlgorithms(false, vectors.getPreferredCompressionAlgorithms());
        }
        if (vectors.getPreferredHashAlgorithms() != null) {
            gen.setPreferredHashAlgorithms(false, vectors.getPreferredHashAlgorithms());
        }
        if (vectors.getPreferredSymmetricAlgorithms() != null) {
            gen.setPreferredSymmetricAlgorithms(false, vectors.getPreferredSymmetricAlgorithms());
        }
        if (vectors.getPreferredCompressionAlgorithms() != null) {
            gen.setPreferredCompressionAlgorithms(false, vectors.getPreferredCompressionAlgorithms());
        }
        if (vectors.isPrimaryUserID()) {
            gen.setPrimaryUserID(false, true);
        }
        return gen;
    }

    /**
     * Set PGPSignatureSubpacketGenerator to a set of defaults
     * @param gen
     * @return
     */
    private static PGPSignatureSubpacketGenerator defaultHashSettings (PGPSignatureSubpacketGenerator gen) {
        gen.setPreferredHashAlgorithms(false, new int[] { HashAlgorithmTags.SHA512 });
        gen.setPreferredSymmetricAlgorithms(false, new int[] { SymmetricKeyAlgorithmTags.AES_256 });
        gen.setPreferredCompressionAlgorithms(false, new int[] { CompressionAlgorithmTags.ZIP });
        gen.setFeature(true, Features.FEATURE_MODIFICATION_DETECTION);
        gen.setKeyFlags(false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER);
        return gen;
    }

    /**
     * Convert the specified reason string a byte representation. See {@link RevocationReasonTags}
     *
     * @param reason The reason in string
     * @return The byte representation
     */
    private static byte revokeReason(String reason) {
        switch (reason) {
            case "NO_REASON":
                return RevocationReasonTags.NO_REASON;
            case "KEY_SUPERSEDED":
                return RevocationReasonTags.KEY_SUPERSEDED;
            case "KEY_COMPROMISED":
                return RevocationReasonTags.KEY_COMPROMISED;
            case "KEY_RETIRED":
                return RevocationReasonTags.KEY_RETIRED;
            case "USER_NO_LONGER_VALID":
                return RevocationReasonTags.USER_NO_LONGER_VALID;
        }
        return RevocationReasonTags.NO_REASON;
    }

    /**
     * Revokes a public key ring
     *
     * @param privateKey The private key which is used for revocation.
     * @param publicKeyRing The public key ring to be revoked
     * @param keyId The id of the key/subkey to be revoked, 0 if all keys
     * @param revocationReason The reason why the key is being revoked.
     * @return The new key ring with the recovation certificate set
     * @throws PGPException
     * @throws OXException
     */
    public static PGPPublicKeyRing revokeKey(PGPPrivateKey privateKey, PGPPublicKeyRing publicKeyRing, long keyId, String revocationReason) throws PGPException, OXException {
        privateKey = Objects.requireNonNull(privateKey, "privateKey must not be null");
        publicKeyRing = Objects.requireNonNull(publicKeyRing, "publicKeyRing must not be null");
        ModifyingPGPPublicKeyRing ret = new ModifyingPGPPublicKeyRing(publicKeyRing);
        Iterator<PGPPublicKey> pkeys = publicKeyRing.getPublicKeys();
        PGPPublicKey master = getPublicMasterKey(publicKeyRing);
        if (master == null) {
            throw PGPKeysExceptionCodes.MASTER_KEY_NOT_FOUND.create();
        }

        while (pkeys.hasNext()) {
            PGPPublicKey pub = pkeys.next();
            if (pub.getKeyID() == keyId || keyId == 0) {
                if (ret.removePublicKey(pub)) {
                    PGPSignatureSubpacketGenerator subHashGenerator = new PGPSignatureSubpacketGenerator();
                    PGPSignatureSubpacketGenerator subUnHashGenerator = new PGPSignatureSubpacketGenerator();
                    PGPSignatureGenerator generator = new PGPSignatureGenerator(
                        new BcPGPContentSignerBuilder(pub.getAlgorithm(), org.bouncycastle.openpgp.PGPUtil.SHA1));
                    if (pub.isMasterKey()) {
                        generator.init(PGPSignature.KEY_REVOCATION, privateKey);
                        master = pub;
                    } else {
                        generator.init(PGPSignature.SUBKEY_REVOCATION, privateKey);
                    }
                    subHashGenerator.setSignatureCreationTime(false, new Date());
                    subHashGenerator.setRevocationReason(false, revokeReason(revocationReason), revocationReason);
                    subUnHashGenerator.setRevocationKey(false, pub.getAlgorithm(), pub.getFingerprint());
                    generator.setHashedSubpackets(subHashGenerator.generate());
                    generator.setUnhashedSubpackets(subUnHashGenerator.generate());
                    if (pub.isMasterKey()) {
                        PGPSignature signature = generator.generateCertification(pub);
                        pub = PGPPublicKey.addCertification(pub, signature);
                    } else {
                        PGPSignature signature = generator.generateCertification(master, pub);
                        pub = PGPPublicKey.addCertification(pub, signature);
                    }

                    ret.addPublicKey(pub);
                }
                else {
                   throw new PGPException("Error while removing public key: key not found in keyring");
                }
            }
        }

        return ret.getRing();
    }
}
