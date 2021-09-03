/*
 * Copyright 2021 Ona Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smartregister.fhircore.anc.data.model

import androidx.compose.runtime.Stable
import org.hl7.fhir.r4.model.CarePlan
import java.util.*

@Stable
data class AncPatientItem(
    val patientIdentifier: String = "",
    val name: String = "",
    val gender: String = "",
    val age: String = "",
    val demographics: String = "",
    val atRisk: String = ""
)

@Stable
data class AncPatientDetailItem(
    val patientDetails: AncPatientItem,
    val patientDetailsHead: AncPatientItem,
)

@Stable
data class CarePlanItem(
    val title: String,
    val periodStartDate: Date
)
