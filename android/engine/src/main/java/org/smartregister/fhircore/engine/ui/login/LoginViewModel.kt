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

package org.smartregister.fhircore.engine.ui.login

import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.app.Application
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.sync.State
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.smartregister.fhircore.engine.auth.AuthCredentials
import org.smartregister.fhircore.engine.auth.AuthenticationService
import org.smartregister.fhircore.engine.configuration.app.ConfigurableApplication
import org.smartregister.fhircore.engine.configuration.view.LoginViewConfiguration
import org.smartregister.fhircore.engine.data.remote.model.response.OAuthResponse
import org.smartregister.fhircore.engine.data.remote.model.response.UserResponse
import org.smartregister.fhircore.engine.data.remote.shared.ResponseCallback
import org.smartregister.fhircore.engine.data.remote.shared.ResponseHandler
import org.smartregister.fhircore.engine.util.DefaultDispatcherProvider
import org.smartregister.fhircore.engine.util.DispatcherProvider
import org.smartregister.fhircore.engine.util.G6PD
import org.smartregister.fhircore.engine.util.SharedPreferencesHelper
import org.smartregister.fhircore.engine.util.USER_QUESTIONNAIRE_PUBLISHER_SHARED_PREFERENCE_KEY
import org.smartregister.fhircore.engine.util.USER_SHARED_PREFERENCE_KEY
import org.smartregister.fhircore.engine.util.extension.decodeJson
import retrofit2.Call
import retrofit2.Response
import timber.log.Timber

class LoginViewModel(
  application: Application,
  val authenticationService: AuthenticationService,
  loginViewConfiguration: LoginViewConfiguration,
  private val dispatcher: DispatcherProvider = DefaultDispatcherProvider
) : AndroidViewModel(application), AccountManagerCallback<Bundle> {

  val sharedSyncStatus = MutableSharedFlow<State>()

  private val accountManager = AccountManager.get(application)

  val responseBodyHandler =
    object : ResponseHandler<ResponseBody> {
      override fun handleResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
        response.body()?.run {
          storeUserPreferences(application, this)
          _showProgressBar.postValue(false)
        }
      }

      override fun handleFailure(call: Call<ResponseBody>, throwable: Throwable) {
        Timber.e(throwable)
        _loginError.postValue(throwable.localizedMessage)
        _showProgressBar.postValue(false)
      }
    }

  private fun storeUserPreferences(application: Application, responseBody: ResponseBody) {
    val responseBodyString = responseBody.string()
    Timber.i(responseBodyString)

    val sharedPreferences = SharedPreferencesHelper.init(application.applicationContext)
    sharedPreferences.write(USER_SHARED_PREFERENCE_KEY, responseBodyString)

    val userResponse = responseBodyString.decodeJson<UserResponse>()

    if (userResponse.realmAccess.roles.contains(G6PD)) {
      sharedPreferences.write(USER_QUESTIONNAIRE_PUBLISHER_SHARED_PREFERENCE_KEY, G6PD)
    }
  }

  private val userInfoResponseCallback: ResponseCallback<ResponseBody> by lazy {
    object : ResponseCallback<ResponseBody>(responseBodyHandler) {}
  }

  private val secureSharedPreference =
    (application as ConfigurableApplication).secureSharedPreference

  val oauthResponseHandler =
    object : ResponseHandler<OAuthResponse> {
      override fun handleResponse(call: Call<OAuthResponse>, response: Response<OAuthResponse>) {
        if (!response.isSuccessful) {
          val errorResponse = response.errorBody()?.string()
          _loginError.postValue(errorResponse?.decodeJson<LoginError>()?.errorDescription)
          Timber.e("Error fetching access token %s", errorResponse)
          if (attemptLocalLogin()) _navigateToHome.value = true
          _showProgressBar.postValue(false)
          return
        }
        with(authenticationService) {
          addAuthenticatedAccount(accountManager, response, username.value!!)
          secureSharedPreference.saveCredentials(
            AuthCredentials(username.value!!, password.value!!, response.body()?.accessToken!!)
          )
          getUserInfo().enqueue(userInfoResponseCallback)
          _navigateToHome.value = true
          _showProgressBar.postValue(false)
        }
      }

      override fun handleFailure(call: Call<OAuthResponse>, throwable: Throwable) {
        Timber.e(throwable.stackTraceToString())
        if (attemptLocalLogin()) {
          _navigateToHome.value = true
          return
        }
        _loginError.postValue(throwable.localizedMessage)
        _showProgressBar.postValue(false)
      }
    }

  private fun attemptLocalLogin(): Boolean {
    val (localUsername, localPassword) =
      secureSharedPreference.retrieveCredentials() ?: return false
    return (localUsername.contentEquals(username.value, ignoreCase = false) &&
      localPassword.contentEquals(password.value))
  }

  private val oauthResponseCallback: ResponseCallback<OAuthResponse> by lazy {
    object : ResponseCallback<OAuthResponse>(oauthResponseHandler) {}
  }

  private val _navigateToHome = MutableLiveData<Boolean>()
  val navigateToHome: LiveData<Boolean>
    get() = _navigateToHome

  private val _username = MutableLiveData<String>()
  val username: LiveData<String>
    get() = _username

  private val _password = MutableLiveData<String>()
  val password: LiveData<String>
    get() = _password

  private val _loginError = MutableLiveData<String>()
  val loginError: LiveData<String>
    get() = _loginError

  private val _showProgressBar = MutableLiveData(false)
  val showProgressBar
    get() = _showProgressBar

  private val _loginViewConfiguration = MutableLiveData(loginViewConfiguration)
  val loginViewConfiguration: LiveData<LoginViewConfiguration>
    get() = _loginViewConfiguration

  fun loginUser() {
    viewModelScope.launch(dispatcher.io()) {
      if (authenticationService.skipLogin() ||
          authenticationService.isSessionActive(secureSharedPreference.retrieveSessionToken())
      ) {
        _navigateToHome.postValue(true)
      } else {
        authenticationService.loadAccount(
          accountManager,
          secureSharedPreference.retrieveSessionUsername(),
          this@LoginViewModel
        )
      }
    }
  }

  fun updateViewConfigurations(registerViewConfiguration: LoginViewConfiguration) {
    _loginViewConfiguration.value = registerViewConfiguration
  }

  fun onUsernameUpdated(username: String) {
    _loginError.value = ""
    _username.value = username
  }

  fun onPasswordUpdated(password: String) {
    _loginError.value = ""
    _password.value = password
  }

  override fun run(future: AccountManagerFuture<Bundle>?) {
    val bundle = future?.result ?: bundleOf()
    bundle.getString(AccountManager.KEY_AUTHTOKEN)?.run {
      if (this.isNotEmpty() && authenticationService.isSessionActive(this)) {
        _navigateToHome.value = true
      }
    }
  }

  fun attemptRemoteLogin() {
    if (username.value != null && password.value != null) {
      _loginError.postValue("")
      _showProgressBar.postValue(true)
      authenticationService
        .fetchToken(username.value!!, password.value!!.toCharArray())
        .enqueue(oauthResponseCallback)
    }
  }
}
