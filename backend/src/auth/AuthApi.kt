package backend.auth

import api.*
import backend.api.ApiException
import backend.data.MallCatalog
import backend.db.MallRepository
import api.DtoMapper.toDomain
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val JWT_AUTH = "jwt"

private val USERNAME_RE = Regex("^[A-Za-z0-9_.-]{3,32}$")
private const val MIN_PASSWORD = 8
private const val MAX_PASSWORD = 128

fun Application.installAuth(jwt: JwtService, users: UserRepository) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "hackyeah"
            verifier(jwt.verifier)
            validate { cred ->
                // A token for a user deleted from the DB stops working
                val name = cred.payload.getClaim("username").asString() ?: return@validate null
                if (users.findByUsername(name) != null) JWTPrincipal(cred.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorDto("unauthorized", "Valid Bearer token required"))
            }
        }
    }
}

fun Route.authApi(users: UserRepository, jwt: JwtService) {
    route("${ApiPaths.PREFIX}/auth") {
        post("register") {
            val req = call.receive<CredentialsRequest>()
            validate(req)
            val user = users.create(req.username, PasswordHasher.hash(req.password))
                ?: throw ApiException(HttpStatusCode.Conflict, "username_taken", "Username is already taken")
            call.respond(HttpStatusCode.Created, tokenFor(user, jwt))
        }
        post("login") {
            val req = call.receive<CredentialsRequest>()
            val user = users.findByUsername(req.username)
            // Hash a dummy when the user is missing so timing does not reveal which usernames exist
            val ok = PasswordHasher.verify(req.password, user?.passwordHash ?: DUMMY_HASH)
            println(ok)
            println(user)
            if (user == null || !ok) {
                throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid username or password")
            }
            call.respond(tokenFor(user, jwt))
        }
        authenticate(JWT_AUTH) {
            get("me") {
                val p = call.principal<JWTPrincipal>()!!
                call.respond(p.toUserDto())
            }
        }
    }
}

/** Gallery management. Requires a valid token with role ADMIN. */
fun Route.adminApi(catalog: MallCatalog, repository: MallRepository) {
    authenticate(JWT_AUTH) {
        route(ApiPaths.ADMIN_MALLS) {
            // Creates a gallery; the server assigns the mall id and ids of stores sent with id <= 0
            post {
                requireAdmin(call)
                val dto = call.receive<MallDto>()
                val id = repository.nextMallId()
                saveMall(repository, dto.copy(id = id))
                catalog.reload(repository)
                call.respond(HttpStatusCode.Created, mapOf("id" to id))
            }
            put("{mallId}") {
                requireAdmin(call)
                val id = pathId(call)
                val dto = call.receive<MallDto>()
                if (dto.id != id) {
                    throw ApiException(HttpStatusCode.BadRequest, "bad_request", "Body id ${dto.id} does not match path id $id")
                }
                val existed = catalog.find(id) != null
                saveMall(repository, dto)
                catalog.reload(repository)
                call.respond(if (existed) HttpStatusCode.OK else HttpStatusCode.Created, mapOf("id" to id))
            }
            delete("{mallId}") {
                requireAdmin(call)
                val id = pathId(call)
                if (!repository.delete(id)) {
                    throw ApiException(HttpStatusCode.NotFound, "mall_not_found", "Mall '$id' not found")
                }
                catalog.reload(repository)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun pathId(call: ApplicationCall): Long =
    call.parameters["mallId"]?.toLongOrNull()
        ?: throw ApiException(HttpStatusCode.BadRequest, "bad_request", "Invalid mall id")

private fun bad(message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, "bad_request", message)

private fun validateMall(dto: MallDto) {
    if (dto.name.isBlank()) bad("Mall name must not be blank")
    if (dto.sizeX <= 0 || dto.sizeY <= 0) bad("Mall size must be positive")
    if (dto.upperLeft.latitude <= dto.downRight.latitude || dto.upperLeft.longitude >= dto.downRight.longitude) {
        bad("upperLeft must be north-west of downRight")
    }
    if (dto.floors.isEmpty()) bad("A mall needs at least one floor")
    if (dto.floors.map { it.number }.distinct().size != dto.floors.size) bad("Floor numbers must be unique")
    fun noShape(svg: String?, polygon: List<PointDto>) = svg.isNullOrBlank() && polygon.size < 3
    if (dto.floors.any { f -> noShape(f.outlineSvg, f.outline) || f.stores.any { noShape(it.outlineSvg, it.outline) } }) {
        bad("Every floor and store outline needs at least 3 points")
    }
    if (dto.floors.any { f -> f.stores.any { it.name.isBlank() } }) bad("Store name must not be blank")
}

/** Validates, assigns ids to new stores (id <= 0) and stores the mall in one transaction. */
private fun saveMall(repository: MallRepository, dto: MallDto) {
    validateMall(dto)
    val mall = runCatching { dto.toDomain() }.getOrElse { bad("Invalid shape: ${it.message}") }
    var nextStoreId = repository.nextStoreInstanceId()
    val withIds = mall.copy(floors = mall.floors.map { floor ->
        floor.copy(stores = floor.stores.map { store ->
            if (store.Instanceid > 0) store else {
                val id = nextStoreId++
                store.copy(Instanceid = id, Shopid = if (store.Shopid > 0) store.Shopid else id)
            }
        })
    })
    repository.save(withIds)
}

private fun requireAdmin(call: ApplicationCall) {
    val role = call.principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
    if (role != "ADMIN") throw ApiException(HttpStatusCode.Forbidden, "forbidden", "ADMIN role required")
}

private fun validate(req: CredentialsRequest) {
    if (!USERNAME_RE.matches(req.username)) {
        throw ApiException(HttpStatusCode.BadRequest, "bad_request", "Username must be 3-32 chars of letters, digits, '_', '.', '-'")
    }
    if (req.password.length !in MIN_PASSWORD..MAX_PASSWORD) {
        throw ApiException(HttpStatusCode.BadRequest, "bad_request", "Password must be $MIN_PASSWORD-$MAX_PASSWORD characters")
    }
}

private fun tokenFor(user: User, jwt: JwtService) =
    TokenResponse(jwt.issue(user), jwt.expiresInSeconds, UserDto(user.id, user.username, user.role))

private fun JWTPrincipal.toUserDto() = UserDto(
    id = payload.subject.toLong(),
    username = payload.getClaim("username").asString(),
    role = payload.getClaim("role").asString(),
)

private val DUMMY_HASH = PasswordHasher.hash("dummy-password-for-timing")

/** Creates the first administrator from ADMIN_USERNAME / ADMIN_PASSWORD if it does not exist yet. */
fun bootstrapAdmin(users: UserRepository, username: String?, password: String?) {
    if (username.isNullOrBlank() || password.isNullOrBlank()) return
    require(USERNAME_RE.matches(username) && password.length >= MIN_PASSWORD) {
        "ADMIN_USERNAME / ADMIN_PASSWORD do not meet the username/password rules"
    }
    if (users.findByUsername(username) == null) {
        users.create(username, PasswordHasher.hash(password), "ADMIN")
        println("Created admin user '$username'")
    }
}
