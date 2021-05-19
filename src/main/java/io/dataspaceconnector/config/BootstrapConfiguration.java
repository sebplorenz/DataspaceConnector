package io.dataspaceconnector.config;

import de.fraunhofer.iais.eis.Message;
import de.fraunhofer.iais.eis.MessageProcessedNotificationMessage;
import de.fraunhofer.iais.eis.RejectionMessage;
import de.fraunhofer.iais.eis.Representation;
import de.fraunhofer.iais.eis.RepresentationInstance;
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.ResourceCatalog;
import de.fraunhofer.isst.ids.framework.communication.broker.IDSBrokerService;
import de.fraunhofer.isst.ids.framework.configuration.ConfigurationUpdateException;
import io.dataspaceconnector.model.AbstractDescription;
import io.dataspaceconnector.model.Catalog;
import io.dataspaceconnector.model.OfferedResource;
import io.dataspaceconnector.model.OfferedResourceDesc;
import io.dataspaceconnector.model.RequestedResourceDesc;
import io.dataspaceconnector.model.templates.ResourceTemplate;
import io.dataspaceconnector.services.ids.ConnectorService;
import io.dataspaceconnector.services.ids.DeserializationService;
import io.dataspaceconnector.services.resources.CatalogService;
import io.dataspaceconnector.services.resources.TemplateBuilder;
import io.dataspaceconnector.utils.MessageUtils;
import io.dataspaceconnector.utils.TemplateUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.Response;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class allows to load JSON-LD files which contain infomodel representations
 * of entities which will be registered in the connector during start-up.
 * In addition an additional configuration file can be loaded which provides
 * information on e.g. broker usage and the used clearing house.
 */
@Component
@Log4j2
@RequiredArgsConstructor
@Transactional
public class BootstrapConfiguration {

    /**
     * File extension used for JSON-LD files.
     */
    private static final String JSON_LD_EXTENSION = "jsonld";

    /**
     * File name for bootstrap property files.
     */
    private static final String PROPERTIES_NAME = "bootstrap";

    /**
     * File extension used for bootstrap property files.
     */
    private static final String PROPERTIES_EXT = "properties";

    /**
     * Some entires in bootstrap property files allow multiple values.
     * This is the delimiter which is used to separate the values.
     */
    private static final String MULTI_VALUE_DELIM = ",";

    /**
     * Set of entries in bootstrap property files which are allowed to have
     * multiple values.
     */
    private static final Set<String> MULTI_VALUE_PROPS = SetUtils.hashSet(
            "resource.download.auto"
    );

    /**
     * Root where search for bootstrapping file starts.
     */
    @Value("${bootstrap.path}")
    private String bootstrapPath;

    /**
     * Spring application context. Needed for shutdowns in case of errors.
     */
    private final @NotNull ApplicationContext context;

    /**
     * Service for deserializing ids entities.
     */
    private final @NotNull DeserializationService deserializationService;

    /**
     * The template builder.
     */
    private final @NotNull TemplateBuilder<OfferedResource, OfferedResourceDesc>
            templateBuilder;

    /**
     * The catalog service.
     */
    private final @NotNull CatalogService catalogService;

    /**
     * The platform transaction manager.
     */
    private final @NotNull PlatformTransactionManager transactionManager;

    /**
     * The service for communication with the ids broker.
     */
    private final @NotNull IDSBrokerService brokerService;

    /**
     * Service for the current connector configuration.
     */
    private final @NonNull ConnectorService connectorService;

    /**
     * Bootstrap the connector.
     * Will load JSON-LD files which contain ids catalog entities and register
     * them to the DSC. Additionally, property files will be loaded which can
     * provide information on the clearing house & broker which shall be used
     * and which resources needs to be registered at the broker.
     */
    @PostConstruct
    @Transactional
    public void bootstrap() {
        if (log.isInfoEnabled()) {
            log.info("Start bootstrapping of Connector.");
        }

        // try to retrieve data and properties
        final List<File> jsonFiles =
                findFilesByExtension(bootstrapPath, null, JSON_LD_EXTENSION);
        if (jsonFiles.isEmpty() && log.isInfoEnabled()) {
            log.info("No catalog files for bootstrapping found.");
        }

        final Properties properties = retrieveBootstrapConfig();
        if (properties.isEmpty() && log.isInfoEnabled()) {
            log.info("No config files for bootstrapping found.");
        }

        final Map<URI, Resource> idsResources = new ConcurrentHashMap<>();

        // register content of all found catalog files
        if (!processIdsFiles(jsonFiles, properties, idsResources)) {
            if (log.isErrorEnabled()) {
                log.error("An error occurred while bootstrapping IDS catalogs.");
            }
            SpringApplication.exit(context, () -> -1);
        }

        // register resources at broker
        if (!registerAtBroker(properties, idsResources)) {
            if (log.isErrorEnabled()) {
                log.error("An error occurred while registering resources at the broker.");
            }
            SpringApplication.exit(context, () -> -1);
        }

        if (log.isInfoEnabled()) {
            log.info("Finished bootstrapping of connector.");
        }
    }

    private boolean registerAtBroker(final Properties properties,
                                     final Map<URI, Resource> idsResources) {
        final var knownBrokers = new HashSet<String>();
        try {
            connectorService.updateConfigModel();
        } catch (ConfigurationUpdateException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to update config model.", e);
            }
            return false;
        }
        // iterate over all registered resources
        for (Map.Entry<URI, Resource> entry : idsResources.entrySet()) {
            if (properties.containsKey("broker.register." + entry.getKey().toString())) {
                final var brokerURL = (String) properties.get("broker.register." + entry.getKey());

                try {
                    Response response;
                    if (!knownBrokers.contains(brokerURL)) {
                        knownBrokers.add(brokerURL);
                        response = brokerService.updateSelfDescriptionAtBroker(brokerURL);
                        if (validateBrokerResponse(response, brokerURL)) {
                            if (log.isInfoEnabled()) {
                                log.info("Registered connector at broker '" + brokerURL + "'.");
                            }
                        } else {
                            return false;
                        }
                    }

                    response = brokerService.updateResourceAtBroker(brokerURL, entry.getValue());
                    if (!response.isSuccessful()) {
                        if (log.isErrorEnabled()) {
                            log.error("Failed to update resource description for resource '"
                                    + entry.getValue().getId().toString() + "' at broker '"
                                    + brokerURL + "'.");
                        }

                        return false;
                    }
                    if (validateBrokerResponse(response, brokerURL)) {
                        if (log.isInfoEnabled()) {
                            log.info("Registered resource with IDS ID '"
                                    + entry.getKey().toString()
                                    + "' at broker '" + brokerURL + "'.");
                        }
                    } else {
                        return false;
                    }
                } catch (IOException e) {
                    if (log.isErrorEnabled()) {
                        log.error("Could not register resource with IDS id '" + entry.getKey()
                                + "' at the broker '" + brokerURL + "'.", e);
                    }

                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Check if a broker request was successfully processed by a broker.
     *
     * @param response  the broker response
     * @param brokerURL the URL of the called broker
     * @return true if the broker successfully processed the message, false otherwise
     * @throws IOException if the response's body cannot be extracted as string.
     */
    private boolean validateBrokerResponse(final Response response, final String brokerURL)
            throws IOException {
        if (!response.isSuccessful()) {
            if (log.isErrorEnabled()) {
                log.error("Failed to sent message to a broker '"
                        + brokerURL + "'.");
            }

            return false;
        }
        final var body = Objects.requireNonNull(response.body()).string();
        final var responseMessage =
                getMessage(body);
        if (responseMessage == null) {
            if (log.isErrorEnabled()) {
                log.error("Could not parse response after sending a request "
                        + "to a broker.");
            }
            return false;
        }
        if (!(responseMessage instanceof MessageProcessedNotificationMessage)) {
            if (log.isErrorEnabled()) {
                if (responseMessage instanceof RejectionMessage) {
                    final var payload = getMultipartPart(body, "payload");
                    log.error("The broker rejected the message. Reason: "
                            + MessageUtils.extractRejectionReason(
                            (RejectionMessage) responseMessage)
                            + ((payload != null) ? " - " + payload : ""));
                } else {
                    log.error("An error occurred while registering the "
                            + "connector at the broker.");
                }
            }
            return false;
        }

        return true;
    }

    /**
     * Extract the IDS message from a multipart message string.
     *
     * @param body a multipart message
     * @return The IDS message contained in the multipart message, null if any error occurs.
     */
    private Message getMessage(final String body) {
        final var part = getMultipartPart(body, "header");
        if (part != null) {
            return deserializationService.getMessage(part);
        }

        if (log.isErrorEnabled()) {
            log.error("Could not find IDS message in multipart message.");
        }
        return null;
    }

    /**
     * Extract a part with given name from a multipart message.
     *
     * @param message the multipart message
     * @param partName the part name
     * @return part with given name, null if the part does not exist in given message
     */
    private String getMultipartPart(final String message, final String partName) {
        final String boundary;
        if (message.contains("\r\n")) {
            boundary = message.split("\r\n")[0];
        } else {
            boundary = message.split("\n")[0];
        }
        final var multipart = new MultipartStream(
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)),
                boundary.substring(2).getBytes(StandardCharsets.UTF_8),
                4096,
                null
        );

        try {
            boolean next = multipart.skipPreamble();
            final var outputStream = new ByteArrayOutputStream();
            final var pattern = Pattern.compile("name=\"([a-zA-Z]+)\"");
            while (next) {
                final var header = multipart.readHeaders();
                final var matcher = pattern.matcher(header);
                if (!matcher.find()) {
                    if (log.isErrorEnabled()) {
                        log.error("Could not find name of multipart part.");
                    }
                    return null;
                }
                if (matcher.group().equals("name=\"" + partName + "\"")) {
                    multipart.readBodyData(outputStream);

                    return outputStream.toString();
                } else {
                    multipart.discardBodyData();
                }
                next = multipart.readBoundary();
            }

        } catch (IOException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to parse multipart message.", e);
            }
            return null;
        }

        if (log.isErrorEnabled()) {
            log.error("Could not find part '" + partName + "' in multipart message.");
        }
        return null;
    }

    /**
     * Load a list of JSON-LD files which contain ids catalog entities and
     * register them at the connector.
     *
     * @param jsonFiles    List of JSON-LD files to load
     * @param properties   additional properties which are required but not
     *                     present in ids representations
     * @param idsResources (IDS ID, IDS-Resource) map that cointains bootstrapped elements
     * @return true if all catalogs were loaded successfully or were already
     * registered, false otherwise
     */
    private boolean processIdsFiles(final List<File> jsonFiles,
                                    final Properties properties,
                                    final Map<URI, Resource> idsResources) {
        final Set<ResourceCatalog> catalogs = new HashSet<>();

        // deserialize all files
        for (final var jsonFile : jsonFiles) {
            try {
                catalogs.add(deserializationService.getResourceCatalog(
                        Files.readString(jsonFile.toPath()))
                );
            } catch (IOException e) {
                if (log.isErrorEnabled()) {
                    log.error("Could not deserialize ids catalog file '"
                            + jsonFile.getPath() + "'.", e);
                }
                return false;
            }
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        // iterate over all deserialized catalogs
        for (final ResourceCatalog catalog : catalogs) {
            // check for duplicates
            // get all known catalogs for every bootstrap
            // processor to detect duplicated bootstrap files
            final Boolean duplicate = template.execute(transactionStatus -> {
                Page<Catalog> knownCatalogs = catalogService.getAll(Pageable.unpaged());
                boolean catalogDuplicate = false;
                for (Catalog knownCatalog : knownCatalogs) {
                    Hibernate.initialize(knownCatalog.getAdditional());
                    if (knownCatalog.getAdditional().containsKey("idsId")
                            && knownCatalog.getAdditional().get("idsId")
                            .equals(catalog.getId().toString())) {
                        catalogDuplicate = true;
                        break;
                    }
                }
                return catalogDuplicate;
            });

            if (duplicate != null && duplicate) {
                if (log.isInfoEnabled()) {
                    log.info("Catlog with IDS id '" + catalog.getId().toString() + "' is "
                            + "already registered and will be skipped.");
                }
                continue;
            }

            if (!registerCatalog(catalog, properties, idsResources)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Transform an ids resource catalog to dsc format and register the catalog
     * in the connector.
     *
     * @param catalog      the ids resource catalog entity
     * @param properties   additional properties which are missing in ids entity
     * @param idsResources (IDS ID, IDS-Resource) map that cointains bootstrapped elements
     * @return true if the catalog could be registered, false otherwise
     */
    protected boolean registerCatalog(final ResourceCatalog catalog,
                                      final Properties properties,
                                      final Map<URI, Resource> idsResources) {
        // create templates
        final var catalogTemplate = TemplateUtils.getCatalogTemplate(catalog);

        final var offeredResources = new ArrayList<ResourceTemplate<OfferedResourceDesc>>();
        for (final Resource resource : catalog.getOfferedResource()) {
            final var resourceTemplate =
                    TemplateUtils.getOfferedResourceTemplate(resource);
            fillResourceTemplate(resourceTemplate, properties, resource);

            offeredResources.add(resourceTemplate);

            idsResources.put(resource.getId(), resource);
        }
        catalogTemplate.setOfferedResources(offeredResources);

        // requested resources are skipped
        final var requestedResources = new ArrayList<ResourceTemplate<RequestedResourceDesc>>();
        catalogTemplate.setRequestedResources(requestedResources);

        // perform registration
        templateBuilder.build(catalogTemplate);
        if (log.isInfoEnabled()) {
            log.info("Bootstrapped catalog with IDS ID '" + catalog.getId().toString() + "'.");
        }

        return true;
    }

    /**
     * Extract representations, artifacts, and contract offers for
     * a resource template from an ids resource.
     *
     * @param resourceTemplate the resource template
     * @param properties       additional properties, required for
     *                         transformation
     * @param resource         the ids resource
     * @param <T>              either {@link OfferedResourceDesc}
     *                         or {@link RequestedResourceDesc}
     */
    @SuppressWarnings("unchecked")
    private <T extends AbstractDescription<?>> void fillResourceTemplate(
            final ResourceTemplate<T> resourceTemplate,
            final Properties properties,
            final Resource resource) {
        // add ids id to additional fields
        resourceTemplate.getDesc().getAdditional().put("idsId", resource.getId().toString());

        // ensure necessary properties are available
        if (!properties.containsKey("resource.remoteUrl."
                + resource.getId().toString())) {
            if (log.isErrorEnabled()) {
                log.error("Remote URL for resource with id '"
                        + resource.getId() + "' is not "
                        + "provided in " + PROPERTIES_NAME
                        + "." + PROPERTIES_EXT + " file(s). The key "
                        + "'resource.remoteUrl."
                        + resource.getId().toString()
                        + "' must be used.");
            }
            throw new IllegalStateException();
        }

        // collect all artifact IDs from artifacts inside representations
        final var artifacts = new ArrayList<URI>();
        for (final Representation representation : resource.getRepresentation()) {
            for (final RepresentationInstance instance : representation.getInstance()) {
                artifacts.add(instance.getId());
            }
        }

        resourceTemplate.setRepresentations(
                TemplateUtils.getRepresentationTemplates(
                        resource,
                        artifacts,
                        properties.containsKey("resource.download.auto")
                                && ((Set<String>) properties.get("resource.download.auto"))
                                .contains(resource.getId().toString()),
                        URI.create(
                                properties.getProperty(
                                        "resource.remoteUrl." + resource.getId()))
                )
        );

        resourceTemplate.setContracts(TemplateUtils.getContractTemplates(resource));
    }

    /**
     * Collect all bootstrap configuration files and merge them into a single
     * {@link Properties} object. In case of conflicts for values which don't
     * support multiple values, the first one found will be used.
     *
     * @return properties which contain the merged content of all bootstrap
     * config files
     */
    @SuppressWarnings("unchecked")
    private @NotNull Properties retrieveBootstrapConfig() {
        final Properties config = new Properties();
        final List<File> propertyFiles = findFilesByExtension(bootstrapPath, PROPERTIES_NAME,
                PROPERTIES_EXT);

        // iterate all bootstrap.properties files
        final Properties properties = new Properties();
        for (final File propertyFile : propertyFiles) {
            properties.clear();
            try {
                properties.load(FileUtils.openInputStream(propertyFile));
            } catch (IOException e) {
                if (log.isErrorEnabled()) {
                    log.error("Could not open properties file '" + propertyFile.getPath() + "'.",
                            e);
                }
            }
            // iterate all properties from file and check for duplicates
            for (final Map.Entry<Object, Object> property : properties.entrySet()) {
                if (config.containsKey(property.getKey())) {
                    if (MULTI_VALUE_PROPS.contains((String) property.getKey())) {
                        final Set<String> multipleValues = Arrays.stream(
                                ((String) property.getValue()).split(MULTI_VALUE_DELIM))
                                .map(String::trim).collect(Collectors.toSet());
                        final Set<String> existingValues =
                                (Set<String>) config.get(property.getKey());
                        config.put(property.getKey(), existingValues.addAll(multipleValues));
                    } else {
                        if (log.isWarnEnabled()) {
                            log.warn("Collision for single-value property '" + property.getKey()
                                    + "' found. Going to keep the old value '"
                                    + config.get(property.getKey()) + "'; new value '"
                                    + property.getValue() + "' will be ignored.");
                        }
                    }
                } else {
                    if (MULTI_VALUE_PROPS.contains((String) property.getKey())) {
                        final Set<String> multipleValues = Arrays.stream(
                                ((String) property.getValue()).split(MULTI_VALUE_DELIM))
                                .map(String::trim).collect(Collectors.toSet());
                        config.put(property.getKey(), multipleValues);
                    } else {
                        config.put(property.getKey(), property.getValue());
                    }
                }
            }
        }

        return config;
    }

    /**
     * Find all files with given extension in a given path. Optionally a
     * filename can be provided, too. If filename is set to null, all files
     * with matching extension will be returned. The search includes
     * subdirectories.
     *
     * @param path      the starting path for searching
     * @param filename  optional filename which is searched, null for all
     *                  files
     * @param extension the searched file extension
     * @return a list of all files which are stored at given path (and
     * subdirectories) with required extension and optional required filename
     * @throws IllegalStateException if the given path does not exist
     */
    private @NotNull List<File> findFilesByExtension(final String path, final String filename,
                                                     final String extension) {
        // validate input
        final File base = new File(path);
        if (!base.exists()) {
            throw new IllegalStateException("File '" + path + "' does not exist.");
        }

        final List<File> files = new ArrayList<>();
        if (base.isDirectory()) {
            // if the base file is a directory iterate all child files
            for (final File child : Objects.requireNonNull(base.listFiles())) {
                if (child.isDirectory()) {
                    files.addAll(findFilesByExtension(child.getPath(), filename, extension));
                } else {
                    if (FilenameUtils.getExtension(child.getName()).equals(extension)
                            && (filename == null
                            || FilenameUtils.removeExtension(
                            FilenameUtils.getName(child.getName())).equals(filename))) {
                        files.add(child);
                    }
                }
            }
        } else {
            // check if the base file itself is a json-ld file
            if (FilenameUtils.getExtension(base.getName()).equals(extension)
                    && (filename == null
                    || FilenameUtils.getName(base.getName()).equals(filename))) {
                files.add(base);
            }
        }

        return files;
    }

}