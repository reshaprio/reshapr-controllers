/*
 * Copyright The Reshapr Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reshapr.kubernetes.operator.auth;


import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the projected service account token from the mounted volume.
 * Kubernetes projected service account tokens are mounted at a configurable path
 * (default: {@code /var/run/secrets/reshapr.io/serviceaccount/token}).
 * This provider reads the token content on demand so that token rotation is
 * transparently handled.
 * @author laurent
 */
@Singleton
public class ServiceAccountTokenProvider {

   private static final Logger logger = Logger.getLogger(ServiceAccountTokenProvider.class);

   /** Default path where the projected SA token is mounted. */
   private static final String DEFAULT_TOKEN_PATH = "/var/run/secrets/reshapr/serviceaccount/token";

   private final Path tokenPath;

   public ServiceAccountTokenProvider() {
      this(Path.of(DEFAULT_TOKEN_PATH));
   }

   /** Constructor allowing a custom token path (useful for testing). */
   public ServiceAccountTokenProvider(Path tokenPath) {
      this.tokenPath = tokenPath;
   }

   /**
    * Read and return the current projected service account token.
    *
    * @return The token string.
    * @throws IOException if the token file cannot be read.
    */
   public String getToken() throws IOException {
      logger.debugf("Reading projected SA token from '%s'", tokenPath);
      return Files.readString(tokenPath).trim();
   }
}

