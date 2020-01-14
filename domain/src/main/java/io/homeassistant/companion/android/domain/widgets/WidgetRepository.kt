package io.homeassistant.companion.android.domain.widgets

interface WidgetRepository {

    suspend fun saveServiceCallData(
        appWidgetId: Int,
        domainStr: String,
        serviceStr: String,
        serviceDataStr: String
    )

    suspend fun loadDomain(appWidgetId: Int): String?
    suspend fun loadService(appWidgetId: Int): String?
    suspend fun loadServiceData(appWidgetId: Int): String?

    suspend fun loadLabel(appWidgetId: Int): String?
    suspend fun saveLabel(appWidgetId: Int, data: String?)

    suspend fun deleteWidgetData(appWidgetId: Int)

    suspend fun saveStringPref(key: String, data: String?)
    suspend fun loadStringPref(key: String): String?
}
