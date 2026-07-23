import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.error_load_events
import house_points.front.generated.resources.error_poll_events
import house_points.front.generated.resources.error_record_points
import house_points.front.generated.resources.error_void_event
import org.jetbrains.compose.resources.getString

/** Wraps the `/api/events` endpoints (public history/polling) and the point-award/void endpoints (auth). */
class EventsRepository(private val client: HttpClient) {
    suspend fun listPaginated(
        pageSize: Int = 20,
        beforeId: Int? = null,
        teacherId: Int? = null,
        houseId: Int? = null,
    ): EventsPage {
        val response = client.get("$API_BASE_URL/events") {
            parameter("page_size", pageSize)
            beforeId?.let { parameter("before_id", it) }
            teacherId?.let { parameter("teacher_id", it) }
            houseId?.let { parameter("house_id", it) }
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_load_events)))
        }
        return response.body()
    }

    suspend fun listSince(sinceId: Int, pageSize: Int = 20): EventsSince {
        val response = client.get("$API_BASE_URL/events/since") {
            parameter("since_id", sinceId)
            parameter("page_size", pageSize)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_poll_events)))
        }
        return response.body()
    }

    suspend fun addPoints(houseId: Int, points: Int): Int {
        val response = client.post("$API_BASE_URL/houses/$houseId/points") {
            contentType(ContentType.Application.Json)
            setBody(AddPointsRequest(points))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_record_points)))
        }
        return response.body<CreatedId>().id
    }

    suspend fun deleteEvent(eventId: Int) {
        val response = client.delete("$API_BASE_URL/events/$eventId")
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_void_event)))
        }
    }
}
