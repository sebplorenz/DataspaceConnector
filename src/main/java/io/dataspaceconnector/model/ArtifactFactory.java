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
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32C;

/**
 * Creates and updates an artifact.
 */
@Component
public final class ArtifactFactory implements AbstractFactory<Artifact, ArtifactDesc> {

    /**
     * Default remote id assigned to all artifacts.
     */
    public static final URI DEFAULT_REMOTE_ID = URI.create("genesis");

    /**
     * Default remote address assigned to all artifacts.
     */
    public static final URI DEFAULT_REMOTE_ADDRESS = URI.create("genesis");

    /**
     * Default title assigned to all artifacts.
     */
    public static final String DEFAULT_TITLE = "";

    /**
     * Default download setting assigned to all artifacts.
     */
    public static final boolean DEFAULT_AUTO_DOWNLOAD = false;

    /**
     * Create a new artifact.
     * @param desc The description of the new artifact.
     * @return The new artifact.
     * @throws IllegalArgumentException if desc is null.
     */
    @Override
    public Artifact create(final ArtifactDesc desc) {
        Utils.requireNonNull(desc, ErrorMessages.DESC_NULL);

        final var artifact = new ArtifactImpl();
        artifact.setAgreements(new ArrayList<>());
        artifact.setRepresentations(new ArrayList<>());
        if (desc.getBootstrapId() != null) {
            artifact.setBootstrapId(URI.create(desc.getBootstrapId()));
        }

        update(artifact, desc);

        return artifact;
    }

    /**
     * Update an artifact.
     * @param artifact The artifact to be updated.
     * @param desc     The new artifact description.
     * @return True if the artifact has been modified.
     * @throws IllegalArgumentException if any of the parameters is null.
     */
    @Override
    public boolean update(final Artifact artifact, final ArtifactDesc desc) {
        Utils.requireNonNull(artifact, ErrorMessages.ENTITY_NULL);
        Utils.requireNonNull(desc, ErrorMessages.DESC_NULL);

        final var hasUpdatedRemoteId = updateRemoteId(artifact, desc.getRemoteId());
        final var hasUpdatedRemoteAddress = updateRemoteAddress(artifact, desc.getRemoteAddress());
        final var hasUpdatedTitle = updateTitle(artifact, desc.getTitle());
        final var hasUpdatedAutoDownload = updateAutoDownload(artifact, desc.isAutomatedDownload());
        final var hasUpdatedData = updateData(artifact, desc);
        final var hasUpdatedAdditional = this.updateAdditional(artifact, desc.getAdditional());
        final boolean hasUpdatedBootstrapId;
        if (desc.getBootstrapId() != null) {
            hasUpdatedBootstrapId =
                    this.updateBootstrapId(artifact, URI.create(desc.getBootstrapId()));
        } else {
            hasUpdatedBootstrapId = false;
        }

        return hasUpdatedRemoteId || hasUpdatedRemoteAddress || hasUpdatedTitle
               || hasUpdatedAutoDownload || hasUpdatedData || hasUpdatedAdditional
               || hasUpdatedBootstrapId;
    }

    private boolean updateRemoteId(final Artifact artifact, final URI remoteId) {
        final var newUri =
                MetadataUtils.updateUri(artifact.getRemoteId(), remoteId, DEFAULT_REMOTE_ID);
        newUri.ifPresent(artifact::setRemoteId);

        return newUri.isPresent();
    }

    private boolean updateRemoteAddress(final Artifact artifact, final URI remoteAddress) {
        final var newUri = MetadataUtils
                .updateUri(artifact.getRemoteAddress(), remoteAddress, DEFAULT_REMOTE_ADDRESS);
        newUri.ifPresent(artifact::setRemoteAddress);

        return newUri.isPresent();
    }

    private boolean updateTitle(final Artifact artifact, final String title) {
        final var newTitle = MetadataUtils.updateString(artifact.getTitle(), title, DEFAULT_TITLE);
        newTitle.ifPresent(artifact::setTitle);

        return newTitle.isPresent();
    }

    private boolean updateAutoDownload(final Artifact artifact, final boolean autoDownload) {
        if (artifact.isAutomatedDownload() != autoDownload) {
            artifact.setAutomatedDownload(autoDownload);
            return true;
        }

        return false;
    }

    private boolean updateAdditional(final Artifact artifact,
                                     final Map<String, String> additional) {
        final var newAdditional =
                MetadataUtils
                        .updateStringMap(artifact.getAdditional(), additional, new HashMap<>());
        newAdditional.ifPresent(artifact::setAdditional);

        return newAdditional.isPresent();
    }

    private boolean updateData(final Artifact artifact, final ArtifactDesc desc) {
        boolean hasChanged;
        if (isRemoteData(desc)) {
            hasChanged = updateRemoteData((ArtifactImpl) artifact, desc.getAccessUrl(),
                                          desc.getUsername(), desc.getPassword());
        } else {
            hasChanged = updateLocalData((ArtifactImpl) artifact, desc.getValue());
        }

        return hasChanged;
    }

    private static boolean isRemoteData(final ArtifactDesc desc) {
        return desc.getAccessUrl() != null && desc.getAccessUrl().getPath().length() > 0;
    }

    private boolean updateLocalData(final ArtifactImpl artifact, final String value) {
        final var newData = new LocalData();
        final var data = value == null ? null : value.getBytes(StandardCharsets.UTF_16);
        newData.setValue(data);

        final var oldData = artifact.getData();
        if (oldData instanceof LocalData) {
            if (!oldData.equals(newData)) {
                artifact.setData(newData);
                updateByteSize(artifact, data);
                return true;
            }
        } else {
            artifact.setData(newData);
            updateByteSize(artifact, data);
            return true;
        }

        return false;
    }

    private boolean updateRemoteData(final ArtifactImpl artifact, final URL accessUrl,
                                     final String username, final String password) {
        final var newData = new RemoteData();
        newData.setAccessUrl(accessUrl);
        newData.setUsername(username);
        newData.setPassword(password);

        final var oldData = artifact.getData();
        if (oldData instanceof RemoteData) {
            if (!oldData.equals(newData)) {
                artifact.setData(newData);
                return true;
            }
        } else {
            artifact.setData(newData);
            return true;
        }

        return false;
    }

    /**
     * Update the byte and checksum of an artifact. This will not update
     * the actual data.
     * @param artifact The artifact which byte and checksum needs to be
     *                recalculated.
     * @param bytes The data.
     * @return true if the artifact has been modified.
     */
    public boolean updateByteSize(final Artifact artifact, final byte[] bytes) {
        var hasChanged = false;
        final var checkSum = calculateChecksum(bytes);

        if (bytes != null && artifact.getByteSize() != bytes.length) {
            artifact.setByteSize(bytes.length);
            hasChanged = true;
        }

        if (artifact.getCheckSum() != checkSum) {
            artifact.setCheckSum(checkSum);
            hasChanged = true;
        }

        return hasChanged;
    }

    private boolean updateBootstrapId(final Artifact artifact, final URI bootstrapId) {
        final Optional<URI> newBootstrapId;
        if (bootstrapId == null && artifact.getBootstrapId() == null) {
            newBootstrapId = Optional.empty();
        } else {
            newBootstrapId = MetadataUtils
                    .updateUri(
                            artifact.getBootstrapId(),
                            bootstrapId,
                            artifact.getBootstrapId());
        }

        newBootstrapId.ifPresent(artifact::setBootstrapId);

        return newBootstrapId.isPresent();
    }

    private long calculateChecksum(final byte[] bytes) {
        if (bytes == null) {
            return 0;
        }

        final var checksum = new CRC32C();
        checksum.update(bytes, 0, bytes.length);
        return checksum.getValue();
    }
}
