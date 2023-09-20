 package com.lagradost

 import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
 import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
 import com.lagradost.cloudstream3.R
 import com.lagradost.cloudstream3.app
 import com.lagradost.cloudstream3.syncproviders.AccountManager
 import com.lagradost.cloudstream3.syncproviders.AuthAPI
 import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
 import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager

 class FStreamApi(index: Int) : InAppAuthAPIManager(index) {
     override val name = "FStream"
     override val idPrefix = "fstream"
     override val icon = R.drawable.ic_baseline_extension_24
     override val requiresUsername = true
     override val requiresPassword = true
     override val requiresServer = false
     override val createAccountUrl = "https://alldebrid.com/register/"

     companion object {
         const val ALLDEBRID_USER_KEY: String = "alldebrid_user"
     }

     override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
         return getKey(accountId, ALLDEBRID_USER_KEY)
     }

     override fun loginInfo(): AuthAPI.LoginInfo? {
         val data = getLatestLoginData() ?: return null
         return AuthAPI.LoginInfo(name = data.username ?: data.server, accountIndex = accountIndex)
     }

     override suspend fun login(data: InAppAuthAPI.LoginData): Boolean {
         if (data.username.isNullOrBlank() || data.password.isNullOrBlank()) return false // we require a server
         try {
             val isValid = app.get("http://api.alldebrid.com/v4/user?agent=${data.username}&apikey=${data.password}").text.contains("\"status\": \"success\",")
             if(!isValid) return false
         } catch (e: Exception) {
             return false
         }

         switchToNewAccount()
         setKey(accountId, ALLDEBRID_USER_KEY, data)
         registerAccount()
         initialize()
         AccountManager.inAppAuths

         return true
     }

     override fun logOut() {
         removeAccountKeys()
         initializeData()
     }

     private fun initializeData() {
         val data = getLatestLoginData() ?: run {
             FStreamProvider.blackInkApiAppName = null
             FStreamProvider.blackInkApiKey = null
             return
         }
         FStreamProvider.blackInkApiAppName = data.username
         FStreamProvider.blackInkApiKey = data.password
     }

     override suspend fun initialize() {
         initializeData()
     }
 }
