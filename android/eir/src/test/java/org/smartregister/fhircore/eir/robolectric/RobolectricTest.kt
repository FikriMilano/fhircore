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

package org.smartregister.fhircore.eir.robolectric

import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.mockk.clearAllMocks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.AfterClass
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.smartregister.fhircore.eir.shadow.SecureSharedPreferenceShadow

@RunWith(FhircoreTestRunner::class)
@Config(
  sdk = [Build.VERSION_CODES.O_MR1],
  shadows = [SecureSharedPreferenceShadow::class],
  application = EirTestApplication::class
)
abstract class RobolectricTest {
  /** Get the liveData value by observing but wait for 3 seconds if not ready then stop observing */
  @Throws(InterruptedException::class)
  fun <T> getLiveDataValue(liveData: LiveData<T>): T? {
    val data = arrayOfNulls<Any>(1)
    val latch = CountDownLatch(1)
    val observer: Observer<T> =
      object : Observer<T> {
        override fun onChanged(o: T?) {
          data[0] = o
          latch.countDown()
          liveData.removeObserver(this)
        }
      }
    liveData.observeForever(observer)
    latch.await(3, TimeUnit.SECONDS)
    return data[0] as T?
  }

  companion object {
    @AfterClass
    fun tearDown() {
      clearAllMocks()
    }
  }
}