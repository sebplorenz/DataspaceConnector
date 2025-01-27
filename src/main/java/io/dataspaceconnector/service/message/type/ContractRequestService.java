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
package io.dataspaceconnector.service.message.type;

import de.fraunhofer.iais.eis.ContractAgreementMessageImpl;
import de.fraunhofer.iais.eis.ContractRequest;
import de.fraunhofer.iais.eis.ContractRequestMessageBuilder;
import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.iais.eis.util.Util;
import de.fraunhofer.ids.messaging.util.IdsMessageUtils;
import io.dataspaceconnector.exception.MessageException;
import io.dataspaceconnector.exception.MessageResponseException;
import io.dataspaceconnector.exception.RdfBuilderException;
import io.dataspaceconnector.model.message.ContractRequestMessageDesc;
import io.dataspaceconnector.util.ErrorMessages;
import io.dataspaceconnector.util.IdsUtils;
import io.dataspaceconnector.util.Utils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;

/**
 * Message service for ids contract request messages.
 */
@Service
public final class ContractRequestService
        extends AbstractMessageService<ContractRequestMessageDesc> {

    /**
     * @throws IllegalArgumentException     if desc is null.
     * @throws ConstraintViolationException if security tokes is null or another error appears
     *                                      when building the message.
     */
    @Override
    public Message buildMessage(final ContractRequestMessageDesc desc)
            throws ConstraintViolationException {
        Utils.requireNonNull(desc, ErrorMessages.DESC_NULL);

        final var connectorId = getConnectorService().getConnectorId();
        final var modelVersion = getConnectorService().getOutboundModelVersion();
        final var token = getConnectorService().getCurrentDat();

        final var recipient = desc.getRecipient();
        final var contractId = desc.getTransferContract();

        return new ContractRequestMessageBuilder()
                ._issued_(IdsMessageUtils.getGregorianNow())
                ._modelVersion_(modelVersion)
                ._issuerConnector_(connectorId)
                ._senderAgent_(connectorId)
                ._securityToken_(token)
                ._recipientConnector_(Util.asList(recipient))
                ._transferContract_(contractId)
                .build();
    }

    @Override
    protected Class<?> getResponseMessageType() {
        return ContractAgreementMessageImpl.class;
    }

    /**
     * Build and send a description request message.
     *
     * @param recipient The recipient.
     * @param request   The contract request.
     * @return The response map.
     * @throws MessageException         if message handling failed.
     * @throws RdfBuilderException      if the contract request rdf string could not be built.
     * @throws IllegalArgumentException if contract request is null.
     */
    public Map<String, String> sendMessage(final URI recipient, final ContractRequest request)
            throws MessageException, RdfBuilderException {
        Utils.requireNonNull(request, ErrorMessages.ENTITY_NULL);

        final var contractRdf = IdsUtils.toRdf(request);
        return send(new ContractRequestMessageDesc(recipient, request.getId()), contractRdf);
    }

    /**
     * Check if the response message is of type contract agreement.
     *
     * @param response The response as map.
     * @return True if the response type is as expected.
     * @throws MessageResponseException If the response could not be read.
     */
    public boolean validateResponse(final Map<String, String> response)
            throws MessageResponseException {
        return isValidResponseType(response);
    }
}
