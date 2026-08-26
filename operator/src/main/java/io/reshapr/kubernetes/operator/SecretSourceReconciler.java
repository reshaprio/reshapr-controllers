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
package io.reshapr.kubernetes.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.reshapr.client.ApiClient;
import io.reshapr.client.ApiException;
import io.reshapr.client.api.DefaultApi;
import io.reshapr.client.model.OAuth2ClientConfiguration;
import io.reshapr.client.model.Secret;
import io.reshapr.client.model.SecretType;
import io.reshapr.kubernetes.api.model.Condition;
import io.reshapr.kubernetes.api.model.Status;
import io.reshapr.kubernetes.api.secretsource.v1alpha1.OAuth2ClientConfigurationSpec;
import io.reshapr.kubernetes.api.secretsource.v1alpha1.SecretSource;
import io.reshapr.kubernetes.api.secretsource.v1alpha1.SecretSourceSpec;
import io.reshapr.kubernetes.api.secretsource.v1alpha1.SecretSourceStatus;
import io.reshapr.kubernetes.api.secretsource.v1alpha1.SecretSpec;
import io.reshapr.kubernetes.api.secretsource.v1alpha1.SecretValuesFromSpec;
import io.reshapr.kubernetes.operator.auth.ReshaprAnnotations;
import io.reshapr.kubernetes.operator.auth.ReshaprApiClientFactory;
import io.reshapr.kubernetes.operator.auth.ReshaprAuthenticationException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_ALL_NAMESPACES;

/**
 * Reconciler for SecretSource custom resource.
 * <p>
 * Keeps a reShapr {@link SecretSource} custom resource in sync with its counterparts in
 * the control plane. Each entry of {@code spec.secrets} is materialized as a control
 * plane {@link Secret}, either from inlined values or by loading them from a Kubernetes
 * {@code Secret} in the same namespace via {@link SecretValuesFromSpec}. The reconciler
 * tracks one {@link Condition} per secret name in the status, using the {@code message}
 * field of the condition to persist the control plane secret id, so that follow-up
 * reconciliations update the existing Secret in place. Secrets that are removed from
 * the spec are deleted from the control plane on the next reconciliation. On deletion
 * of the resource, all tracked control plane Secrets are removed unless the
 * {@code keepOnDelete} flag is set.
 * <p>
 * A shared {@link InformerEventSource} watches all Kubernetes {@code Secret}
 * resources in the cluster and re-triggers the owning SecretSource(s) whenever
 * a referenced Secret is created, updated or deleted — so a change to an already
 * imported Kubernetes Secret is immediately propagated to the control plane.
 * @author laurent
 */
@ControllerConfiguration(informer = @Informer(namespaces = WATCH_ALL_NAMESPACES))
@SuppressWarnings("unused")
@ApplicationScoped
public class SecretSourceReconciler extends BaseReshaprReconciler<SecretSource> implements Cleaner<SecretSource> {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** Kubernetes client used to fetch Secret resources referenced by {@code valuesFrom}. */
   private final KubernetesClient client;

   @Inject
   public SecretSourceReconciler(ReshaprApiClientFactory apiClientFactory, KubernetesClient client) {
      super(apiClientFactory);
      this.client = client;
   }

   /**
    * Register a shared informer on Kubernetes {@code Secret} resources so that any
    * change made to a Secret referenced by a {@link SecretSource} (via
    * {@link SecretValuesFromSpec#getSecretRef()}) triggers a reconciliation of the
    * owning SecretSource(s). A single cluster-wide informer is used regardless of the
    * number of SecretSources; the {@link SecondaryToPrimaryMapper} scans the primary
    * cache (scoped to the Secret's namespace) to route events. Because the mapping is
    * evaluated from the live primary cache, a deleted SecretSource is automatically
    * "unwatched": once it leaves the primary cache, no reconciliation is triggered
    * for it, so there is no per-CR watcher to unregister explicitly on cleanup.
    */
   @Override
   public List<EventSource<?, SecretSource>> prepareEventSources(EventSourceContext<SecretSource> context) {
      SecondaryToPrimaryMapper<io.fabric8.kubernetes.api.model.Secret> secretMapper = kubeSecret -> {
         String kubeSecretNamespace = kubeSecret.getMetadata().getNamespace();
         String kubeSecretName = kubeSecret.getMetadata().getName();
         return context.getPrimaryCache()
               .list(kubeSecretNamespace, ss -> referencesKubernetesSecret(ss, kubeSecretName))
               .map(ResourceID::fromResource)
               .collect(Collectors.toSet());
      };

      InformerEventSourceConfiguration<io.fabric8.kubernetes.api.model.Secret> config =
            InformerEventSourceConfiguration
                  .from(io.fabric8.kubernetes.api.model.Secret.class, SecretSource.class)
                  .withSecondaryToPrimaryMapper(secretMapper)
                  .build();

      return List.of(new InformerEventSource<>(config, context));
   }

   /** Whether a given SecretSource references a Kubernetes Secret with the given name in its {@code valuesFrom}. */
   private static boolean referencesKubernetesSecret(SecretSource secretSource, String kubeSecretName) {
      SecretSourceSpec spec = secretSource.getSpec();
      if (spec == null || spec.getSecrets() == null) {
         return false;
      }
      for (SecretSpec entry : spec.getSecrets()) {
         SecretValuesFromSpec valuesFrom = entry.getValuesFrom();
         if (valuesFrom != null && kubeSecretName.equals(valuesFrom.getSecretRef())) {
            return true;
         }
      }
      return false;
   }

   @Override
   protected UpdateControl<SecretSource> doReconcile(SecretSource secretSource, Context<SecretSource> context, ApiClient apiClient) {
      DefaultApi api = new DefaultApi(apiClient);
      SecretSourceSpec spec = secretSource.getSpec();
      String namespace = secretSource.getMetadata().getNamespace();
      String name = secretSource.getMetadata().getName();
      String organization = secretSource.getMetadata().getAnnotations().get(ReshaprAnnotations.ORGANIZATION);

      logger.infof("Starting reconcile operation for SecretSource '%s' (namespace=%s)", name, namespace);

      SecretSourceStatus status = secretSource.getStatus();
      if (status == null) {
         status = new SecretSourceStatus();
         secretSource.setStatus(status);
      }

      if (spec == null || spec.getSecrets() == null || spec.getSecrets().isEmpty()) {
         logger.warnf("SecretSource '%s' has no secrets in spec — nothing to reconcile", name);
         return recordStatus(secretSource, Status.ERROR, "No secrets defined in spec");
      }

      List<Condition> reconciledConditions = new ArrayList<>();
      boolean allReady = true;

      for (SecretSpec secretSpec : spec.getSecrets()) {
         if (secretSpec.getName() == null || secretSpec.getName().isBlank()) {
            logger.warnf("SecretSource '%s' has a Secret entry without name — skipping", name);
            allReady = false;
            continue;
         }

         Condition condition = getOrCreateCondition(status, secretSpec.getName());
         reconciledConditions.add(condition);

         // If the Secret is loaded from a Kubernetes Secret, resolve it first.
         io.fabric8.kubernetes.api.model.Secret kubeSecret = null;
         if (secretSpec.getValuesFrom() != null) {
            String secretRef = secretSpec.getValuesFrom().getSecretRef();
            if (secretRef == null || secretRef.isBlank()) {
               condition.setStatus(Status.ERROR);
               condition.setMessage("Missing valuesFrom.secretRef");
               touchConditionTime(condition);
               allReady = false;
               continue;
            }
            kubeSecret = client.secrets().inNamespace(namespace).withName(secretRef).get();
            if (kubeSecret == null) {
               logger.errorf("Kubernetes Secret '%s' not found in namespace '%s' for SecretSource '%s'",
                     secretRef, namespace, name);
               condition.setStatus(Status.ERROR);
               condition.setMessage("Kubernetes Secret '" + secretRef + "' not found");
               touchConditionTime(condition);
               allReady = false;
               continue;
            }
         }

         try {
            String previousId = getSecretIdOrNull(condition);
            String secretId = ensureSecretIsPresent(api, organization, secretSpec, kubeSecret, previousId, name);
            condition.setStatus(Status.READY);
            condition.setMessage(secretId);
         } catch (ApiException e) {
            logger.errorf(e, "Control plane error while syncing Secret '%s' for SecretSource '%s'",
                  secretSpec.getName(), name);
            condition.setStatus(Status.ERROR);
            condition.setMessage("Control plane error: " + e.getCode() + " " + safeMessage(e));
            allReady = false;
         }
         touchConditionTime(condition);
      }

      // Delete control plane Secrets whose condition is no longer represented in the spec.
      if (status.getConditions() != null) {
         for (Condition previous : status.getConditions()) {
            if (previous.getType() == null || reconciledConditions.stream().anyMatch(c -> previous.getType().equals(c.getType()))) {
               continue;
            }
            String orphanId = getSecretIdOrNull(previous);
            if (orphanId != null && !spec.isKeepOnDelete()) {
               deleteSecretQuietly(api, orphanId, previous.getType(), name);
            }
         }
      }
      status.setConditions(reconciledConditions);

      Long generation = secretSource.getMetadata().getGeneration();
      status.setStatus(allReady ? Status.READY : Status.ERROR);
      status.setMessage(null);
      if (generation != null) {
         status.setObservedGeneration(generation);
      }
      logger.infof("Finishing reconcile operation for SecretSource '%s' — %d secret(s) reconciled, globalStatus=%s",
            name, reconciledConditions.size(), status.getStatus());
      return UpdateControl.patchStatus(secretSource);
   }

   /** Surface a reconciliation status (and message) on the {@link SecretSource} custom resource. */
   @Override
   protected UpdateControl<SecretSource> recordStatus(SecretSource secretSource, Status status, String message) {
      SecretSourceStatus secretSourceStatus = secretSource.getStatus();
      if (secretSourceStatus == null) {
         secretSourceStatus = new SecretSourceStatus();
      }
      secretSourceStatus.setStatus(status);
      secretSourceStatus.setMessage(message);
      secretSource.setStatus(secretSourceStatus);
      return UpdateControl.patchStatus(secretSource);
   }

   @Override
   public DeleteControl cleanup(SecretSource secretSource, Context<SecretSource> context) {
      String name = secretSource.getMetadata().getName();
      SecretSourceSpec spec = secretSource.getSpec();

      logger.infof("Starting cleanup operation for SecretSource '%s'", name);

      if (spec != null && spec.isKeepOnDelete()) {
         logger.infof("'keepOnDelete' is set for SecretSource '%s' — leaving control plane Secrets in place", name);
         return DeleteControl.defaultDelete();
      }

      try {
         ApiClient apiClient = authenticatedClientFor(secretSource);
         if (apiClient == null) {
            logger.warnf("Missing reShapr annotations on SecretSource '%s' — cannot delete control plane Secrets, removing finalizer", name);
            return DeleteControl.defaultDelete();
         }
         DefaultApi api = new DefaultApi(apiClient);

         SecretSourceStatus status = secretSource.getStatus();
         if (status == null || status.getConditions() == null) {
            logger.infof("No conditions recorded for SecretSource '%s' — nothing to delete", name);
            return DeleteControl.defaultDelete();
         }

         int deletedCount = 0;
         for (Condition condition : status.getConditions()) {
            String secretId = getSecretIdOrNull(condition);
            if (secretId == null) {
               continue;
            }
            try {
               api.deleteSecret(secretId);
               deletedCount++;
               logger.infof("Deleted control plane Secret id=%s ('%s') for SecretSource '%s'",
                     secretId, condition.getType(), name);
            } catch (ApiException e) {
               if (e.getCode() == 404) {
                  logger.infof("Control plane Secret id=%s ('%s') already deleted (HTTP 404) — continuing",
                        secretId, condition.getType());
                  continue;
               }
               logger.errorf(e, "Control plane error while deleting Secret id=%s ('%s') for SecretSource '%s' — retrying",
                     secretId, condition.getType(), name);
               return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
            }
         }
         logger.infof("Finishing cleanup operation for SecretSource '%s' — %d control plane Secret(s) deleted",
               name, deletedCount);
         return DeleteControl.defaultDelete();
      } catch (ReshaprAuthenticationException e) {
         logger.errorf(e, "Authentication failed while deleting control plane Secrets for '%s' — retrying", name);
         return DeleteControl.noFinalizerRemoval().rescheduleAfter(RETRY_DELAY_MS);
      }
   }

   /**
    * Ensure a control plane Secret exists for the given {@link SecretSpec}: update the
    * previously created one when {@code previousId} is set and still present, otherwise
    * create a new Secret. Returns the control plane Secret id.
    */
   private String ensureSecretIsPresent(DefaultApi api, String organization, SecretSpec secretSpec,
         io.fabric8.kubernetes.api.model.Secret kubeSecret, String previousId, String sourceName) throws ApiException {
      if (previousId != null) {
         try {
            Secret existing = api.getSecret(previousId);
            if (existing != null) {
               updateWithSecretSpec(existing, secretSpec, kubeSecret);
               api.updateSecret(previousId, existing);
               logger.infof("Secret '%s' updated in control plane with id=%s for SecretSource '%s'",
                     secretSpec.getName(), previousId, sourceName);
               return previousId;
            }
         } catch (ApiException e) {
            if (e.getCode() != 404) {
               throw e;
            }
            logger.warnf("Recorded control plane Secret id=%s for '%s' no longer exists (HTTP 404) — recreating",
                  previousId, secretSpec.getName());
         }
      }
      Secret secret = new Secret();
      secret.setOrganizationId(organization);
      updateWithSecretSpec(secret, secretSpec, kubeSecret);
      Secret created = api.createSecret(secret);
      Object id = created != null ? created.getId() : null;
      String createdId = id != null ? id.toString() : null;
      logger.infof("Secret '%s' created in control plane with id=%s for SecretSource '%s'",
            secretSpec.getName(), createdId, sourceName);
      return createdId;
   }

   /** Populate a control plane {@link Secret} from a {@link SecretSpec}, decoding from a Kubernetes Secret when requested. */
   private void updateWithSecretSpec(Secret secret, SecretSpec secretSpec,
         io.fabric8.kubernetes.api.model.Secret kubeSecret) {
      secret.setName(secretSpec.getName());
      secret.setDescription(secretSpec.getDescription());
      secret.setType(resolveType(secretSpec.getType()));
      secret.setUseElicitation(secretSpec.getUseElicitation() != null ? secretSpec.getUseElicitation().toString() : null);

      Map<String, String> data = kubeSecret != null ? kubeSecret.getData() : null;
      SecretValuesFromSpec valuesFromSpec = secretSpec.getValuesFrom();

      if (valuesFromSpec != null && data != null) {
         if (shouldCopyFromSecret(valuesFromSpec.getUsernameKey(), data)) {
            secret.setUsername(decodeSecretValue(data.get(valuesFromSpec.getUsernameKey())));
         }
         if (shouldCopyFromSecret(valuesFromSpec.getPasswordKey(), data)) {
            secret.setPassword(decodeSecretValue(data.get(valuesFromSpec.getPasswordKey())));
         }
         if (shouldCopyFromSecret(valuesFromSpec.getTokenKey(), data)) {
            secret.setToken(decodeSecretValue(data.get(valuesFromSpec.getTokenKey())));
         }
         if (shouldCopyFromSecret(valuesFromSpec.getTokenHeaderKey(), data)) {
            secret.setTokenHeader(decodeSecretValue(data.get(valuesFromSpec.getTokenHeaderKey())));
         }
         if (shouldCopyFromSecret(valuesFromSpec.getCertPemKey(), data)) {
            secret.setCertPem(decodeSecretValue(data.get(valuesFromSpec.getCertPemKey())));
         }
      } else {
         secret.setUsername(secretSpec.getUsername());
         secret.setPassword(secretSpec.getPassword());
         secret.setToken(secretSpec.getToken());
         secret.setTokenHeader(secretSpec.getTokenHeader());
         secret.setCertPem(secretSpec.getCertPem());
      }

      // OAuth2 client configuration — inlined fields, with an optional override of the
      // clientSecret loaded from the referenced Kubernetes Secret.
      OAuth2ClientConfigurationSpec oauth2Spec = secretSpec.getOauth2ClientConfiguration();
      if (oauth2Spec != null) {
         OAuth2ClientConfiguration oauth2 = new OAuth2ClientConfiguration();
         oauth2.setClientId(oauth2Spec.getClientId());
         oauth2.setAuthorizationEndpoint(oauth2Spec.getAuthorizationEndpoint());
         oauth2.setTokenEndpoint(oauth2Spec.getTokenEndpoint());
         String clientSecretValue = oauth2Spec.getClientSecret();
         if (valuesFromSpec != null && data != null
               && shouldCopyFromSecret(valuesFromSpec.getOauth2ClientSecretKey(), data)) {
            clientSecretValue = decodeSecretValue(data.get(valuesFromSpec.getOauth2ClientSecretKey()));
         }
         oauth2.setClientSecret(clientSecretValue);
         secret.setOauth2ClientConfiguration(oauth2);
      } else {
         secret.setOauth2ClientConfiguration(null);
      }
   }

   /** Resolve the target control plane {@link SecretType}, defaulting to {@code ENDPOINT}. */
   private SecretType resolveType(String type) {
      if (type == null || type.isBlank()) {
         return SecretType.ENDPOINT;
      }
      try {
         return SecretType.valueOf(type.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         logger.warnf("Unknown Secret type '%s' — defaulting to ENDPOINT", type);
         return SecretType.ENDPOINT;
      }
   }

   /** Look up (or create) the condition tracking a single secret by name in the status. */
   private Condition getOrCreateCondition(SecretSourceStatus status, String secretName) {
      if (status.getConditions() == null) {
         status.setConditions(new ArrayList<>());
      }
      for (Condition candidate : status.getConditions()) {
         if (secretName.equals(candidate.getType())) {
            return candidate;
         }
      }
      Condition created = new Condition();
      created.setType(secretName);
      created.setStatus(Status.UNKNOWN);
      return created;
   }

   /** Refresh the {@code lastTransitionTime} timestamp on a condition to now (UTC, ISO-8601). */
   private void touchConditionTime(Condition condition) {
      condition.setLastTransitionTime(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
   }

   /** Extract the control plane Secret id previously persisted in the {@code message} of a condition. */
   private String getSecretIdOrNull(Condition condition) {
      if (condition == null || condition.getStatus() != Status.READY) {
         return null;
      }
      String message = condition.getMessage();
      return message == null || message.isBlank() ? null : message;
   }

   /** Best-effort deletion of a control plane Secret that is no longer represented in the spec. */
   private void deleteSecretQuietly(DefaultApi api, String secretId, String secretName, String source) {
      try {
         api.deleteSecret(secretId);
         logger.infof("Deleted orphan control plane Secret id=%s ('%s') for SecretSource '%s'",
               secretId, secretName, source);
      } catch (ApiException e) {
         if (e.getCode() == 404) {
            logger.infof("Orphan control plane Secret id=%s ('%s') already deleted (HTTP 404)", secretId, secretName);
         } else {
            logger.warnf(e, "Failed to delete orphan control plane Secret id=%s ('%s') for '%s' — possible orphan",
                  secretId, secretName, source);
         }
      }
   }

   private static boolean shouldCopyFromSecret(String key, Map<String, String> data) {
      return key != null && !key.isBlank() && data != null && data.containsKey(key);
   }

   private static String decodeSecretValue(String encodedValue) {
      return new String(Base64.getDecoder().decode(encodedValue));
   }
}
