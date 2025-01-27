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
package io.dataspaceconnector.controller.resource.view;

import java.util.UUID;

import io.dataspaceconnector.controller.resource.RelationControllers;
import io.dataspaceconnector.controller.resource.ResourceControllers.CatalogController;
import io.dataspaceconnector.model.Catalog;
import lombok.NoArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Assembles the REST resource for a catalog.
 */
@Component
@NoArgsConstructor
public class CatalogViewAssembler
        implements RepresentationModelAssembler<Catalog, CatalogView>, SelfLinking {
    /**
     * Construct the CatalogView from a Catalog.
     * @param catalog The catalog.
     * @return The new view.
     */
    @Override
    public CatalogView toModel(final Catalog catalog) {
        final var modelMapper = new ModelMapper();
        final var view = modelMapper.map(catalog, CatalogView.class);
        view.add(getSelfLink(catalog.getId()));

        final var offeredResLink = WebMvcLinkBuilder
                .linkTo(methodOn(RelationControllers.CatalogsToOfferedResources.class)
                .getResource(catalog.getId(), null, null))
                .withRel("offers");
        view.add(offeredResLink);

        return view;
    }

    @Override
    public final Link getSelfLink(final UUID entityId) {
        return ViewAssemblerHelper.getSelfLink(entityId, CatalogController.class);
    }
}
