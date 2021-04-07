package de.fraunhofer.isst.dataspaceconnector.utils;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.GregorianCalendar;

import de.fraunhofer.iais.eis.Action;
import de.fraunhofer.iais.eis.Artifact;
import de.fraunhofer.iais.eis.ArtifactBuilder;
import de.fraunhofer.iais.eis.ConnectorEndpointBuilder;
import de.fraunhofer.iais.eis.ContentType;
import de.fraunhofer.iais.eis.Contract;
import de.fraunhofer.iais.eis.ContractAgreementBuilder;
import de.fraunhofer.iais.eis.ContractOffer;
import de.fraunhofer.iais.eis.ContractOfferBuilder;
import de.fraunhofer.iais.eis.DutyBuilder;
import de.fraunhofer.iais.eis.Frequency;
import de.fraunhofer.iais.eis.GeoPointBuilder;
import de.fraunhofer.iais.eis.IANAMediaTypeBuilder;
import de.fraunhofer.iais.eis.Language;
import de.fraunhofer.iais.eis.Permission;
import de.fraunhofer.iais.eis.PermissionBuilder;
import de.fraunhofer.iais.eis.Representation;
import de.fraunhofer.iais.eis.RepresentationBuilder;
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ResourceBuilder;
import de.fraunhofer.iais.eis.Rule;
import de.fraunhofer.iais.eis.TemporalEntityBuilder;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import de.fraunhofer.iais.eis.util.Util;
import de.fraunhofer.isst.dataspaceconnector.exceptions.RdfBuilderException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MappingUtilsTest {
    
    private final ZonedDateTime date =
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(1616772571804L), ZoneOffset.UTC);

    @Test
    public void fromIdsResource_inputNull_throwNullPointerException() {
        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsResource(null));
    }

    @Test
    public void fromIdsResource_validInput_returnResourceTemplate() {
        /* ARRANGE */
        final var resource = getResource();
        resource.setProperty("test", "test");

        /* ACT */
        final var result = MappingUtils.fromIdsResource(resource);

        /* ASSERT */
        assertEquals(resource.getId(), result.getDesc().getRemoteId());
        assertEquals(resource.getKeyword().get(0).getValue(), result.getDesc().getKeywords().get(0));
        assertEquals(resource.getDescription().toString(), result.getDesc().getDescription());
        assertEquals(resource.getPublisher(), result.getDesc().getPublisher());
        assertEquals(resource.getStandardLicense(), result.getDesc().getLicence());
        assertEquals(resource.getLanguage().toString(), result.getDesc().getLanguage());
        assertEquals(resource.getTitle().toString(), result.getDesc().getTitle());
        assertEquals(resource.getSovereign(), result.getDesc().getSovereign());
        assertEquals(resource.getResourceEndpoint().get(0).getEndpointDocumentation().get(0), result.getDesc().getEndpointDocumentation());

        final var additional = result.getDesc().getAdditional();
        assertEquals(resource.getAccrualPeriodicity().toRdf(), additional.get("ids:accrualPeriodicity"));
        assertEquals(resource.getContentPart().toString(), additional.get("ids:contentPart"));
        assertEquals(resource.getContentStandard().toString(), additional.get("ids:contentStandard"));
        assertEquals(resource.getContentType().toRdf(), additional.get("ids:contentType"));
        assertEquals(resource.getCreated().toXMLFormat(), additional.get("ids:created"));
        assertEquals(resource.getCustomLicense().toString(), additional.get("ids:customLicense"));
        assertEquals(resource.getDefaultRepresentation().toString(), additional.get("ids:defaultRepresentation"));
        assertEquals(resource.getModified().toXMLFormat(), additional.get("ids:modified"));
        assertEquals(resource.getResourceEndpoint().toString(), additional.get("ids:resourceEndpoint"));
        assertEquals(resource.getResourcePart().toString(), additional.get("ids:resourcePart"));
        assertEquals(resource.getSample().toString(), additional.get("ids:sample"));
        assertEquals(resource.getShapesGraph().toString(), additional.get("ids:shapesGraph"));
        assertEquals(resource.getSpatialCoverage().toString(), additional.get("ids:spatialCoverage"));
        assertEquals(resource.getTemporalCoverage().toString(), additional.get("ids:temporalCoverage"));
        assertEquals(resource.getTemporalResolution().toString(), additional.get("ids:temporalResolution"));
        assertEquals(resource.getTheme().toString(), additional.get("ids:theme"));
        assertEquals(resource.getVariant().toString(), additional.get("ids:variant"));
        assertEquals(resource.getVersion(), additional.get("ids:version"));
        assertEquals("test", additional.get("test"));
    }

    @Test
    public void fromIdsResource_keywordsNull_throwNullPointerException() {
        /* ARRANGE */
        final var resource = getResourceWithKeywordsNull();

        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsResource(resource));
    }

    @Test
    public void fromIdsRepresentation_inputNull_throwNullPointerException() {
        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsRepresentation(null));
    }

    @Test
    public void fromIdsRepresentation_validInput_returnRepresentationTemplate() {
        /* ARRANGE */
        final var representation = getRepresentation();
        representation.setProperty("test", "test");

        /* ACT */
        final var result = MappingUtils.fromIdsRepresentation(representation);

        /* ASSERT */
        assertEquals(representation.getId(), result.getDesc().getRemoteId());
        assertEquals(representation.getMediaType().getFilenameExtension(), result.getDesc().getMediaType());
        assertEquals(representation.getLanguage().toString(), result.getDesc().getLanguage());
        assertEquals(representation.getRepresentationStandard().toString(), result.getDesc().getStandard());

        final var additional = result.getDesc().getAdditional();
        assertEquals(representation.getCreated().toXMLFormat(), additional.get("ids:created"));
        assertEquals(representation.getModified().toXMLFormat(), additional.get("ids:modified"));
        assertEquals(representation.getShapesGraph().toString(), additional.get("ids:shapesGraph"));
    }

    @Test
    public void fromIdsRepresentation_representationMediaTypeNull_throwNullPointerException() {
        /* ARRANGE */
        final var representation = getRepresentationWithMediaTypeNull();

        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsRepresentation(representation));
    }

    @Test
    public void fromIdsArtifact_artifactNull_throwNullPointerException() {
        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsArtifact(null, true));
    }

    @Test
    public void fromIdsArtifact_validInput_returnArtifactTemplate() {
        /* ARRANGE */
        final var artifact = getArtifact();
        artifact.setProperty("test", "test");
        final var download = true;

        /* ACT */
        final var result = MappingUtils.fromIdsArtifact(artifact, download);

        /* ASSERT */
        assertEquals(artifact.getId(), result.getDesc().getRemoteId());
        assertEquals(artifact.getFileName(), result.getDesc().getTitle());
        assertEquals(download, result.getDesc().isAutomatedDownload());

        final var additional = result.getDesc().getAdditional();
        assertEquals(artifact.getByteSize().toString(), additional.get("ids:byteSize"));
        assertEquals(artifact.getCheckSum(), additional.get("ids:checkSum"));
        assertEquals(artifact.getCreationDate().toXMLFormat(), additional.get("ids:creationDate"));
        assertEquals(artifact.getDuration().toString(), additional.get("ids:duration"));
        assertEquals("test", additional.get("test"));
    }

    @Test
    public void fromIdsArtifact_artifactCreatedNull_throwNullPointerException() {
        /* ARRANGE */
        final var artifact = getArtifactWithCreationDateNull();

        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsArtifact(artifact, true));
    }

    @Test
    public void fromIdsContract_inputNull_throwNullPointerException() {
        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsContract(null));
    }

    @SneakyThrows
    @Test
    public void fromIdsContract_validInput_returnContractTemplate() {
        /* ARRANGE */
        final var contract = getContract();
        contract.setProperty("test", "test");

        /* ACT */
        final var result = MappingUtils.fromIdsContract(contract);

        /* ASSERT */
        assertEquals(contract.getId(), result.getDesc().getRemoteId());
        assertEquals(contract.getProvider(), result.getDesc().getProvider());
        assertEquals(contract.getConsumer(), result.getDesc().getConsumer());
        assertTrue(date.isEqual(result.getDesc().getStart()));
        assertTrue(date.isEqual(result.getDesc().getEnd()));

        final var additional = result.getDesc().getAdditional();
        assertEquals("test", additional.get("test"));
    }

    @Test
    public void fromIdsContract_contractEndNull_throwNullPointerException() {
        /* ARRANGE */
        final var contract = getContractWithEndDateNull();

        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsContract(contract));
    }

    @Test
    public void fromIdsRule_inputNull_throwRdfBuilderException() {
        /* ACT && ASSERT */
        assertThrows(RdfBuilderException.class, () -> MappingUtils.fromIdsRule(null));
    }

    @Test
    public void fromIdsRule_validInput_returnRuleTemplate() {
        /* ARRANGE */
        final var rule = getRule();

        /* ACT */
        final var result = MappingUtils.fromIdsRule(rule);

        /* ASSERT */
        assertEquals(rule.getId(), result.getDesc().getRemoteId());
        assertEquals(rule.getTitle().toString(), result.getDesc().getTitle());
        assertEquals(rule.toRdf(), result.getDesc().getValue());
    }

    @Test
    public void fromIdsRule_ruleTitleNull_throwNullPointerException() {
        /* ARRANGE */
        final var rule = getRuleWithTitleNull();

        /* ACT && ASSERT */
        assertThrows(NullPointerException.class, () -> MappingUtils.fromIdsRule(rule));
    }

    /**************************************************************************
     * Utilities.
     *************************************************************************/

    @SneakyThrows
    private Resource getResource() {
        return new ResourceBuilder(URI.create("https://w3id.org/idsa/autogen/resource/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._contractOffer_(Util.asList(getContractOffer()))
                ._created_(getDateAsXMLGregorianCalendar())
                ._description_(Util.asList(new TypedLiteral("description", "EN")))
                ._language_(Util.asList(Language.EN))
                ._modified_(getDateAsXMLGregorianCalendar())
                ._publisher_(URI.create("http://publisher.com"))
                ._representation_(Util.asList(getRepresentation()))
                ._resourceEndpoint_(Util.asList(new ConnectorEndpointBuilder(URI.create("https://w3id.org/idsa/autogen/connectorEndpoint/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                        ._accessURL_(URI.create("http://connector-endpoint.com"))
                        ._endpointDocumentation_(Util.asList(URI.create("http://connector-endpoint-docs.com")))
                        .build()))
                ._sovereign_(URI.create("http://sovereign.com"))
                ._standardLicense_(URI.create("http://license.com"))
                ._title_(Util.asList(new TypedLiteral("title", "EN")))
                ._version_("1.0")
                ._accrualPeriodicity_(Frequency.DAILY)
                ._contentPart_(Util.asList(new ResourceBuilder().build()))
                ._contentStandard_(URI.create("http://standard.com"))
                ._contentType_(ContentType.SCHEMA_DEFINITION)
                ._customLicense_(URI.create("http://license.com"))
                ._defaultRepresentation_(Util.asList(getRepresentation()))
                ._resourcePart_(Util.asList(new ResourceBuilder().build()))
                ._sample_(Util.asList(new ResourceBuilder().build()))
                ._shapesGraph_(URI.create("http://shapes-graph.com"))
                ._sovereign_(URI.create("http://sovereign.com"))
                ._spatialCoverage_(Util.asList(new GeoPointBuilder()
                        ._latitude_(12.3f)
                        ._longitude_(45.6f)
                        .build()))
                ._standardLicense_(URI.create("http://license.com"))
                ._temporalCoverage_(Util.asList(new TemporalEntityBuilder()
                        ._hasDuration_(DatatypeFactory.newInstance().newDuration("P3M"))
                        .build()))
                ._temporalResolution_(Frequency.DAILY)
                ._theme_(Util.asList(URI.create("http://theme.com")))
                ._variant_(new ResourceBuilder().build())
                ._keyword_(Util.asList(new TypedLiteral("keyword", "EN")))
                .build();
    }

    @SneakyThrows
    private Resource getResourceWithKeywordsNull() {
        return new ResourceBuilder(URI.create("https://w3id.org/idsa/autogen/resource/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._contractOffer_(Util.asList(getContractOffer()))
                ._created_(getDateAsXMLGregorianCalendar())
                ._description_(Util.asList(new TypedLiteral("description", "EN")))
                ._language_(Util.asList(Language.EN))
                ._modified_(getDateAsXMLGregorianCalendar())
                ._publisher_(URI.create("http://publisher.com"))
                ._representation_(Util.asList(getRepresentation()))
                ._resourceEndpoint_(Util.asList(new ConnectorEndpointBuilder(URI.create("https://w3id.org/idsa/autogen/connectorEndpoint/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                        ._accessURL_(URI.create("http://connector-endpoint.com"))
                        ._endpointDocumentation_(Util.asList(URI.create("http://connector-endpoint-docs.com")))
                        .build()))
                ._sovereign_(URI.create("http://sovereign.com"))
                ._standardLicense_(URI.create("http://license.com"))
                ._title_(Util.asList(new TypedLiteral("title", "EN")))
                ._version_("1.0")
                ._accrualPeriodicity_(Frequency.DAILY)
                ._contentPart_(Util.asList(new ResourceBuilder().build()))
                ._contentStandard_(URI.create("http://standard.com"))
                ._contentType_(ContentType.SCHEMA_DEFINITION)
                ._customLicense_(URI.create("http://license.com"))
                ._defaultRepresentation_(Util.asList(getRepresentation()))
                ._resourcePart_(Util.asList(new ResourceBuilder().build()))
                ._sample_(Util.asList(new ResourceBuilder().build()))
                ._shapesGraph_(URI.create("http://shapes-graph.com"))
                ._sovereign_(URI.create("http://sovereign.com"))
                ._spatialCoverage_(Util.asList(new GeoPointBuilder()
                        ._latitude_(12.3f)
                        ._longitude_(45.6f)
                        .build()))
                ._standardLicense_(URI.create("http://license.com"))
                ._temporalCoverage_(Util.asList(new TemporalEntityBuilder()
                        ._hasDuration_(DatatypeFactory.newInstance().newDuration("P3M"))
                        .build()))
                ._temporalResolution_(Frequency.DAILY)
                ._theme_(Util.asList(URI.create("http://theme.com")))
                ._variant_(new ResourceBuilder().build())
                .build();
    }

    private ContractOffer getContractOffer() {
        return new ContractOfferBuilder(URI.create("https://w3id.org/idsa/autogen/contractOffer/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                .build();
    }

    private Representation getRepresentation() {
        return new RepresentationBuilder(URI.create("https://w3id.org/idsa/autogen/representation/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._created_(getDateAsXMLGregorianCalendar())
                ._instance_(Util.asList(getArtifact()))
                ._language_(Language.EN)
                ._mediaType_(new IANAMediaTypeBuilder(URI.create("https://w3id.org/idsa/autogen/mediaType/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                        ._filenameExtension_("json")
                        .build())
                ._modified_(getDateAsXMLGregorianCalendar())
                ._representationStandard_(URI.create("http://standard.com"))
                ._shapesGraph_(URI.create("http://shapes-graph.com"))
                .build();
    }

    private Representation getRepresentationWithMediaTypeNull() {
        return new RepresentationBuilder(URI.create("https://w3id.org/idsa/autogen/representation/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._created_(getDateAsXMLGregorianCalendar())
                ._instance_(Util.asList(getArtifact()))
                ._language_(Language.EN)
                ._modified_(getDateAsXMLGregorianCalendar())
                ._representationStandard_(URI.create("http://standard.com"))
                ._shapesGraph_(URI.create("http://shapes-graph.com"))
                .build();
    }

    private Artifact getArtifact() {
        return new ArtifactBuilder(URI.create("https://w3id.org/idsa/autogen/artifact/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._byteSize_(BigInteger.ONE)
                ._checkSum_("check sum")
                ._creationDate_(getDateAsXMLGregorianCalendar())
                ._duration_(new BigDecimal("123.4"))
                ._fileName_("file name")
                .build();
    }

    private Artifact getArtifactWithCreationDateNull() {
        return new ArtifactBuilder(URI.create("https://w3id.org/idsa/autogen/artifact/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._byteSize_(BigInteger.ONE)
                ._checkSum_("check sum")
                ._duration_(new BigDecimal("123.4"))
                ._fileName_("file name")
                .build();
    }

    private Contract getContract() {
        return new ContractAgreementBuilder(URI.create("https://w3id.org/idsa/autogen/contractAgreement/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._provider_(URI.create("http://provider.com"))
                ._consumer_(URI.create("http://consumer.com"))
                ._permission_(Util.asList((Permission) getRule()))
                ._contractDate_(getDateAsXMLGregorianCalendar())
                ._contractStart_(getDateAsXMLGregorianCalendar())
                ._contractEnd_(getDateAsXMLGregorianCalendar())
                .build();
    }

    private Contract getContractWithEndDateNull() {
        return new ContractAgreementBuilder(URI.create("https://w3id.org/idsa/autogen/contractAgreement/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._provider_(URI.create("http://provider.com"))
                ._consumer_(URI.create("http://consumer.com"))
                ._permission_(Util.asList((Permission) getRule()))
                ._contractDate_(getDateAsXMLGregorianCalendar())
                ._contractStart_(getDateAsXMLGregorianCalendar())
                .build();
    }

    private Rule getRule() {
        return new PermissionBuilder(URI.create("https://w3id.org/idsa/autogen/permission/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._title_(Util.asList(new TypedLiteral("Example Usage Policy")))
                ._description_(Util.asList(new TypedLiteral("usage-logging")))
                ._action_(Util.asList(Action.USE))
                ._postDuty_(Util.asList(new DutyBuilder(URI.create("https://w3id.org/idsa/autogen/duty/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                        ._action_(Util.asList(Action.LOG))
                        .build()))
                .build();
    }

    private Rule getRuleWithTitleNull() {
        return new PermissionBuilder(URI.create("https://w3id.org/idsa/autogen/permission/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                ._description_(Util.asList(new TypedLiteral("usage-logging")))
                ._action_(Util.asList(Action.USE))
                ._postDuty_(Util.asList(new DutyBuilder(URI.create("https://w3id.org/idsa/autogen/duty/591467af-9633-4a4e-8bcf-47ba4e6679ea"))
                        ._action_(Util.asList(Action.LOG))
                        .build()))
                .build();
    }

    @SneakyThrows
    private XMLGregorianCalendar getDateAsXMLGregorianCalendar() {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(Date.from(date.toInstant()));
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
    }

}
