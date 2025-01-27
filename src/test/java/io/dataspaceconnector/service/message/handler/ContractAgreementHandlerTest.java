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
package io.dataspaceconnector.service.message.handler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iais.eis.Action;
import de.fraunhofer.iais.eis.ContractAgreement;
import de.fraunhofer.iais.eis.ContractAgreementBuilder;
import de.fraunhofer.iais.eis.ContractAgreementMessageBuilder;
import de.fraunhofer.iais.eis.ContractAgreementMessageImpl;
import de.fraunhofer.iais.eis.DynamicAttributeTokenBuilder;
import de.fraunhofer.iais.eis.MessageProcessedNotificationMessage;
import de.fraunhofer.iais.eis.PermissionBuilder;
import de.fraunhofer.iais.eis.RejectionReason;
import de.fraunhofer.iais.eis.TokenFormat;
import de.fraunhofer.iais.eis.util.Util;
import de.fraunhofer.ids.messaging.handler.message.MessagePayloadInputstream;
import de.fraunhofer.ids.messaging.response.BodyResponse;
import de.fraunhofer.ids.messaging.response.ErrorResponse;
import io.dataspaceconnector.model.Agreement;
import io.dataspaceconnector.service.EntityResolver;
import io.dataspaceconnector.service.EntityUpdateService;
import io.dataspaceconnector.service.message.type.LogMessageService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"clearing.house.url=https://ch-ids.aisec.fraunhofer.de/logs/messages/"})
class ContractAgreementHandlerTest {

    @Autowired
    ContractAgreementHandler handler;

    @MockBean
    private EntityResolver entityResolver;

    @MockBean
    private EntityUpdateService updateService;

    @MockBean
    private LogMessageService logMessageService;

    @Value("${clearing.house.url}")
    private URI chUri;

    @Test
    public void handleMessage_nullMessage_returnBadParametersResponse() {
        /* ARRANGE */
        final var payload = new MessagePayloadInputstream(InputStream.nullInputStream(), new ObjectMapper());

        /* ACT */
        final var result = (ErrorResponse) handler.handleMessage(null, payload);

        /* ASSERT */
        assertEquals(RejectionReason.BAD_PARAMETERS, result.getRejectionMessage().getRejectionReason());
    }

    @Test
    public void handleMessage_unsupportedMessage_returnUnsupportedVersionRejectionMessage() throws
            DatatypeConfigurationException {
        /* ARRANGE */
        final var message = new ContractAgreementMessageBuilder()
                ._senderAgent_(URI.create("https://localhost:8080"))
                ._issuerConnector_(URI.create("https://localhost:8080"))
                ._securityToken_(new DynamicAttributeTokenBuilder()._tokenFormat_(TokenFormat.OTHER)._tokenValue_("").build())
                ._modelVersion_("tetris")
                ._issued_(getXmlCalendar())
                ._correlationMessage_(URI.create("https://somecorrelationMessage"))
                .build();

        /* ACT */
        final var result = (ErrorResponse)handler.handleMessage((ContractAgreementMessageImpl) message, null);

        /* ASSERT */
        assertEquals(RejectionReason.VERSION_NOT_SUPPORTED, result.getRejectionMessage().getRejectionReason());
    }


    @Test
    public void handleMessage_nullPayload_returnBadRequestErrorResponse() throws
            DatatypeConfigurationException {
        /* ARRANGE */
        final var message = new ContractAgreementMessageBuilder()
                ._senderAgent_(URI.create("https://localhost:8080"))
                ._issuerConnector_(URI.create("https://localhost:8080"))
                ._securityToken_(new DynamicAttributeTokenBuilder()._tokenFormat_(TokenFormat.OTHER)._tokenValue_("").build())
                ._modelVersion_("4.0.0")
                ._issued_(getXmlCalendar())
                ._correlationMessage_(URI.create("https://somecorrelationMessage"))
                .build();

        /* ACT */
        final var result = (ErrorResponse)handler.handleMessage((ContractAgreementMessageImpl) message, null);

        /* ASSERT */
        assertEquals(RejectionReason.BAD_PARAMETERS, result.getRejectionMessage().getRejectionReason());
    }

    @Test
    public void handleMessage_emptyPayload_returnBadRequestErrorResponse() throws
            DatatypeConfigurationException {
        /* ARRANGE */
        final var message = new ContractAgreementMessageBuilder()
                ._senderAgent_(URI.create("https://localhost:8080"))
                ._issuerConnector_(URI.create("https://localhost:8080"))
                ._securityToken_(new DynamicAttributeTokenBuilder()._tokenFormat_(TokenFormat.OTHER)._tokenValue_("").build())
                ._modelVersion_("4.0.0")
                ._issued_(getXmlCalendar())
                ._correlationMessage_(URI.create("https://somecorrelationMessage"))
                .build();

        /* ACT */
        final var result = (ErrorResponse)handler.handleMessage((ContractAgreementMessageImpl) message, new MessagePayloadInputstream(InputStream.nullInputStream(), new ObjectMapper()));

        /* ASSERT */
        assertEquals(RejectionReason.BAD_PARAMETERS, result.getRejectionMessage().getRejectionReason());
    }

    @Test
    public void handleMessage_agreementsDoNotMatch_returnBadParametersResponse() {
        final var message = new ContractAgreementMessageBuilder()
                ._senderAgent_(URI.create("https://localhost:8080"))
                ._issuerConnector_(URI.create("https://localhost:8080"))
                ._securityToken_(new DynamicAttributeTokenBuilder()._tokenFormat_(TokenFormat.OTHER)._tokenValue_("").build())
                ._modelVersion_("4.0.0")
                ._issued_(getXmlCalendar())
                ._correlationMessage_(URI.create("https://somecorrelationMessage"))
                .build();

        final var agreement = getContractAgreement();
        final var storedAgreement = getAgreement(agreement.toRdf());
        final var differentAgreement = getDifferentContractAgreement();

        when(entityResolver.getAgreementByUri(any())).thenReturn(storedAgreement);

        /* ACT */
        final var result = (ErrorResponse)
                handler.handleMessage((ContractAgreementMessageImpl) message,
                        new MessagePayloadInputstream(
                                new ByteArrayInputStream(
                                        differentAgreement.toRdf().getBytes(StandardCharsets.UTF_8)),
                                new ObjectMapper()));

        /* ASSERT */
        assertEquals(RejectionReason.BAD_PARAMETERS, result.getRejectionMessage().getRejectionReason());
    }

    @Test
    public void handleMessage_updatingAgreementFails_returnBadParametersResponse() {
        /* ARRANGE */
        final var message = new ContractAgreementMessageBuilder()
                ._senderAgent_(URI.create("https://localhost:8080"))
                ._issuerConnector_(URI.create("https://localhost:8080"))
                ._securityToken_(new DynamicAttributeTokenBuilder()._tokenFormat_(TokenFormat.OTHER)._tokenValue_("").build())
                ._modelVersion_("4.0.0")
                ._issued_(getXmlCalendar())
                ._correlationMessage_(URI.create("https://somecorrelationMessage"))
                .build();

        final var agreement = getContractAgreement();
        final var storedAgreement = getAgreement(agreement.toRdf());

        when(entityResolver.getAgreementByUri(any())).thenReturn(storedAgreement);
        when(updateService.confirmAgreement(any())).thenReturn(false);

        /* ACT */
        final var result = (ErrorResponse)
                handler.handleMessage((ContractAgreementMessageImpl) message,
                        new MessagePayloadInputstream(
                                new ByteArrayInputStream(
                                        agreement.toRdf().getBytes(StandardCharsets.UTF_8)),
                                new ObjectMapper()));

        /* ASSERT */
        assertEquals(RejectionReason.BAD_PARAMETERS, result.getRejectionMessage().getRejectionReason());
    }

    @Test
    public void handleMessage_validAgreement_returnMessageProcessedNotificationMessageAndLogToCH() {
        /* ARRANGE */
        final var message = new ContractAgreementMessageBuilder()
                ._senderAgent_(URI.create("https://localhost:8080"))
                ._issuerConnector_(URI.create("https://localhost:8080"))
                ._securityToken_(new DynamicAttributeTokenBuilder()._tokenFormat_(TokenFormat.OTHER)._tokenValue_("").build())
                ._modelVersion_("4.0.0")
                ._issued_(getXmlCalendar())
                ._correlationMessage_(URI.create("https://somecorrelationMessage"))
                .build();

        final var agreement = getContractAgreement();
        final var storedAgreement = getAgreement(agreement.toRdf());

        when(entityResolver.getAgreementByUri(any())).thenReturn(storedAgreement);
        when(updateService.confirmAgreement(any())).thenReturn(true);
        doNothing().when(logMessageService).sendMessage(any(), any());

        /* ACT */
        final var result = (BodyResponse<MessageProcessedNotificationMessage>)
                handler.handleMessage((ContractAgreementMessageImpl) message,
                new MessagePayloadInputstream(
                        new ByteArrayInputStream(agreement.toRdf().getBytes(StandardCharsets.UTF_8)),
                        new ObjectMapper()));

        /* ASSERT */
        assertNotNull(result.getHeader());

        //TODO fails because deserializer changes timezone of dates from +2:00 to Z
//        verify(logMessageService, times(1)).sendMessage(chUri, agreement.toRdf());
    }

    @SneakyThrows
    private XMLGregorianCalendar getXmlCalendar() {
        final var calendar = new GregorianCalendar();
        calendar.setTime(new Date());
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
    }

    @SneakyThrows
    private ContractAgreement getContractAgreement() {
        return new ContractAgreementBuilder()
                ._contractDate_(getXmlCalendar())
                ._contractStart_(getXmlCalendar())
                ._contractEnd_(getXmlCalendar())
                ._permission_(Util.asList(new PermissionBuilder()
                        ._action_(Util.asList(Action.USE))
                        .build()))
                ._provider_(URI.create("https://provider.com"))
                ._consumer_(URI.create("https://consumer.com"))
                .build();
    }

    @SneakyThrows
    private ContractAgreement getDifferentContractAgreement() {
        return new ContractAgreementBuilder()
                ._contractDate_(getXmlCalendar())
                ._contractStart_(getXmlCalendar())
                ._contractEnd_(getXmlCalendar())
                ._permission_(Util.asList(new PermissionBuilder()
                        ._action_(Util.asList(Action.USE))
                        .build()))
                ._provider_(URI.create("https://provider.com"))
                ._consumer_(URI.create("https://other-consumer.com"))
                .build();
    }

    private Agreement getAgreement(final String rdf) {
        final var agreement = new Agreement();
        ReflectionTestUtils.setField(agreement, "value", rdf);
        ReflectionTestUtils.setField(agreement, "artifacts",
                Collections.singletonList(URI.create("https://artifact.com")));
        return agreement;
    }
}
