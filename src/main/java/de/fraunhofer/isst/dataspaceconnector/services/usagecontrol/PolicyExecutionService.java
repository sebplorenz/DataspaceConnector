package de.fraunhofer.isst.dataspaceconnector.services.usagecontrol;

import de.fraunhofer.iais.eis.ContractAgreement;
import de.fraunhofer.iais.eis.Rule;
import de.fraunhofer.isst.dataspaceconnector.config.ConnectorConfiguration;
import de.fraunhofer.isst.dataspaceconnector.exceptions.MessageBuilderException;
import de.fraunhofer.isst.dataspaceconnector.exceptions.MessageException;
import de.fraunhofer.isst.dataspaceconnector.exceptions.PolicyExecutionException;
import de.fraunhofer.isst.dataspaceconnector.exceptions.RdfBuilderException;
import de.fraunhofer.isst.dataspaceconnector.model.messages.LogMessageDesc;
import de.fraunhofer.isst.dataspaceconnector.model.messages.NotificationMessageDesc;
import de.fraunhofer.isst.dataspaceconnector.services.ids.ConnectorService;
import de.fraunhofer.isst.dataspaceconnector.services.messages.types.LogMessageService;
import de.fraunhofer.isst.dataspaceconnector.services.messages.types.NotificationService;
import de.fraunhofer.isst.dataspaceconnector.utils.IdsUtils;
import de.fraunhofer.isst.dataspaceconnector.utils.PolicyUtils;
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
    public void sendAgreementToClearingHouse(final ContractAgreement agreement) {
        try {
            final var recipient = connectorConfig.getClearingHouse();
            final var rdf = IdsUtils.toRdf(agreement);

            // Build ids response message.
            final var desc = new LogMessageDesc();
            desc.setRecipient(recipient);
            logMessageService.sendMessage(desc, rdf);
        } catch (MessageBuilderException | RdfBuilderException | MessageException exception) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to send contract agreement to clearing house. [exception=({})]",
                        exception.getMessage(), exception);
            }
        }
    }

    /**
     * Send a message to the clearing house. Allow the access only if that operation was successful.
     *
     * @param element The accessed element.
     * @throws PolicyExecutionException If the access could not be successfully logged.
     */
    public void logDataAccess(final URI element) throws PolicyExecutionException {
        final var url = connectorConfig.getClearingHouse();
        final var logMessage = buildLog(element).toString();

        try {
            final var desc = new LogMessageDesc();
            desc.setRecipient(url);
            final var response = logMessageService.sendMessage(desc, logMessage);

            if (response == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No response received.");
                }
                throw new PolicyExecutionException("Log message has no valid response.");
            }
        } catch (MessageException e) {
            if (log.isDebugEnabled()) {
                log.debug("Unsuccessful logging. [exception=({})]", e.getMessage(), e);
            }
            throw new PolicyExecutionException("Log message could not be sent.");
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
        final var recipient = PolicyUtils.getEndpoint(rule);
        final var logMessage = buildLog(element).toString();

        Map<String, String> response;
        try {
            final var desc = new NotificationMessageDesc();
            desc.setRecipient(URI.create(recipient));
            response = notificationService.sendMessage(desc, logMessage);

            if (response == null) {
                if (log.isDebugEnabled()) {
                    log.debug("No response received. [response=({})]", response);
                }
                throw new PolicyExecutionException("Notification has no valid response.");
            }
        } catch (MessageException e) {
            if (log.isDebugEnabled()) {
                log.debug("Notification not sent. [exception=({})]", e.getMessage(), e);
            }
            throw new PolicyExecutionException("Notification was not successful.");
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

        return new HashMap<>() {{
            put("target", target);
            put("issuerConnector", id);
            put("accessed", new Date());
        }};
    }
}
