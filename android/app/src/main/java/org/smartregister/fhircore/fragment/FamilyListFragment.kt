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

package org.smartregister.fhircore.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.ListAdapter
import org.smartregister.fhircore.R
import org.smartregister.fhircore.adapter.FamilyItemRecyclerViewAdapter
import org.smartregister.fhircore.domain.Pagination
import org.smartregister.fhircore.model.FamilyItem
import org.smartregister.fhircore.viewholder.FamilyItemViewHolder
import org.smartregister.fhircore.viewmodel.FamilyListViewModel

class FamilyListFragment : BaseListFragment<FamilyItem, FamilyItemViewHolder>() {
    internal val listViewModel by activityViewModels<FamilyListViewModel>()

    override fun getFragmentListLayout(): Int {
        return R.layout.family_fragment_list
    }

    override fun getFragmentListId(): Int {
        return R.id.family_list
    }

    override fun getEmptyListView(): Int {
        return R.id.empty_list_message_container
    }

    override fun buildAdapter(): ListAdapter<FamilyItem, FamilyItemViewHolder> {
        return FamilyItemRecyclerViewAdapter(this::onFamilyItemClicked)
    }

    private fun onFamilyItemClicked(familyItem: FamilyItem) {

    }

    override fun getObservableList(): MutableLiveData<Pair<List<FamilyItem>, Pagination>> {
        return listViewModel.paginatedDataList
    }

    override fun getObservableProgressBar(): Int {
        return R.id.loader_overlay
    }

    override fun loadData(currentSearch: String?, page: Int, pageSize: Int) {
        listViewModel.searchResults(currentSearch, page, pageSize)
    }
}
