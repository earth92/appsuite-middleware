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

import static com.openexchange.java.Autoboxing.L;
import static com.openexchange.java.Autoboxing.l;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PGPDataDecryptor;
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import com.openexchange.exception.OXException;
import com.openexchange.pgp.core.exceptions.PGPCoreExceptionCodes;

/**
 * {@link PGPDecrypter} - Wrapper for providing stream based PGP decryption
 *
 * @author <a href="mailto:benjamin.gruedelbach@open-xchange.com">Benjamin Gruedelbach</a>
 * @since v7.8.4
 */
public class PGPDecrypter {

    private final PGPKeyRetrievalStrategy keyRetrievalStrategy;
    private static final int BUFFERSIZE = 1024;
    private MDCValidationMode mdcValidationMode = MDCValidationMode.WARN_ON_MISSING;

    public enum MDCValidationMode {

        /**
         * gently - Returns a warning-result if no MDC package was found for integrity verification
         */
        WARN_ON_MISSING,

        /**
         * Strict - Throws an exception if no MDC package was found for integrity verification
         */
        FAIL_ON_MISSING,
    };

    public static class PGPDataContainer {

        private final PGPPrivateKey privateKey;
        private final PGPPublicKeyEncryptedData data;

        private PGPDataContainer(PGPPrivateKey privateKey, PGPPublicKeyEncryptedData data) {
            this.privateKey = privateKey;
            this.data = data;
        }

        public PGPPrivateKey getPrivateKey() {
            return privateKey;
        }

        public PGPPublicKeyEncryptedData getData() {
            return data;
        }
    }

    /**
     * Initializes a new {@link PGPDecrypter}.
     *
     * @param keyRetrievalStrategy A strategy for retrieving public and private PGP keys in order to decrypt data and verify signatures
     */
    public PGPDecrypter(PGPKeyRetrievalStrategy keyRetrievalStrategy) {
        keyRetrievalStrategy = Objects.requireNonNull(keyRetrievalStrategy, "keyRetrievalStrategy must not be null");
        this.keyRetrievalStrategy = keyRetrievalStrategy;
    }

    /**
     * Initializes a new {@link PGPDecrypter}.
     *
     * @param secretKey The secret key used for decryption
     * @param publicKeyRetrievalStrategy A strategy to retrieve public keys only for signature verification.
     */
    public PGPDecrypter(final PGPSecretKey secretKey, final PGPKeyRetrievalStrategy publicKeyRetrievalStrategy) {
        this.keyRetrievalStrategy = new PGPKeyRetrievalStrategy() {

            @Override
            public PGPPrivateKey getSecretKey(long keyId, String userIdentity, char[] password) throws Exception {
                return PGPSecretKeyDecoder.decodePrivateKey(secretKey, password);
            }

            @Override
            public PGPPublicKey getPublicKey(long keyId) throws Exception {
                return publicKeyRetrievalStrategy.getPublicKey(keyId);
            }
        };
    }

    /**
     * Internal method to retrieve a private key. This retrieves the first key suitable for a PGPEncryptedData object.
     *
     * @param encryptedDataList A list of encrypted data packets to retrieve the key for.
     * @param userID The ID of the user to get a key for
     * @param password The user's password of the key
     * @return The first suitable decoded private key for a PGPEncryptedData object
     * @throws OXException if no key was found
     * @throws Exception
     */
    @SuppressWarnings("synthetic-access")
    private PGPDataContainer getDataContainer(PGPEncryptedDataList encryptedDataList, String userID, char[] password) throws Exception {
        //Processing decrypted data
        PGPPrivateKey privateKey = null;
        PGPPublicKeyEncryptedData encryptedData = null;
        Iterator<PGPEncryptedData> encryptedDataListIterator = encryptedDataList.getEncryptedDataObjects();
        while (encryptedDataListIterator.hasNext()) {
            encryptedData = (PGPPublicKeyEncryptedData) encryptedDataListIterator.next();
            privateKey = getPrivateKey(encryptedData, userID, password);
            if (privateKey != null) {
                break;
            }
        }
        return new PGPDataContainer(privateKey, encryptedData);
    }



    /**
     * Internal method to retrieve a private key
     *
     * @param encryptedData A encrypted data packet to receive the key for
     * @param userID the ID of the user to get a key for
     * @param password The user's password of the key
     * @return The decoded private key for the given packet and userID, or null, if no such key was found
     * @throws Exception
     */
    private PGPPrivateKey getPrivateKey(PGPPublicKeyEncryptedData encryptedData, String userID, char[] password) throws Exception {
        return this.keyRetrievalStrategy.getSecretKey(encryptedData.getKeyID(), userID,password);
    }

    /**
     * Internal method to retrieve a public key
     *
     * @param onePassSignature The signature packet to get the public key for
     * @return The public key for the given packet, or null if no such key was found
     * @throws Exception
     */
    private PGPPublicKey getPublicKey(PGPOnePassSignature onePassSignature) throws Exception {
        return this.keyRetrievalStrategy.getPublicKey(onePassSignature.getKeyID());
    }

    /**
     * Get a list of keyIds from an EncryptedDataList Padded with "("
     *
     * @param encryptedDataList
     * @return formatted list of 8 digit hex key Ids
     */
    private String getMissingKeyIds(PGPEncryptedDataList encryptedDataList) {
        StringBuilder sb = new StringBuilder();
        Iterator<PGPEncryptedData> dataListIterator = encryptedDataList.getEncryptedDataObjects();
        PGPPublicKeyEncryptedData encryptedData = null;
        sb.append(" ( ");
        while (dataListIterator.hasNext()) {
            encryptedData = (PGPPublicKeyEncryptedData) dataListIterator.next();
            String keyId = Long.toHexString(encryptedData.getKeyID()).substring(8).toUpperCase();
            if (!sb.toString().contains(keyId)) { // avoid repeats
                if (sb.length() > 8) {
                    sb.append(", "); // already more than one added
                }
                sb.append("0x");
                sb.append(keyId);
            }
        }
        sb.append(" )");
        return (sb.toString());
    }

    /**
     * Extracts PGP encrypted data from an decoder InputStream
     *
     * @param decoderStream the PGP InputStream to extract the PGP encrypted data from.
     * @return The PGP encrypted data extracted from the PGP stream
     * @throws IOException
     * @throws OXException
     */
    private PGPEncryptedDataList getPGPEncryptedData(InputStream decoderStream) throws IOException, OXException {

        PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(decoderStream, new BcKeyFingerprintCalculator());

        //reading first part of the stream
        Object firstObject = pgpObjectFactory.nextObject();
        if (firstObject == null) {
            throw PGPCoreExceptionCodes.NO_PGP_DATA_FOUND.create();
        }

        PGPEncryptedDataList encryptedDataList;
        //the first object might be a PGP marker packet.
        if (firstObject instanceof PGPEncryptedDataList) {
            encryptedDataList = (PGPEncryptedDataList) firstObject;
        } else {
            encryptedDataList = (PGPEncryptedDataList) pgpObjectFactory.nextObject();
        }

        if (encryptedDataList == null) {
            //No encrypted data found (i.e if a signature was supplied)
            throw PGPCoreExceptionCodes.NO_PGP_DATA_FOUND.create();
        }
        return encryptedDataList;
    }

    /**
     * Decrypts the given data
     *
     * @param publicKeyEncryptedData The data to decrypt
     * @return The InputStream to the decrypted clear text data
     * @throws PGPException
     */
    private InputStream decrypt(PGPDataContainer publicKeyEncryptedData) throws PGPException {
        return publicKeyEncryptedData.getData().getDataStream(getDecryptionFactory(publicKeyEncryptedData));
    }

    /**
     * Gets the ID of the master key for a given PGPPublicKey
     *
     * This method searches for sub-key-binding on the given public key and return the ID of the issuer key.
     *
     * @param publicKey The public key to get the master key ID from
     * @return The ID of the publicKey's master key, or null if the mater key could not be determined.
     */
    private static Long getPublicMasterKeyId(PGPPublicKey publicKey) {

        if (publicKey.isMasterKey()) {
            //The given key is the actual master key
            return L(publicKey.getKeyID());
        }

        //Get the subkey binding and return the ID of the master key
        @SuppressWarnings("rawtypes") Iterator subkeyBinding = publicKey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING);
        if (subkeyBinding.hasNext()) {
            PGPSignature signature = (PGPSignature)subkeyBinding.next();
            return L(signature.getKeyID());
        }
        return null;
    }

    /**
     * Tries to obtain the master key for the given key
     *
     * @param key The key to obtain the master key from
     * @return The key if it is an master key, the master-key obtained from sub-key-binding signatures, or null if no binding found
     * @throws Exception
     */
    private PGPPublicKey getMasterKey(PGPPublicKey key) throws Exception {
        if (key.isMasterKey()) {
           return key;
        }
        Long masterKeyId = getPublicMasterKeyId(key);
        if (masterKeyId != null) {
            return this.keyRetrievalStrategy.getPublicKey(l(masterKeyId));
        }

        return null;
    }

    /**
     * Controls whether or not to treat a key, fetched from a {@link PGPKeyRetrievalStrategy}, as found.
     *
     * @param key The key to check
     * @return true, if the key should be considered as found, false if it should be considered as not found.
     */
    protected boolean keyFound(PGPPrivateKey key) {
        return key != null;
    }

    /**
     * Creates a factory for creating a {@link PGPDataDecryptor}
     *
     * @param publicKeyEncryptedData The data to decrypt with the factory.
     * @return A factory able to decrypt the given PGP data
     */
    protected PublicKeyDataDecryptorFactory getDecryptionFactory(PGPDataContainer publicKeyEncryptedData) {
        return new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(publicKeyEncryptedData.getPrivateKey());
    }

    /**
     * Defines how to handle missing MDC (Modification detection code) packages.
     *
     * @param mdcValidationMode
     * @return this
     */
    public PGPDecrypter setMDCValidationMode(MDCValidationMode mdcValidationMode) {
        this.mdcValidationMode = mdcValidationMode;
        return this;
    }

    /**
     * Decrypts data
     *
     * @param input The input stream to read the PGP data from
     * @param output The output stream to write the decoded data to
     * @param userID The PGP user identity of the user who want's to decode the data
     * @param password The password of the user's key which will be retrieved using the given strategy
     * @return A list of Signature verification results, or an empty list, if the encrypted data was not signed
     * @throws Exception
     */
    public PGPDecryptionResult decrypt(InputStream input, OutputStream output, String userID, char[] password) throws Exception {
        List<PGPSignatureVerificationResult> signatureVerificationResults = new ArrayList<>();
        MDCVerificationResult mdcVerificationResult  = null;
        try (InputStream decoderStream = PGPUtil.getDecoderStream(input)) {

            //Gets a list of PGP data from the stream
            PGPEncryptedDataList encryptedDataList = getPGPEncryptedData(decoderStream);

            //Get the public key encrypted data for the given user's private key
            PGPDataContainer publicKeyEncryptedData = getDataContainer(encryptedDataList, userID, password);
            if (!keyFound(publicKeyEncryptedData.getPrivateKey())) {
                throw PGPCoreExceptionCodes.PRIVATE_KEY_NOT_FOUND.create(userID + getMissingKeyIds(encryptedDataList));
            }

            //Decrypting the data
            InputStream clearDataStream = decrypt(publicKeyEncryptedData);
            //Processing decrypted pgp data
            PGPObjectFactory plainFact = new PGPObjectFactory(clearDataStream, new BcKeyFingerprintCalculator());
            Object pgpObject = plainFact.nextObject();
            PGPOnePassSignatureList onePassSignatureList = null;
            PGPOnePassSignature onePassSignature = null;
            PGPPublicKey singatureVerifyKey = null;
            boolean signatureInitialized = false;
            boolean signatureVerificationKeyFound = false;
            boolean signatureVerificationError = false;
            while (pgpObject != null) {

                //Handling compressed data
                if (pgpObject instanceof PGPCompressedData) {
                    PGPCompressedData compressedData = (PGPCompressedData) pgpObject;
                    plainFact = new PGPObjectFactory(compressedData.getDataStream(), new BcKeyFingerprintCalculator());
                    pgpObject = plainFact.nextObject();
                }

                //Processing the raw data
                else if (pgpObject instanceof PGPLiteralData) {
                    PGPLiteralData pgpLiteralData = (PGPLiteralData) pgpObject;
                    InputStream literalInputStream = pgpLiteralData.getInputStream();
                    byte[] buffer = new byte[BUFFERSIZE];
                    int len = 0;
                    while ((len = literalInputStream.read(buffer)) > -1) {
                        if (signatureInitialized) {
                            //If we have a signature we are going to update the OPS for verifying the signature later on
                            onePassSignature.update(buffer, 0, len);
                        }

                        //Writing the decrypted message to the output stream
                        output.write(buffer, 0, len);
                    }
                    try {
                        pgpObject = plainFact.nextObject();
                    } catch (IOException ex) {
                        // If next object is a signature, and we simply don't know the algorithm, don't fail
                        // Ignore signature for now
                        if (ex.getMessage() != null && ex.getMessage().contains("unknown signature key algorithm")) {
                            pgpObject = null;
                        } else {
                            throw ex;
                        }
                    }
                }

                //handling one pass signatures for initializing signature calculation
                else if (pgpObject instanceof PGPOnePassSignatureList) {
                    onePassSignatureList = (PGPOnePassSignatureList) pgpObject;
                    //By now we are only supporting one signature
                    onePassSignature = onePassSignatureList.get(0);
                    singatureVerifyKey = getPublicKey(onePassSignature);
                    if (singatureVerifyKey != null) {
                        try {
                            onePassSignature.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), singatureVerifyKey);
                            signatureInitialized = true;
                            signatureVerificationKeyFound = true;
                        } catch (PGPException e) {
                            signatureVerificationError = true;
                            signatureVerificationResults.add(new PGPSignatureVerificationResult(e.getLocalizedMessage()));
                        }

                    }
                    pgpObject = plainFact.nextObject();
                }

                //Handling signatures
                else if (pgpObject instanceof PGPSignatureList) {
                    PGPSignatureList signatureList = (PGPSignatureList) pgpObject;
                    if (signatureList.size() > 0) {
                        //By now we are only supporting one signature
                        PGPSignature signature = signatureList.get(0);
                        if (signatureInitialized) {
                            //Verify signatures
                            PGPSignatureVerificationResult pgpSignatureVerificationResult = new PGPSignatureVerificationResult(signature, onePassSignature.verify(signature));
                            if (singatureVerifyKey != null) {
                               pgpSignatureVerificationResult.setIssuerKey(singatureVerifyKey);
                               Iterator<String> userIds = null;
                               PGPPublicKey masterKey = getMasterKey(singatureVerifyKey);
                               if (masterKey != null) {
                                   userIds = masterKey.getUserIDs();
                                   if (userIds != null) {
                                       //Adding user-id so that it is possible for a caller to determine who created this signature
                                       while (userIds.hasNext()) {
                                           pgpSignatureVerificationResult.addIssuerUserId(userIds.next());
                                       }
                                   }
                               }
                            }
                            signatureVerificationResults.add(pgpSignatureVerificationResult);
                        }
                        else if (!signatureVerificationKeyFound && !signatureVerificationError) {
                            //Key not found for verifying the signature; KeyRetrievalStrategy is responsible for logging this;
                            signatureVerificationResults.add(new PGPSignatureVerificationResult(signature, false, true));
                        }
                    }
                    pgpObject = plainFact.nextObject();
                }
            }

            //Perform integrity/MDC validation
            mdcVerificationResult = MDCVerificationResult.createFrom(publicKeyEncryptedData);
            if (mdcVerificationResult.isPresent()) {
                if (!mdcVerificationResult.verified){
                    throw PGPCoreExceptionCodes.PGP_EXCEPTION.create("Integrity check of the message failed.");
                }
            }
            else {
               if (mdcValidationMode == MDCValidationMode.FAIL_ON_MISSING) {
                    throw PGPCoreExceptionCodes.PGP_EXCEPTION.create("Itegrity protection not found.");
               }
            }
        }
        output.flush();
        return new PGPDecryptionResult(signatureVerificationResults, mdcVerificationResult);
    }
}
