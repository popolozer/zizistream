package com.lagradost

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.lagradost.cloudstream3.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.AcraApplication.Companion.openBrowser
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.ui.settings.SettingsAccount.Companion.showLoginInfo
import com.lagradost.cloudstream3.ui.settings.SettingsAccount.Companion.addAccount
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute


class FStreamSettingsFragment(private val plugin: Plugin, val fstreamApi: FStreamApi) :
    BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val id = plugin.resources!!.getIdentifier("fstream_settings", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        val layout = plugin.resources!!.getLayout(id)
        return inflater.inflate(layout, container, false)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = plugin.resources!!.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return this.findViewById(id)
    }

    private fun getDrawable(name: String): Drawable? {
        val id = plugin.resources!!.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return ResourcesCompat.getDrawable(plugin.resources!!, id, null)
    }

    private fun getString(name: String): String? {
        val id = plugin.resources!!.getIdentifier(name, "string", BuildConfig.LIBRARY_PACKAGE_NAME)
        return plugin.resources!!.getString(id)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val infoView = view.findView<LinearLayout>("fstream_info")
        val infoTextView = view.findView<TextView>("info_main_text")
        val infoSubTextView = view.findView<TextView>("info_sub_text")
        val infoImageView = view.findView<ImageView>("fstream_info_imageview")
        val loginView = view.findView<LinearLayout>("fstream_login")
        val loginTextView = view.findView<TextView>("main_text")
        val loginImageView = view.findView<ImageView>("fstream_login_imageview")


        infoTextView.text = getString("alldebrid_info_title") ?: "Alldebrid"
        infoSubTextView.text = getString("alldebrid_info_summary") ?: ""
        infoImageView.setImageDrawable(getDrawable("fstream_logo"))
        infoImageView.imageTintList =
            ColorStateList.valueOf(view.context.colorFromAttribute(R.attr.white))


        loginImageView.setImageDrawable(getDrawable("fstream_logo"))
        loginImageView.imageTintList =
            ColorStateList.valueOf(view.context.colorFromAttribute(R.attr.white))

        // object : View.OnClickListener is required to make it compile because otherwise it used invoke-customs
        infoView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                openBrowser(fstreamApi.createAccountUrl)
            }
        })


        loginTextView.text = getString("alldebrid_account_title")


        loginView.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val info = fstreamApi.loginInfo()
                if (info != null) {
                    showLoginInfo(activity, fstreamApi, info)
                } else {
                    addAccount(activity, fstreamApi)
                }
            }
        })
    }
}