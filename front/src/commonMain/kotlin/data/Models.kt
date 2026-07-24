import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed request/response DTOs for the House Points API. Field names follow
 * the backend's snake_case JSON via [SerialName]; see `back/ARCHITECTURE.md`
 * and `SPECS.md §5` for the authoritative contract.
 */

@Serializable
data class House(
    val id: Int,
    val name: String,
    @SerialName("total_points") val totalPoints: Int,
)

@Serializable
data class Teacher(
    val id: Int,
    val username: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class PointEvent(
    val id: Int,
    @SerialName("house_id") val houseId: Int,
    @SerialName("teacher_id") val teacherId: Int,
    val points: Int,
    // Always AI-generated server-side (CommentGenerator); hp_point_events.comment is NOT NULL.
    val comment: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class EventsPage(
    val events: List<PointEvent>,
    @SerialName("next_id") val nextId: Int? = null,
)

@Serializable
data class EventsSince(
    val events: List<PointEvent>,
    @SerialName("last_id") val lastId: Int? = null,
)

@Serializable
data class TokenPair(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class LogoutRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class AddPointsRequest(val points: Int)

@Serializable
data class CreateHouseRequest(val name: String)

@Serializable
data class CreateUserRequest(
    val username: String,
    val password: String,
    val role: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class UpdateDisplayNameRequest(@SerialName("display_name") val displayName: String)

/** Response of `PATCH /api/me/display-name`: a fresh access token only — no refresh token rotation. */
@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class CreatedId(val id: Int)

@Serializable
data class ApiError(val error: String)
