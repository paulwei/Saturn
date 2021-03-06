/**
 * Copyright 2016 vip.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 * </p>
 **/

package com.vip.saturn.job.console.utils;

import com.vip.saturn.job.console.exception.SaturnJobConsoleException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public class PasswordUtils {

	public static final String HASH_METHOD_PLANTEXT = "plaintext";

	public static final String HASH_METHOD_PBKDF2 = "PBKDF2WithHmacSHA1";

	private static final Logger log = LoggerFactory.getLogger(PasswordUtils.class);

	private static final int ITERATIONS = 10 * 1000;

	private static final int SALT_LEN = 8;

	private static final int KEY_LEN = 256;

	public static String genPassword(String password, String hashMethod) throws Exception {
		byte[] salt = SecureRandom.getInstance("SHA1PRNG").generateSeed(SALT_LEN);
		return genPassword(password, salt, hashMethod);
	}

	public static String genPassword(String password, byte[] salt, String hashMethod) throws Exception {
		if (!isHashMethodSupported(hashMethod)) {
			throw new SaturnJobConsoleException(String.format("hash method [%s] is not supported", hashMethod));
		}

		if (HASH_METHOD_PLANTEXT.equals(hashMethod)) {
			return password;
		}

		return hash(password, salt) + "$" + Hex.encodeHexString(salt);
	}

	/**
	 * ???????????????PBKDF2WithHmacSHA1
	 */
	public static String hash(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
		SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(HASH_METHOD_PBKDF2);
		SecretKey key = secretKeyFactory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LEN));
		return Hex.encodeHexString(key.getEncoded());
	}

	public static void validate(String password, String passwordInDB, String hashMethod)
			throws SaturnJobConsoleException {
		if (!isHashMethodSupported(hashMethod)) {
			throw new SaturnJobConsoleException(String.format("hash method [%s] is not supported", hashMethod));
		}

		if (PasswordUtils.HASH_METHOD_PLANTEXT.equals(hashMethod)) {
			if (!password.equals(passwordInDB)) {
				throw new SaturnJobConsoleException(SaturnJobConsoleException.ERROR_CODE_AUTHN_FAIL, "???????????????????????????");
			}
			return;
		}

		String[] saltAndPassword = passwordInDB.split("\\$");
		if (saltAndPassword.length != 2) {
			log.debug("malformed password in db");
			throw new SaturnJobConsoleException(SaturnJobConsoleException.ERROR_CODE_AUTHN_FAIL, "???????????????????????????");
		}

		String hashOfRequestPassword;
		try {
			hashOfRequestPassword = hash(password, getSalt(saltAndPassword[1]));
		} catch (Exception e) {
			throw new SaturnJobConsoleException(SaturnJobConsoleException.ERROR_CODE_AUTHN_FAIL, "???????????????????????????");
		}

		if (!hashOfRequestPassword.equals(new String(saltAndPassword[0]))) {
			throw new SaturnJobConsoleException(SaturnJobConsoleException.ERROR_CODE_AUTHN_FAIL, "???????????????????????????");
		}
	}

	public static boolean isHashMethodSupported(String hashMethod) {
		return HASH_METHOD_PBKDF2.equals(hashMethod) || HASH_METHOD_PLANTEXT.equals(hashMethod);
	}

	private static byte[] getSalt(String s) throws DecoderException {
		return Hex.decodeHex(s.toCharArray());
	}

}
