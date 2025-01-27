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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.dataspaceconnector.util.ErrorMessages;
import io.dataspaceconnector.util.MetadataUtils;
import io.dataspaceconnector.util.Utils;
import org.springframework.stereotype.Component;

/**
 * Creates and updates a ContractRule.
 */
@Component
public class ContractRuleFactory implements AbstractFactory<ContractRule, ContractRuleDesc> {

    /**
     * The default remote id assigned to all contract rules.
     */
    public static final URI DEFAULT_REMOTE_ID = URI.create("genesis");

    /**
     * The default title assigned to all contract rules.
     */
    public static final String DEFAULT_TITLE = "";

    /**
     * The default rule assigned to all contract rules.
     */
    public static final String DEFAULT_RULE = "";

    /**
     * Create a new ContractRule.
     * @param desc The description of the new ContractRule.
     * @return The new ContractRule.
     * @throws IllegalArgumentException if desc is null.
     */
    @Override
    public ContractRule create(final ContractRuleDesc desc) {
        Utils.requireNonNull(desc, ErrorMessages.DESC_NULL);

        final var rule = new ContractRule();
        rule.setContracts(new ArrayList<>());

        update(rule, desc);

        return rule;
    }

    /**
     * Update a ContractRule.
     * @param contractRule The ContractRule to be updated.
     * @param desc         The new ContractRule description.
     * @return True if the ContractRule has been modified.
     * @throws IllegalArgumentException if any of the parameters is null.
     */
    @Override
    public boolean update(final ContractRule contractRule, final ContractRuleDesc desc) {
        Utils.requireNonNull(contractRule, ErrorMessages.ENTITY_NULL);
        Utils.requireNonNull(desc, ErrorMessages.DESC_NULL);

        final var hasUpdatedRemoteId = this.updateRemoteId(contractRule, desc.getRemoteId());
        final var hasUpdatedTitle = this.updateTitle(contractRule, desc.getTitle());
        final var hasUpdatedRule = this.updateRule(contractRule, desc.getValue());
        final var hasUpdatedAdditional = this.updateAdditional(contractRule, desc.getAdditional());

        return hasUpdatedRemoteId || hasUpdatedTitle || hasUpdatedRule || hasUpdatedAdditional;
    }

    private boolean updateRemoteId(final ContractRule contractRule, final URI remoteId) {
        final var newUri = MetadataUtils.updateUri(
                contractRule.getRemoteId(), remoteId, DEFAULT_REMOTE_ID);
        newUri.ifPresent(contractRule::setRemoteId);

        return newUri.isPresent();
    }

    private boolean updateTitle(final ContractRule contractRule, final String title) {
        final var newTitle =
                MetadataUtils.updateString(contractRule.getTitle(), title, DEFAULT_TITLE);
        newTitle.ifPresent(contractRule::setTitle);

        return newTitle.isPresent();
    }

    private boolean updateRule(final ContractRule contractRule, final String rule) {
        final var newRule = MetadataUtils.updateString(contractRule.getValue(), rule, DEFAULT_RULE);
        newRule.ifPresent(contractRule::setValue);

        return newRule.isPresent();
    }

    private boolean updateAdditional(
            final ContractRule contractRule, final Map<String, String> additional) {
        final var newAdditional = MetadataUtils.updateStringMap(
                contractRule.getAdditional(), additional, new HashMap<>());
        newAdditional.ifPresent(contractRule::setAdditional);

        return newAdditional.isPresent();
    }
}
