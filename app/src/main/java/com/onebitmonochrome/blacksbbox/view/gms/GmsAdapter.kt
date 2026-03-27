package com.onebitmonochrome.blacksbbox.view.gms

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.bean.GmsBean
import com.onebitmonochrome.blacksbbox.databinding.ItemGmsBinding


class GmsAdapter(
    private val onWipeClicked: (GmsBean) -> Unit,
) : RVHolderFactory() {

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return GmsVH(inflate(R.layout.item_gms, parent), onWipeClicked)
    }


    class GmsVH(
        itemView: View,
        private val onWipeClicked: (GmsBean) -> Unit,
    ) : RVHolder<GmsBean>(itemView) {

        private val binding = ItemGmsBinding.bind(itemView)
        override fun setContent(item: GmsBean, isSelected: Boolean, payload: Any?) {
            binding.tvTitle.text = item.userName
            binding.checkbox.isChecked = item.isInstalledGms
            binding.btnWipe.isVisible = item.hasGmsTraces
            binding.btnWipe.setOnClickListener {
                onWipeClicked(item)
            }
            binding.checkbox.setOnCheckedChangeListener  { buttonView, _ ->
                if(buttonView.isPressed){
                    binding.root.performClick()
                }
            }
        }
    }
}