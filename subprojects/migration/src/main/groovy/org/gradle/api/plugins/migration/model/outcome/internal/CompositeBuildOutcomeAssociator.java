/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.migration.model.outcome.internal;

import java.util.Arrays;
import java.util.List;

public class CompositeBuildOutcomeAssociator implements BuildOutcomeAssociator {

    private final List<BuildOutcomeAssociator> associators;

    public CompositeBuildOutcomeAssociator(BuildOutcomeAssociator... associators) {
        this.associators = Arrays.asList(associators);
    }

    public Class<? extends BuildOutcome> findAssociationType(BuildOutcome from, BuildOutcome to) {
        for (BuildOutcomeAssociator associator : associators) {
            Class<? extends BuildOutcome> outcomeType = associator.findAssociationType(from, to);
            if (outcomeType != null) {
                return outcomeType;
            }
        }

        return null;
    }
}
