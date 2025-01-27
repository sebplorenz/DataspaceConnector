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
package io.dataspaceconnector.service.ids.builder;

import java.math.BigInteger;

import de.fraunhofer.iais.eis.ArtifactBuilder;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import io.dataspaceconnector.model.Artifact;
import io.dataspaceconnector.util.IdsUtils;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Converts DSC Artifacts to Infomodel Artifacts.
 */
@Component
@NoArgsConstructor
public final class IdsArtifactBuilder
        extends AbstractIdsBuilder<Artifact, de.fraunhofer.iais.eis.Artifact> {

    @Override
    protected de.fraunhofer.iais.eis.Artifact createInternal(final Artifact artifact,
                                                             final int currentDepth,
                                                             final int maxDepth)
            throws ConstraintViolationException {
        // Prepare artifact attributes.
        final var created = IdsUtils.getGregorianOf(artifact
                                                            .getCreationDate());

        return new ArtifactBuilder(getAbsoluteSelfLink(artifact))
                ._byteSize_(BigInteger.valueOf(artifact.getByteSize()))
                ._checkSum_(BigInteger.valueOf(artifact.getCheckSum()).toString())
                ._creationDate_(created)
                ._fileName_(artifact.getTitle())
                .build();
    }
}
