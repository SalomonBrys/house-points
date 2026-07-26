import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import team_points.front.generated.resources.Res
import team_points.front.generated.resources.error_create_team
import team_points.front.generated.resources.error_load_teams
import team_points.front.generated.resources.error_remove_team
import team_points.front.generated.resources.error_upload_team_image
import org.jetbrains.compose.resources.getString

/** Wraps `/api/teams`. `listActive` is public; create/deactivate require admin auth. */
class TeamsRepository(private val client: HttpClient) {
    suspend fun listActive(): List<Team> {
        val response = client.get("$API_BASE_URL/teams")
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_load_teams)))
        }
        return response.body()
    }

    suspend fun create(name: String): Int {
        val response = client.post("$API_BASE_URL/teams") {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_create_team)))
        }
        return response.body<CreatedId>().id
    }

    suspend fun deactivate(teamId: Int) {
        val response = client.delete("$API_BASE_URL/teams/$teamId")
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_remove_team)))
        }
    }

    suspend fun uploadImage(teamId: Int, image: PickedImage) {
        val response = client.post("$API_BASE_URL/teams/$teamId/image") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "image",
                            image.bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, image.mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"${image.fileName}\"")
                            },
                        )
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.errorMessage(getString(Res.string.error_upload_team_image)))
        }
    }
}
