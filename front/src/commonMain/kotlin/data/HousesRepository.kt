import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.error_create_house
import house_points.front.generated.resources.error_load_houses
import house_points.front.generated.resources.error_remove_house
import org.jetbrains.compose.resources.getString

/** Wraps `/api/houses`. `listActive` is public; create/deactivate require admin auth. */
class HousesRepository(private val client: HttpClient) {
    suspend fun listActive(): List<House> {
        val response = client.get("$API_BASE_URL/houses")
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_load_houses)))
        }
        return response.body()
    }

    suspend fun create(name: String): Int {
        val response = client.post("$API_BASE_URL/houses") {
            contentType(ContentType.Application.Json)
            setBody(CreateHouseRequest(name))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_create_house)))
        }
        return response.body<CreatedId>().id
    }

    suspend fun deactivate(houseId: Int) {
        val response = client.delete("$API_BASE_URL/houses/$houseId")
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_remove_house)))
        }
    }
}
