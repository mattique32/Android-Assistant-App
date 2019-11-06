package io.homeassistant.companion.android.data

import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

class HomeAssistantRetrofit(url: String) {

    val retrofit: Retrofit = Retrofit
        .Builder()
        .addConverterFactory(JacksonConverterFactory.create())
        .baseUrl(url)
        .build()

}
