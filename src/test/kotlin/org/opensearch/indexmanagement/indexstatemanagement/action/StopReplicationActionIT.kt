/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.indexmanagement.indexstatemanagement.action

import org.opensearch.indexmanagement.indexstatemanagement.IndexStateManagementRestTestCase
import org.opensearch.indexmanagement.indexstatemanagement.model.Policy
import org.opensearch.indexmanagement.indexstatemanagement.model.State
import org.opensearch.indexmanagement.indexstatemanagement.randomErrorNotification
import org.opensearch.indexmanagement.spi.indexstatemanagement.Step
import org.opensearch.indexmanagement.waitFor
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale

class StopReplicationActionIT : IndexStateManagementRestTestCase() {
    private val testIndexName = javaClass.simpleName.lowercase(Locale.ROOT)

    fun `test failure in stop_replication on a non-replicated index`() {
        val indexName = "${testIndexName}_index_1"
        val policyID = "${testIndexName}_testPolicyName_1"
        val actionConfig = StopReplicationAction(0)
        val states =
            listOf(
                State("StopReplicationState", listOf(actionConfig), listOf()),
            )

        val policy =
            Policy(
                id = policyID,
                description = "$testIndexName description",
                schemaVersion = 1L,
                lastUpdatedTime = Instant.now().truncatedTo(ChronoUnit.MILLIS),
                errorNotification = randomErrorNotification(),
                defaultState = states[0].name,
                states = states,
            )
        createPolicy(policy, policyID)
        createIndex(indexName, policyID)

        val managedIndexConfig = getExistingManagedIndexConfig(indexName)
        // Change the start time so the job will trigger in 2 seconds.
        updateManagedIndexConfigStartTime(managedIndexConfig)

        waitFor { assertEquals(policyID, getExplainManagedIndexMetaData(indexName).policyID) }

        // Need to wait two cycles.
        // Change the start time so the job will trigger in 2 seconds.
        updateManagedIndexConfigStartTime(managedIndexConfig)
        waitFor {
            // Expecting the step to fail as there's no replication in progress on this index
            assertEquals(Step.StepStatus.FAILED, getExplainManagedIndexMetaData(indexName).stepMetaData?.stepStatus)
            assertTrue(
                getExplainManagedIndexMetaData(indexName).info.toString().contains("cause=No replication in progress for index:" + indexName),
            )
        }
    }
}
