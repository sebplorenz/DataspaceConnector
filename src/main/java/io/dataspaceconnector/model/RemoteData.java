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

import java.net.URL;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;

import io.dataspaceconnector.model.util.UrlConverter;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import static io.dataspaceconnector.model.config.DatabaseConstants.URI_COLUMN_LENGTH;

/**
 * Bundles information needed for accessing remote backends.
 */
@Entity
@Getter
@Setter(AccessLevel.PACKAGE)
@SQLDelete(sql = "UPDATE data SET deleted=true WHERE id=?")
@Where(clause = "deleted = false")
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
public class RemoteData extends Data {

    /**
     * Access url of the backend.
     */
    @Convert(converter = UrlConverter.class)
    @Column(length = URI_COLUMN_LENGTH)
    private URL accessUrl;

    /**
     * The username for accessing the backend.
     */
    private String username;

    /**
     * The password for accessing the backend.
     */
    private String password;
}
