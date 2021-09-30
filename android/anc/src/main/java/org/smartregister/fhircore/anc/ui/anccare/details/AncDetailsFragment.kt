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

package org.smartregister.fhircore.anc.ui.anccare.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.IParser
import com.google.android.fhir.FhirEngine
import kotlinx.android.synthetic.main.fragment_anc_details.*
import org.json.JSONObject
import org.smartregister.fhircore.anc.AncApplication
import org.smartregister.fhircore.anc.R
import org.smartregister.fhircore.anc.data.anc.AncPatientRepository
import org.smartregister.fhircore.anc.data.anc.model.AncOverviewItem
import org.smartregister.fhircore.anc.data.anc.model.AncPatientDetailItem
import org.smartregister.fhircore.anc.data.anc.model.CarePlanItem
import org.smartregister.fhircore.anc.data.anc.model.UpcomingServiceItem
import org.smartregister.fhircore.anc.databinding.FragmentAncDetailsBinding
import org.smartregister.fhircore.engine.cql.LibraryEvaluator
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceDataSource
import org.smartregister.fhircore.engine.data.remote.fhir.resource.FhirResourceService
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.util.FileUtil
import org.smartregister.fhircore.engine.util.extension.createFactory
import timber.log.Timber

class AncDetailsFragment : Fragment() {

  lateinit var patientId: String
  private lateinit var fhirEngine: FhirEngine

  lateinit var ancDetailsViewModel: AncDetailsViewModel

  private lateinit var ancPatientRepository: AncPatientRepository

  private var carePlanAdapter = CarePlanAdapter()

  private val upcomingServicesAdapter = UpcomingServicesAdapter()

  private val lastSeen = UpcomingServicesAdapter()

  lateinit var binding: FragmentAncDetailsBinding

  lateinit var parser: IParser

  lateinit var fhirResourceService: FhirResourceService

  lateinit var fhirResourceDataSource: FhirResourceDataSource

  lateinit var libraryEvaluator: LibraryEvaluator

  lateinit var fileUtil: FileUtil

  var libraryData = ""
  var helperData = ""
  var valueSetData = ""
  var testData = ""
  val evaluatorId = "ANCRecommendationA2"
  val contextCQL = "patient"
  val contextLabel = "mom-with-anemia"

  var CQL_BASE_URL = ""
  var LIBRARY_URL = ""
  var HELPER_URL = ""
  var VALUE_SET_URL = ""
  var PATIENT_URL = ""
  var CQL_CONFIG_FILE_NAME = "configs/cql_configs.properties"

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = DataBindingUtil.inflate(inflater, R.layout.fragment_anc_details, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    patientId = arguments?.getString(QuestionnaireActivity.QUESTIONNAIRE_ARG_PATIENT_KEY) ?: ""
    libraryEvaluator = LibraryEvaluator()
    parser = FhirContext.forR4().newJsonParser()
    fhirResourceService =
      FhirResourceService.create(
        parser,
        activity?.applicationContext!!,
        AncApplication.getContext().applicationConfiguration
      )

    fhirResourceDataSource = FhirResourceDataSource(fhirResourceService)

    fhirEngine = AncApplication.getContext().fhirEngine

    setupViews()

    ancPatientRepository = getAncPatientRepository()

    ancDetailsViewModel =
      ViewModelProvider(
        this,
        AncDetailsViewModel(ancPatientRepository, patientId = patientId).createFactory()
      )[AncDetailsViewModel::class.java]

    binding.txtViewPatientId.text = patientId

    Timber.d(patientId)

    ancDetailsViewModel
      .fetchDemographics()
      .observe(viewLifecycleOwner, this::handlePatientDemographics)

    ancDetailsViewModel.fetchCarePlan().observe(viewLifecycleOwner, this::handleCarePlan)

    ancDetailsViewModel.fetchObservation().observe(viewLifecycleOwner, this::handleObservation)

    ancDetailsViewModel
      .fetchUpcomingServices()
      .observe(viewLifecycleOwner, this::handleUpcomingServices)
    ancDetailsViewModel.fetchCarePlan().observe(viewLifecycleOwner, this::handleCarePlan)

    fileUtil = FileUtil()
    CQL_BASE_URL =
      context?.let { fileUtil.getProperty("smart_register_base_url", it, CQL_CONFIG_FILE_NAME) }!!
    LIBRARY_URL =
      CQL_BASE_URL +
        context?.let { fileUtil.getProperty("cql_library_url", it, CQL_CONFIG_FILE_NAME) }
    HELPER_URL =
      CQL_BASE_URL +
        context?.let { fileUtil.getProperty("cql_helper_library_url", it, CQL_CONFIG_FILE_NAME) }
    VALUE_SET_URL =
      CQL_BASE_URL +
        context?.let { fileUtil.getProperty("cql_value_set_url", it, CQL_CONFIG_FILE_NAME) }
    PATIENT_URL =
      CQL_BASE_URL +
        context?.let { fileUtil.getProperty("cql_patient_url", it, CQL_CONFIG_FILE_NAME) }

    showCQLCard()

    ancDetailsViewModel.fetchLastSeen().observe(viewLifecycleOwner, this::handleLastSeen)
  }

  private fun handleObservation(ancOverviewItem: AncOverviewItem) {
    binding.txtViewEDDDoseDate.text = ancOverviewItem.EDD
    binding.txtViewGAPeriod.text = ancOverviewItem.GA
    binding.txtViewFetusesCount.text = ancOverviewItem.noOfFetusses
    binding.txtViewRiskValue.text = ancOverviewItem.risk
  }

  private fun handleUpcomingServices(listEncounters: List<UpcomingServiceItem>) {
    when {
      listEncounters.isEmpty() -> {
        binding.txtViewNoUpcomingServices.visibility = View.VISIBLE
        binding.upcomingServicesListView.visibility = View.GONE
        binding.txtViewUpcomingServicesSeeAllHeading.visibility = View.GONE
        binding.imageViewUpcomingServicesSeeAllArrow.visibility = View.GONE
      }
      else -> {
        binding.txtViewNoUpcomingServices.visibility = View.GONE
        binding.upcomingServicesListView.visibility = View.VISIBLE
        binding.txtViewUpcomingServicesSeeAllHeading.visibility = View.VISIBLE
        binding.txtViewUpcomingServicesSeeAllHeading.visibility = View.VISIBLE
        populateUpcomingServicesList(listEncounters)
      }
    }
  }

  private fun handleLastSeen(listEncounters: List<UpcomingServiceItem>) {
    when {
      listEncounters.isEmpty() -> {
        binding.txtViewNoLastSeenServices.visibility = View.VISIBLE
        binding.lastSeenListView.visibility = View.GONE
      }
      else -> {
        binding.txtViewNoLastSeenServices.visibility = View.GONE
        binding.lastSeenListView.visibility = View.VISIBLE
        populateLastSeenList(listEncounters)
      }
    }
  }

  private fun setupViews() {
    binding.carePlanListView.apply {
      adapter = carePlanAdapter
      layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
    }

    binding.upcomingServicesListView.apply {
      adapter = upcomingServicesAdapter
      layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
    }

    binding.lastSeenListView.apply {
      adapter = lastSeen
      layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
    }
  }

  companion object {
    fun newInstance(bundle: Bundle = Bundle()) = AncDetailsFragment().apply { arguments = bundle }
  }

  private fun handlePatientDemographics(patient: AncPatientDetailItem) {
    with(patient) {
      val patientDetails =
        this.patientDetails.name +
          ", " +
          this.patientDetails.gender +
          ", " +
          this.patientDetails.age
      val patientId =
        this.patientDetailsHead.demographics + " ID: " + this.patientDetails.patientIdentifier
      binding.txtViewPatientDetails.text = patientDetails
      binding.txtViewPatientId.text = patientId
    }
  }

  private fun handleCarePlan(immunizations: List<CarePlanItem>) {
    when {
      immunizations.isEmpty() -> {
        binding.txtViewNoCarePlan.visibility = View.VISIBLE
        binding.txtViewCarePlanSeeAllHeading.visibility = View.GONE
        binding.imageViewSeeAllArrow.visibility = View.GONE
        binding.carePlanListView.visibility = View.GONE
      }
      else -> {
        binding.txtViewNoCarePlan.visibility = View.GONE
        binding.txtViewCarePlanSeeAllHeading.visibility = View.VISIBLE
        binding.imageViewSeeAllArrow.visibility = View.VISIBLE
        binding.carePlanListView.visibility = View.VISIBLE
        populateImmunizationList(immunizations)
      }
    }
  }

  private fun populateImmunizationList(listCarePlan: List<CarePlanItem>) {
    carePlanAdapter.submitList(listCarePlan)
  }

  private fun populateUpcomingServicesList(upcomingServiceItem: List<UpcomingServiceItem>) {
    upcomingServicesAdapter.submitList(upcomingServiceItem)
  }

  private fun populateLastSeenList(upcomingServiceItem: List<UpcomingServiceItem>) {
    lastSeen.submitList(upcomingServiceItem)
  }

  fun buttonCQLSetOnClickListener() {
    button_CQLEvaluate.setOnClickListener { loadCQLLibraryData() }
  }

  fun loadCQLLibraryData() {
    ancDetailsViewModel
      .fetchCQLLibraryData(parser, fhirResourceDataSource, LIBRARY_URL)
      .observe(viewLifecycleOwner, this::handleCQLLibraryData)
  }

  fun loadCQLHelperData() {
    ancDetailsViewModel
      .fetchCQLFhirHelperData(parser, fhirResourceDataSource, HELPER_URL)
      .observe(viewLifecycleOwner, this::handleCQLHelperData)
  }

  fun loadCQLValueSetData() {
    ancDetailsViewModel
      .fetchCQLValueSetData(parser, fhirResourceDataSource, VALUE_SET_URL)
      .observe(viewLifecycleOwner, this::handleCQLValueSetData)
  }

  fun loadCQLPatientData() {
    ancDetailsViewModel
      .fetchCQLPatientData(parser, fhirResourceDataSource, "$PATIENT_URL$patientId/\$everything")
      .observe(viewLifecycleOwner, this::handleCQLPatientData)
  }

  fun handleCQLLibraryData(auxLibraryData: String) {
    libraryData = auxLibraryData
    loadCQLHelperData()
  }

  fun handleCQLHelperData(auxHelperData: String) {
    helperData = auxHelperData
    loadCQLValueSetData()
  }

  fun handleCQLValueSetData(auxValueSetData: String) {
    valueSetData = auxValueSetData
    loadCQLPatientData()
  }

  fun handleCQLPatientData(auxPatientData: String) {
    testData = libraryEvaluator.processCQLPatientBundle(auxPatientData)
    val parameters =
      libraryEvaluator.runCql(
        libraryData,
        helperData,
        valueSetData,
        testData,
        evaluatorId,
        contextCQL,
        contextLabel
      )
    val jsonObject = JSONObject(parameters)
    textView_CQLResults.text = jsonObject.toString(4)
    textView_CQLResults.visibility = View.VISIBLE
  }

  val ANC_TEST_PATIENT_ID = "e8725b4c-6db0-4158-a24d-50a5ddf1c2ed"
  fun showCQLCard() {
    if (patientId == ANC_TEST_PATIENT_ID) {
      textView_EvaluateCQLHeader.visibility = View.VISIBLE
      cardView_CQLSection.visibility = View.VISIBLE
      buttonCQLSetOnClickListener()
    }
  }

  fun getAncPatientRepository(): AncPatientRepository {
    return AncPatientRepository(
      (requireActivity().application as AncApplication).fhirEngine,
      AncPatientItemMapper
    )
  }
}
