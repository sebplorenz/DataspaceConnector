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
package io.dataspaceconnector.model;

import io.dataspaceconnector.util.ErrorMessages;
import io.dataspaceconnector.util.MetadataUtils;
import io.dataspaceconnector.util.Utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for creating and updating resources.
 * @param <T> The resource type.
 * @param <D> The description type.
 */
public abstract class ResourceFactory<T extends Resource, D extends ResourceDesc<T>>
        implements AbstractFactory<T, D> {

    /**
     * The default title assigned to all resources.
     */
    public static final String DEFAULT_TITLE = "";

    /**
     * The default description assigned to all resources.
     */
    public static final String DEFAULT_DESCRIPTION = "";

    /**
     * The default keywords assigned to all resources.
     */
    public static final List<String> DEFAULT_KEYWORDS = List.of("DSC");

    /**
     * The default publisher assigned to all resources.
     */
    public static final URI DEFAULT_PUBLISHER = URI.create("");

    /**
     * The default language assigned to all resources.
     */
    public static final String DEFAULT_LANGUAGE = "";

    /**
     * The default license assigned to all resources.
     */
    public static final URI DEFAULT_LICENSE = URI.create("");

    /**
     * The default sovereign assigned to all resources.
     */
    public static final URI DEFAULT_SOVEREIGN = URI.create("");

    /**
     * The default endpoint documentation assigned to all resources.
     */
    public static final URI DEFAULT_ENDPOINT_DOCS = URI.create("");

    /**
     * Create a new resource.
     * @param desc The description of the new resource.
     * @return The new resource.
     * @throws IllegalArgumentException if desc is null.
     */
    @Override
    public T create(final D desc) {
        Utils.requireNonNull(desc, ErrorMessages.DESC_NULL);

        final var resource = createInternal(desc);
        resource.setRepresentations(new ArrayList<>());
        resource.setContracts(new ArrayList<>());
        resource.setCatalogs(new ArrayList<>());

        update(resource, desc);

        return resource;
    }

    /**
     * Create a new resource. Implement type specific stuff here.
     * @param desc The description passed to the factory.
     * @return The new resource.
     */
    protected abstract T createInternal(D desc);

    /**
     * Update a resource.
     * @param resource The resource to be updated.
     * @param desc     The new resource description.
     * @return True if the resource has been modified.
     * @throws IllegalArgumentException if any of the parameters is null.
     */
    @Override
    public boolean update(final T resource, final D desc) {
        Utils.requireNonNull(resource, ErrorMessages.ENTITY_NULL);
        Utils.requireNonNull(desc, ErrorMessages.DESC_NULL);

        final var hasUpdatedTitle = updateTitle(resource, desc.getTitle());
        final var hasUpdatedDesc = updateDescription(resource, desc.getDescription());
        final var hasUpdatedKeywords = updateKeywords(resource, desc.getKeywords());
        final var hasUpdatedPublisher = updatePublisher(resource, desc.getPublisher());
        final var hasUpdatedLanguage = updateLanguage(resource, desc.getLanguage());
        final var hasUpdatedLicense = updateLicense(resource, desc.getLicense());
        final var hasUpdatedSovereign = updateSovereign(resource, desc.getSovereign());
        final var hasUpdatedEndpointDocs =
                updateEndpointDocs(resource, desc.getEndpointDocumentation());
        final var hasUpdatedAdditional = updateAdditional(resource, desc.getAdditional());
        final boolean hasUpdatedBootstrapId;
        if (desc.getBootstrapId() != null) {
            hasUpdatedBootstrapId =
                    this.updateBootstrapId(resource, URI.create(desc.getBootstrapId()));
        } else {
            hasUpdatedBootstrapId = false;
        }

        final var hasChildUpdated = updateInternal(resource, desc);

        final var hasUpdated = hasChildUpdated || hasUpdatedTitle || hasUpdatedDesc
                               || hasUpdatedKeywords || hasUpdatedPublisher || hasUpdatedLanguage
                               || hasUpdatedLicense || hasUpdatedSovereign || hasUpdatedEndpointDocs
                               || hasUpdatedAdditional || hasUpdatedBootstrapId;

        if (hasUpdated) {
            resource.setVersion(resource.getVersion() + 1);
        }

        return hasUpdated;
    }

    /**
     * Update a resource. Implement type specific stuff here.
     * @param resource The resource to be updated.
     * @param desc     The description passed to the factory.
     * @return true if the resource has been modified.
     */
    protected boolean updateInternal(final T resource, final D desc) {
        return false;
    }

    /**
     * Update a resource's title.
     * @param resource The resource.
     * @param title    The new title.
     * @return true if the resource's title has been modified.
     */
    protected final boolean updateTitle(final Resource resource, final String title) {
        final var newTitle = MetadataUtils.updateString(resource.getTitle(), title, DEFAULT_TITLE);
        newTitle.ifPresent(resource::setTitle);

        return newTitle.isPresent();
    }

    /**
     * Update a resource's description.
     * @param resource    The resource.
     * @param description The new description.
     * @return true if the resource's description has been modified.
     */
    protected final boolean updateDescription(final Resource resource, final String description) {
        final var newDesc = MetadataUtils.updateString(
                resource.getDescription(), description, DEFAULT_DESCRIPTION);
        newDesc.ifPresent(resource::setDescription);

        return newDesc.isPresent();
    }

    /**
     * Update a resource's keywords.
     * @param resource The resource.
     * @param keywords The new keywords.
     * @return true if the resource's keywords have been modified.
     */
    protected final boolean updateKeywords(final Resource resource, final List<String> keywords) {
        final var newKeys =
                MetadataUtils.updateStringList(resource.getKeywords(), keywords, DEFAULT_KEYWORDS);
        newKeys.ifPresent(resource::setKeywords);

        return newKeys.isPresent();
    }

    /**
     * Update a resource's publisher.
     * @param resource  The resource.
     * @param publisher The new publisher.
     * @return true if the resource's publisher has been modified.
     */
    protected final boolean updatePublisher(final Resource resource, final URI publisher) {
        final var newPublisher =
                MetadataUtils.updateUri(resource.getPublisher(), publisher, DEFAULT_PUBLISHER);
        newPublisher.ifPresent(resource::setPublisher);

        return newPublisher.isPresent();
    }

    /**
     * Update a resource's language.
     * @param resource The resource.
     * @param language The new language.
     * @return true if the resource's language has been modified.
     */
    protected final boolean updateLanguage(final Resource resource, final String language) {
        final var newLanguage =
                MetadataUtils.updateString(resource.getLanguage(), language, DEFAULT_LANGUAGE);
        newLanguage.ifPresent(resource::setLanguage);

        return newLanguage.isPresent();
    }

    /**
     * Update a resource's license.
     * @param resource The resource.
     * @param license  The new license.
     * @return true if the resource's license has been modified.
     */
    protected final boolean updateLicense(final Resource resource, final URI license) {
        final var newLicense = MetadataUtils.updateUri(resource.getLicense(),
                license, DEFAULT_LICENSE);
        newLicense.ifPresent(resource::setLicense);

        return newLicense.isPresent();
    }

    /**
     * Update a resource's sovereign.
     * @param resource  The resource.
     * @param sovereign The new sovereign.
     * @return true if the resource's sovereign has been modified.
     */
    protected final boolean updateSovereign(final Resource resource, final URI sovereign) {
        final var newPublisher =
                MetadataUtils.updateUri(resource.getSovereign(), sovereign, DEFAULT_SOVEREIGN);
        newPublisher.ifPresent(resource::setSovereign);

        return newPublisher.isPresent();
    }

    /**
     * Update a resource's endpoint documentation.
     * @param resource     The resource.
     * @param endpointDocs The new endpoint documentation.
     * @return true if the resource's endpoint documentation has been modified.
     */
    protected final boolean updateEndpointDocs(final Resource resource, final URI endpointDocs) {
        final var newPublisher = MetadataUtils.updateUri(
                resource.getEndpointDocumentation(), endpointDocs, DEFAULT_ENDPOINT_DOCS);
        newPublisher.ifPresent(resource::setEndpointDocumentation);

        return newPublisher.isPresent();
    }

    private boolean updateBootstrapId(final Resource resource, final URI bootstrapId) {
        final Optional<URI> newBootstrapId;
        if (bootstrapId == null && resource.getBootstrapId() == null) {
            newBootstrapId = Optional.empty();
        } else {
            newBootstrapId = MetadataUtils
                    .updateUri(
                            resource.getBootstrapId(),
                            bootstrapId,
                            resource.getBootstrapId());
        }

        newBootstrapId.ifPresent(resource::setBootstrapId);

        return newBootstrapId.isPresent();
    }

    private boolean updateAdditional(
            final Resource resource, final Map<String, String> additional) {
        final var newAdditional = MetadataUtils.updateStringMap(
                resource.getAdditional(), additional, new HashMap<>());
        newAdditional.ifPresent(resource::setAdditional);

        return newAdditional.isPresent();
    }
}
