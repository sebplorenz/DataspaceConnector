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
package io.dataspaceconnector.service.ids.updater;

import de.fraunhofer.iais.eis.Representation;
import io.dataspaceconnector.exception.ResourceNotFoundException;
import io.dataspaceconnector.service.resource.RepresentationService;
import io.dataspaceconnector.util.MappingUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Updates a dsc representation based on a provided ids representation.
 */
@Component
@RequiredArgsConstructor
public final class RepresentationUpdater implements InfomodelUpdater<Representation,
        io.dataspaceconnector.model.Representation> {

    /**
     * Service for representations.
     */
    private final @NonNull RepresentationService representationService;

    /**
     * {@inheritDoc}
     */
    @Override
    public io.dataspaceconnector.model.Representation update(
            final Representation entity) throws ResourceNotFoundException {
        final var entityId = representationService.identifyByRemoteId(entity.getId());
        if (entityId.isEmpty()) {
            throw new ResourceNotFoundException(entity.getId().toString());
        }

        final var template = MappingUtils.fromIdsRepresentation(entity);
        return representationService.update(entityId.get(), template.getDesc());
    }
}
