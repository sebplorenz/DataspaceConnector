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

import java.net.URI;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import io.dataspaceconnector.model.util.UriConverter;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import static io.dataspaceconnector.model.config.DatabaseConstants.URI_COLUMN_LENGTH;

/**
 * A ContractRule defines a rule that should be enforced.
 */
@Entity
@Table(name = "contractrule")
@SQLDelete(sql = "UPDATE contractrule SET deleted=true WHERE id=?")
@Where(clause = "deleted = false")
@Getter
@Setter(AccessLevel.PACKAGE)
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
public class ContractRule extends AbstractEntity {

    /**
     * Serial version uid.
     **/
    private static final long serialVersionUID = 1L;

    /**
     * The rule id on provider side.
     */
    @Convert(converter = UriConverter.class)
    @Column(length = URI_COLUMN_LENGTH)
    private URI remoteId;

    /**
     * The title of the rule.
     */
    private String title;

    /**
     * The definition of the rule.
     **/
    @Lob
    private String value;

    /**
     * The contracts in which this rule is used.
     */
    @ManyToMany(mappedBy = "rules")
    private List<Contract> contracts;
}
