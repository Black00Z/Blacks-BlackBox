package com.onebitmonochrome.blacksbbox.view.gms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Switch
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import com.afollestad.materialdialogs.MaterialDialog
import com.onebitmonochrome.blacksbbox.R
import com.onebitmonochrome.blacksbbox.bean.GmsBean
import com.onebitmonochrome.blacksbbox.databinding.ActivityGmsBinding
import com.onebitmonochrome.blacksbbox.util.InjectionUtil
import com.onebitmonochrome.blacksbbox.util.inflate
import com.onebitmonochrome.blacksbbox.util.toast
import com.onebitmonochrome.blacksbbox.view.base.LoadingActivity


class GmsManagerActivity : LoadingActivity() {

    private lateinit var viewModel: GmsViewModel

    private lateinit var mAdapter: RVAdapter<GmsBean>

    private val viewBinding: ActivityGmsBinding by inflate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.gms_manager, true)
        initViewModel()

        initRecyclerView()
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, InjectionUtil.getGmsFactory())[GmsViewModel::class.java]
        showLoading()

        viewModel.mInstalledLiveData.observe(this) {
            hideLoading()
            mAdapter.setItems(it)
        }

        viewModel.mUpdateInstalledLiveData.observe(this) { result ->
            if (result == null) {
                return@observe
            }

            val items = mAdapter.getItems()
            for (index in items.indices) {
                val bean = items[index]
                if (bean.userID == result.userID) {
                    if (result.success) {
                        bean.isInstalledGms = result.isInstalledGms
                        bean.hasGmsTraces = result.hasGmsTraces
                    }
                    mAdapter.replaceAt( index,bean)
                    break
                }
            }

            hideLoading()

            if (result.success) {
                toast(result.msg)
            } else {
                MaterialDialog(this).show {
                    title(R.string.gms_manager)
                    message(text = result.msg)
                    positiveButton(R.string.done)
                }
            }
        }

        viewModel.getInstalledUser()
    }

    private fun initRecyclerView() {
        mAdapter = RVAdapter<GmsBean>(this, GmsAdapter { bean ->
            wipeGms(bean.userID)
        }).bind(viewBinding.recyclerView)
            .setItemClickListener { view, item, _ ->
                val checkbox = view.findViewById<Switch>(R.id.checkbox)
                if (item.isInstalledGms) {
                    uninstallGms(item.userID, checkbox)
                } else {
                    installGms(item.userID, checkbox)
                }
            }
        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)

    }

    private fun installGms(userID: Int, checkbox: Switch){
        MaterialDialog(this).show {
            title(R.string.enable_gms)
            message(text = getString(R.string.enable_gms_hint) + "\n\n" + getString(R.string.enable_gms_warning))
            positiveButton(R.string.done){
                showLoading()
                viewModel.installGms(userID)
            }
            negativeButton(R.string.cancel){
                checkbox.isChecked = !checkbox.isChecked
            }
        }
    }

    private fun uninstallGms(userID: Int, checkbox: Switch){
        MaterialDialog(this).show {
            title(R.string.disable_gms)
            message(R.string.disable_gms_hint)
            positiveButton(R.string.done){
                showLoading()
                viewModel.uninstallGms(userID)
            }
            negativeButton(R.string.cancel){
                checkbox.isChecked = !checkbox.isChecked
            }
        }
    }

    private fun wipeGms(userID: Int) {
        MaterialDialog(this).show {
            title(R.string.wipe_gms)
            message(R.string.wipe_gms_hint)
            positiveButton(R.string.done) {
                showLoading()
                viewModel.wipeGms(userID)
            }
            negativeButton(R.string.cancel)
        }
    }


    companion object{
        fun start(context: Context){
            val intent = Intent(context,GmsManagerActivity::class.java)
            context.startActivity(intent)
        }
    }
}