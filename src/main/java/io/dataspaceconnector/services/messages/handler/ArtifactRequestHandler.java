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
package io.dataspaceconnector.services.messages.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iais.eis.ArtifactRequestMessageImpl;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.isst.ids.framework.messaging.model.messages.MessageHandler;
import de.fraunhofer.isst.ids.framework.messaging.model.messages.MessagePayload;
import de.fraunhofer.isst.ids.framework.messaging.model.messages.SupportedMessageType;
import de.fraunhofer.isst.ids.framework.messaging.model.responses.BodyResponse;
import de.fraunhofer.isst.ids.framework.messaging.model.responses.MessageResponse;
import io.dataspaceconnector.config.ConnectorConfiguration;
import io.dataspaceconnector.exceptions.*;
import io.dataspaceconnector.model.QueryInput;
import io.dataspaceconnector.model.messages.ArtifactResponseMessageDesc;
import io.dataspaceconnector.services.EntityResolver;
import io.dataspaceconnector.services.messages.MessageResponseService;
import io.dataspaceconnector.services.messages.types.ArtifactResponseService;
import io.dataspaceconnector.services.messages.types.LogMessageService;
import io.dataspaceconnector.services.usagecontrol.ContractManager;
import io.dataspaceconnector.services.usagecontrol.DataProvisionVerifier;
import io.dataspaceconnector.services.usagecontrol.VerificationInput;
import io.dataspaceconnector.services.usagecontrol.VerificationResult;
import io.dataspaceconnector.utils.ErrorMessages;
import io.dataspaceconnector.utils.MessageUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;

import java.io.IOException;
import java.net.URI;

/**
 * This @{@link ArtifactRequestHandler} handles all incoming messages that have a
 * {@link de.fraunhofer.iais.eis.ArtifactRequestMessageImpl} as part one in the multipart message.
 * This header must have the correct '@type' reference as defined in the
 * {@link de.fraunhofer.iais.eis.ArtifactRequestMessageImpl} JsonTypeName annotation.
 */
@Component
@Log4j2
@SupportedMessageType(ArtifactRequestMessageImpl.class)
@RequiredArgsConstructor
public class ArtifactRequestHandler implements MessageHandler<ArtifactRequestMessageImpl> {

    /**
     * Service for building and sending message responses.
     */
    private final @NonNull MessageResponseService responseService;

    /**
     * Service for handling artifact response messages.
     */
    private final @NonNull ArtifactResponseService messageService;

    /**
     * Service for resolving entities.
     */
    private final @NonNull EntityResolver entityResolver;

    /**
     * Service for connector usage control configurations.
     */
    private final @NonNull ConnectorConfiguration connectorConfig;

    /**
     * Service for contract processing.
     */
    private final @NonNull ContractManager contractManager;

    /**
     * The verifier for the data access.
     */
    private final @NonNull DataProvisionVerifier accessVerifier;

    /**
     * Service for Clearing House logging
     */
    private final @NonNull LogMessageService logMessageService;

    /**
     * This message implements the logic that is needed to handle the message. As it returns the
     * input as string the messagePayload-InputStream is converted to a String.
     *
     * @param message The request message.
     * @param payload The message payload.
     * @return The response message.
     * @throws RuntimeException If the response body failed to be build.
     */
    @Override
    public MessageResponse handleMessage(final ArtifactRequestMessageImpl message,
                                         final MessagePayload payload) throws RuntimeException {
        // Validate incoming message.
        try {
            messageService.validateIncomingMessage(message);
        } catch (MessageEmptyException exception) {
            return responseService.handleMessageEmptyException(exception);
        } catch (VersionNotSupportedException exception) {
            return responseService.handleInfoModelNotSupportedException(exception,
                    message.getModelVersion());
        }

        // Read relevant parameters for message processing.
        final var requestedArtifact = MessageUtils.extractRequestedArtifact(message);
        final var transferContract = MessageUtils.extractTransferContract(message);
        final var issuer = MessageUtils.extractIssuerConnector(message);
        final var messageId = MessageUtils.extractMessageId(message);

        if (requestedArtifact == null || requestedArtifact.toString().equals("")) {
            // Without a requested artifact, the message processing will be aborted.
            return responseService.handleMissingRequestedArtifact(requestedArtifact,
                    transferContract, issuer, messageId);
        }

        // Check agreement only if contract negotiation is turned on.
        final var negotiation = connectorConfig.isPolicyNegotiation();
        if (negotiation) {
            if (transferContract == null || transferContract.toString().equals("")) {
                // Without a transfer contract, the message processing will be aborted.
                return responseService.handleMissingTransferContract(requestedArtifact,
                        transferContract, issuer, messageId);
            }

            try {
                final var agreement = contractManager.validateTransferContract(
                        transferContract, requestedArtifact);

                final var input = new VerificationInput(requestedArtifact, issuer, agreement);
                if (accessVerifier.verify(input) == VerificationResult.DENIED) {
                    throw new PolicyRestrictionException(ErrorMessages.POLICY_RESTRICTION);
                }
            } catch (ResourceNotFoundException | IllegalArgumentException exception) {
                // Agreement could not be loaded or deserialized.
                return responseService.handleMessageProcessingFailed(exception,
                        requestedArtifact, transferContract, issuer, messageId);
            } catch (PolicyRestrictionException exception) {
                // Conditions not fulfilled.
                return responseService.handlePolicyRestrictionException(exception,
                        requestedArtifact, transferContract, issuer, messageId);
            } catch (ContractException exception) {
                // Invalid transfer contract.
                return responseService.handleInvalidTransferContract(exception, requestedArtifact,
                        transferContract, issuer, messageId);
            }
        }

        // Either without contract negotiation or if all conditions are fulfilled, data is returned.
        try {
            // Process query input.
            final var queryInput = getQueryInputFromPayload(payload);
            return returnData(requestedArtifact, transferContract, issuer, messageId, queryInput);
        } catch (InvalidInputException exception) {
            return responseService.handleInvalidQueryInput(exception, requestedArtifact,
                    transferContract, issuer, messageId);
        } catch (Exception exception) {
            // Failed to retrieve data.
            return responseService.handleFailedToRetrieveData(exception, requestedArtifact,
                    issuer, messageId);
        }
    }

    /**
     * Get data by requested artifact and return within an artifact response message.
     *
     * @param requestedArtifact The requested artifact.
     * @param transferContract  The id of the transfer contract.
     * @param issuer            The issuer connector.
     * @param messageId         The message id.
     * @param queryInput        The query input.
     * @return A message response.
     */
    private MessageResponse returnData(final URI requestedArtifact, final URI transferContract,
                                       final URI issuer, final URI messageId,
                                       final QueryInput queryInput) {
        try {
            final var data = entityResolver.getDataByArtifactId(requestedArtifact, queryInput);

            // Build ids response message.
            final var desc = new ArtifactResponseMessageDesc(issuer, messageId, transferContract);
            final var header = messageService.buildMessage(desc);

            // Send ids response message.
            BodyResponse<?> response = BodyResponse.create(header, Base64Utils.encodeToString(data.readAllBytes()));
            logMessageService.logResponseMessage(response);
            return response;
        } catch (MessageBuilderException | ConstraintViolationException | IOException exception) {
            return responseService.handleResponseMessageBuilderException(exception, issuer,
                    messageId);
        }
    }

    /**
     * Read query parameters from message payload.
     *
     * @param messagePayload The message's payload.
     * @return the query input.
     * @throws InvalidInputException If the query input is not empty but invalid.
     */
    private QueryInput getQueryInputFromPayload(final MessagePayload messagePayload)
            throws InvalidInputException {
        try {
            final var payload = MessageUtils.getStreamAsString(messagePayload);
            if (payload.equals("")) {
                // Query input is optional, so no rejection message will be sent. Query input will
                // be checked for null value in HttpService.class.
                return null;
            } else {
                return new ObjectMapper().readValue(payload, QueryInput.class);
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid query input. [exception=({})]", e.getMessage(), e);
            }
            throw new InvalidInputException("Invalid query input.", e);
        }
    }
}
