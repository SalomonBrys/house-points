import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import house_points.front.generated.resources.Res
import house_points.front.generated.resources.error_create_user
import house_points.front.generated.resources.error_remove_user
import org.jetbrains.compose.resources.getString

/** Wraps `/api/users` — admin-only teacher/admin account management. */
class UsersRepository(private val client: HttpClient) {
    suspend fun create(username: String, password: String, role: String, displayName: String): Int {
        val response = client.post("$API_BASE_URL/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(username, password, role, displayName))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_create_user)))
        }
        return response.body<CreatedId>().id
    }

    suspend fun deactivate(userId: Int) {
        val response = client.delete("$API_BASE_URL/users/$userId")
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_remove_user)))
        }
    }
}
