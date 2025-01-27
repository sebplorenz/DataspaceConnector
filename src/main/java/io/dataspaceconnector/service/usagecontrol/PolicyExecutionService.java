/*
 * Copyright 2020 Fraunhofer Institute for Software and Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dataspaceconnector.service.usagecontrol;

import de.fraunhofer.iais.eis.ContractAgreement;
import de.fraunhofer.iais.eis.Permission;
import de.fraunhofer.iais.eis.Rule;
import io.dataspaceconnector.config.ConnectorConfiguration;
import io.dataspaceconnector.exception.PolicyExecutionException;
import io.dataspaceconnector.exception.RdfBuilderException;
import io.dataspaceconnector.service.ids.ConnectorService;
import io.dataspaceconnector.service.message.type.LogMessageService;
import io.dataspaceconnector.service.message.type.NotificationService;
import io.dataspaceconnector.util.IdsUtils;
import io.dataspaceconnector.util.RuleUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes policy conditions. Refers to the ids policy enforcement point (PEP).
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class PolicyExecutionService {

    /**
     * Service for configuring policy settings.
     */
    private final @NonNull ConnectorConfiguration connectorConfig;

    /**
     * Service for the current connector configuration.
     */
    private final @NonNull ConnectorService connectorService;

    /**
     * Service for ids notification messages.
     */
    private final @NonNull NotificationService notificationService;

    /**
     * Service for ids log messages.
     */
    private final @NonNull LogMessageService logMessageService;

    /**
     * Send contract agreement to clearing house.
     *
     * @param agreement The ids contract agreement.
     */
    public void sendAgreement(final ContractAgreement agreement) {
        try {
            final var recipient = connectorConfig.getClearingHouse();
            if (!recipient.equals(URI.create(""))) {
                logMessageService.sendMessage(recipient, IdsUtils.toRdf(agreement));
            }
        } catch (PolicyExecutionException | RdfBuilderException exception) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to send contract agreement to clearing house. "
                        + "[exception=({})]", exception.getMessage());
            }
        }
    }

    /**
     * Send a message to the clearing house. Allow the access only if that operation was successful.
     *
     * @param target The target object.
     * @throws PolicyExecutionException if the access could not be successfully logged.
     */
    public void logDataAccess(final URI target) throws PolicyExecutionException {
        final var recipient = connectorConfig.getClearingHouse();
        if (!recipient.equals(URI.create(""))) {
            logMessageService.sendMessage(recipient, buildLog(target).toString());
        }
    }

    /**
     * Send a message to the clearing house. Allow the access only if that operation was successful.
     *
     * @param rule    The ids rule.
     * @param element The accessed element.
     * @throws PolicyExecutionException If the notification has not been successful.
     */
    public void reportDataAccess(final Rule rule, final URI element)
            throws PolicyExecutionException {
        if (rule instanceof Permission) {
            final var postDuty = ((Permission) rule).getPostDuty().get(0);
            final var recipient = RuleUtils.getEndpoint(postDuty);

            final var logItem = buildLog(element).toString();

            notificationService.sendMessage(URI.create(recipient), logItem);
        } else if (log.isWarnEnabled()) {
                log.warn("Reporting data access is only supported for permissions.");
        }
    }

    /**
     * Build a log information object.
     *
     * @param target The accessed element.
     * @return The log line.
     */
    private Map<String, Object> buildLog(final URI target) {
        final var id = connectorService.getConnectorId();

        final var output = new HashMap<String, Object>();
        output.put("target", target);
        output.put("issuerConnector", id);
        output.put("accessed", new Date());

        return output;
    }
}
